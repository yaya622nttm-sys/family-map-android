package com.family.map.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.map.data.FirebaseRepository
import com.family.map.model.Member
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * メインマップ画面
 * - Googleマップに全メンバーのカスタムピンを表示
 * - 4秒ごとにFirebaseをポーリングして更新
 * - 下部チップでメンバーを選択してカメラ移動
 */
@Composable
fun MapScreen(
    myUserId: String,
    roomCode: String,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseRepo = remember { FirebaseRepository() }

    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(35.6812, 139.7671), // デフォルト：東京
            12f
        )
    }

    // ── 4秒ごとのポーリング ──
    LaunchedEffect(roomCode) {
        while (isActive) {
            try {
                val fetched = firebaseRepo.getMembers(roomCode)
                members = fetched

                // 初回取得時に自分のピンにカメラを移動
                if (members.isNotEmpty() && cameraPositionState.position.zoom == 12f) {
                    val me = members.find { it.userId == myUserId }
                    if (me != null && me.lat != 0.0 && me.lng != 0.0) {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(me.lat, me.lng), 15f)
                        )
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "接続を確認してください", Toast.LENGTH_SHORT).show()
            }
            delay(4_000L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Google マップ ──
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false
            )
        ) {
            members.forEach { member ->
                if (member.lat == 0.0 && member.lng == 0.0) return@forEach
                val pos = LatLng(member.lat, member.lng)
                val alpha = if (member.isStale()) 0.4f else 1.0f
                Marker(
                    state = MarkerState(position = pos),
                    title = member.name,
                    snippet = if (member.isStale()) "5分以上更新なし" else null,
                    icon = createBubbleMarker(context, member.color, member.name.take(1)),
                    alpha = alpha
                )
            }
        }

        // ── 上部オーバーレイ ──
        TopOverlay(
            roomCode = roomCode,
            context = context
        )

        // ── 履歴ボタン ──
        FloatingActionButton(
            onClick = onOpenHistory,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 100.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Icon(Icons.Default.History, contentDescription = "履歴")
        }

        // ── 下部メンバーチップ ──
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 24.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(members) { member ->
                MemberChip(
                    member = member,
                    isMe = member.userId == myUserId,
                    onClick = {
                        if (member.lat != 0.0 && member.lng != 0.0) {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(member.lat, member.lng), 16f
                                    )
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 上部オーバーレイ（アプリ名・メンバー人数・LIVEバッジ）
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopOverlay(
    roomCode: String,
    context: Context
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        Color(0xE6FF8C42),
                        Color(0xE6FFD166)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📍 家族マップ",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ルームコード", roomCode))
                    Toast.makeText(context, "ルームコード「$roomCode」をコピーしました", Toast.LENGTH_SHORT).show()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "家族マップに参加しよう！\nルームコード：$roomCode")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "ルームコードをシェア"))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "シェア", tint = Color.White)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// カスタムピンマーカー（カラーバブル＋頭文字）
// ──────────────────────────────────────────────────────────────────────────────

/**
 * メンバーカラーと頭文字からカスタムBitmapDescriptorを生成する
 */
fun createBubbleMarker(context: Context, colorHex: String, initial: String): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val size = (48 * density).toInt()
    val tailHeight = (12 * density).toInt()
    val bitmapHeight = size + tailHeight

    val bitmap = Bitmap.createBitmap(size, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pinColor = try {
        android.graphics.Color.parseColor(colorHex)
    } catch (e: Exception) {
        android.graphics.Color.parseColor("#4285F4")
    }

    // 円（バブル本体）
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    // 白い縁取り
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = (2 * density)
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - (1 * density), strokePaint)

    // 下向き三角（ピン先端）
    val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    val path = android.graphics.Path().apply {
        moveTo(size * 0.35f, size * 0.85f)
        lineTo(size * 0.65f, size * 0.85f)
        lineTo(size * 0.5f, bitmapHeight.toFloat())
        close()
    }
    canvas.drawPath(path, trianglePaint)

    // 頭文字テキスト
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = size * 0.4f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = size / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(initial.uppercase(), size / 2f, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
