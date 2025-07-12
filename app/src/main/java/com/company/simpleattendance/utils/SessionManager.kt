package com.company.simpleattendance.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_EMP_CODE = "emp_code"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
    
    fun saveSession(token: String, userId: String, email: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }
    
    fun saveUserProfile(firstName: String?, empCode: String?) {
        prefs.edit()
            .putString(KEY_FIRST_NAME, firstName)
            .putString(KEY_EMP_CODE, empCode)
            .apply()
    }
    
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getFirstName(): String? = prefs.getString(KEY_FIRST_NAME, null)
    fun getEmpCode(): String? = prefs.getString(KEY_EMP_CODE, null)
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    
    fun getUserEmail(): String? = getEmail()
    
    fun clearSession() {
        prefs.edit().clear().apply()
    }
    
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}