package com.family.map.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.family.map.MainActivity
import com.family.map.R
import com.family.map.data.FirebaseRepository
import com.family.map.data.LocationRepository
import com.family.map.data.PrefsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 位置情報を取得してFirebaseに送信し続けるフォアグラウンドサービス
 *
 * - 30秒ごとに位置情報を取得
 * - Firebase の members と history に書き込む
 * - 常駐通知（Android 要件）を表示する
 */
class LocationService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "family_map_location"

        const val ACTION_START = "com.family.map.START_LOCATION"
        const val ACTION_STOP = "com.family.map.STOP_LOCATION"

        fun startIntent(context: Context) =
            Intent(context, LocationService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context) =
            Intent(context, LocationService::class.java).apply { action = ACTION_STOP }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: PrefsRepository
    private lateinit var locationRepo: LocationRepository
    private lateinit var firebaseRepo: FirebaseRepository

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsRepository(this)
        locationRepo = LocationRepository(this)
        firebaseRepo = FirebaseRepository()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startLocationUpdates()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ──────────────────────────────────────────────────────
    // 位置情報の更新ループ
    // ──────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        serviceScope.launch {
            try {
                locationRepo.locationFlow(intervalMs = 30_000L).collect { location ->
                    val userId = prefs.userId
                    val roomCode = prefs.roomCode
                    val name = prefs.name
                    val color = prefs.color
                    val lat = location.latitude
                    val lng = location.longitude
                    val ts = System.currentTimeMillis()

                    if (roomCode.isBlank() || name.isBlank()) return@collect

                    // members ノードを更新
                    runCatching {
                        firebaseRepo.updateMyLocation(roomCode, userId, name, color, lat, lng, ts)
                    }

                    // history ノードに書き込む
                    runCatching {
                        firebaseRepo.saveHistory(roomCode, userId, lat, lng, ts)
                    }
                }
            } catch (e: Exception) {
                // 位置情報取得失敗時はサービスを継続（次回のコールで再試行）
            }
        }
    }

    // ──────────────────────────────────────────────────────
    // 通知
    // ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_text)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopIntent
            )
            .build()
    }
}
