package com.example.passwordsafe.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import com.example.passwordsafe.core.PwsDatabase
import com.example.passwordsafe.core.PwsRecord

/** Поля для заполнения, найденные на экране другого приложения. */
class ParsedForm(
    val usernameIds: List<AutofillId>,
    val passwordIds: List<AutofillId>,
    val webDomain: String?,
    val packageName: String,
) {
    val isEmpty get() = usernameIds.isEmpty() && passwordIds.isEmpty()
    val allIds get() = usernameIds + passwordIds
}

object StructureParser {
    private val userWords = listOf("user", "login", "email", "e-mail", "mail", "phone", "account", "логин", "почт", "телефон", "имя пользователя")
    private val passWords = listOf("pass", "pwd", "пароль", "pin")

    fun parse(structure: AssistStructure): ParsedForm {
        val users = mutableListOf<AutofillId>()
        val passwords = mutableListOf<AutofillId>()
        var domain: String? = null
        var lastText: AutofillId? = null

        fun visit(n: AssistStructure.ViewNode) {
            n.webDomain?.takeIf { it.isNotBlank() }?.let { if (domain == null) domain = it }
            val id = n.autofillId
            if (id != null && n.autofillType == View.AUTOFILL_TYPE_TEXT && n.visibility == View.VISIBLE) {
                when (classify(n)) {
                    Kind.PASSWORD -> {
                        passwords.add(id)
                        // Безымянное текстовое поле перед паролем — почти наверняка логин
                        if (users.isEmpty()) lastText?.let { users.add(it) }
                    }
                    Kind.USERNAME -> users.add(id)
                    Kind.TEXT -> lastText = id
                    Kind.NONE -> Unit
                }
            }
            for (i in 0 until n.childCount) visit(n.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
        return ParsedForm(users.distinct(), passwords.distinct(), domain?.removePrefix("www."), structure.activityComponent.packageName)
    }

    private enum class Kind { USERNAME, PASSWORD, TEXT, NONE }

    private fun classify(n: AssistStructure.ViewNode): Kind {
        val hints = n.autofillHints?.map { it.lowercase() } ?: emptyList()
        if (hints.any { it.contains("password") }) return Kind.PASSWORD
        if (hints.any { it.contains("username") || it.contains("email") || it.contains("phone") }) return Kind.USERNAME

        val html = n.htmlInfo
        if (html != null) {
            if (html.tag.equals("input", true)) {
                val type = html.attributes?.firstOrNull { it.first == "type" }?.second?.lowercase()
                when (type) {
                    "password" -> return Kind.PASSWORD
                    "email", "tel" -> return Kind.USERNAME
                    "hidden", "submit", "button", "checkbox", "radio", "search" -> return Kind.NONE
                }
                val name = html.attributes?.filter { it.first == "name" || it.first == "id" || it.first == "autocomplete" }
                    ?.joinToString(" ") { it.second ?: "" }?.lowercase() ?: ""
                if (passWords.any { name.contains(it) }) return Kind.PASSWORD
                if (userWords.any { name.contains(it) }) return Kind.USERNAME
                return Kind.TEXT
            }
            return Kind.NONE
        }

        val variation = n.inputType and InputType.TYPE_MASK_VARIATION
        val cls = n.inputType and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        ) return Kind.PASSWORD
        if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return Kind.PASSWORD
        if (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        ) return Kind.USERNAME

        val text = listOfNotNull(n.idEntry, n.hint, n.contentDescription?.toString()).joinToString(" ").lowercase()
        if (passWords.any { text.contains(it) }) return Kind.PASSWORD
        if (userWords.any { text.contains(it) }) return Kind.USERNAME
        if (n.className?.contains("EditText") == true || cls == InputType.TYPE_CLASS_TEXT) return Kind.TEXT
        return Kind.NONE
    }
}

/** Подбор записей базы под сайт или приложение. */
object Matcher {
    private val noise = setOf("com", "ru", "org", "net", "android", "app", "apps", "mobile", "client", "www", "google", "gms", "ui", "main", "release")

    fun query(form: ParsedForm): String = form.webDomain ?: form.packageName

    fun matches(db: PwsDatabase, form: ParsedForm): List<PwsRecord> {
        val domain = form.webDomain?.lowercase()
        return if (domain != null) {
            val base = baseDomain(domain)
            db.records.filter { r ->
                val host = hostOf(r.url)
                (host.isNotEmpty() && (host == domain || domain.endsWith(".$host") || host.endsWith(".$domain") || baseDomain(host) == base)) ||
                    (host.isEmpty() && r.title.lowercase().contains(base.substringBefore('.')))
            }
        } else {
            val tokens = form.packageName.lowercase().split('.', '_').filter { it.length >= 4 && it !in noise }
            if (tokens.isEmpty()) return emptyList()
            db.records.filter { r ->
                val hay = (r.title + " " + r.url + " " + r.notes).lowercase()
                hay.contains(form.packageName.lowercase()) || tokens.any { hay.contains(it) }
            }
        }.sortedBy { it.title.lowercase() }
    }

    private fun hostOf(url: String): String =
        url.trim().lowercase().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').removePrefix("www.")

    private fun baseDomain(host: String): String {
        val parts = host.split('.')
        if (parts.size <= 2) return host
        // Двухуровневые зоны вроде com.ru, co.uk, msk.ru
        val lastTwo = parts.takeLast(2).joinToString(".")
        return if (lastTwo in twoLevelZones) parts.takeLast(3).joinToString(".") else lastTwo
    }

    private val twoLevelZones = setOf(
        "com.ru", "net.ru", "org.ru", "msk.ru", "spb.ru", "msk.su", "co.uk", "org.uk", "com.ua", "co.ua",
        "com.by", "com.kz", "com.au", "co.jp", "com.br", "com.tr", "co.il", "com.cn",
    )
}
