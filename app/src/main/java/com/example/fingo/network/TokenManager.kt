package com.example.fingo.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * JWT token'ını EncryptedSharedPreferences'ta güvenli şekilde saklar.
 * yapilanlar.md: "Token süresi 30 dakikadır. Token saklama için EncryptedSharedPreferences kullanın."
 */
object TokenManager {

    private const val PREFS_NAME = "fingo_secure_prefs"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_USERNAME = "username"

    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback: emülatör / eski cihaz uyumluluğu
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_TOKEN, null)
    }

    fun saveUsername(context: Context, username: String) {
        getPrefs(context).edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(context: Context): String? {
        return getPrefs(context).getString(KEY_USERNAME, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        return getToken(context) != null
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /** Token'ı kullanarak authenticated ApiService döner */
    fun getApiService(context: Context): ApiService? {
        val token = getToken(context) ?: return null
        return ApiClient.authenticated(token)
    }
}
