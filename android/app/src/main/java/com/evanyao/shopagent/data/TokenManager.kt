package com.evanyao.shopagent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class TokenManager(
    private val context: Context
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val PHONE_KEY = stringPreferencesKey("phone")
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    suspend fun getToken(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun getRefreshToken(): String? {
        return context.dataStore.data.first()[REFRESH_TOKEN_KEY]
    }

    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_TOKEN_KEY] = token
        }
    }

    suspend fun getUserId(): Long? {
        return context.dataStore.data.first()[USER_ID_KEY]?.toLongOrNull()
    }

    suspend fun saveUserId(userId: Long) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId.toString()
        }
    }

    suspend fun getUsername(): String? {
        return context.dataStore.data.first()[USERNAME_KEY]
    }

    suspend fun saveUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
        }
    }

    suspend fun getPhone(): String? {
        return context.dataStore.data.first()[PHONE_KEY]
    }

    suspend fun savePhone(phone: String) {
        context.dataStore.edit { preferences ->
            preferences[PHONE_KEY] = phone
        }
    }

    suspend fun isProfileCompleted(): Boolean {
        val userId = getUserId() ?: return false
        val key = booleanPreferencesKey("profile_completed_$userId")
        return context.dataStore.data.first()[key] ?: false
    }

    suspend fun setProfileCompleted() {
        val userId = getUserId() ?: return
        val key = booleanPreferencesKey("profile_completed_$userId")
        context.dataStore.edit { preferences ->
            preferences[key] = true
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
