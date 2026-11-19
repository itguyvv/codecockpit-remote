package io.github.itguyvv.codecockpitremote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.itguyvv.codecockpitremote.net.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "connections")

/**
 * Persisted desktop profiles. Tokens and the pinned certificate fingerprint live here
 * too — the datastore is excluded from cloud backup / device transfer (see res/xml).
 */
class ConnectionStore(private val context: Context) {

    private val key = stringPreferencesKey("servers")
    private val onboardingKey = booleanPreferencesKey("onboarding_done")
    private val appLockKey = booleanPreferencesKey("app_lock_enabled")
    private val json = Json { ignoreUnknownKeys = true }

    /** Null until the first read completes, so the UI can avoid flashing the wrong gate */
    val onboardingDone: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[onboardingKey] ?: false
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[onboardingKey] = true }
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[appLockKey] ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[appLockKey] = enabled }
    }

    val servers: Flow<List<ServerProfile>> = context.dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        try {
            json.decodeFromString<List<ServerProfile>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun add(name: String, host: String, port: Int, secret: String): ServerProfile {
        val profile = ServerProfile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { host },
            host = host.trim(),
            port = port,
            secret = secret
        )
        update { it + profile }
        return profile
    }

    suspend fun remove(id: String) {
        update { list -> list.filterNot { it.id == id } }
    }

    suspend fun updateProfile(profile: ServerProfile) {
        update { list -> list.map { if (it.id == profile.id) profile else it } }
    }

    private suspend fun update(edit: (List<ServerProfile>) -> List<ServerProfile>) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                try {
                    json.decodeFromString<List<ServerProfile>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            prefs[key] = json.encodeToString(edit(current))
        }
    }
}
