package com.family.map.model

/**
 * Firebaseの rooms/{roomCode}/history/{userId}/{date}/{ts} に対応するデータクラス
 */
data class LocationPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val ts: Long = 0L               // Unix timestamp (ms)
)
