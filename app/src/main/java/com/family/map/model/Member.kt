package com.family.map.model

/**
 * Firebaseの rooms/{roomCode}/members/{userId} に対応するデータクラス
 */
data class Member(
    val userId: String = "",
    val name: String = "",
    val color: String = "#4285F4",  // hex color
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val ts: Long = 0L               // Unix timestamp (ms)
) {
    /** 5分以上更新がない場合は stale とみなす */
    fun isStale(): Boolean {
        if (ts == 0L) return true
        return System.currentTimeMillis() - ts > 5 * 60 * 1000L
    }
}
