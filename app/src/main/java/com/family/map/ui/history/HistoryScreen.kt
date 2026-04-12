package com.family.map.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.map.data.FirebaseRepository
import com.family.map.model.LocationPoint
import com.family.map.model.Member
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.family.map.ui.map.createBubbleMarker
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 移動履歴画面
 * - メンバー選択（ドロップダウン）
 * - 日付選択（過去7日）
 * - Polylineで軌跡表示
 * - 時刻スライダーで特定時刻の位置を確認
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    roomCode: String,
    members: List<Member>,
    myUserId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val firebaseRepo = remember { FirebaseRepository() }
    val dateFormat = remember { SimpleDateFormat("M月d日(E)", Locale.JAPANESE) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.JAPANESE) }

    // 過去7日分の日付リスト
    val dateList = remember {
        (0..6).map { daysAgo ->
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.time
        }
    }

    var selectedMember by remember { mutableStateOf(members.find { it.userId == myUserId } ?: members.firstOrNull()) }
    var selectedDate by remember { mutableStateOf(dateList.first()) }
    var history by remember { mutableStateOf<List<LocationPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(1f) }

    var memberMenuExpanded by remember { mutableStateOf(false) }
    var dateMenuExpanded by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(35.6812, 139.7671), 12f)
    }

    // 履歴を取得
    LaunchedEffect(selectedMember, selectedDate) {
        val member = selectedMember ?: return@LaunchedEffect
        isLoading = true
        try {
            history = firebaseRepo.getHistory(roomCode, member.userId, selectedDate)
            sliderValue = 1f

            // 軌跡にカメラをフィット
            if (history.size >= 2) {
                val bounds = LatLngBounds.builder().apply {
                    history.forEach { include(LatLng(it.lat, it.lng)) }
                }.build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 80)
                )
            } else if (history.size == 1) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(history[0].lat, history[0].lng), 16f
                    )
                )
            }
        } catch (e: Exception) {
            history = emptyList()
        } finally {
            isLoading = false
        }
    }

    // スライダーで絞り込んだ点
    val sliderIndex = if (history.isEmpty()) 0
    else ((sliderValue * (history.size - 1)).roundToInt()).coerceIn(0, history.size - 1)

    val displayedHistory = if (history.isEmpty()) emptyList()
    else history.subList(0, sliderIndex + 1)

    val memberColor = try {
        Color(android.graphics.Color.parseColor(selectedMember?.color ?: "#4285F4"))
    } catch (e: Exception) {
        Color(0xFF4285F4)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("移動履歴", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── 選択コントロール ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // メンバー選択
                        Box(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { memberMenuExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text("メンバー", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = selectedMember?.name ?: "選択",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                            DropdownMenu(
                                expanded = memberMenuExpanded,
                                onDismissRequest = { memberMenuExpanded = false }
                            ) {
                                members.forEach { member ->
                                    DropdownMenuItem(
                                        text = { Text(member.name) },
                                        onClick = {
                                            selectedMember = member
                                            memberMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 日付選択
                        Box(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { dateMenuExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text("日付", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = dateFormat.format(selectedDate),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                            DropdownMenu(
                                expanded = dateMenuExpanded,
                                onDismissRequest = { dateMenuExpanded = false }
                            ) {
                                dateList.forEach { date ->
                                    DropdownMenuItem(
                                        text = { Text(dateFormat.format(date)) },
                                        onClick = {
                                            selectedDate = date
                                            dateMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 件数表示
                    Text(
                        text = if (isLoading) "読み込み中..." else "${history.size} 件の記録",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // ── 地図 ──
            Box(modifier = Modifier.weight(1f)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = true)
                ) {
                    if (displayedHistory.size >= 2) {
                        // 軌跡のPolyline
                        Polyline(
                            points = displayedHistory.map { LatLng(it.lat, it.lng) },
                            color = memberColor,
                            width = 8f
                        )
                        // 始点マーカー（緑）
                        Marker(
                            state = MarkerState(position = LatLng(displayedHistory.first().lat, displayedHistory.first().lng)),
                            title = "出発 " + timeFormat.format(Date(displayedHistory.first().ts)),
                            icon = createBubbleMarker(context, "#34A853", "S")
                        )
                        // 現在表示中の最終地点マーカー
                        Marker(
                            state = MarkerState(position = LatLng(displayedHistory.last().lat, displayedHistory.last().lng)),
                            title = timeFormat.format(Date(displayedHistory.last().ts)),
                            icon = selectedMember?.let { m ->
                                createBubbleMarker(context, m.color, m.name.take(1))
                            }
                        )
                    } else if (displayedHistory.size == 1) {
                        Marker(
                            state = MarkerState(position = LatLng(displayedHistory[0].lat, displayedHistory[0].lng)),
                            title = timeFormat.format(Date(displayedHistory[0].ts))
                        )
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                if (!isLoading && history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text("この日の記録はありません", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            // ── 時刻スライダー ──
            if (history.size >= 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val currentPoint = history[sliderIndex]
                    Text(
                        text = "表示中: ${timeFormat.format(Date(currentPoint.ts))}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(timeFormat.format(Date(history.first().ts)), fontSize = 11.sp)
                        Text(timeFormat.format(Date(history.last().ts)), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
