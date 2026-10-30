package com.example.passwordsafe.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(val uri: Uri, val name: String, val lastOpened: Long)

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    /** -1 — никогда, 0 — сразу при уходе в фон, иначе минуты. */
    var autoLockMinutes: Int
        get() = sp.getInt("autolock", 5)
        set(v) = sp.edit().putInt("autolock", v).apply()

    var clipboardSeconds: Int
        get() = sp.getInt("clip", 30)
        set(v) = sp.edit().putInt("clip", v).apply()

    var lockOnScreenOff: Boolean
        get() = sp.getBoolean("screenoff", true)
        set(v) = sp.edit().putBoolean("screenoff", v).apply()

    var sortByRecent: Boolean
        get() = sp.getBoolean("sortRecent", false)
        set(v) = sp.edit().putBoolean("sortRecent", v).apply()

    var biometricOfferDeclined: Set<String>
        get() = sp.getStringSet("bioDeclined", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("bioDeclined", v).apply()

    var recentFiles: List<RecentFile>
        get() {
            val arr = JSONArray(sp.getString("recent", "[]"))
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                RecentFile(Uri.parse(o.getString("uri")), o.getString("name"), o.optLong("t"))
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("uri", it.uri.toString()).put("name", it.name).put("t", it.lastOpened)) }
            sp.edit().putString("recent", arr.toString()).apply()
        }

    fun touchRecent(uri: Uri, name: String) {
        recentFiles = listOf(RecentFile(uri, name, System.currentTimeMillis())) +
            recentFiles.filter { it.uri != uri }.take(7)
    }

    fun removeRecent(uri: Uri) {
        recentFiles = recentFiles.filter { it.uri != uri }
    }
}
