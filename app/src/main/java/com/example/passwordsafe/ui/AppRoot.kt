package com.example.passwordsafe.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.passwordsafe.data.BiometricStore
import com.example.passwordsafe.data.Prefs
import com.example.passwordsafe.data.RefreshResult
import com.example.passwordsafe.data.SaveResult
import com.example.passwordsafe.data.Vault
import kotlinx.coroutines.launch

sealed interface Screen {
    data object List : Screen
    data class Detail(val id: String) : Screen
    data class Edit(val id: String?) : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot(activity: FragmentActivity, incoming: Uri?, onIncomingHandled: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ui = remember { UiActions(ctx.applicationContext, scope, snackbar) }
    val session by Vault.session.collectAsState()

    CompositionLocalProvider(LocalUi provides ui) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = session != null,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "root",
                ) { open ->
                    if (open) VaultNav(activity) else UnlockScreen(activity, incoming, onIncomingHandled)
                }
                SnackbarHost(
                    snackbar,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp),
                )
            }
        }

        if (ui.conflict) {
            AlertDialog(
                onDismissRequest = { ui.conflict = false },
                title = { Text("Файл изменён") },
                text = {
                    Text(
                        "Файл базы изменился с момента открытия — возможно, его сохранили на другом устройстве. " +
                            "Перезаписать его вашей версией? Прежняя версия попадёт в резервную копию.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        ui.conflict = false
                        scope.launch {
                            val r = Vault.save(ctx, force = true)
                            ui.handleSave(r)
                            if (r is SaveResult.Ok) ui.toast("Сохранено")
                        }
                    }) { Text("Перезаписать") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        ui.conflict = false
                        Vault.lock("Файл изменён на другом устройстве. Откройте его заново, ваши последние правки не сохранены.")
                    }) { Text("Отменить мои правки") }
                },
            )
        }

        ui.saveError?.let { msg ->
            AlertDialog(
                onDismissRequest = { ui.saveError = null },
                title = { Text("Не удалось сохранить") },
                text = { Text("$msg\n\nИзменения остаются в памяти, пока база открыта.") },
                confirmButton = {
                    TextButton(onClick = {
                        ui.saveError = null
                        scope.launch { ui.handleSave(Vault.save(ctx)) }
                    }) { Text("Повторить") }
                },
                dismissButton = { TextButton(onClick = { ui.saveError = null }) { Text("Позже") } },
            )
        }

        val offer by BioOfferState.pending.collectAsState()
        val o = offer
        if (o != null && session != null) {
            AlertDialog(
                onDismissRequest = { },
                icon = { Icon(Icons.Rounded.Fingerprint, null) },
                title = { Text("Вход по отпечатку") },
                text = { Text("Открывать эту базу отпечатком пальца? Мастер-пароль будет храниться зашифрованным в защищённом хранилище телефона.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val cipher = runCatching { BiometricStore.encryptCipher() }.getOrNull()
                            val ok = cipher?.let {
                                BiometricStore.authenticate(activity, "Включить вход по отпечатку", "Подтвердите отпечатком", it)
                            }
                            if (ok != null) {
                                BiometricStore.save(ctx, o.uri, ok, o.password)
                                ui.toast("Вход по отпечатку включён")
                            }
                            BioOfferState.clear()
                        }
                    }) { Text("Включить") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        Prefs.biometricOfferDeclined = Prefs.biometricOfferDeclined + o.uri.toString()
                        BioOfferState.clear()
                    }) { Text("Не сейчас") }
                },
            )
        }
    }
}

@Composable
private fun VaultNav(activity: FragmentActivity) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf<Screen>(Screen.List) }
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.size > 1) { pop() }

    // При возвращении в приложение подтягиваем изменения файла (синхронизация с ПК через облако).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            when (Vault.refreshIfChanged(ctx)) {
                RefreshResult.Reloaded -> ui.toast("База обновлена из файла")
                RefreshResult.NeedsPassword -> Vault.lock("Файл изменён на другом устройстве — введите пароль заново.")
                else -> Unit
            }
        }
    }

    AnimatedContent(
        targetState = stack.toList(),
        transitionSpec = {
            val forward = targetState.size >= initialState.size
            if (forward) slideInHorizontally { it / 3 } + fadeIn() togetherWith slideOutHorizontally { -it / 6 } + fadeOut()
            else slideInHorizontally { -it / 6 } + fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut()
        },
        contentKey = { it.size.toString() + it.last().toString() },
        label = "nav",
    ) { st ->
        when (val s = st.last()) {
            Screen.List -> ListScreen(
                onOpen = { push(Screen.Detail(it)) },
                onAdd = { push(Screen.Edit(null)) },
                onSettings = { push(Screen.Settings) },
            )
            is Screen.Detail -> DetailScreen(
                id = s.id,
                onBack = ::pop,
                onEdit = { push(Screen.Edit(s.id)) },
            )
            is Screen.Edit -> EditScreen(
                id = s.id,
                onClose = ::pop,
                onSaved = { newId ->
                    pop()
                    if (s.id == null) push(Screen.Detail(newId))
                },
            )
            Screen.Settings -> SettingsScreen(activity = activity, onBack = ::pop)
        }
    }
}
