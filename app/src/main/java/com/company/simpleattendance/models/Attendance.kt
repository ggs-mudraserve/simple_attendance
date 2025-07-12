package com.company.simpleattendance.models

data class Attendance(
    val id: String,
    val employee_id: String,
    val attendance_date: String,
    val in_time: String?,
    val out_time: String?,
    val total_minutes: Int,
    val status: String
)

enum class AttendanceStatus(val value: String) {
    PRESENT("Present"),
    LATE("Late"),
    HALF_DAY("Half Day"),
    ABSENT("Absent"),
    HOLIDAY("Holiday")
}

data class WifiAllowed(
    val id: Int,
    val bssid: String,
    val label: String?
)