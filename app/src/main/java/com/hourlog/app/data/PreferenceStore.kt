package com.hourlog.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.hourlog.app.domain.Preferences
import com.hourlog.app.export.BackupCodec
import kotlinx.coroutines.flow.map

private val Context.preferenceDataStore by preferencesDataStore("hourlog_preferences")
class PreferenceStore(context: Context) {
    private val store = context.preferenceDataStore
    private val key = stringPreferencesKey("settings_v1")
    val flow = store.data.map { it[key]?.let(BackupCodec::decodePreferences) ?: Preferences() }
    suspend fun replace(prefs: Preferences) {
        prefs.validate()
        store.edit { it[key] = BackupCodec.encodePreferences(prefs) }
    }
}
