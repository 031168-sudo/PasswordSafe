package com.example.passwordsafe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconMatchTest {
    private val apps = listOf(
        AppInfo("com.tuya.smart", "Tuya Smart"),
        AppInfo("com.xiaomi.smarthome", "Mi Home"),
        AppInfo("ru.sberbankmobile", "СберБанк"),
        AppInfo("ru.mail.mailapp", "Почта Mail"),
        AppInfo("com.example.passwordsafe", "Пароли"),
    )

    @Test
    fun appsByName() {
        assertEquals("com.tuya.smart", IconMatch.bestApp(apps, "Tuya", "", "com.example.passwordsafe"))
        assertEquals("com.xiaomi.smarthome", IconMatch.bestApp(apps, "Xiaomi", "https://account.xiaomi.com", "com.example.passwordsafe"))
        assertEquals("ru.sberbankmobile", IconMatch.bestApp(apps, "Сбербанк Онлайн", "", "com.example.passwordsafe"))
        assertNull(IconMatch.bestApp(apps, "Почта", "", "com.example.passwordsafe"))
        assertNull(IconMatch.bestApp(apps, "Пароли", "", "com.example.passwordsafe"))
    }

    @Test
    fun categories() {
        assertEquals(Category.EMAIL, IconMatch.category("andrey@gmail.com", "", ""))
        assertEquals(Category.EMAIL, IconMatch.category("Почта", "", ""))
        assertEquals(Category.SMART_HOME, IconMatch.category("Tuya", "", ""))
        assertEquals(Category.WIFI, IconMatch.category("Роутер дома", "192.168.1.1", ""))
        assertEquals(Category.SOCIAL, IconMatch.category("VK", "", ""))
        assertEquals(Category.WEBSITE, IconMatch.category("Альфаномия", "https://alfanomy.ru", ""))
        assertNull(IconMatch.category("Максим", "", ""))
    }
}
