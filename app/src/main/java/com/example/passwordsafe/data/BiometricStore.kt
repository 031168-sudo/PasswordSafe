package com.example.passwordsafe.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * Мастер-пароль, зашифрованный ключом из Android Keystore. Ключ доступен только после
 * подтверждения отпечатком и становится недействительным при добавлении нового отпечатка.
 */
object BiometricStore {
    private const val KEY_ALIAS = "pwsafe_master_key"
    private const val PREFS = "biometric"

    fun isAvailable(ctx: Context) =
        BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSaved(ctx: Context, uri: Uri) = prefs(ctx).contains(uri.toString())

    fun remove(ctx: Context, uri: Uri) = prefs(ctx).edit().remove(uri.toString()).apply()

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    private fun deleteKey() = runCatching { keyStore().deleteEntry(KEY_ALIAS) }

    /** Шифр для сохранения пароля (после подтверждения отпечатком). */
    fun encryptCipher(): Cipher {
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        }
    }

    /** Шифр для расшифровки; null — отпечатки изменились, сохранённый пароль больше недействителен. */
    fun decryptCipher(ctx: Context, uri: Uri): Cipher? {
        val stored = prefs(ctx).getString(uri.toString(), null) ?: return null
        val iv = Base64.decode(stored.substringBefore(':'), Base64.NO_WRAP)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            prefs(ctx).edit().clear().apply()
            null
        }
    }

    fun save(ctx: Context, uri: Uri, cipher: Cipher, password: CharArray) {
        val bb = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val plain = ByteArray(bb.remaining()).also { bb.get(it) }
        val ct = cipher.doFinal(plain)
        plain.fill(0)
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs(ctx).edit().putString(uri.toString(), value).apply()
    }

    fun load(ctx: Context, uri: Uri, cipher: Cipher): CharArray? {
        val stored = prefs(ctx).getString(uri.toString(), null) ?: return null
        val ct = Base64.decode(stored.substringAfter(':'), Base64.NO_WRAP)
        val plain = cipher.doFinal(ct)
        val cb = Charsets.UTF_8.decode(ByteBuffer.wrap(plain))
        plain.fill(0)
        return CharArray(cb.remaining()).also { cb.get(it) }
    }

    /** Показать запрос отпечатка. Возвращает подтверждённый шифр или null (отмена/ошибка). */
    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String, cipher: Cipher): Cipher? =
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(result.cryptoObject?.cipher)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Ввести пароль")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
}
