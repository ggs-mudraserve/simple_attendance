package com.company.simpleattendance

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.company.simpleattendance.models.AuthResponse
import com.company.simpleattendance.models.Profile
import com.company.simpleattendance.network.ApiClient
import com.company.simpleattendance.utils.SessionManager
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class LoginActivity : AppCompatActivity() {
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var errorTextView: TextView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var apiClient: ApiClient
    private lateinit var sessionManager: SessionManager
    private lateinit var gson: Gson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        
        // Check if user is already logged in
        if (sessionManager.isLoggedIn()) {
            navigateToMain()
            return
        }
        
        setContentView(R.layout.activity_login)
        
        initViews()
        apiClient = ApiClient()
        gson = Gson()
        
        loginButton.setOnClickListener { performLogin() }
    }

    private fun initViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        errorTextView = findViewById(R.id.errorTextView)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun performLogin() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        
        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields")
            return
        }
        
        setLoading(true)
        hideError()
        
        // First check if user is already logged in on another device
        checkDeviceStatus(email) { canLogin ->
            if (canLogin) {
                authenticateUser(email, password)
            } else {
                setLoading(false)
                showError(getString(R.string.error_device_already_active))
            }
        }
    }

    private fun checkDeviceStatus(email: String, callback: (Boolean) -> Unit) {
        apiClient.makeRestRequest(
            endpoint = "/profile?email=eq.$email&select=android_login",
            token = null
        ) { success, response ->
            runOnUiThread {
                if (success) {
                    try {
                        val profileType = object : com.google.gson.reflect.TypeToken<Array<Profile>>() {}.type
                        val profiles = gson.fromJson<Array<Profile>>(response, profileType)
                        if (profiles.isNotEmpty()) {
                            callback(!profiles[0].android_login)
                        } else {
                            callback(true) // User doesn't exist, allow login attempt
                        }
                    } catch (e: JsonSyntaxException) {
                        callback(true)
                    }
                } else {
                    callback(true) // If check fails, allow login attempt
                }
            }
        }
    }

    private fun authenticateUser(email: String, password: String) {
        val loginData = mapOf(
            "email" to email,
            "password" to password
        )
        
        apiClient.makeAuthRequest(
            endpoint = "/token?grant_type=password",
            json = gson.toJson(loginData)
        ) { success, response ->
            runOnUiThread {
                if (success) {
                    try {
                        val authResponse = gson.fromJson(response, AuthResponse::class.java)
                        updateDeviceStatus(authResponse.user.id, authResponse.access_token) {
                            sessionManager.saveSession(
                                authResponse.access_token,
                                authResponse.user.id,
                                authResponse.user.email
                            )
                            navigateToMain()
                        }
                    } catch (e: JsonSyntaxException) {
                        setLoading(false)
                        showError(getString(R.string.error_invalid_credentials))
                    }
                } else {
                    setLoading(false)
                    showError(getString(R.string.error_invalid_credentials))
                }
            }
        }
    }

    private fun updateDeviceStatus(userId: String, token: String, callback: () -> Unit) {
        val deviceId = sessionManager.getDeviceId(this)
        val updateData = mapOf(
            "android_login" to true,
            "device_id" to deviceId
        )
        
        apiClient.makeRestRequest(
            endpoint = "/profile?id=eq.$userId",
            method = "PATCH",
            json = gson.toJson(updateData),
            token = token
        ) { success, _ ->
            runOnUiThread {
                setLoading(false)
                if (success) {
                    callback()
                } else {
                    showError("Failed to update device status")
                }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        emailEditText.isEnabled = !loading
        passwordEditText.isEnabled = !loading
    }

    private fun showError(message: String) {
        errorTextView.text = message
        errorTextView.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorTextView.visibility = View.GONE
    }
}