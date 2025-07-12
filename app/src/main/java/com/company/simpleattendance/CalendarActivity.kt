package com.company.simpleattendance

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.company.simpleattendance.models.Attendance
import com.company.simpleattendance.models.AttendanceStatus
import com.company.simpleattendance.network.ApiClient
import com.company.simpleattendance.utils.SessionManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

class CalendarActivity : AppCompatActivity() {
    private lateinit var monthYearTextView: TextView
    private lateinit var prevMonthButton: Button
    private lateinit var nextMonthButton: Button
    private lateinit var calendarContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    
    private lateinit var apiClient: ApiClient
    private lateinit var sessionManager: SessionManager
    private lateinit var gson: Gson
    
    private var currentCalendar = Calendar.getInstance()
    private var attendanceMap = mutableMapOf<String, Attendance>()
    private var holidaySet = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        
        initViews()
        apiClient = ApiClient()
        sessionManager = SessionManager(this)
        gson = Gson()
        
        setupClickListeners()
        
        // Limit navigation to current and previous month only
        val today = Calendar.getInstance()
        currentCalendar.set(Calendar.DAY_OF_MONTH, 1)
        
        updateMonthDisplay()
        loadAttendanceData()
    }

    private fun initViews() {
        monthYearTextView = findViewById(R.id.monthYearTextView)
        prevMonthButton = findViewById(R.id.prevMonthButton)
        nextMonthButton = findViewById(R.id.nextMonthButton)
        calendarContainer = findViewById(R.id.calendarContainer)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        prevMonthButton.setOnClickListener { navigateMonth(-1) }
        nextMonthButton.setOnClickListener { navigateMonth(1) }
    }

    private fun navigateMonth(direction: Int) {
        val today = Calendar.getInstance()
        today.set(Calendar.DAY_OF_MONTH, 1)
        
        val newCalendar = Calendar.getInstance()
        newCalendar.time = currentCalendar.time
        newCalendar.add(Calendar.MONTH, direction)
        
        // Only allow current month and previous month
        val monthsDiff = (today.get(Calendar.YEAR) - newCalendar.get(Calendar.YEAR)) * 12 + 
                        (today.get(Calendar.MONTH) - newCalendar.get(Calendar.MONTH))
        
        if (monthsDiff >= 0 && monthsDiff <= 1) {
            currentCalendar = newCalendar
            updateMonthDisplay()
            loadAttendanceData()
        }
        
        updateNavigationButtons()
    }

    private fun updateNavigationButtons() {
        val today = Calendar.getInstance()
        today.set(Calendar.DAY_OF_MONTH, 1)
        
        val monthsDiff = (today.get(Calendar.YEAR) - currentCalendar.get(Calendar.YEAR)) * 12 + 
                        (today.get(Calendar.MONTH) - currentCalendar.get(Calendar.MONTH))
        
        prevMonthButton.isEnabled = monthsDiff < 1
        nextMonthButton.isEnabled = monthsDiff > 0
    }

    private fun updateMonthDisplay() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthYearTextView.text = monthFormat.format(currentCalendar.time)
        updateNavigationButtons()
    }

    private fun loadAttendanceData() {
        setLoading(true)
        attendanceMap.clear()
        holidaySet.clear()
        
        val userId = sessionManager.getUserId() ?: return
        val yearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCalendar.time)
        
        // Load attendance data for the month
        apiClient.makeRestRequest(
            endpoint = "/attendance?employee_id=eq.$userId&attendance_date=gte.$yearMonth-01&attendance_date=lt.${getNextMonth()}-01",
            token = sessionManager.getToken()
        ) { success, response ->
            runOnUiThread {
                if (success) {
                    try {
                        val attendanceType = object : com.google.gson.reflect.TypeToken<Array<Attendance>>() {}.type
                        val attendanceList = gson.fromJson<Array<Attendance>>(response, attendanceType)
                        attendanceList.forEach { attendance ->
                            attendanceMap[attendance.attendance_date] = attendance
                        }
                        loadHolidays()
                    } catch (e: JsonSyntaxException) {
                        setLoading(false)
                        renderCalendar()
                    }
                } else {
                    setLoading(false)
                    renderCalendar()
                }
            }
        }
    }

    private fun loadHolidays() {
        val yearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCalendar.time)
        
        apiClient.makeRestRequest(
            endpoint = "/holidays?holiday_date=gte.$yearMonth-01&holiday_date=lt.${getNextMonth()}-01",
            token = sessionManager.getToken()
        ) { success, response ->
            runOnUiThread {
                setLoading(false)
                if (success) {
                    try {
                        val holidayType = object : com.google.gson.reflect.TypeToken<Array<Map<String, String>>>() {}.type
                        val holidays = gson.fromJson<Array<Map<String, String>>>(response, holidayType)
                        holidays.forEach { holiday ->
                            holiday["holiday_date"]?.let { holidaySet.add(it) }
                        }
                    } catch (e: JsonSyntaxException) {
                        // Continue without holidays
                    }
                }
                renderCalendar()
            }
        }
    }

    private fun getNextMonth(): String {
        val nextMonth = Calendar.getInstance()
        nextMonth.time = currentCalendar.time
        nextMonth.add(Calendar.MONTH, 1)
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(nextMonth.time)
    }

    private fun renderCalendar() {
        calendarContainer.removeAllViews()
        
        val cal = Calendar.getInstance()
        cal.time = currentCalendar.time
        cal.set(Calendar.DAY_OF_MONTH, 1)
        
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        var currentDay = 1
        var dayOfWeek = firstDayOfWeek
        
        // Create 6 weeks (rows) maximum
        for (week in 0 until 6) {
            val weekRow = LinearLayout(this)
            weekRow.orientation = LinearLayout.HORIZONTAL
            weekRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            
            // Create 7 days (columns) for each week
            for (day in 0 until 7) {
                if ((week == 0 && day < firstDayOfWeek) || currentDay > daysInMonth) {
                    // Empty cell
                    val emptyView = TextView(this)
                    val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    params.setMargins(2, 2, 2, 2)
                    emptyView.layoutParams = params
                    weekRow.addView(emptyView)
                } else {
                    // Day button
                    val dayCalendar = Calendar.getInstance()
                    dayCalendar.set(currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH), currentDay)
                    val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dayCalendar.time)
                    
                    val dayButton = Button(this)
                    dayButton.text = currentDay.toString()
                    dayButton.textSize = 12f
                    dayButton.setPadding(4, 4, 4, 4)
                    
                    // Set color based on attendance status
                    setDayColor(dayButton, dateString, dayCalendar.get(Calendar.DAY_OF_WEEK))
                    
                    // Safe click listener with error handling
                    dayButton.setOnClickListener { 
                        try {
                            showAttendanceDetail(dateString)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Error loading attendance details", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    params.setMargins(2, 2, 2, 2)
                    dayButton.layoutParams = params
                    
                    weekRow.addView(dayButton)
                    currentDay++
                }
            }
            
            calendarContainer.addView(weekRow)
            
            // Stop if we've added all days
            if (currentDay > daysInMonth) break
        }
    }

    private fun setDayColor(dayButton: Button, dateString: String, dayOfWeek: Int) {
        val attendance = attendanceMap[dateString]
        
        when {
            holidaySet.contains(dateString) || dayOfWeek == Calendar.SUNDAY -> {
                dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.holiday_color))
                dayButton.setTextColor(Color.WHITE)
            }
            attendance != null -> {
                when (attendance.status) {
                    AttendanceStatus.PRESENT.value -> {
                        dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.present_color))
                        dayButton.setTextColor(Color.WHITE)
                    }
                    AttendanceStatus.LATE.value -> {
                        dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.late_color))
                        dayButton.setTextColor(Color.BLACK)
                    }
                    AttendanceStatus.HALF_DAY.value -> {
                        dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.half_day_color))
                        dayButton.setTextColor(Color.WHITE)
                    }
                    AttendanceStatus.ABSENT.value -> {
                        dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.absent_color))
                        dayButton.setTextColor(Color.WHITE)
                    }
                    AttendanceStatus.HOLIDAY.value -> {
                        dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.holiday_color))
                        dayButton.setTextColor(Color.WHITE)
                    }
                    null -> {
                        // Status not yet calculated - show neutral color
                        dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.secondary_text_color))
                        dayButton.setTextColor(Color.WHITE)
                    }
                    else -> {
                        // Unknown status - show neutral color
                        dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.secondary_text_color))
                        dayButton.setTextColor(Color.WHITE)
                    }
                }
            }
            else -> {
                // No attendance data - show light background so date is visible
                dayButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_color))
                dayButton.setTextColor(ContextCompat.getColor(this, R.color.text_color))
                
                // Add a subtle border so it's clearly visible
                dayButton.background = ContextCompat.getDrawable(this, android.R.drawable.btn_default)
            }
        }
    }

    private fun showAttendanceDetail(dateString: String) {
        try {
            val attendance = attendanceMap[dateString]
            
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_attendance_detail)
            
            val dateTextView = dialog.findViewById<TextView>(R.id.dateTextView)
            val inTimeTextView = dialog.findViewById<TextView>(R.id.inTimeTextView)
            val outTimeTextView = dialog.findViewById<TextView>(R.id.outTimeTextView)
            val statusTextView = dialog.findViewById<TextView>(R.id.statusTextView)
            val closeButton = dialog.findViewById<Button>(R.id.closeButton)
            
            // Safe date parsing
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
                val parsedDate = inputFormat.parse(dateString)
                dateTextView.text = outputFormat.format(parsedDate ?: Date())
            } catch (e: Exception) {
                dateTextView.text = dateString
            }
            
            if (attendance != null) {
                inTimeTextView.text = formatTime(attendance.in_time) ?: "Not marked"
                outTimeTextView.text = formatTime(attendance.out_time) ?: "Not marked"
                
                val status = attendance.status ?: "Pending calculation (8 PM)"
                statusTextView.text = status
                
                // Set status color based on value
                when (attendance.status) {
                    AttendanceStatus.PRESENT.value -> {
                        statusTextView.setTextColor(ContextCompat.getColor(this, R.color.present_color))
                        statusTextView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                    }
                    AttendanceStatus.LATE.value -> {
                        statusTextView.setTextColor(Color.parseColor("#F57C00"))
                        statusTextView.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    }
                    AttendanceStatus.HALF_DAY.value -> {
                        statusTextView.setTextColor(Color.parseColor("#FF9800"))
                        statusTextView.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    }
                    AttendanceStatus.ABSENT.value -> {
                        statusTextView.setTextColor(Color.parseColor("#FF5252"))
                        statusTextView.setBackgroundColor(Color.parseColor("#FFEBEE"))
                    }
                    AttendanceStatus.HOLIDAY.value -> {
                        statusTextView.setTextColor(Color.parseColor("#757575"))
                        statusTextView.setBackgroundColor(Color.parseColor("#F5F5F5"))
                    }
                    null -> {
                        statusTextView.setTextColor(Color.parseColor("#2196F3"))
                        statusTextView.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    }
                    else -> {
                        statusTextView.setTextColor(Color.parseColor("#666666"))
                        statusTextView.setBackgroundColor(Color.parseColor("#F5F5F5"))
                    }
                }
            } else {
                inTimeTextView.text = "Not marked"
                outTimeTextView.text = "Not marked"
                statusTextView.text = "No data"
                statusTextView.setTextColor(Color.parseColor("#666666"))
                statusTextView.setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
            
            closeButton.setOnClickListener { dialog.dismiss() }
            
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error showing attendance details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(timeString: String?): String? {
        if (timeString == null || timeString.isEmpty()) return null
        return try {
            // Try different date formats
            val inputFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            )
            
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            
            for (inputFormat in inputFormats) {
                try {
                    val date = inputFormat.parse(timeString)
                    if (date != null) {
                        return outputFormat.format(date)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            
            // If all parsing fails, return the original string
            timeString
        } catch (e: Exception) {
            timeString
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        prevMonthButton.isEnabled = !loading
        nextMonthButton.isEnabled = !loading
    }
}