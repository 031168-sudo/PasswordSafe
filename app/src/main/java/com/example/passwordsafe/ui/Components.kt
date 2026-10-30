package com.example.passwordsafe.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passwordsafe.core.PasswordGenerator

@Composable
fun Avatar(title: String, size: Dp = 44.dp) {
    val c = avatarColor(title.ifEmpty { "?" })
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.65f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color(0xFF0B1220),
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** Пароль с подсветкой цифр и спецсимволов. */
@Composable
fun ColoredPassword(pw: String, modifier: Modifier = Modifier) {
    val digit = MaterialTheme.colorScheme.secondary
    val symbol = Color(0xFFF2A65A)
    val text = buildAnnotatedString {
        for (ch in pw) {
            when {
                ch.isDigit() -> withStyle(SpanStyle(color = digit)) { append(ch) }
                !ch.isLetterOrDigit() -> withStyle(SpanStyle(color = symbol)) { append(ch) }
                else -> append(ch)
            }
        }
    }
    Text(text, style = PasswordTextStyle, modifier = modifier)
}

private val strengthLabels = listOf("Очень слабый", "Слабый", "Средний", "Надёжный", "Очень надёжный")
private val strengthColors = listOf(
    Color(0xFFE5484D), Color(0xFFF2A65A), Color(0xFFE8C547), Color(0xFF7BC96F), Color(0xFF3DDC97),
)

@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return
    val s = PasswordGenerator.strength(password)
    val bits = PasswordGenerator.entropyBits(password).toInt()
    val color by animateColorAsState(strengthColors[s], label = "c")
    val fraction by animateFloatAsState((s + 1) / 5f, label = "f")
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${strengthLabels[s]} · ≈$bits бит",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
fun AppLogo(size: Dp = 88.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF0E1626)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Shield,
            contentDescription = null,
            tint = Mint,
            modifier = Modifier.size(size * 0.6f),
        )
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = Color(0xFF0E1626),
            modifier = Modifier.size(size * 0.26f),
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(0.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
