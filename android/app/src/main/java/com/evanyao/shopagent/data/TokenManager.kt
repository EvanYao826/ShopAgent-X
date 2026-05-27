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
        private val GENDER_KEY = stringPreferencesKey("gender")
        private val SKIN_TYPE_KEY = stringPreferencesKey("skin_type")
        private val PREFERENCE_TAGS_KEY = stringPreferencesKey("preference_tags")
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

    suspend fun saveGender(gender: String) {
        context.dataStore.edit { preferences ->
            preferences[GENDER_KEY] = gender
        }
    }

    suspend fun getGender(): String? {
        return context.dataStore.data.first()[GENDER_KEY]
    }

    suspend fun saveSkinType(skinType: String) {
        context.dataStore.edit { preferences ->
            preferences[SKIN_TYPE_KEY] = skinType
        }
    }

    suspend fun getSkinType(): String? {
        return context.dataStore.data.first()[SKIN_TYPE_KEY]
    }

    suspend fun savePreferenceTags(tags: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[PREFERENCE_TAGS_KEY] = tags.joinToString(",")
        }
    }

    suspend fun getPreferenceTags(): List<String> {
        val raw = context.dataStore.data.first()[PREFERENCE_TAGS_KEY]
        return raw?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
