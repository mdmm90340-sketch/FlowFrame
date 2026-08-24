package com.flowframe.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val wifiOnly: Boolean = false,
    val maxConcurrentDownloads: Int = 2,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = false,
)

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("flowframe_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())

    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setWifiOnly(value: Boolean) = update(_state.value.copy(wifiOnly = value))

    fun setMaxConcurrentDownloads(value: Int) = update(
        _state.value.copy(maxConcurrentDownloads = value.coerceIn(1, 3)),
    )

    fun setTheme(value: ThemePreference) = update(_state.value.copy(theme = value))

    fun setDynamicColor(value: Boolean) = update(_state.value.copy(dynamicColor = value))

    private fun read(): AppSettings = AppSettings(
        wifiOnly = preferences.getBoolean(KEY_WIFI_ONLY, false),
        maxConcurrentDownloads = preferences.getInt(KEY_MAX_CONCURRENT, 2).coerceIn(1, 3),
        theme = runCatching {
            ThemePreference.valueOf(preferences.getString(KEY_THEME, ThemePreference.SYSTEM.name).orEmpty())
        }.getOrDefault(ThemePreference.SYSTEM),
        dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, false),
    )

    private fun update(value: AppSettings) {
        preferences.edit()
            .putBoolean(KEY_WIFI_ONLY, value.wifiOnly)
            .putInt(KEY_MAX_CONCURRENT, value.maxConcurrentDownloads)
            .putString(KEY_THEME, value.theme.name)
            .putBoolean(KEY_DYNAMIC_COLOR, value.dynamicColor)
            .apply()
        _state.value = value
    }

    private companion object {
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_MAX_CONCURRENT = "max_concurrent"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}

