package com.example.data.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kyaathi_prefs")

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    TELUGU("te");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

class LanguagePreferences(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("app_language")

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        val saved = prefs[keyLanguage]
        if (saved != null) {
            AppLanguage.fromCode(saved)
        } else {
            val sysLang = Locale.getDefault().language
            if (sysLang == "te") AppLanguage.TELUGU else AppLanguage.ENGLISH
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[keyLanguage] = language.code
        }
    }
}

fun Context.createLocalizedContext(language: AppLanguage): Context {
    val locale = Locale(language.code)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }
val LocalLocalizedContext = staticCompositionLocalOf<Context> {
    error("LocalLocalizedContext not provided")
}
