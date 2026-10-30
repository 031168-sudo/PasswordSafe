package com.example.passwordsafe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.passwordsafe.core.GeneratorOptions
import com.example.passwordsafe.core.PasswordGenerator

private var lastOptions = GeneratorOptions()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorSheet(onDismiss: () -> Unit, onUse: ((String) -> Unit)?) {
    val ui = LocalUi.current
    var opts by remember { mutableStateOf(lastOptions) }
    var pw by remember { mutableStateOf(PasswordGenerator.generate(opts)) }

    fun update(o: GeneratorOptions) {
        opts = o
        lastOptions = o
        pw = PasswordGenerator.generate(o)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Генератор паролей", style = MaterialTheme.typography.titleLarge)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ColoredPassword(pw) }
                    IconButton(onClick = { pw = PasswordGenerator.generate(opts) }) { Icon(Icons.Rounded.Refresh, "Обновить") }
                }
            }
            StrengthMeter(pw)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Длина", Modifier.width(72.dp))
                Slider(
                    value = opts.length.toFloat(),
                    onValueChange = { update(opts.copy(length = it.toInt())) },
                    valueRange = 8f..64f,
                    modifier = Modifier.weight(1f),
                )
                Text("${opts.length}", Modifier.width(36.dp), style = MaterialTheme.typography.titleMedium)
            }
            ToggleRow("Строчные a–z", opts.lower) { update(opts.copy(lower = it)) }
            ToggleRow("Заглавные A–Z", opts.upper) { update(opts.copy(upper = it)) }
            ToggleRow("Цифры 0–9", opts.digits) { update(opts.copy(digits = it)) }
            ToggleRow("Символы !@#…", opts.symbols) { update(opts.copy(symbols = it)) }
            ToggleRow("Без похожих (l, 1, O, 0)", opts.excludeAmbiguous) { update(opts.copy(excludeAmbiguous = it)) }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { ui.copy("Пароль", pw) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
                if (onUse != null) Button(onClick = { onUse(pw) }, modifier = Modifier.weight(1f)) { Text("Использовать") }
            }
        }
    }
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
