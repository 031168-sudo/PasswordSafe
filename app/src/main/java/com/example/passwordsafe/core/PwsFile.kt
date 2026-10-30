package com.example.passwordsafe.core

import org.bouncycastle.crypto.engines.TwofishEngine
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.KeyParameter
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.MessageDigest

class WrongPasswordException : Exception("Неверный мастер-пароль")
class CorruptFileException(msg: String) : Exception(msg)

/**
 * Чтение и запись файлов Password Safe v3 (.psafe3) по спецификации formatV3.txt:
 * растяжение ключа SHA-256, Twofish-256 (ECB для ключей, CBC для данных), HMAC-SHA256.
 */
object PwsFile {
    private val TAG = "PWS3".toByteArray(Charsets.US_ASCII)
    private val EOF = "PWS3-EOFPWS3-EOF".toByteArray(Charsets.US_ASCII)
    const val MIN_ITER = 2048
    const val DEFAULT_ITER = 262144
    private const val PREAMBLE = 4 + 32 + 4 + 32 + 32 + 32 + 16 // 152
    private val DEFAULT_VERSION = byteArrayOf(0x0D, 0x03)

    /** Кодировки, в которых пробуем пароль: Password Safe для Windows в старых версиях брал кодовую страницу ANSI. */
    private val passwordCharsets: List<Charset> = buildList {
        add(Charsets.UTF_8)
        runCatching { add(Charset.forName("windows-1251")) }
    }

    fun isPsafe3(bytes: ByteArray) = bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(TAG)

    fun stretch(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(password)
        md.update(salt)
        var x = md.digest()
        repeat(iterations) { x = md.digest(x) }
        return x
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    private fun ecb(key: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray {
        val e = TwofishEngine()
        e.init(encrypt, KeyParameter(key))
        val out = ByteArray(data.size)
        var i = 0
        while (i < data.size) { e.processBlock(data, i, out, i); i += 16 }
        return out
    }

    private fun cbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val e = TwofishEngine()
        e.init(false, KeyParameter(key))
        val out = ByteArray(data.size)
        var prev = iv.copyOf()
        val tmp = ByteArray(16)
        var i = 0
        while (i < data.size) {
            e.processBlock(data, i, tmp, 0)
            for (j in 0 until 16) out[i + j] = (tmp[j].toInt() xor prev[j].toInt()).toByte()
            prev = data.copyOfRange(i, i + 16)
            i += 16
        }
        return out
    }

    private fun cbcEncrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val e = TwofishEngine()
        e.init(true, KeyParameter(key))
        val out = ByteArray(data.size)
        val prev = iv.copyOf()
        val tmp = ByteArray(16)
        var i = 0
        while (i < data.size) {
            for (j in 0 until 16) tmp[j] = (data[i + j].toInt() xor prev[j].toInt()).toByte()
            e.processBlock(tmp, 0, out, i)
            System.arraycopy(out, i, prev, 0, 16)
            i += 16
        }
        return out
    }

    /** Открыть базу. Пароль передаётся как CharArray, чтобы его можно было затереть. */
    fun read(bytes: ByteArray, password: CharArray): PwsDatabase {
        if (bytes.size < PREAMBLE + 16 + 32 || !isPsafe3(bytes)) throw CorruptFileException("Это не файл Password Safe v3")
        val salt = bytes.copyOfRange(4, 36)
        val iter = readLeInt(bytes, 36)
        if (iter < 0) throw CorruptFileException("Неверное число итераций")
        val hp = bytes.copyOfRange(40, 72)

        var stretched: ByteArray? = null
        for (cs in passwordCharsets) {
            val pwBytes = encode(password, cs) ?: continue
            val candidate = stretch(pwBytes, salt, iter)
            pwBytes.fill(0)
            if (MessageDigest.isEqual(sha256(candidate), hp)) { stretched = candidate; break }
            candidate.fill(0)
        }
        if (stretched == null) throw WrongPasswordException()
        return decryptBody(bytes, KeyMaterial(salt, iter, stretched))
    }

    /** Открыть базу уже известным растянутым ключом (при повторном чтении после изменения файла). */
    fun readWithKey(bytes: ByteArray, key: KeyMaterial): PwsDatabase {
        if (!isPsafe3(bytes)) throw CorruptFileException("Это не файл Password Safe v3")
        val salt = bytes.copyOfRange(4, 36)
        val iter = readLeInt(bytes, 36)
        if (!salt.contentEquals(key.salt) || iter != key.iterations) throw WrongPasswordException()
        if (!MessageDigest.isEqual(sha256(key.stretchedKey), bytes.copyOfRange(40, 72))) throw WrongPasswordException()
        return decryptBody(bytes, KeyMaterial(salt, iter, key.stretchedKey.copyOf()))
    }

    private fun encode(password: CharArray, cs: Charset): ByteArray? {
        val encoder = cs.newEncoder()
        if (!password.all { encoder.canEncode(it) }) return null
        val bb = encoder.encode(java.nio.CharBuffer.wrap(password))
        val out = ByteArray(bb.remaining())
        bb.get(out)
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    private fun decryptBody(bytes: ByteArray, key: KeyMaterial): PwsDatabase {
        val k = ecb(key.stretchedKey, bytes.copyOfRange(72, 104), false)
        val l = ecb(key.stretchedKey, bytes.copyOfRange(104, 136), false)
        val iv = bytes.copyOfRange(136, 152)

        // Ищем блок EOF: после него идут 32 байта HMAC.
        var eofPos = -1
        val expected = bytes.size - 48
        if (expected >= PREAMBLE && bytes.copyOfRange(expected, expected + 16).contentEquals(EOF)) eofPos = expected
        var p = PREAMBLE
        while (eofPos < 0 && p + 16 <= bytes.size) {
            if (bytes.copyOfRange(p, p + 16).contentEquals(EOF)) { eofPos = p; break }
            p += 16
        }
        if (eofPos < 0 || eofPos + 16 + 32 > bytes.size) throw CorruptFileException("Файл повреждён: нет маркера конца")
        val storedMac = bytes.copyOfRange(eofPos + 16, eofPos + 48)

        val plain = cbcDecrypt(k, iv, bytes.copyOfRange(PREAMBLE, eofPos))
        k.fill(0)

        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(l))
        l.fill(0)

        val header = mutableListOf<PwsField>()
        val records = mutableListOf<PwsRecord>()
        var current: PwsRecord? = null
        var inHeader = true
        var off = 0
        while (off + 16 <= plain.size) {
            val len = readLeInt(plain, off)
            val type = plain[off + 4].toInt() and 0xff
            if (len < 0 || off + 5 + len > plain.size) throw CorruptFileException("Файл повреждён: неверная длина поля")
            val data = plain.copyOfRange(off + 5, off + 5 + len)
            mac.update(data, 0, data.size)
            val total = 5 + len
            off += maxOf(16, (total + 15) / 16 * 16)

            if (inHeader) {
                if (type == HeaderType.END) inHeader = false else header.add(PwsField(type, data))
            } else {
                if (type == RecordType.END) {
                    current?.let { records.add(it) }
                    current = null
                } else {
                    if (current == null) current = PwsRecord()
                    current.fields.add(PwsField(type, data))
                }
            }
        }
        plain.fill(0)
        current?.let { records.add(it) } // запись без завершающего поля — не теряем

        val calc = ByteArray(32)
        mac.doFinal(calc, 0)
        if (!MessageDigest.isEqual(calc, storedMac)) throw CorruptFileException("Файл повреждён: не совпадает контрольная сумма (HMAC)")

        return PwsDatabase(header, records, key)
    }

    /** Сериализовать базу в байты .psafe3. */
    fun write(db: PwsDatabase, appName: String, nowSec: Long = System.currentTimeMillis() / 1000): ByteArray {
        // Обязательные поля заголовка: версия первой, время и автор сохранения.
        if (db.header.none { it.type == HeaderType.VERSION }) db.header.add(0, PwsField(HeaderType.VERSION, DEFAULT_VERSION.copyOf()))
        else {
            val v = db.header.first { it.type == HeaderType.VERSION }
            db.header.remove(v); db.header.add(0, v)
        }
        if (db.header.none { it.type == HeaderType.UUID }) {
            db.header.add(1, PwsField(HeaderType.UUID, randomBytes(16)))
        }
        db.setHeaderRaw(HeaderType.LAST_SAVE_TIME, leInt(nowSec.toInt()))
        db.setHeaderString(HeaderType.LAST_SAVE_WHAT, appName)

        val key = db.key
        val k = randomBytes(32)
        val l = randomBytes(32)
        val iv = randomBytes(16)
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(l))

        val body = ByteArrayOutputStream()
        fun putField(type: Int, data: ByteArray) {
            val total = 5 + data.size
            val size = maxOf(16, (total + 15) / 16 * 16)
            val block = randomBytes(size)
            System.arraycopy(leInt(data.size), 0, block, 0, 4)
            block[4] = type.toByte()
            System.arraycopy(data, 0, block, 5, data.size)
            body.write(block)
            mac.update(data, 0, data.size)
        }
        for (f in db.header) putField(f.type, f.data)
        putField(HeaderType.END, ByteArray(0))
        for (r in db.records) {
            for (f in r.fields) if (f.type != RecordType.END) putField(f.type, f.data)
            putField(RecordType.END, ByteArray(0))
        }
        val plain = body.toByteArray()
        val enc = cbcEncrypt(k, iv, plain)
        plain.fill(0)

        val out = ByteArrayOutputStream()
        out.write(TAG)
        out.write(key.salt)
        out.write(leInt(key.iterations))
        out.write(sha256(key.stretchedKey))
        out.write(ecb(key.stretchedKey, k, true))
        out.write(ecb(key.stretchedKey, l, true))
        out.write(iv)
        out.write(enc)
        out.write(EOF)
        val calc = ByteArray(32)
        mac.doFinal(calc, 0)
        out.write(calc)
        k.fill(0); l.fill(0)
        return out.toByteArray()
    }

    /** Проверить, что пароль соответствует ключу открытой базы. */
    fun verifyPassword(key: KeyMaterial, password: CharArray): Boolean {
        for (cs in passwordCharsets) {
            val pw = encode(password, cs) ?: continue
            val s = stretch(pw, key.salt, key.iterations)
            pw.fill(0)
            val ok = MessageDigest.isEqual(s, key.stretchedKey)
            s.fill(0)
            if (ok) return true
        }
        return false
    }

    /** Новый ключевой материал для пароля (новая база или смена мастер-пароля). */
    fun newKey(password: CharArray, iterations: Int = DEFAULT_ITER): KeyMaterial {
        val salt = randomBytes(32)
        val pw = encode(password, Charsets.UTF_8)!!
        val stretched = stretch(pw, salt, iterations)
        pw.fill(0)
        return KeyMaterial(salt, iterations, stretched)
    }

    fun newDatabase(password: CharArray, name: String): PwsDatabase {
        val db = PwsDatabase(
            mutableListOf(PwsField(HeaderType.VERSION, DEFAULT_VERSION.copyOf()), PwsField(HeaderType.UUID, randomBytes(16))),
            mutableListOf(),
            newKey(password),
        )
        if (name.isNotBlank()) db.name = name
        return db
    }
}
