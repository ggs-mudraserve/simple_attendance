package com.company.simpleattendance.models

data class Profile(
    val id: String,
    val email: String?,
    val first_name: String?,
    val last_name: String?,
    val emp_code: String,
    val is_active: Boolean,
    val android_login: Boolean,
    val device_id: String?
)

data class AuthResponse(
    val access_token: String,
    val user: User
)

data class User(
    val id: String,
    val email: String
)