package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import com.example.model.OptionArrangement
import com.example.model.QuestionColumnMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localeDataStore by preferencesDataStore(name = "shikkhascan_prefs")

class LocalePreferences(private val context: Context) {
    private val languageKey = stringPreferencesKey("app_language")
    private val geminiApiKeyPref = stringPreferencesKey("gemini_api_key")
    private val columnModePref = stringPreferencesKey("question_column_mode")
    private val optionArrangementPref = stringPreferencesKey("option_arrangement")

    val languageFlow: Flow<AppLanguage> = context.localeDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[languageKey])
    }

    val geminiApiKeyFlow: Flow<String> = context.localeDataStore.data.map { prefs ->
        prefs[geminiApiKeyPref].orEmpty()
    }

    val columnModeFlow: Flow<QuestionColumnMode> = context.localeDataStore.data.map { prefs ->
        QuestionColumnMode.fromRaw(prefs[columnModePref])
    }

    val optionArrangementFlow: Flow<OptionArrangement> = context.localeDataStore.data.map { prefs ->
        OptionArrangement.fromRaw(prefs[optionArrangementPref])
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.localeDataStore.edit { prefs ->
            prefs[languageKey] = language.code
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        val cleaned = apiKey.trim()
        context.localeDataStore.edit { prefs ->
            if (cleaned.isEmpty()) {
                prefs.remove(geminiApiKeyPref)
            } else {
                prefs[geminiApiKeyPref] = cleaned
            }
        }
    }

    suspend fun setColumnMode(mode: QuestionColumnMode) {
        context.localeDataStore.edit { prefs ->
            prefs[columnModePref] = mode.rawValue
        }
    }

    suspend fun setOptionArrangement(arrangement: OptionArrangement) {
        context.localeDataStore.edit { prefs ->
            prefs[optionArrangementPref] = arrangement.rawValue
        }
    }
}
