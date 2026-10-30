package com.example.passwordsafe.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.UUID

/** Одно поле формата Password Safe v3: тип и сырые байты. Неизвестные поля сохраняются как есть. */
class PwsField(val type: Int, var data: ByteArray) {
    fun copy() = PwsField(type, data.copyOf())
}

/** Типы полей записи (formatV3.txt). */
object RecordType {
    const val UUID = 0x01
    const val GROUP = 0x02
    const val TITLE = 0x03
    const val USERNAME = 0x04
    const val NOTES = 0x05
    const val PASSWORD = 0x06
    const val CTIME = 0x07
    const val PMTIME = 0x08
    const val ATIME = 0x09
    const val XTIME = 0x0a
    const val RMTIME = 0x0c
    const val URL = 0x0d
    const val AUTOTYPE = 0x0e
    const val PWHIST = 0x0f
    const val EMAIL = 0x14
    const val PROTECTED = 0x15
    const val END = 0xff
}

/** Типы полей заголовка. */
object HeaderType {
    const val VERSION = 0x00
    const val UUID = 0x01
    const val LAST_SAVE_TIME = 0x04
    const val LAST_SAVE_WHAT = 0x06
    const val LAST_SAVE_USER = 0x07
    const val LAST_SAVE_HOST = 0x08
    const val DB_NAME = 0x09
    const val DB_DESCRIPTION = 0x0a
    const val EMPTY_GROUPS = 0x11
    const val END = 0xff
}

private val rng = SecureRandom()

internal fun randomBytes(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

internal fun leInt(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

internal fun readLeInt(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8) or
        ((b[off + 2].toInt() and 0xff) shl 16) or ((b[off + 3].toInt() and 0xff) shl 24)

/** Запись (одна учётная запись). Хранит все поля по порядку, включая неизвестные. */
class PwsRecord(val fields: MutableList<PwsField> = mutableListOf()) {

    private fun field(type: Int) = fields.firstOrNull { it.type == type }

    fun getString(type: Int): String = field(type)?.data?.toString(Charsets.UTF_8) ?: ""

    fun setString(type: Int, value: String) {
        val existing = field(type)
        if (value.isEmpty()) {
            if (existing != null) fields.remove(existing)
        } else if (existing != null) {
            existing.data = value.toByteArray(Charsets.UTF_8)
        } else {
            fields.add(PwsField(type, value.toByteArray(Charsets.UTF_8)))
        }
    }

    /** Время в секундах (time_t, 4 или 8 байт LE), 0 если нет. */
    fun getTime(type: Int): Long {
        val d = field(type)?.data ?: return 0
        return when (d.size) {
            4 -> readLeInt(d, 0).toLong() and 0xffffffffL
            8 -> ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).long
            else -> 0
        }
    }

    fun setTime(type: Int, seconds: Long) {
        val data = leInt(seconds.toInt())
        val existing = field(type)
        if (existing != null) existing.data = data else fields.add(PwsField(type, data))
    }

    var uuid: UUID
        get() {
            val d = field(RecordType.UUID)?.data
            if (d == null || d.size != 16) return UUID(0, 0)
            val bb = ByteBuffer.wrap(d)
            return UUID(bb.long, bb.long)
        }
        set(value) {
            val data = ByteBuffer.allocate(16).putLong(value.mostSignificantBits).putLong(value.leastSignificantBits).array()
            val existing = field(RecordType.UUID)
            if (existing != null) existing.data = data else fields.add(0, PwsField(RecordType.UUID, data))
        }

    /** UUID в виде 32 hex-символов, как в ссылках-алиасах [[...]]. */
    val uuidHex: String get() = field(RecordType.UUID)?.data?.joinToString("") { "%02x".format(it) } ?: ""

    var title: String
        get() = getString(RecordType.TITLE)
        set(v) = setString(RecordType.TITLE, v)
    var group: String
        get() = getString(RecordType.GROUP)
        set(v) = setString(RecordType.GROUP, v)
    var username: String
        get() = getString(RecordType.USERNAME)
        set(v) = setString(RecordType.USERNAME, v)
    var password: String
        get() = getString(RecordType.PASSWORD)
        set(v) = setString(RecordType.PASSWORD, v)
    var notes: String
        get() = getString(RecordType.NOTES)
        set(v) = setString(RecordType.NOTES, v)
    var url: String
        get() = getString(RecordType.URL)
        set(v) = setString(RecordType.URL, v)
    var email: String
        get() = getString(RecordType.EMAIL)
        set(v) = setString(RecordType.EMAIL, v)

    val createdAt get() = getTime(RecordType.CTIME)
    val modifiedAt get() = maxOf(getTime(RecordType.RMTIME), getTime(RecordType.PMTIME), getTime(RecordType.CTIME))
    val passwordChangedAt get() = getTime(RecordType.PMTIME)

    /** Группа как список уровней (в pwsafe уровни разделены точкой, точка в имени экранируется "\."). */
    val groupPath: List<String> get() = splitGroup(group)

    fun deepCopy() = PwsRecord(fields.map { it.copy() }.toMutableList())

    /** Обновляет пароль с учётом истории паролей (поле 0x0f), если она включена в записи. */
    fun changePassword(newPassword: String, nowSec: Long) {
        val old = password
        if (old == newPassword) return
        val hist = PasswordHistory.parse(getString(RecordType.PWHIST))
        if (hist != null && hist.enabled && old.isNotEmpty()) {
            val changed = if (passwordChangedAt != 0L) passwordChangedAt else createdAt
            hist.add(changed, old)
            setString(RecordType.PWHIST, hist.serialize())
        }
        password = newPassword
        setTime(RecordType.PMTIME, nowSec)
    }

    companion object {
        fun create(nowSec: Long): PwsRecord = PwsRecord().apply {
            uuid = UUID.randomUUID()
            setTime(RecordType.CTIME, nowSec)
            setTime(RecordType.PMTIME, nowSec)
        }

        fun splitGroup(group: String): List<String> {
            if (group.isEmpty()) return emptyList()
            val parts = mutableListOf<String>()
            val sb = StringBuilder()
            var i = 0
            while (i < group.length) {
                val c = group[i]
                if (c == '\\' && i + 1 < group.length && group[i + 1] == '.') {
                    sb.append('.'); i += 2; continue
                }
                if (c == '.') { parts.add(sb.toString()); sb.clear() } else sb.append(c)
                i++
            }
            parts.add(sb.toString())
            return parts
        }

        fun joinGroup(path: List<String>): String = path.joinToString(".") { it.replace(".", "\\.") }
    }
}

/** История паролей: "fmmnn" + nn записей "TTTTTTTTLLLL<пароль>" (hex). */
class PasswordHistory(var enabled: Boolean, var max: Int, val items: MutableList<Pair<Long, String>>) {
    fun add(time: Long, pw: String) {
        items.add(time to pw)
        while (items.size > max && items.isNotEmpty()) items.removeAt(0)
    }

    fun serialize(): String = buildString {
        append(if (enabled) '1' else '0')
        append("%02x".format(max))
        append("%02x".format(items.size))
        for ((t, p) in items) {
            append("%08x".format(t))
            append("%04x".format(p.length))
            append(p)
        }
    }

    companion object {
        fun parse(s: String): PasswordHistory? {
            if (s.length < 5) return null
            return try {
                val enabled = s[0] == '1'
                val max = s.substring(1, 3).toInt(16)
                val n = s.substring(3, 5).toInt(16)
                var pos = 5
                val items = mutableListOf<Pair<Long, String>>()
                repeat(n) {
                    val t = s.substring(pos, pos + 8).toLong(16); pos += 8
                    val len = s.substring(pos, pos + 4).toInt(16); pos += 4
                    items.add(t to s.substring(pos, pos + len)); pos += len
                }
                PasswordHistory(enabled, max, items)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Ключевой материал открытой базы: позволяет сохранять файл, не храня мастер-пароль. */
class KeyMaterial(val salt: ByteArray, val iterations: Int, val stretchedKey: ByteArray) {
    fun wipe() {
        stretchedKey.fill(0)
    }
}

/** Открытая база паролей. */
class PwsDatabase(
    val header: MutableList<PwsField>,
    val records: MutableList<PwsRecord>,
    var key: KeyMaterial,
) {
    private fun headerField(type: Int) = header.firstOrNull { it.type == type }

    var name: String
        get() = headerField(HeaderType.DB_NAME)?.data?.toString(Charsets.UTF_8) ?: ""
        set(value) = setHeaderString(HeaderType.DB_NAME, value)

    val lastSaveTime: Long
        get() = headerField(HeaderType.LAST_SAVE_TIME)?.data?.let {
            if (it.size >= 4) readLeInt(it, 0).toLong() and 0xffffffffL else 0
        } ?: 0

    val emptyGroups: List<String>
        get() = header.filter { it.type == HeaderType.EMPTY_GROUPS }.map { it.data.toString(Charsets.UTF_8) }

    fun setHeaderString(type: Int, value: String) {
        val f = headerField(type)
        if (f != null) f.data = value.toByteArray(Charsets.UTF_8)
        else header.add(PwsField(type, value.toByteArray(Charsets.UTF_8)))
    }

    fun setHeaderRaw(type: Int, value: ByteArray) {
        val f = headerField(type)
        if (f != null) f.data = value else header.add(PwsField(type, value))
    }

    fun findByUuidHex(hex: String): PwsRecord? = records.firstOrNull { it.uuidHex.equals(hex, ignoreCase = true) }

    /** Пароль записи с учётом алиасов: [[uuid]] и [~uuid~] ссылаются на пароль другой записи. */
    fun resolvePassword(r: PwsRecord): String {
        val pw = r.password
        val m = ALIAS.matchEntire(pw) ?: return pw
        val target = findByUuidHex(m.groupValues[1] + m.groupValues[2]) ?: return pw
        return if (target === r) pw else target.password
    }

    fun resolveUsername(r: PwsRecord): String {
        val m = ALIAS.matchEntire(r.password) ?: return r.username
        if (r.username.isNotEmpty()) return r.username
        return findByUuidHex(m.groupValues[1] + m.groupValues[2])?.username ?: ""
    }

    fun allGroups(): List<String> =
        (records.map { it.group } + emptyGroups).filter { it.isNotEmpty() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    companion object {
        private val ALIAS = Regex("""\[\[([0-9a-fA-F]{32})]]|\[~([0-9a-fA-F]{32})~]""")
    }
}
