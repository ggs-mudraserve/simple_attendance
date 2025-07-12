# Simple Attendance Android App

A lightweight native Android application for employee attendance tracking with Wi-Fi geofencing and single-device enforcement.

## Features

- **Secure Authentication**: Login with email/password via Supabase Auth
- **Single Device Policy**: Prevents multiple logins from different devices
- **Wi-Fi Geofencing**: Attendance marking only allowed from approved Wi-Fi networks
- **Time Capping**: 
  - Mark IN: Capped at 10:00 AM (IST)
  - Mark OUT: Capped at 19:30 PM (IST)
- **Calendar View**: View past attendance with color-coded status
- **Lightweight**: <5MB APK size with minimal dependencies

## Architecture

- **Native Android**: Pure Kotlin/Java implementation
- **Minimal Dependencies**: Only OkHttp (2MB) and Gson (240KB)
- **Direct API Calls**: No heavy SDK, direct REST calls to Supabase
- **Optimized Storage**: SharedPreferences for session management

## Database Schema

The app uses existing Supabase tables:

- `profile`: Employee profiles with device enforcement
- `attendance`: Daily attendance records
- `wifi_allowed`: Approved Wi-Fi network BSSIDs
- `holidays`: Holiday calendar

## APK Optimization

- ProGuard/R8 enabled for code shrinking
- Vector drawables instead of bitmap images
- Minimal UI components
- No unnecessary libraries

## Setup Instructions

1. **Build Requirements**:
   - Android Studio Arctic Fox or later
   - Kotlin 1.8.20+
   - Min SDK: 24 (Android 7.0)
   - Target SDK: 34

2. **Configuration**:
   - Supabase URL and anon key are configured in `SupabaseConfig.kt`
   - Database functions are pre-deployed

3. **Build Commands**:
   ```bash
   ./gradlew assembleRelease
   ./gradlew assembleDebug
   ```

## Database Functions

The app includes a pre-deployed function:
- `calculate_daily_attendance(date)`: Calculates attendance status based on work hours

## Security Features

- JWT token storage in SharedPreferences
- Row Level Security policies on Supabase
- Device ID validation
- Network validation via BSSID checking

## Resource Usage

- **APK Size**: <5MB
- **Runtime Memory**: <50MB
- **Startup Time**: <2 seconds
- **Network**: Minimal API calls only when needed

## App Flow

1. **Login**: Check device status → Authenticate → Update device info
2. **Dashboard**: Load today's attendance → Show Mark IN/OUT buttons
3. **Attendance**: Validate Wi-Fi → Apply time caps → Submit to database
4. **Calendar**: Load monthly data → Display color-coded calendar → Show details

## Attendance Status Colors

- 🟢 **Green**: Present (>8h 20m)
- 🟡 **Yellow**: Late (>6h 20m)
- 🟠 **Orange**: Half Day (>4h)
- 🔴 **Red**: Absent (<4h or no IN time)
- ⚪ **Grey**: Holiday/Sunday