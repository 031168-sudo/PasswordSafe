package com.example.passwordsafe.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/** Копирование в буфер с пометкой «секретно» и автоочисткой. */
object Clipboard {
    private val handler = Handler(Looper.getMainLooper())
    private var clearTask: Runnable? = null

    /** Возвращает число секунд до очистки (0 — не очищается). */
    fun copy(ctx: Context, label: String, text: String, secret: Boolean): Int {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (secret) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= 33) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                else putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)

        clearTask?.let { handler.removeCallbacks(it) }
        val seconds = Prefs.clipboardSeconds
        if (seconds <= 0) return 0
        val app = ctx.applicationContext
        val task = Runnable { clear(app) }
        clearTask = task
        handler.postDelayed(task, seconds * 1000L)
        return seconds
    }

    fun clear(ctx: Context) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
