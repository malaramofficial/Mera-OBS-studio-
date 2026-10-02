package com.malaramofficial.mobilestudio.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets

/**
 * Secure storage for sensitive streaming keys and credentials.
 * Includes sanitization methods to prevent credential leakage in logging.
 */
class SecureCredentialStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "malaram_studio_secure_creds",
        Context.MODE_PRIVATE
    )

    fun saveStreamKey(destinationId: String = "youtube", streamKey: String) {
        val encoded = Base64.encodeToString(streamKey.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        prefs.edit().putString(KEY_PREFIX + destinationId, encoded).apply()
    }

    fun storeStreamKey(streamKey: String, destinationId: String = "youtube") {
        saveStreamKey(destinationId, streamKey)
    }

    fun getStreamKey(destinationId: String = "youtube"): String {
        val encoded = prefs.getString(KEY_PREFIX + destinationId, null) ?: return ""
        return try {
            String(Base64.decode(encoded, Base64.NO_WRAP), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun clearCredentials(destinationId: String) {
        prefs.edit().remove(KEY_PREFIX + destinationId).apply()
    }

    companion object {
        private const val KEY_PREFIX = "rtmp_key_"

        /**
         * Sanitizes a stream key so it can be safely displayed in the UI (e.g. "••••••••1234").
         */
        fun maskStreamKey(key: String): String {
            if (key.length <= 4) return "••••"
            val lastFour = key.takeLast(4)
            return "••••••••$lastFour"
        }
    }
}
