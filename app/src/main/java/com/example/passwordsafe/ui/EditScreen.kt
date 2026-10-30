package com.example.passwordsafe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.passwordsafe.core.GeneratorOptions
import com.example.passwordsafe.core.PasswordGenerator
import com.example.passwordsafe.core.PwsRecord
import com.example.passwordsafe.core.RecordType
import com.example.passwordsafe.data.Vault
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(id: String?, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val session by Vault.session.collectAsState()
    val db = session?.db ?: return
    val original = remember(id) { id?.let { db.findByUuidHex(it) } }

    var title by rememberSaveable { mutableStateOf(original?.title ?: "") }
    var group by rememberSaveable { mutableStateOf(original?.groupPath?.joinToString(" › ") ?: "") }
    var username by rememberSaveable { mutableStateOf(original?.username ?: "") }
    var password by rememberSaveable {
        mutableStateOf(original?.password ?: PasswordGenerator.generate(GeneratorOptions()))
    }
    var url by rememberSaveable { mutableStateOf(original?.url ?: "") }
    var email by rememberSaveable { mutableStateOf(original?.email ?: "") }
    var notes by rememberSaveable { mutableStateOf(original?.notes?.replace("\r\n", "\n") ?: "") }
    var reveal by remember { mutableStateOf(original == null) }
    var showGenerator by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val initial = remember {
        listOf(title, group, username, password, url, email, notes)
    }
    val changed = listOf(title, group, username, password, url, email, notes) != initial
    val groups = remember { db.allGroups().map { PwsRecord.splitGroup(it).joinToString(" › ") } }

    fun close() { if (changed) confirmDiscard = true else onClose() }
    BackHandler { close() }

    fun save() {
        if (title.isBlank()) { titleError = true; return }
        saving = true
        val now = System.currentTimeMillis() / 1000
        val groupValue = PwsRecord.joinGroup(group.split("›").map { it.trim() }.filter { it.isNotEmpty() })
        // Заметки в Password Safe хранятся с переводами строк Windows
        val notesValue = notes.replace("\r\n", "\n").replace("\n", "\r\n")
        scope.launch {
            var newId = id ?: ""
            val res = Vault.edit(ctx) { d ->
                val target = original?.let { o -> d.records.firstOrNull { it === o } ?: d.findByUuidHex(o.uuidHex) }
                    ?: PwsRecord.create(now).also { d.records.add(it) }
                target.title = title.trim()
                target.group = groupValue
                target.username = username.trim()
                target.url = url.trim()
                target.email = email.trim()
                if (target.notes != notesValue) target.notes = notesValue
                target.changePassword(password, now)
                target.setTime(RecordType.RMTIME, now)
                newId = target.uuidHex
            }
            saving = false
            ui.handleSave(res)
            onSaved(newId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Новая запись" else "Изменить") },
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.Rounded.Close, "Закрыть") } },
                actions = {
                    Button(onClick = { save() }, enabled = !saving, modifier = Modifier.padding(end = 8.dp)) { Text("Сохранить") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                label = { Text("Название") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) },
                isError = titleError,
                supportingText = { if (titleError) Text("Укажите название") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(expanded = groupMenu && groups.isNotEmpty(), onExpandedChange = { groupMenu = it }) {
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it; groupMenu = true },
                    label = { Text("Группа") },
                    placeholder = { Text("Например: Работа › Почта") },
                    leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                    trailingIcon = { if (groups.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(groupMenu) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable),
                )
                val options = groups.filter { group.isBlank() || it.contains(group.trim(), ignoreCase = true) }
                ExposedDropdownMenu(expanded = groupMenu && options.isNotEmpty(), onDismissRequest = { groupMenu = false }) {
                    options.take(12).forEach { g ->
                        DropdownMenuItem(text = { Text(g) }, onClick = { group = g; groupMenu = false })
                    }
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    leadingIcon = { Icon(Icons.Rounded.Key, null) },
                    singleLine = true,
                    textStyle = PasswordTextStyle,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать")
                            }
                            IconButton(onClick = { showGenerator = true }) {
                                Icon(Icons.Rounded.AutoAwesome, "Сгенерировать", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                StrengthMeter(password, Modifier.padding(horizontal = 4.dp))
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Сайт") },
                leadingIcon = { Icon(Icons.Rounded.Language, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметки") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showGenerator) {
        GeneratorSheet(onDismiss = { showGenerator = false }, onUse = { password = it; reveal = true; showGenerator = false })
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Отменить изменения?") },
            text = { Text("Внесённые правки не будут сохранены.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Не сохранять") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Продолжить") } },
        )
    }
}
