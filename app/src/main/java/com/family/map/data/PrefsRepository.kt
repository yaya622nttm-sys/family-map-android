package com.family.map.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * ユーザー設定（名前・色・userId・ルームコード）をSharedPreferencesに保存・読み込みする
 */
class PrefsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("family_map_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NAME = "name"
        private const val KEY_COLOR = "color"
        private const val KEY_ROOM_CODE = "room_code"
    }

    /** userId を取得。なければUUIDを生成して保存する */
    val userId: String
        get() {
            var id = prefs.getString(KEY_USER_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString().replace("-", "").take(16)
                prefs.edit().putString(KEY_USER_ID, id).apply()
            }
            return id
        }

    var name: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var color: String
        get() = prefs.getString(KEY_COLOR, "#4285F4") ?: "#4285F4"
        set(value) = prefs.edit().putString(KEY_COLOR, value).apply()

    var roomCode: String
        get() = prefs.getString(KEY_ROOM_CODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROOM_CODE, value).apply()

    /** セットアップ完了済みかどうか */
    fun isSetupComplete(): Boolean = name.isNotBlank() && roomCode.isNotBlank()

    /** セットアップ内容を一括保存 */
    fun saveSetup(name: String, color: String, roomCode: String) {
        prefs.edit()
            .putString(KEY_NAME, name)
            .putString(KEY_COLOR, color)
            .putString(KEY_ROOM_CODE, roomCode)
            .apply()
    }
}
