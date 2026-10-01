package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ApiKeySource(val label: String) {
    USER_VAULT("Hardware Keystore Vault (AES-256-GCM)"),
    BUILD_CONFIG("AI Studio Secrets Environment"),
    NONE("Not Configured")
}

data class ApiKeyStatus(
    val isConfigured: Boolean,
    val source: ApiKeySource,
    val maskedPreview: String
)

/**
 * Hardware-backed Android Keystore manager for the Gemini API Key.
 * Never logs or exposes the raw API key in UI or debug output.
 */
class SecureKeyManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _keyStatus = MutableStateFlow(computeStatus())
    val keyStatus: StateFlow<ApiKeyStatus> = _keyStatus.asStateFlow()

    fun getEffectiveApiKey(): String {
        val vaultKey = readEncryptedKey()?.trim().orEmpty()
        if (vaultKey.isNotEmpty()) {
            return vaultKey
        }
        val envKey = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        if (isValidEnvKey(envKey)) {
            return envKey
        }
        return ""
    }

    fun hasValidKey(): Boolean = getEffectiveApiKey().isNotEmpty()

    fun saveApiKey(rawKey: String): Boolean {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

            prefs.edit()
                .putString(KEY_ENCRYPTED_DATA, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(KEY_ENCRYPTED_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putBoolean(KEY_ENV_DISABLED, false)
                .apply()

            _keyStatus.value = computeStatus()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteStoredApiKey() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_DATA)
            .remove(KEY_ENCRYPTED_IV)
            .putBoolean(KEY_ENV_DISABLED, true)
            .apply()
        _keyStatus.value = computeStatus()
    }

    fun restoreEnvironmentKeyIfAvailable() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_DATA)
            .remove(KEY_ENCRYPTED_IV)
            .putBoolean(KEY_ENV_DISABLED, false)
            .apply()
        _keyStatus.value = computeStatus()
    }

    private fun computeStatus(): ApiKeyStatus {
        val vaultKey = readEncryptedKey()?.trim().orEmpty()
        if (vaultKey.isNotEmpty()) {
            return ApiKeyStatus(
                isConfigured = true,
                source = ApiKeySource.USER_VAULT,
                maskedPreview = maskKey(vaultKey)
            )
        }
        val envDisabled = prefs.getBoolean(KEY_ENV_DISABLED, false)
        val envKey = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        if (!envDisabled && isValidEnvKey(envKey)) {
            return ApiKeyStatus(
                isConfigured = true,
                source = ApiKeySource.BUILD_CONFIG,
                maskedPreview = maskKey(envKey)
            )
        }
        return ApiKeyStatus(
            isConfigured = false,
            source = ApiKeySource.NONE,
            maskedPreview = "No API Key Active"
        )
    }

    private fun maskKey(key: String): String {
        if (key.length <= 6) return "••••••••"
        val suffix = key.takeLast(4)
        return "••••••••••••••••$suffix"
    }

    private fun isValidEnvKey(key: String): Boolean {
        return key.isNotEmpty() &&
            key != "MY_GEMINI_API_KEY" &&
            key != "YOUR_API_KEY" &&
            !key.startsWith("PLACEHOLDER")
    }

    private fun readEncryptedKey(): String? {
        val encDataB64 = prefs.getString(KEY_ENCRYPTED_DATA, null) ?: return null
        val ivB64 = prefs.getString(KEY_ENCRYPTED_IV, null) ?: return null
        return try {
            val encryptedBytes = Base64.decode(encDataB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_gemini_master_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "jarvis_secure_vault"
        private const val KEY_ENCRYPTED_DATA = "enc_gemini_key"
        private const val KEY_ENCRYPTED_IV = "enc_gemini_iv"
        private const val KEY_ENV_DISABLED = "env_key_disabled"
    }
}
