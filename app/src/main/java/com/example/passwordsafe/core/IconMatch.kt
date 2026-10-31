package com.example.passwordsafe.core

/** Установленное приложение: пакет и подпись под иконкой. */
data class AppInfo(val pkg: String, val label: String)

enum class Category {
    GOV, BANK, EMAIL, MOBILE, WIFI, SMART_HOME, SHOP, SOCIAL, MEDIA, GAMES, CLOUD, SERVER, CAMERA, CAR, WORK, WEBSITE
}

/** Подбор значка для записи: по установленному приложению или по смыслу названия. Без Android-зависимостей. */
object IconMatch {
    private val split = Regex("[^\\p{L}\\p{N}]+")

    /** Слишком общие слова — по ним приложение не подбираем. */
    private val noise = setOf(
        "com", "ru", "org", "net", "www", "http", "https", "app", "apps", "mobile", "android", "client",
        "login", "user", "admin", "account", "аккаунт", "пароль", "password", "pass", "test", "тест",
        "home", "smart", "my", "мой", "моя", "личный", "кабинет", "online", "онлайн", "pro", "plus", "lite",
        "почта", "mail", "email", "банк", "bank", "карта", "карты", "card", "магазин", "shop", "сервер", "server", "роутер", "камера",
        "the", "and", "для", "new", "old", "старый", "новый", "main", "web", "site", "сайт", "pin", "code", "код",
    )

    private val twoLevelZones = setOf("com.ru", "net.ru", "org.ru", "msk.ru", "spb.ru", "co.uk", "com.ua", "com.by", "com.kz")

    /** Значимые слова: из названия и из имени сайта (без зоны). */
    fun tokens(title: String, url: String): List<String> {
        val words = title.lowercase().split(split).filter { it.isNotEmpty() }
        val host = url.trim().lowercase().substringAfter("://").substringBefore('/').substringBefore('?')
            .substringBefore(':').removePrefix("www.")
        val hostWords = if (host.contains('.')) {
            val parts = host.split('.')
            val zoneLen = if (parts.takeLast(2).joinToString(".") in twoLevelZones) 2 else 1
            parts.dropLast(zoneLen).flatMap { it.split('-') }
        } else emptyList()
        return (words + hostWords).filter { it.length >= 3 && it !in noise }.distinct()
    }

    /** Пакет установленного приложения, которое соответствует записи, или null. */
    fun bestApp(apps: List<AppInfo>, title: String, url: String, selfPkg: String): String? {
        val tokens = tokens(title, url)
        val full = title.trim().lowercase()
        if (tokens.isEmpty() && full.length < 3) return null
        var bestPkg: String? = null
        var bestScore = 0
        var bestLabelLen = Int.MAX_VALUE
        for (app in apps) {
            if (app.pkg == selfPkg) continue
            val label = app.label.trim().lowercase()
            val labelWords = label.split(split).filter { it.isNotEmpty() }
            val pkgParts = app.pkg.lowercase().split('.', '_')
            var score = if (full.length >= 3 && label == full) 4 else 0
            for (t in tokens) {
                if (t in labelWords) score = maxOf(score, 3)
                else if (t.length >= 4 && labelWords.any { it.length >= 4 && (it.startsWith(t) || t.startsWith(it)) }) score = maxOf(score, 2)
                if (t.length >= 4 && t in pkgParts) score = maxOf(score, 3)
                else if (t.length >= 5 && pkgParts.any { it.contains(t) }) score = maxOf(score, 2)
            }
            if (score > bestScore || (score == bestScore && score > 0 && label.length < bestLabelLen)) {
                bestScore = score; bestPkg = app.pkg; bestLabelLen = label.length
            }
        }
        return if (bestScore >= 2) bestPkg else null
    }

    // Короткие ключевые слова (до 3 букв) сравниваются целым словом, длинные — как часть текста.
    private val keywords: List<Pair<Category, List<String>>> = listOf(
        Category.GOV to listOf("госуслуг", "gosuslugi", "налог", "nalog", "фнс", "сфр", "пфр", "mos.ru", "мос.ру", "росреестр", "rosreestr", "gov", "пенсион"),
        Category.BANK to listOf(
            "банк", "bank", "сбер", "sber", "тинькофф", "tinkoff", "tbank", "втб", "vtb", "альфа-банк", "альфабанк", "alfabank", "alfa-bank",
            "газпромбанк", "райффайзен", "raiffeisen", "открытие", "совкомбанк", "халва", "qiwi", "киви", "yoomoney", "юмани", "paypal",
            "visa", "mastercard", "мир", "карта", "card", "кошел", "wallet", "брокер", "broker", "инвест", "invest", "crypto", "крипт", "binance",
        ),
        Category.EMAIL to listOf("mail", "почт", "outlook", "inbox", "rambler", "рамблер", "imap", "smtp", "pop3"),
        Category.MOBILE to listOf("мтс", "mts", "билайн", "beeline", "мегафон", "megafon", "tele2", "теле2", "yota", "йота", "sim", "сим", "puk", "оператор", "сотов"),
        Category.WIFI to listOf("wifi", "wi-fi", "вайфай", "вай-фай", "роутер", "router", "маршрутизатор", "keenetic", "кинетик", "mikrotik", "микротик", "tp-link", "tplink", "ssid", "wlan"),
        Category.SMART_HOME to listOf(
            "tuya", "туя", "smart life", "smartlife", "xiaomi", "сяоми", "ксиоми", "mi home", "mihome", "aqara", "акара", "yeelight", "sonoff",
            "ewelink", "умный дом", "умн", "алиса", "станци", "home assistant", "homekit", "zigbee", "розетк", "кондиц", "сплит",
        ),
        Category.SHOP to listOf(
            "ozon", "озон", "wildberries", "вайлдберриз", "вб", "wb", "aliexpress", "алиэкспресс", "али", "avito", "авито", "магазин", "shop",
            "market", "маркет", "lamoda", "ламода", "мвидео", "эльдорадо", "dns", "днс", "amazon", "ebay", "леруа", "lemana", "ikea", "икеа", "самокат", "лавка",
        ),
        Category.SOCIAL to listOf(
            "vk", "вк", "вконтакте", "vkontakte", "telegram", "телеграм", "whatsapp", "ватсап", "facebook", "фейсбук", "instagram", "инстаграм",
            "одноклассники", "ok", "twitter", "max", "discord", "skype", "скайп", "viber", "вайбер", "signal", "tiktok", "тикток", "дзен", "dzen", "linkedin",
        ),
        Category.MEDIA to listOf(
            "youtube", "ютуб", "rutube", "кинопоиск", "kinopoisk", "netflix", "ivi", "иви", "okko", "окко", "кион", "kion", "wink", "premier",
            "spotify", "music", "музык", "twitch", "кино", "tv", "тв", "iptv", "подписк",
        ),
        Category.GAMES to listOf("steam", "стим", "epic games", "playstation", "psn", "xbox", "nintendo", "battle.net", "blizzard", "игр", "game", "ubisoft", "origin", "wargaming", "lesta"),
        Category.CLOUD to listOf("cloud", "облак", "dropbox", "icloud", "google drive", "яндекс диск", "яндекс.диск", "disk.yandex", "onedrive", "nextcloud", "mega.nz", "диск", "drive", "backup", "бэкап"),
        Category.SERVER to listOf(
            "ssh", "server", "сервер", "vps", "vds", "github", "gitlab", "bitbucket", "ftp", "sftp", "хостинг", "hosting", "домен", "domain",
            "reg.ru", "timeweb", "beget", "selectel", "mysql", "postgres", "database", "база данных", "root", "админк", "cpanel", "proxmox",
            "docker", "jenkins", "api", "rdp", "vpn", "linux", "ubuntu", "nas", "synology",
        ),
        Category.CAMERA to listOf("камер", "camera", "видеонаблюд", "hikvision", "dahua", "ezviz", "imou", "ipcam", "nvr", "dvr", "регистратор", "домофон"),
        Category.CAR to listOf("авто", "auto", "машин", "car", "гибдд", "штраф", "осаго", "каско", "автокод", "drom", "дром", "парковк", "parking", "яндекс go", "такси", "taxi", "бензин", "азс"),
        Category.WORK to listOf("работ", "work", "офис", "office", "crm", "1с", "1c", "битрикс", "bitrix", "jira", "confluence", "slack", "zoom", "teams", "корп", "corp", "мойсклад", "контур", "сбис", "эдо", "бухгалт"),
    )

    fun category(title: String, url: String, group: String): Category? {
        val hay = listOf(title, url, group).joinToString(" ").lowercase()
        if (title.contains('@')) return Category.EMAIL
        val words = hay.split(split).filter { it.isNotEmpty() }.toSet()
        for ((cat, keys) in keywords) {
            if (keys.any { k -> if (k.length <= 3) k in words else hay.contains(k) }) return cat
        }
        return if (url.isNotBlank()) Category.WEBSITE else null
    }
}
