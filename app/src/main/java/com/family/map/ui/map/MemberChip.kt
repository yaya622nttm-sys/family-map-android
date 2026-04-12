package com.family.map.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.map.model.Member

/**
 * マップ下部に表示されるメンバー選択チップ
 * タップすると該当メンバーのピンにカメラが移動する
 */
@Composable
fun MemberChip(
    member: Member,
    isMe: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (member.isStale()) 0.4f else 1.0f
    val pinColor = try {
        Color(android.graphics.Color.parseColor(member.color))
    } catch (e: Exception) {
        Color(0xFF4285F4)
    }
    val initial = member.name.take(1).uppercase()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(
                width = if (isMe) 2.dp else 1.dp,
                color = if (isMe) pinColor else Color.LightGray,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 頭文字バブル
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(pinColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = member.name + if (isMe) "（自分）" else "",
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                color = Color.Black
            )
            if (member.isStale()) {
                Text(
                    text = "更新なし",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
