package com.example.passwordsafe.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.passwordsafe.core.PwsRecord
import com.example.passwordsafe.data.Prefs
import com.example.passwordsafe.data.Vault
import kotlinx.coroutines.launch

private const val ALL = "\u0000all"
private const val NONE = "\u0000none"

fun urlHost(url: String): String {
    val u = url.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
    return u.removePrefix("www.")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ListScreen(onOpen: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val session by Vault.session.collectAsState()
    val rev by Vault.revision.collectAsState()
    val dirty by Vault.dirty.collectAsState()
    val s = session ?: return
    val db = s.db

    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(ALL) }
    var sortRecent by remember { mutableStateOf(Prefs.sortByRecent) }
    var menu by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    val topGroups = remember(rev) {
        db.records.mapNotNull { it.groupPath.firstOrNull()?.takeIf { g -> g.isNotEmpty() } }
            .distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val hasUngrouped = remember(rev) { db.records.any { it.group.isEmpty() } }

    val filtered = remember(rev, query, group, sortRecent) {
        val q = query.trim().lowercase()
        db.records.filter { r ->
            val inGroup = when (group) {
                ALL -> true
                NONE -> r.group.isEmpty()
                else -> r.groupPath.firstOrNull() == group
            }
            inGroup && (q.isEmpty() || listOf(r.title, r.username, r.url, r.group, r.notes, r.email).any { it.lowercase().contains(q) })
        }.let { list ->
            if (sortRecent) list.sortedByDescending { it.modifiedAt }
            else list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }

    // Разделы по полному пути группы (кроме режима «недавние»)
    val sections: List<Pair<String, List<PwsRecord>>> = remember(filtered, sortRecent) {
        if (sortRecent) listOf("" to filtered)
        else filtered.groupBy { it.groupPath.joinToString(" › ") }
            .toSortedMap(compareBy<String> { it.isEmpty() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it }.thenBy { it })
            .toList()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                db.name.ifBlank { s.fileName.removeSuffix(".psafe3") },
                                style = MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${db.records.size} ${plural(db.records.size, "запись", "записи", "записей")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { Vault.lock(null) }) { Icon(Icons.Rounded.Lock, "Заблокировать") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Меню") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (sortRecent) "Сортировать по названию" else "Сначала изменённые") },
                                    leadingIcon = { Icon(Icons.Rounded.Sort, null) },
                                    onClick = {
                                        menu = false
                                        sortRecent = !sortRecent
                                        Prefs.sortByRecent = sortRecent
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Генератор паролей") },
                                    leadingIcon = { Icon(Icons.Rounded.Password, null) },
                                    onClick = { menu = false; showGenerator = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Настройки") },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                    onClick = { menu = false; onSettings() },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp),
                        placeholder = { Text("Поиск", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Очистить") }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }

            if (topGroups.isNotEmpty()) item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { GroupChip("Все", group == ALL) { group = ALL } }
                    items(topGroups) { g -> GroupChip(g, group == g) { group = if (group == g) ALL else g } }
                    if (hasUngrouped) item { GroupChip("Без группы", group == NONE) { group = if (group == NONE) ALL else NONE } }
                }
            } else item { Spacer(Modifier.height(12.dp)) }

            if (dirty) item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Изменения не записаны в файл",
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { scope.launch { ui.handleSave(Vault.save(ctx)) } }) { Text("Сохранить") }
                    }
                }
            }

            if (filtered.isEmpty()) item {
                EmptyState(
                    if (db.records.isEmpty()) "Пока пусто" else "Ничего не найдено",
                    if (db.records.isEmpty()) "Добавьте первую запись кнопкой внизу" else "Попробуйте изменить запрос или группу",
                )
            }

            sections.forEach { (title, recs) ->
                if (title.isNotEmpty() || (sections.size > 1 && !sortRecent)) {
                    stickyHeader(key = "h:$title") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                title.ifEmpty { "Без группы" },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                items(recs, key = { "r:" + System.identityHashCode(it) }) { r ->
                    RecordRow(
                        r,
                        username = db.resolveUsername(r),
                        onClick = { onOpen(r.uuidHex) },
                        onCopyPassword = { ui.copy("Пароль", db.resolvePassword(r)) },
                        onCopyUser = { ui.copy("Логин", db.resolveUsername(r), secret = false) },
                    )
                }
            }
        }

        if (!WindowInsets.isImeVisible) ExtendedFloatingActionButton(
            onClick = onAdd,
            expanded = expanded,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Новая запись") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    if (showGenerator) GeneratorSheet(onDismiss = { showGenerator = false }, onUse = null)
}

@Composable
private fun GroupChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun RecordRow(
    r: PwsRecord,
    username: String,
    onClick: () -> Unit,
    onCopyPassword: () -> Unit,
    onCopyUser: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordAvatar(r)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title.ifEmpty { "(без названия)" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(username.takeIf { it.isNotEmpty() }, urlHost(r.url).takeIf { it.isNotEmpty() }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (username.isNotEmpty()) IconButton(onClick = onCopyUser) {
                Icon(Icons.Rounded.Person, "Копировать логин", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCopyPassword) {
                Icon(Icons.Rounded.Key, "Копировать пароль", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun EmptyState(title: String, text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(64.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}
