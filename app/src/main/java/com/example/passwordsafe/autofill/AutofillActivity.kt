package com.example.passwordsafe.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.passwordsafe.core.PwsRecord
import com.example.passwordsafe.data.Vault
import com.example.passwordsafe.ui.AppTheme
import com.example.passwordsafe.ui.Avatar
import com.example.passwordsafe.ui.UnlockScreen
import com.example.passwordsafe.ui.urlHost

/** Открывается из подсказки автозаполнения: разблокировка и выбор записи. */
class AutofillActivity : FragmentActivity() {

    companion object {
        const val EXTRA_USER_IDS = "user_ids"
        const val EXTRA_PASS_IDS = "pass_ids"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_PACKAGE = "package"
    }

    @Suppress("DEPRECATION")
    private fun ids(key: String): List<AutofillId> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(key, AutofillId::class.java)
        else intent.getParcelableArrayListExtra(key)) ?: emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val userIds = ids(EXTRA_USER_IDS)
        val passIds = ids(EXTRA_PASS_IDS)
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val form = ParsedForm(userIds, passIds, domain, pkg)
        val target = domain ?: appLabel(pkg)

        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val session by Vault.session.collectAsState()
                    if (session == null) {
                        UnlockScreen(this, null, {}, subtitle = "Автозаполнение для «$target»")
                    } else {
                        Picker(form, target) { r ->
                            val db = Vault.session.value?.db ?: return@Picker
                            val ds = PwAutofillService.datasetFor(this, db, r, userIds, passIds)
                            setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, ds))
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    @Composable
    private fun Picker(form: ParsedForm, target: String, onPick: (PwsRecord) -> Unit) {
        val rev by Vault.revision.collectAsState()
        val db = Vault.session.collectAsState().value?.db ?: return
        var query by remember { mutableStateOf("") }
        val matches = remember(rev) { Matcher.matches(db, form) }
        val list = remember(rev, query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) matches + db.records.filter { it !in matches }.sortedBy { it.title.lowercase() }
            else db.records.filter { r -> listOf(r.title, r.username, r.url, r.group).any { it.lowercase().contains(q) } }
                .sortedBy { it.title.lowercase() }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Выберите запись", style = MaterialTheme.typography.headlineSmall)
                    Text("для «$target»", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { setResult(Activity.RESULT_CANCELED); finish() }) { Icon(Icons.Rounded.Close, "Закрыть") }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Поиск") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            if (matches.isNotEmpty() && query.isBlank()) {
                Text(
                    "Подходящие",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { "r:" + System.identityHashCode(it) }) { r ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onPick(r) },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(r.title)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title.ifEmpty { "(без названия)" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOf(db.resolveUsername(r), urlHost(r.url)).filter { it.isNotEmpty() }.joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
