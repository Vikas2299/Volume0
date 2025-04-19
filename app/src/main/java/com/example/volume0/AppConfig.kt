package com.example.volume0

import android.content.Context
import android.content.SharedPreferences

data class AppConfig(
    val name: String,
    val packageName: String,
    var isEnabled: Boolean
)

object AppConfigManager {
    private const val PREFS_NAME = "AppConfigPrefs"
    private const val KEY_ENABLED_PREFIX = "enabled_"

    // List of supported apps, including Hulu
    val supportedApps = listOf(
        AppConfig("SoundCloud", "com.soundcloud.android", true),
//        AppConfig("YouTube", "com.google.android.apps.youtube.unplugged", true),
//        AppConfig("YouTube TV", "com.google.android.youtube.tv", true),
//        AppConfig("Spotify", "com.spotify.music", true),
//        AppConfig("Pandora", "com.pandora.android", true),
//        AppConfig("Hulu", "com.hulu.plus", true)
    )

    // Save toggle state
    fun saveToggleState(context: Context, packageName: String, isEnabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED_PREFIX + packageName, isEnabled).apply()
    }

    // Load toggle state
    fun loadToggleState(context: Context, packageName: String, default: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED_PREFIX + packageName, default)
    }

    // Initialize apps with saved states
    fun getAppConfigs(context: Context): List<AppConfig> {
        return supportedApps.map {
            AppConfig(it.name, it.packageName, loadToggleState(context, it.packageName, it.isEnabled))
        }
    }
}