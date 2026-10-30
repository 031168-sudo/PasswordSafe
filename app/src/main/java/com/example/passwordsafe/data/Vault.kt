package com.example.passwordsafe.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.passwordsafe.BuildConfig
import com.example.passwordsafe.core.PwsDatabase
import com.example.passwordsafe.core.PwsFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SaveResult {
    data object Ok : SaveResult()
    /** Файл на диске изменился с момента открытия (например, синхронизация с ПК). */
    data object Conflict : SaveResult()
    data class Error(val message: String) : SaveResult()
}

enum class RefreshResult { Unchanged, Reloaded, NeedsPassword, Unavailable }

/** Открытая база в памяти + файл, из которого она прочитана. */
class Session(val uri: Uri, val fileName: String, var db: PwsDatabase, var fileHash: ByteArray)

object Vault {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    /** Увеличивается при любом изменении данных — UI перечитывает списки. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    /** Есть изменения, которые не удалось записать в файл. */
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty

    /** Почему база была заблокирована (показывается на экране входа). */
    val lockMessage = MutableStateFlow<String?>(null)

    private val mutex = Mutex()
    private val appTag get() = "Сейф паролей Android ${BuildConfig.VERSION_NAME}"

    val isOpen get() = _session.value != null

    fun bump() { _revision.value++ }

    fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "База"
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    private fun readBytes(ctx: Context, uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось открыть файл")

    suspend fun open(ctx: Context, uri: Uri, password: CharArray) {
        val bytes = withContext(Dispatchers.IO) { readBytes(ctx, uri) }
        if (!PwsFile.isPsafe3(bytes)) throw IllegalArgumentException("Это не файл Password Safe (.psafe3)")
        val db = withContext(Dispatchers.Default) { PwsFile.read(bytes, password) }
        // Записям без UUID (очень старые файлы) выдаём его в памяти — так их можно однозначно адресовать.
        db.records.filter { it.uuidHex.length != 32 }.forEach { it.uuid = java.util.UUID.randomUUID() }
        val name = displayName(ctx, uri)
        mutex.withLock {
            _session.value?.db?.key?.wipe()
            _session.value = Session(uri, name, db, sha256(bytes))
            _dirty.value = false
        }
        lockMessage.value = null
        Prefs.touchRecent(uri, name)
        bump()
    }

    suspend fun create(ctx: Context, uri: Uri, dbName: String, password: CharArray) {
        val db = withContext(Dispatchers.Default) { PwsFile.newDatabase(password, dbName) }
        val bytes = withContext(Dispatchers.Default) { PwsFile.write(db, appTag) }
        withContext(Dispatchers.IO) { writeBytes(ctx, uri, bytes) }
        val name = displayName(ctx, uri)
        mutex.withLock {
            _session.value?.db?.key?.wipe()
            _session.value = Session(uri, name, db, sha256(bytes))
            _dirty.value = false
        }
        lockMessage.value = null
        Prefs.touchRecent(uri, name)
        bump()
    }

    fun lock(reason: String?) {
        val s = _session.value ?: return
        s.db.key.wipe()
        _session.value = null
        _dirty.value = false
        lockMessage.value = reason
        bump()
    }

    private fun writeBytes(ctx: Context, uri: Uri, bytes: ByteArray) {
        val cr = ctx.contentResolver
        val os = runCatching { cr.openOutputStream(uri, "wt") }.getOrNull() ?: cr.openOutputStream(uri, "w")
            ?: error("Нет доступа на запись")
        os.use { it.write(bytes); it.flush() }
    }

    /** Копия файла перед перезаписью — во внутренней памяти приложения (файл и так зашифрован). */
    private fun backup(ctx: Context, uri: Uri, bytes: ByteArray) {
        runCatching {
            val dir = File(ctx.filesDir, "backups/" + sha256(uri.toString().toByteArray()).take(6).joinToString("") { "%02x".format(it) })
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            File(dir, "$stamp.psafe3").writeBytes(bytes)
            dir.listFiles()?.sortedByDescending { it.name }?.drop(10)?.forEach { it.delete() }
        }
    }

    fun backupCount(ctx: Context): Int {
        val s = _session.value ?: return 0
        val dir = File(ctx.filesDir, "backups/" + sha256(s.uri.toString().toByteArray()).take(6).joinToString("") { "%02x".format(it) })
        return dir.listFiles()?.size ?: 0
    }

    /** Записать базу. Без force при изменении файла извне возвращает Conflict. */
    suspend fun save(ctx: Context, force: Boolean = false): SaveResult = mutex.withLock {
        val s = _session.value ?: return SaveResult.Error("База закрыта")
        try {
            val current = withContext(Dispatchers.IO) { runCatching { readBytes(ctx, s.uri) }.getOrNull() }
            if (current != null && !force && !sha256(current).contentEquals(s.fileHash)) {
                _dirty.value = true
                return SaveResult.Conflict
            }
            val bytes = withContext(Dispatchers.Default) { PwsFile.write(s.db, appTag) }
            withContext(Dispatchers.IO) {
                if (current != null) backup(ctx, s.uri, current)
                writeBytes(ctx, s.uri, bytes)
            }
            s.fileHash = sha256(bytes)
            _dirty.value = false
            SaveResult.Ok
        } catch (e: Exception) {
            _dirty.value = true
            SaveResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Изменить данные и сразу сохранить. */
    suspend fun edit(ctx: Context, change: (PwsDatabase) -> Unit): SaveResult {
        val s = _session.value ?: return SaveResult.Error("База закрыта")
        change(s.db)
        bump()
        return save(ctx)
    }

    /** Если файл изменился извне (синхронизация), перечитать его тем же ключом. */
    suspend fun refreshIfChanged(ctx: Context): RefreshResult {
        if (_dirty.value) return RefreshResult.Unchanged
        return mutex.withLock {
            val s = _session.value ?: return RefreshResult.Unchanged
            val bytes = withContext(Dispatchers.IO) { runCatching { readBytes(ctx, s.uri) }.getOrNull() }
                ?: return RefreshResult.Unavailable
            val hash = sha256(bytes)
            if (hash.contentEquals(s.fileHash)) return RefreshResult.Unchanged
            try {
                val db = withContext(Dispatchers.Default) { PwsFile.readWithKey(bytes, s.db.key) }
                s.db.key.wipe()
                s.db = db
                s.fileHash = hash
                bump()
                RefreshResult.Reloaded
            } catch (e: Exception) {
                RefreshResult.NeedsPassword
            }
        }
    }

    suspend fun changeMasterPassword(ctx: Context, newPassword: CharArray): SaveResult {
        val s = _session.value ?: return SaveResult.Error("База закрыта")
        val old = s.db.key
        val iterations = maxOf(old.iterations, PwsFile.MIN_ITER)
        s.db.key = withContext(Dispatchers.Default) { PwsFile.newKey(newPassword, iterations) }
        val r = save(ctx)
        if (r is SaveResult.Ok) old.wipe() else s.db.key = old
        return r
    }
}
