package com.family.map.data

import com.family.map.model.LocationPoint
import com.family.map.model.Member
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase Realtime Database へのアクセスを担当するリポジトリ
 *
 * データ構造:
 * rooms/{roomCode}/members/{userId}  → Member
 * rooms/{roomCode}/history/{userId}/{dateKey}/{tsKey} → LocationPoint
 */
class FirebaseRepository {

    private val db = FirebaseDatabase.getInstance()

    // ──────────────────────────────────────────────────────
    // メンバー
    // ──────────────────────────────────────────────────────

    /** 自分の位置情報をFirebaseに書き込む */
    suspend fun updateMyLocation(
        roomCode: String,
        userId: String,
        name: String,
        color: String,
        lat: Double,
        lng: Double,
        ts: Long
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val ref = db.getReference("rooms/$roomCode/members/$userId")
        val data = mapOf(
            "name" to name,
            "color" to color,
            "lat" to lat,
            "lng" to lng,
            "ts" to ts
        )
        ref.setValue(data)
            .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    /** ルーム内の全メンバーを一度だけ取得する */
    suspend fun getMembers(roomCode: String): List<Member> =
        suspendCancellableCoroutine { cont ->
            val ref = db.getReference("rooms/$roomCode/members")
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!cont.isActive) return
                    val members = snapshot.children.mapNotNull { child ->
                        try {
                            Member(
                                userId = child.key ?: return@mapNotNull null,
                                name = child.child("name").getValue(String::class.java) ?: "",
                                color = child.child("color").getValue(String::class.java) ?: "#4285F4",
                                lat = child.child("lat").getValue(Double::class.java) ?: 0.0,
                                lng = child.child("lng").getValue(Double::class.java) ?: 0.0,
                                ts = child.child("ts").getValue(Long::class.java) ?: 0L
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    cont.resume(members)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resumeWithException(error.toException())
                }
            })
        }

    // ──────────────────────────────────────────────────────
    // 履歴
    // ──────────────────────────────────────────────────────

    /** 履歴に位置情報を書き込む */
    suspend fun saveHistory(
        roomCode: String,
        userId: String,
        lat: Double,
        lng: Double,
        ts: Long
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val dateKey = dateKeyFromTs(ts)
        val ref = db.getReference("rooms/$roomCode/history/$userId/$dateKey/$ts")
        val data = mapOf(
            "lat" to lat,
            "lng" to lng,
            "ts" to ts
        )
        ref.setValue(data)
            .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    /** 指定ユーザーの指定日の履歴を取得する */
    suspend fun getHistory(
        roomCode: String,
        userId: String,
        date: Date
    ): List<LocationPoint> = suspendCancellableCoroutine { cont ->
        val dateKey = dateKeyFromDate(date)
        val ref = db.getReference("rooms/$roomCode/history/$userId/$dateKey")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!cont.isActive) return
                val points = snapshot.children.mapNotNull { child ->
                    try {
                        LocationPoint(
                            lat = child.child("lat").getValue(Double::class.java) ?: return@mapNotNull null,
                            lng = child.child("lng").getValue(Double::class.java) ?: return@mapNotNull null,
                            ts = child.child("ts").getValue(Long::class.java) ?: return@mapNotNull null
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.ts }
                cont.resume(points)
            }

            override fun onCancelled(error: DatabaseError) {
                if (cont.isActive) cont.resumeWithException(error.toException())
            }
        })
    }

    /** 7日以上前の履歴を削除する（アプリ起動時に実行） */
    fun cleanupOldHistory(roomCode: String, userId: String) {
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        val cutoffKey = dateKeyFromDate(cutoff)
        val ref = db.getReference("rooms/$roomCode/history/$userId")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { dateSnap ->
                    val dateKey = dateSnap.key ?: return@forEach
                    if (dateKey < cutoffKey) {
                        dateSnap.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { /* ignore */ }
        })
    }

    // ──────────────────────────────────────────────────────
    // ユーティリティ
    // ──────────────────────────────────────────────────────

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun dateKeyFromTs(ts: Long): String = dateFormat.format(Date(ts))
    private fun dateKeyFromDate(date: Date): String = dateFormat.format(date)
}
