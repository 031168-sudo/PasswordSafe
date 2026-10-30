package com.example.passwordsafe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PwsFileTest {

    private fun sample(): PwsDatabase {
        val db = PwsFile.newDatabase("Мастер-пароль 1".toCharArray(), "Тест")
        val r = PwsRecord.create(1_700_000_000)
        r.title = "Почта"
        r.group = "Работа.Сервисы"
        r.username = "andrey"
        r.password = "секрет-123"
        r.url = "https://mail.example.com"
        r.notes = "строка 1\r\nстрока 2"
        r.fields.add(PwsField(0x1b, byteArrayOf(1, 2, 3))) // неизвестное поле
        db.records.add(r)
        return db
    }

    @Test
    fun roundTrip() {
        val bytes = PwsFile.write(sample(), "test")
        val db = PwsFile.read(bytes, "Мастер-пароль 1".toCharArray())
        assertEquals("Тест", db.name)
        assertEquals(1, db.records.size)
        val r = db.records[0]
        assertEquals("Почта", r.title)
        assertEquals(listOf("Работа", "Сервисы"), r.groupPath)
        assertEquals("секрет-123", r.password)
        assertEquals("строка 1\r\nстрока 2", r.notes)
        assertTrue(r.fields.any { it.type == 0x1b && it.data.contentEquals(byteArrayOf(1, 2, 3)) })
    }

    @Test
    fun wrongPassword() {
        val bytes = PwsFile.write(sample(), "test")
        try {
            PwsFile.read(bytes, "не тот".toCharArray()); fail()
        } catch (e: WrongPasswordException) { }
    }

    @Test
    fun tamperDetected() {
        val bytes = PwsFile.write(sample(), "test")
        bytes[200] = (bytes[200].toInt() xor 1).toByte()
        try {
            PwsFile.read(bytes, "Мастер-пароль 1".toCharArray()); fail()
        } catch (e: CorruptFileException) { }
    }

    @Test
    fun aliasResolved() {
        val db = sample()
        val base = db.records[0]
        val alias = PwsRecord.create(1_700_000_000).apply { title = "Алиас"; password = "[[${base.uuidHex}]]" }
        db.records.add(alias)
        assertEquals("секрет-123", db.resolvePassword(alias))
    }

    @Test
    fun groupEscaping() {
        assertEquals(listOf("a.b", "c"), PwsRecord.splitGroup("a\\.b.c"))
        assertEquals("a\\.b.c", PwsRecord.joinGroup(listOf("a.b", "c")))
    }

    @Test
    fun generator() {
        val pw = PasswordGenerator.generate(GeneratorOptions(length = 24))
        assertEquals(24, pw.length)
        assertTrue(pw.any { it.isDigit() } && pw.any { it.isUpperCase() } && pw.any { it.isLowerCase() })
    }
}
