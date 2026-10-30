package com.example.passwordsafe.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.passwordsafe.core.WrongPasswordException
import com.example.passwordsafe.data.BiometricStore
import com.example.passwordsafe.data.Prefs
import com.example.passwordsafe.data.Vault
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(
    activity: FragmentActivity,
    incoming: Uri?,
    onIncomingHandled: () -> Unit,
    subtitle: String? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var recents by remember { mutableStateOf(Prefs.recentFiles) }
    var selected by remember { mutableStateOf(recents.firstOrNull()?.uri) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingCreate by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var bioTriedFor by remember { mutableStateOf<Uri?>(null) }
    val lockMessage by Vault.lockMessage.collectAsState()
    val focus = remember { FocusRequester() }

    fun selectedName(): String = recents.firstOrNull { it.uri == selected }?.name
        ?: selected?.let { Vault.displayName(ctx, it) } ?: ""

    LaunchedEffect(incoming) {
        if (incoming != null) {
            selected = incoming
            password = ""
            error = null
            onIncomingHandled()
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            selected = uri
            password = ""
            error = null
        }
    }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = pendingCreate
        pendingCreate = null
        if (uri != null && p != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch {
                busy = true
                try {
                    Vault.create(ctx, uri, p.first, p.second.toCharArray())
                } catch (e: Exception) {
                    error = "Не удалось создать базу: ${e.message}"
                } finally {
                    busy = false
                }
            }
        }
    }

    fun unlockWith(pw: CharArray, fromBiometric: Boolean) {
        val uri = selected ?: return
        scope.launch {
            busy = true
            error = null
            try {
                Vault.open(ctx, uri, pw)
                if (!fromBiometric && BiometricStore.isAvailable(ctx) && !BiometricStore.hasSaved(ctx, uri) &&
                    uri.toString() !in Prefs.biometricOfferDeclined
                ) {
                    BioOfferState.pending.value = BioOffer(uri, pw.copyOf())
                }
                password = ""
            } catch (e: WrongPasswordException) {
                error = if (fromBiometric) "Сохранённый пароль не подошёл — введите новый" else "Неверный мастер-пароль"
                if (fromBiometric) BiometricStore.remove(ctx, uri)
            } catch (e: SecurityException) {
                error = "Нет доступа к файлу. Выберите его заново."
                Prefs.removeRecent(uri)
                recents = Prefs.recentFiles
            } catch (e: Exception) {
                error = e.message ?: "Ошибка открытия"
            } finally {
                pw.fill('\u0000')
                busy = false
            }
        }
    }

    fun unlockBiometric() {
        val uri = selected ?: return
        scope.launch {
            val cipher = BiometricStore.decryptCipher(ctx, uri)
            if (cipher == null) {
                error = "Отпечатки на телефоне изменились — войдите паролем"
                return@launch
            }
            val ok = BiometricStore.authenticate(activity, "Открыть базу", selectedName(), cipher) ?: return@launch
            val pw = runCatching { BiometricStore.load(ctx, uri, ok) }.getOrNull()
            if (pw == null) {
                error = "Не удалось прочитать сохранённый пароль"
                BiometricStore.remove(ctx, uri)
            } else unlockWith(pw, fromBiometric = true)
        }
    }

    val hasBio = selected?.let { BiometricStore.hasSaved(ctx, it) && BiometricStore.isAvailable(ctx) } ?: false

    // Автоматически предлагаем отпечаток при показе экрана
    LaunchedEffect(selected, hasBio) {
        if (hasBio && bioTriedFor != selected) {
            bioTriedFor = selected
            unlockBiometric()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            AppLogo()
            Spacer(Modifier.height(20.dp))
            Text("Сейф паролей", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle ?: "Совместим с Password Safe (.psafe3)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            lockMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { Vault.lockMessage.value = null }) { Icon(Icons.Rounded.Close, "Закрыть") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (selected == null) {
                Text(
                    "Откройте файл базы Password Safe — из памяти телефона, Google Диска, Яндекс Диска или другого хранилища — либо создайте новую.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { openLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Rounded.FolderOpen, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Открыть файл .psafe3")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showCreate = true }, Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Создать новую базу")
                }
            } else {
                // Карточка выбранного файла
                Box {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { menu = true },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selectedName(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    selected?.authority?.let { providerName(it) } ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Rounded.ExpandMore, "Выбрать другой файл")
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        recents.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.name) },
                                leadingIcon = { Icon(Icons.Rounded.Description, null) },
                                onClick = { menu = false; selected = r.uri; password = ""; error = null },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Открыть другой файл…") },
                            leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                            onClick = { menu = false; openLauncher.launch(arrayOf("*/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Создать новую базу…") },
                            leadingIcon = { Icon(Icons.Rounded.Add, null) },
                            onClick = { menu = false; showCreate = true },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                        label = { Text("Мастер-пароль") },
                        singleLine = true,
                        shape = CircleShape,
                        isError = error != null,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) unlockWith(password.toCharArray(), false) }),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать пароль")
                            }
                        },
                    )
                    if (hasBio) {
                        Spacer(Modifier.width(10.dp))
                        FilledTonalIconButton(onClick = { unlockBiometric() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.Fingerprint, "Войти по отпечатку", Modifier.size(30.dp))
                        }
                    }
                }
                AnimatedVisibility(error != null) {
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { unlockWith(password.toCharArray(), false) },
                    enabled = password.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    else {
                        Icon(Icons.Rounded.LockOpen, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Открыть")
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Без доступа к интернету · данные только в вашем файле",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCreate) {
        CreateDatabaseDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, pw ->
                showCreate = false
                pendingCreate = name to pw
                val fileName = (name.ifBlank { "Пароли" }).replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".psafe3"
                createLauncher.launch(fileName)
            },
        )
    }
}

private fun providerName(authority: String): String = when {
    authority.contains("google.android.apps.docs") -> "Google Диск"
    authority.contains("yandex") -> "Яндекс Диск"
    authority.contains("dropbox") -> "Dropbox"
    authority.contains("onedrive") || authority.contains("skydrive") -> "OneDrive"
    authority.contains("externalstorage") -> "Память телефона"
    authority.contains("downloads") -> "Загрузки"
    authority.contains("syncthing") -> "Syncthing"
    authority.contains("nextcloud") -> "Nextcloud"
    else -> authority
}

@Composable
fun CreateDatabaseDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("Пароли") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val mismatch = pw2.isNotEmpty() && pw != pw2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая база") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(
                    pw, { pw = it }, label = { Text("Мастер-пароль") }, singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                        }
                    },
                )
                StrengthMeter(pw)
                OutlinedTextField(
                    pw2, { pw2 = it }, label = { Text("Повторите пароль") }, singleLine = true, isError = mismatch,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { if (mismatch) Text("Пароли не совпадают") },
                )
                Text(
                    "Мастер-пароль невозможно восстановить. Запишите его в надёжном месте.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), pw) }, enabled = pw.length >= 4 && pw == pw2) { Text("Создать файл") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
