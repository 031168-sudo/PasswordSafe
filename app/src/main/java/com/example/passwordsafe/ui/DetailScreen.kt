package com.example.passwordsafe.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.passwordsafe.core.PasswordHistory
import com.example.passwordsafe.core.RecordType
import com.example.passwordsafe.data.Vault
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

fun formatTime(sec: Long): String =
    if (sec <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(sec * 1000))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val session by Vault.session.collectAsState()
    val rev by Vault.revision.collectAsState()
    val db = session?.db ?: return
    val r = remember(rev, id) { db.findByUuidHex(id) }
    if (r == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var reveal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val password = db.resolvePassword(r)
    val username = db.resolveUsername(r)
    val isAlias = password != r.password
    val history = remember(rev, id) { PasswordHistory.parse(r.getString(RecordType.PWHIST)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Изменить") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Удалить") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(r.title, 72.dp)
                Spacer(Modifier.height(12.dp))
                Text(r.title.ifEmpty { "(без названия)" }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (r.group.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Pill(r.groupPath.joinToString(" › "), MaterialTheme.colorScheme.primary)
                }
            }

            SectionCard {
                if (username.isNotEmpty()) {
                    FieldRow(Icons.Rounded.Person, "Логин", username) {
                        IconButton(onClick = { ui.copy("Логин", username, secret = false) }) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
                    }
                    RowDivider()
                }
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isAlias) "Пароль (ссылка на другую запись)" else "Пароль", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        if (reveal) SelectionContainer { ColoredPassword(password) }
                        else Text("•".repeat(minOf(password.length, 16).coerceAtLeast(8)), style = PasswordTextStyle)
                    }
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать")
                    }
                    IconButton(onClick = { ui.copy("Пароль", password) }) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
                }
                StrengthMeter(password, Modifier.padding(start = 56.dp, end = 16.dp, bottom = 10.dp))
                if (r.url.isNotEmpty()) {
                    RowDivider()
                    FieldRow(Icons.Rounded.Language, "Сайт", r.url) {
                        IconButton(onClick = {
                            val u = if (r.url.contains("://")) r.url else "https://${r.url}"
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .onFailure { ui.toast("Не удалось открыть ссылку") }
                        }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть") }
                        IconButton(onClick = { ui.copy("Адрес", r.url, secret = false) }) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
                    }
                }
                if (r.email.isNotEmpty()) {
                    RowDivider()
                    FieldRow(Icons.Rounded.AlternateEmail, "Email", r.email) {
                        IconButton(onClick = { ui.copy("Email", r.email, secret = false) }) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
                    }
                }
            }

            if (r.notes.isNotEmpty()) {
                SectionCard {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Notes, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Заметки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer { Text(r.notes.replace("\r\n", "\n"), style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }

            if (history != null && history.items.isNotEmpty()) {
                SectionCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showHistory = !showHistory }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text("Прежние пароли (${history.items.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Icon(if (showHistory) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(showHistory) {
                        Column {
                            history.items.reversed().forEach { (t, pw) ->
                                Row(Modifier.padding(start = 56.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        ColoredPassword(pw)
                                        Text("до ${formatTime(t)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { ui.copy("Пароль", pw) }) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
                                }
                            }
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InfoLine("Создана", formatTime(r.createdAt))
                InfoLine("Изменена", formatTime(r.modifiedAt))
                if (r.passwordChangedAt > 0) InfoLine("Пароль изменён", formatTime(r.passwordChangedAt))
                if (isAlias) InfoLine("Тип", "Алиас — пароль берётся из связанной записи")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Удалить запись?") },
            text = { Text("«${r.title}» будет удалена из базы. Предыдущая версия файла сохранится в резервной копии.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val res = Vault.edit(ctx) { it.records.remove(r) }
                        ui.handleSave(res)
                        ui.toast("Запись удалена")
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
fun FieldRow(icon: ImageVector, label: String, value: String, actions: @Composable () -> Unit) {
    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
        }
        actions()
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, Modifier.width(140.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

