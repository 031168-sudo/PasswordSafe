package com.example.passwordsafe.ui

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.passwordsafe.data.Clipboard
import com.example.passwordsafe.data.SaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Общие действия интерфейса: уведомления, буфер, обработка результата сохранения. */
class UiActions(private val ctx: Context, private val scope: CoroutineScope, val snackbar: SnackbarHostState) {
    var conflict by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)

    fun toast(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    fun copy(label: String, text: String, secret: Boolean = true) {
        if (text.isEmpty()) return
        val sec = Clipboard.copy(ctx, label, text, secret)
        toast(if (sec > 0) "$label скопирован · очистится через $sec с" else "$label скопирован")
    }

    fun handleSave(r: SaveResult) {
        when (r) {
            SaveResult.Ok -> Unit
            SaveResult.Conflict -> conflict = true
            is SaveResult.Error -> saveError = r.message
        }
    }
}

val LocalUi = staticCompositionLocalOf<UiActions> { error("UiActions не задан") }

/** Предложение включить вход по отпечатку после первого входа паролем. */
class BioOffer(val uri: Uri, val password: CharArray)

object BioOfferState {
    val pending = MutableStateFlow<BioOffer?>(null)

    fun clear() {
        pending.value?.password?.fill('\u0000')
        pending.value = null
    }
}
