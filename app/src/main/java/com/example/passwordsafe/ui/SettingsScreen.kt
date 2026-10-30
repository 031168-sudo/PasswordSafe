package com.example.passwordsafe.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.passwordsafe.BuildConfig
import com.example.passwordsafe.core.PwsFile
import com.example.passwordsafe.data.BiometricStore
import com.example.passwordsafe.data.Prefs
import com.example.passwordsafe.data.SaveResult
import com.example.passwordsafe.data.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val lockOptions = listOf(0 to "Сразу", 1 to "Через 1 минуту", 5 to "Через 5 минут", 15 to "Через 15 минут", 60 to "Через час", -1 to "Никогда")
private val clipOptions = listOf(15 to "15 секунд", 30 to "30 секунд", 60 to "1 минута", 120 to "2 минуты", 0 to "Не очищать")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(activity: FragmentActivity, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val session by Vault.session.collectAsState()
    val s = session ?: return

    var autoLock by remember { mutableIntStateOf(Prefs.autoLockMinutes) }
    var clip by remember { mutableIntStateOf(Prefs.clipboardSeconds) }
    var screenOff by remember { mutableStateOf(Prefs.lockOnScreenOff) }
    var bioOn by remember { mutableStateOf(BiometricStore.hasSaved(ctx, s.uri)) }
    val bioAvailable = remember { BiometricStore.isAvailable(ctx) }
    var autofillOn by remember { mutableStateOf(false) }
    var choose by remember { mutableStateOf<String?>(null) }
    var askPasswordForBio by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        autofillOn = runCatching { ctx.getSystemService(AutofillManager::class.java).hasEnabledAutofillServices() }.getOrDefault(false)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Безопасность")
            SectionCard {
                SettingRow(Icons.Rounded.LockClock, "Автоблокировка", lockOptions.first { it.first == autoLock }.second) { choose = "lock" }
                RowDivider()
                SettingSwitch(Icons.Rounded.PhonelinkLock, "Блокировать при выключении экрана", null, screenOff) {
                    screenOff = it; Prefs.lockOnScreenOff = it
                }
                RowDivider()
                SettingRow(Icons.Rounded.ContentPaste, "Очистка буфера обмена", clipOptions.firstOrNull { it.first == clip }?.second ?: "$clip с") { choose = "clip" }
                RowDivider()
                SettingSwitch(
                    Icons.Rounded.Fingerprint,
                    "Вход по отпечатку",
                    if (bioAvailable) "Для этой базы" else "Отпечаток на телефоне не настроен",
                    bioOn,
                    enabled = bioAvailable,
                ) { on ->
                    if (on) askPasswordForBio = true
                    else {
                        BiometricStore.remove(ctx, s.uri)
                        bioOn = false
                    }
                }
            }

            SectionTitle("Автозаполнение")
            SectionCard {
                SettingRow(
                    Icons.Rounded.Password,
                    if (autofillOn) "Служба автозаполнения выбрана" else "Включить автозаполнение",
                    if (autofillOn) "Логины и пароли подставляются в приложения и сайты"
                    else "Подставлять логины и пароли в другие приложения и браузеры",
                ) {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${ctx.packageName}"))
                    runCatching { ctx.startActivity(intent) }.onFailure {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                }
            }

            SectionTitle("База")
            SectionCard {
                SettingRow(Icons.Rounded.Description, s.fileName, "${s.db.records.size} ${plural(s.db.records.size, "запись", "записи", "записей")} · ${s.db.key.iterations} итераций ключа", null)
                RowDivider()
                SettingRow(Icons.Rounded.Backup, "Резервные копии", "Последние ${Vault.backupCount(ctx)} версий файла хранятся в памяти приложения", null)
                RowDivider()
                SettingRow(Icons.Rounded.Key, "Сменить мастер-пароль", null) { changePassword = true }
                RowDivider()
                SettingRow(Icons.Rounded.Lock, "Закрыть базу", null) { Vault.lock(null) }
            }

            SectionTitle("О приложении")
            SectionCard {
                SettingRow(
                    Icons.Rounded.Info,
                    "Сейф паролей ${BuildConfig.VERSION_NAME}",
                    "Формат Password Safe v3 (Twofish-256, HMAC-SHA256). У приложения нет доступа к интернету.",
                    null,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (choose) {
        "lock" -> ChoiceDialog("Автоблокировка", lockOptions, autoLock, { choose = null }) {
            autoLock = it; Prefs.autoLockMinutes = it; choose = null
        }
        "clip" -> ChoiceDialog("Очистка буфера", clipOptions, clip, { choose = null }) {
            clip = it; Prefs.clipboardSeconds = it; choose = null
        }
    }

    if (askPasswordForBio) {
        PasswordPromptDialog(
            title = "Вход по отпечатку",
            text = "Введите мастер-пароль, чтобы сохранить его для входа отпечатком.",
            onDismiss = { askPasswordForBio = false },
        ) { pw ->
            scope.launch {
                val ok = withContext(Dispatchers.Default) { PwsFile.verifyPassword(s.db.key, pw) }
                if (!ok) {
                    ui.toast("Неверный мастер-пароль"); pw.fill('\u0000'); return@launch
                }
                askPasswordForBio = false
                val cipher = runCatching { BiometricStore.encryptCipher() }.getOrNull()
                val auth = cipher?.let { BiometricStore.authenticate(activity, "Включить вход по отпечатку", "Подтвердите отпечатком", it) }
                if (auth != null) {
                    BiometricStore.save(ctx, s.uri, auth, pw)
                    bioOn = true
                    ui.toast("Вход по отпечатку включён")
                }
                pw.fill('\u0000')
            }
        }
    }

    if (changePassword) {
        ChangePasswordDialog(onDismiss = { changePassword = false }) { old, new ->
            scope.launch {
                val ok = withContext(Dispatchers.Default) { PwsFile.verifyPassword(s.db.key, old) }
                old.fill('\u0000')
                if (!ok) { ui.toast("Текущий пароль неверный"); new.fill('\u0000'); return@launch }
                changePassword = false
                val r = Vault.changeMasterPassword(ctx, new)
                ui.handleSave(r)
                if (r is SaveResult.Ok) {
                    if (BiometricStore.hasSaved(ctx, s.uri)) {
                        BiometricStore.remove(ctx, s.uri)
                        bioOn = false
                        BioOfferState.pending.value = BioOffer(s.uri, new.copyOf())
                    }
                    ui.toast("Мастер-пароль изменён")
                }
                new.fill('\u0000')
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<Int, String>>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (v, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = v == selected, onClick = { onSelect(v) })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = v == selected, onClick = { onSelect(v) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
fun PasswordPromptDialog(title: String, text: String, onDismiss: () -> Unit, onOk: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text)
                OutlinedTextField(
                    pw, { pw = it }, label = { Text("Мастер-пароль") }, singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onOk(pw.toCharArray()) }, enabled = pw.isNotEmpty()) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onOk: (CharArray, CharArray) -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val vt = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val kb = KeyboardOptions(keyboardType = KeyboardType.Password)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена мастер-пароля") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(old, { old = it }, label = { Text("Текущий пароль") }, singleLine = true, visualTransformation = vt, keyboardOptions = kb)
                OutlinedTextField(
                    new, { new = it }, label = { Text("Новый пароль") }, singleLine = true, visualTransformation = vt, keyboardOptions = kb,
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) }
                    },
                )
                StrengthMeter(new)
                OutlinedTextField(
                    new2, { new2 = it }, label = { Text("Повторите новый") }, singleLine = true, visualTransformation = vt, keyboardOptions = kb,
                    isError = new2.isNotEmpty() && new != new2,
                )
                Text(
                    "Файл будет перешифрован. На других устройствах базу нужно будет открыть новым паролем.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(old.toCharArray(), new.toCharArray()) }, enabled = old.isNotEmpty() && new.length >= 4 && new == new2) {
                Text("Сменить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

