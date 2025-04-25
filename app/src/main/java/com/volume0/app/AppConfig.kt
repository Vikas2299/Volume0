package com.volume0.app

import android.content.Context

data class AppConfig(
    val name: String,
    val packageName: String,
    var isEnabled: Boolean,
    var adVolume: Int = 0
)

object AppConfigManager {
    private const val PREFS_NAME = "AppConfigPrefs"
    private const val KEY_ENABLED_PREFIX = "enabled_"
    private const val KEY_VOLUME_PREFIX = "volume_"

    val supportedApps = listOf(
        AppConfig("SoundCloud", "com.soundcloud.android", true, 0),
        AppConfig("Spotify", "com.spotify.music", true, 0),
    )

    fun saveToggleState(context: Context, packageName: String, isEnabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED_PREFIX + packageName, isEnabled).apply()
    }

    fun saveAdVolume(context: Context, packageName: String, adVolume: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_VOLUME_PREFIX + packageName, adVolume).apply()
    }

    fun loadToggleState(context: Context, packageName: String, default: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED_PREFIX + packageName, default)
    }

    fun loadAdVolume(context: Context, packageName: String, default: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_VOLUME_PREFIX + packageName, default)
    }

    fun getAppConfigs(context: Context): List<AppConfig> {
        return supportedApps.map {
            AppConfig(
                it.name,
                it.packageName,
                loadToggleState(context, it.packageName, it.isEnabled),
                loadAdVolume(context, it.packageName, it.adVolume)
            )
        }
    }
}