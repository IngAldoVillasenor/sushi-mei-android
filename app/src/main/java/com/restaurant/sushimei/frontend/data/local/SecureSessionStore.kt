package com.restaurant.sushimei.frontend.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.restaurant.sushimei.frontend.data.model.AuthResponseDto
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ISecureSessionStore {
    fun saveSession(session: AuthResponseDto)
    fun getSession(): AuthResponseDto?
    fun clearSession()
}

/**
 * Secure Session Storage using AndroidKeyStore (AES/GCM/NoPadding).
 * Excludes the usage of deprecated EncryptedSharedPreferences and MasterKey.
 */
class SecureSessionStore(context: Context) : ISecureSessionStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    override fun saveSession(session: AuthResponseDto) {
        try {
            val json = gson.toJson(session)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

            prefs.edit()
                .putString(KEY_IV, ivBase64)
                .putString(KEY_CIPHERTEXT, ciphertextBase64)
                .apply()
        } catch (e: Exception) {
            clearSession()
            throw e
        }
    }

    override fun getSession(): AuthResponseDto? {
        try {
            val ivBase64 = prefs.getString(KEY_IV, null)
            val ciphertextBase64 = prefs.getString(KEY_CIPHERTEXT, null)

            if (ivBase64 == null || ciphertextBase64 == null) return null

            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val plaintext = cipher.doFinal(ciphertext)
            val json = String(plaintext, Charsets.UTF_8)
            return gson.fromJson(json, AuthResponseDto::class.java)
        } catch (e: Exception) {
            // Ignore exception logging for cryptography
            // If the key is invalidated or decryption fails, clear the unreadable session
            clearSession()
            return null
        }
    }

    override fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "secure_auth_prefs"
        private const val KEY_ALIAS = "sushimei_auth_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
    }
}
