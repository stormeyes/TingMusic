package com.tingmusic.data

import android.content.Context
import com.tingmusic.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 用 SharedPreferences 持久化主题等设置。零依赖。 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tingmusic_settings", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(AppTheme.fromStored(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(t: AppTheme) {
        prefs.edit().putString(KEY_THEME, t.name).apply()
        _theme.value = t
    }

    private companion object { const val KEY_THEME = "theme" }
}
