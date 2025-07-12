package com.company.simpleattendance

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.company.simpleattendance.models.Attendance
import com.company.simpleattendance.models.Profile
import com.company.simpleattendance.models.WifiAllowed
import com.company.simpleattendance.network.ApiClient
import com.company.simpleattendance.utils.SessionManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var greetingTextView: TextView
    private lateinit var loginIdTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var markInButton: Button
    private lateinit var markOutButton: Button
    private lateinit var viewAttendanceButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var apiClient: ApiClient
    private lateinit var sessionManager: SessionManager
    private lateinit var gson: Gson
    private lateinit var wifiManager: WifiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        
        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin()
            return
        }
        
        setContentView(R.layout.activity_main)
        
        initViews()
        apiClient = ApiClient()
        gson = Gson()
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        setupClickListeners()
        updateDateDisplay()
        loadUserProfile()
        loadTodayAttendance()
    }

    private fun initViews() {
        greetingTextView = findViewById(R.id.greetingTextView)
        loginIdTextView = findViewById(R.id.loginIdTextView)
        dateTextView = findViewById(R.id.dateTextView)
        markInButton = findViewById(R.id.markInButton)
        markOutButton = findViewById(R.id.markOutButton)
        viewAttendanceButton = findViewById(R.id.viewAttendanceButton)
        statusTextView = findViewById(R.id.statusTextView)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        markInButton.setOnClickListener { markAttendance(true) }
        markOutButton.setOnClickListener { markAttendance(false) }
        viewAttendanceButton.setOnClickListener { 
            startActivity(Intent(this, CalendarActivity::class.java))
        }
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        dateTextView.text = dateFormat.format(Date())
    }

    private fun loadUserProfile() {
        // Check if profile data is already cached
        val cachedFirstName = sessionManager.getFirstName()
        val cachedEmpCode = sessionManager.getEmpCode()
        
        if (cachedFirstName != null && cachedEmpCode != null) {
            // Use cached data
            greetingTextView.text = "Hi $cachedFirstName"
            loginIdTextView.text = "Login ID: $cachedEmpCode"
            return
        }
        
        // Fetch from API if not cached
        val userId = sessionManager.getUserId() ?: return
        
        apiClient.makeRestRequest(
            endpoint = "/profile?id=eq.$userId&select=first_name,emp_code",
            token = sessionManager.getToken()
        ) { success, response ->
            runOnUiThread {
                if (success) {
                    try {
                        val profileType = object : com.google.gson.reflect.TypeToken<Array<Profile>>() {}.type
                        val profiles = gson.fromJson<Array<Profile>>(response, profileType)
                        if (profiles.isNotEmpty()) {
                            val firstName = profiles[0].first_name ?: "User"
                            val empCode = profiles[0].emp_code ?: "N/A"
                            
                            // Save to local storage
                            sessionManager.saveUserProfile(firstName, empCode)
                            
                            // Update UI
                            greetingTextView.text = "Hi $firstName"
                            loginIdTextView.text = "Login ID: $empCode"
                        } else {
                            greetingTextView.text = "Hi User"
                            loginIdTextView.text = "Login ID: N/A"
                        }
                    } catch (e: JsonSyntaxException) {
                        greetingTextView.text = "Hi User"
                        loginIdTextView.text = "Login ID: N/A"
                    }
                } else {
                    greetingTextView.text = "Hi User"
                    loginIdTextView.text = "Login ID: N/A"
                }
            }
        }
    }

    private fun loadTodayAttendance() {
        setLoading(true)
        val userId = sessionManager.getUserId() ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        apiClient.makeRestRequest(
            endpoint = "/attendance?employee_id=eq.$userId&attendance_date=eq.$today&select=in_time,out_time",
            token = sessionManager.getToken()
        ) { success, response ->
            runOnUiThread {
                setLoading(false)
                if (success) {
                    try {
                        val attendanceType = object : com.google.gson.reflect.TypeToken<Array<Attendance>>() {}.type
                        val attendanceList = gson.fromJson<Array<Attendance>>(response, attendanceType)
                        updateButtonStates(if (attendanceList.isNotEmpty()) attendanceList[0] else null)
                    } catch (e: JsonSyntaxException) {
                        Toast.makeText(this@MainActivity, "Error parsing attendance data", Toast.LENGTH_SHORT).show()
                        updateButtonStates(null)
                    }
                } else {
                    if (response.contains("401")) {
                        handleSessionExpired()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to load today's attendance: $response", Toast.LENGTH_LONG).show()
                        updateButtonStates(null)
                    }
                }
            }
        }
    }

    private fun updateButtonStates(attendance: Attendance?) {
        if (attendance?.in_time != null) {
            // User has marked IN
            markInButton.isEnabled = false
            markInButton.setBackgroundColor(ContextCompat.getColor(this, R.color.present_color))
            markOutButton.isEnabled = true
        } else {
            // User hasn't marked IN yet
            markInButton.isEnabled = true
            markInButton.setBackgroundColor(ContextCompat.getColor(this, R.color.success_color))
            markOutButton.isEnabled = false
        }
    }

    private fun markAttendance(isMarkIn: Boolean) {
        validateWifiAndMark(isMarkIn)
    }

    private fun validateWifiAndMark(isMarkIn: Boolean) {
        // Check location permission (required for BSSID access on Android 6+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }
        
        if (!wifiManager.isWifiEnabled) {
            showError("Wi-Fi is disabled. Please enable Wi-Fi.")
            return
        }

        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo == null) {
            showError("Unable to get Wi-Fi information.")
            return
        }

        val bssid = wifiInfo.bssid
        if (bssid == null || bssid == "02:00:00:00:00:00") {
            showError("Unable to get Wi-Fi BSSID. Check location permission.")
            return
        }

        // Normalize BSSID to lowercase for database comparison
        val normalizedBssid = bssid.lowercase()
        
        
        setLoading(true)
        
        // Check if BSSID is allowed
        apiClient.makeRestRequest(
            endpoint = "/wifi_allowed?bssid=eq.$normalizedBssid",
            token = sessionManager.getToken()
        ) { success, response ->
            runOnUiThread {
                if (success) {
                    try {
                        val wifiType = object : com.google.gson.reflect.TypeToken<Array<WifiAllowed>>() {}.type
                        val wifiList = gson.fromJson<Array<WifiAllowed>>(response, wifiType)
                        if (wifiList.isNotEmpty()) {
                            performAttendanceMark(isMarkIn)
                        } else {
                            setLoading(false)
                            // Enhanced error message with BSSID for debugging
                            showError("Wi-Fi not approved. BSSID: $normalizedBssid")
                        }
                    } catch (e: JsonSyntaxException) {
                        setLoading(false)
                        showError("Error parsing Wi-Fi data. BSSID: $normalizedBssid")
                    }
                } else {
                    setLoading(false)
                    if (response.contains("401")) {
                        handleSessionExpired()
                    } else {
                        showError("Network error. BSSID: $normalizedBssid")
                    }
                }
            }
        }
    }

    private fun performAttendanceMark(isMarkIn: Boolean) {
        val userId = sessionManager.getUserId() ?: return
        val currentTime = Calendar.getInstance()
        val timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        currentTime.timeZone = timeZone
        
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentTime.time)
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }.format(currentTime.time)

        if (isMarkIn) {
            // Apply 10:00 AM cap for Mark IN
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)
            
            val adjustedTime = if (hour < 10) {
                currentTime.set(Calendar.HOUR_OF_DAY, 10)
                currentTime.set(Calendar.MINUTE, 0)
                currentTime.set(Calendar.SECOND, 0)
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()).apply {
                    this.timeZone = timeZone
                }.format(currentTime.time)
            } else {
                timestamp
            }
            
            val attendanceData = mapOf(
                "employee_id" to userId,
                "attendance_date" to today,
                "in_time" to adjustedTime,
                "total_minutes" to 0
            )
            
            apiClient.makeRestRequest(
                endpoint = "/attendance",
                method = "POST",
                json = gson.toJson(attendanceData),
                token = sessionManager.getToken()
            ) { success, response ->
                runOnUiThread {
                    setLoading(false)
                    if (success) {
                        showSuccess(getString(R.string.attendance_marked_in))
                        loadTodayAttendance()
                    } else {
                        if (response.contains("401")) {
                            handleSessionExpired()
                        } else {
                            showError("Failed to mark attendance")
                        }
                    }
                }
            }
        } else {
            // Mark OUT - Apply 19:30 PM cap
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)
            
            val adjustedTime = if (hour > 19 || (hour == 19 && minute > 30)) {
                currentTime.set(Calendar.HOUR_OF_DAY, 19)
                currentTime.set(Calendar.MINUTE, 30)
                currentTime.set(Calendar.SECOND, 0)
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()).apply {
                    this.timeZone = timeZone
                }.format(currentTime.time)
            } else {
                timestamp
            }
            
            val updateData = mapOf("out_time" to adjustedTime)
            
            apiClient.makeRestRequest(
                endpoint = "/attendance?employee_id=eq.$userId&attendance_date=eq.$today",
                method = "PATCH",
                json = gson.toJson(updateData),
                token = sessionManager.getToken()
            ) { success, response ->
                runOnUiThread {
                    setLoading(false)
                    if (success) {
                        showSuccess(getString(R.string.attendance_marked_out))
                    } else {
                        if (response.contains("401")) {
                            handleSessionExpired()
                        } else {
                            showError("Failed to mark attendance")
                        }
                    }
                }
            }
        }
    }

    private fun handleSessionExpired() {
        sessionManager.clearSession()
        showError(getString(R.string.error_session_invalid))
        redirectToLogin()
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }


    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        markInButton.isEnabled = !loading
        markOutButton.isEnabled = !loading
        viewAttendanceButton.isEnabled = !loading
    }

    private fun showSuccess(message: String) {
        statusTextView.text = message
        statusTextView.setTextColor(ContextCompat.getColor(this, R.color.success_color))
        statusTextView.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        statusTextView.text = message
        statusTextView.setTextColor(ContextCompat.getColor(this, R.color.error_color))
        statusTextView.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retry attendance marking
                Toast.makeText(this, "Location permission granted. Please try again.", Toast.LENGTH_SHORT).show()
            } else {
                showError("Location permission required to access Wi-Fi BSSID")
            }
        }
    }
}