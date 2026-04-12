package com.family.map

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.family.map.data.FirebaseRepository
import com.family.map.data.PrefsRepository
import com.family.map.model.Member
import com.family.map.service.LocationService
import com.family.map.ui.history.HistoryScreen
import com.family.map.ui.map.MapScreen
import com.family.map.ui.setup.SetupScreen

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PrefsRepository

    // パーミッションリクエストランチャー
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            startLocationService()
        } else {
            showPermissionRationale()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知は任意 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsRepository(this)

        // 7日以上前の履歴をクリーンアップ
        if (prefs.isSetupComplete()) {
            FirebaseRepository().cleanupOldHistory(prefs.roomCode, prefs.userId)
        }

        // 通知パーミッション（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            FamilyMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(prefs)
                }
            }
        }
    }

    @Composable
    private fun AppNavigation(prefs: PrefsRepository) {
        val navController = rememberNavController()
        var members by remember { mutableStateOf<List<Member>>(emptyList()) }

        val startDestination = if (prefs.isSetupComplete()) "map" else "setup"

        NavHost(navController = navController, startDestination = startDestination) {

            // ── セットアップ画面 ──
            composable("setup") {
                SetupScreen(
                    onCreateRoom = { name, color, roomCode ->
                        prefs.saveSetup(name, color, roomCode)
                        requestLocationPermissionAndStart()
                        navController.navigate("map") {
                            popUpTo("setup") { inclusive = true }
                        }
                    },
                    onJoinRoom = { name, color, roomCode ->
                        prefs.saveSetup(name, color, roomCode)
                        requestLocationPermissionAndStart()
                        navController.navigate("map") {
                            popUpTo("setup") { inclusive = true }
                        }
                    }
                )
            }

            // ── マップ画面 ──
            composable("map") {
                MapScreen(
                    myUserId = prefs.userId,
                    roomCode = prefs.roomCode,
                    onOpenHistory = {
                        navController.navigate("history")
                    }
                )
                // マップ画面表示時に位置情報サービス開始
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    requestLocationPermissionAndStart()
                }
            }

            // ── 履歴画面 ──
            composable("history") {
                HistoryScreen(
                    roomCode = prefs.roomCode,
                    members = members,
                    myUserId = prefs.userId,
                    onBack = { navController.popBackStack() }
                )
                // 履歴画面用にメンバーリストを取得
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    try {
                        members = FirebaseRepository().getMembers(prefs.roomCode)
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────
    // 位置情報パーミッション
    // ──────────────────────────────────────────────────────

    private fun requestLocationPermissionAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Android 10+ ではバックグラウンド位置情報も要求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startLocationService()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationService() {
        val intent = LocationService.startIntent(this)
        startForegroundService(intent)
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("位置情報の許可が必要です")
            .setMessage("家族マップは位置情報を使用して家族の現在地を表示します。設定から「常に許可」を選択してください。")
            .setPositiveButton("設定を開く") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Compose テーマ
// ──────────────────────────────────────────────────────────────────────────────

private val LightColors = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF1565C0),
    background = Color(0xFFFAFAFA),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

@Composable
fun FamilyMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
