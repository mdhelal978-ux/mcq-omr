package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localeDataStore by preferencesDataStore(name = "shikkhascan_prefs")

class LocalePreferences(private val context: Context) {
    private val languageKey = stringPreferencesKey("app_language")

    val languageFlow: Flow<AppLanguage> = context.localeDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[languageKey])
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.localeDataStore.edit { prefs ->
            prefs[languageKey] = language.code
        }
    }
}
