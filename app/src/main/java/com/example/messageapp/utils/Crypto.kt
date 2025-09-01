package com.example.messageapp.utils

import android.util.Base64
import com.example.messageapp.BuildConfig
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Criptografia AES-GCM (256 bits)
object Crypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ALGO = "AES"
    private const val IV_LEN = 12
    private const val TAG_LEN_BITS = 128
    private const val PREFIX = "gcm:"

    private val keyBytes: ByteArray by lazy {
        val b64 = BuildConfig.APP_CRYPT_KEY_B64
        val k = try {
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (e: Throwable) {
            throw IllegalStateException("APP_CRYPT_KEY_B64 inválida (Base64).", e)
        }
        require(k.size == 32) { "APP_CRYPT_KEY_B64 deve ter 32 bytes (256 bits) após Base64 decode." }
        k
    }
    private val secretKey by lazy { SecretKeySpec(keyBytes, KEY_ALGO) }
    private val rng = SecureRandom()

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LEN_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val out = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ct, 0, out, iv.size, ct.size)

        return PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(enc: String?): String {
        if (enc.isNullOrBlank()) return ""
        // Novo formato com AES-GCM
        if (enc.startsWith(PREFIX)) {
            val raw = Base64.decode(enc.removePrefix(PREFIX), Base64.NO_WRAP)
            require(raw.size > IV_LEN) { "Ciphertext curto demais." }
            val iv = raw.copyOfRange(0, IV_LEN)
            val ct = raw.copyOfRange(IV_LEN, raw.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LEN_BITS, iv))
            val plain = cipher.doFinal(ct)
            return String(plain, Charsets.UTF_8)
        }

        return try {
            String(Base64.decode(enc, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: Throwable) {
            enc
        }
    }
}