package com.example.passwordsafe.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.passwordsafe.core.AppInfo
import com.example.passwordsafe.core.Category
import com.example.passwordsafe.core.IconMatch
import com.example.passwordsafe.core.PwsRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Иконки установленных приложений для записей (локально, без интернета). */
object AppIcons {
    var enabled by mutableStateOf(true)

    @Volatile private var apps: List<AppInfo>? = null
    private val pkgByKey = ConcurrentHashMap<String, String>() // "" — подходящего приложения нет
    private val bitmaps = ConcurrentHashMap<String, ImageBitmap>()

    private fun installed(ctx: Context): List<AppInfo> {
        apps?.let { return it }
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = runCatching {
            pm.queryIntentActivities(intent, 0).map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }
        }.getOrDefault(emptyList())
        apps = list
        return list
    }

    fun cached(key: String): ImageBitmap? = pkgByKey[key]?.takeIf { it.isNotEmpty() }?.let { bitmaps[it] }

    /** Вызывать не в главном потоке. */
    fun resolve(ctx: Context, key: String, title: String, url: String): ImageBitmap? {
        val pkg = pkgByKey.getOrPut(key) { IconMatch.bestApp(installed(ctx), title, url, ctx.packageName) ?: "" }
        if (pkg.isEmpty()) return null
        bitmaps[pkg]?.let { return it }
        val bmp = runCatching {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val size = 144
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(b))
            b.asImageBitmap()
        }.getOrNull() ?: return null
        bitmaps[pkg] = bmp
        return bmp
    }
}

private fun categoryVisual(c: Category): Pair<ImageVector, Color> = when (c) {
    Category.GOV -> Icons.Rounded.AccountBalance to Color(0xFF5B8DEF)
    Category.BANK -> Icons.Rounded.CreditCard to Color(0xFF2FB874)
    Category.EMAIL -> Icons.Rounded.Email to Color(0xFF4A90E2)
    Category.MOBILE -> Icons.Rounded.SimCard to Color(0xFFE5677A)
    Category.WIFI -> Icons.Rounded.Wifi to Color(0xFF34C3D6)
    Category.SMART_HOME -> Icons.Rounded.Home to Color(0xFFF2A65A)
    Category.SHOP -> Icons.Rounded.ShoppingBag to Color(0xFFA67CF0)
    Category.SOCIAL -> Icons.Rounded.Forum to Color(0xFF3B9BE8)
    Category.MEDIA -> Icons.Rounded.Movie to Color(0xFFE5484D)
    Category.GAMES -> Icons.Rounded.SportsEsports to Color(0xFF7B68EE)
    Category.CLOUD -> Icons.Rounded.Cloud to Color(0xFF6CA6E8)
    Category.SERVER -> Icons.Rounded.Dns to Color(0xFF8A94A8)
    Category.CAMERA -> Icons.Rounded.Videocam to Color(0xFF5FAF8A)
    Category.CAR -> Icons.Rounded.DirectionsCar to Color(0xFFE8A13A)
    Category.WORK -> Icons.Rounded.Work to Color(0xFF9C7A54)
    Category.WEBSITE -> Icons.Rounded.Language to Color(0xFF5B8DEF)
}

/** Значок записи: иконка установленного приложения → значок по смыслу → первая буква. */
@Composable
fun RecordAvatar(r: PwsRecord, size: Dp = 44.dp) {
    val ctx = LocalContext.current.applicationContext
    val title = r.title
    val url = r.url
    val key = title + "\n" + url
    val enabled = AppIcons.enabled
    val appIcon by produceState(if (enabled) AppIcons.cached(key) else null, key, enabled) {
        value = if (enabled) withContext(Dispatchers.IO) { AppIcons.resolve(ctx, key, title, url) } else null
    }
    val category = remember(key, r.group) { IconMatch.category(title, url, r.group) }
    val shape = RoundedCornerShape(size * 0.32f)

    val icon = appIcon
    when {
        icon != null -> Image(icon, contentDescription = null, modifier = Modifier.size(size))
        category != null -> {
            val (vector, color) = categoryVisual(category)
            Box(
                Modifier
                    .size(size)
                    .clip(shape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(vector, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
            }
        }
        else -> Avatar(title, size)
    }
}
