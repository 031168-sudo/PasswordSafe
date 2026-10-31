package com.example.passwordsafe

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.passwordsafe.data.Prefs
import com.example.passwordsafe.data.Vault

class App : Application() {
    private val handler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable { Vault.lock(reason = null) }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        com.example.passwordsafe.ui.AppIcons.enabled = Prefs.appIcons

        // Автоблокировка: приложение ушло в фон — запускаем таймер, вернулось — отменяем.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                val minutes = Prefs.autoLockMinutes
                if (minutes < 0) return
                if (minutes == 0) Vault.lock(null) else handler.postDelayed(lockRunnable, minutes * 60_000L)
            }

            override fun onStart(owner: LifecycleOwner) {
                handler.removeCallbacks(lockRunnable)
            }
        })

        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Prefs.lockOnScreenOff) Vault.lock(null)
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
