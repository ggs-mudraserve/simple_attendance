# **Product Requirements Document: Employee Attendance Android App**

| Document Version | Date | Author | Status |
| :---- | :---- | :---- | :---- |
| 1.0 | July 12, 2025 | Gemini AI | Final Draft |

## **1\. Introduction 📝**

This document outlines the requirements for an Android application designed for employee attendance tracking. The app will serve as the primary tool for employees to mark their daily attendance. It will leverage an existing Supabase backend for authentication and data storage, ensuring data integrity and security through Wi-Fi network validation.

## **2\. Goals and Objectives 🎯**

* **Primary Goal:** To provide a simple, reliable, and secure method for employees to record their daily work attendance.  
* **Objective 1:** Automate the attendance process, reducing manual tracking.  
* **Objective 2:** Ensure attendance is marked only from the authorized office location via Wi-Fi validation.  
* **Objective 3:** Provide employees with a clear view of their historical attendance records.  
* **Objective 4:** Enforce a single-device policy to prevent proxy attendance.

## **3\. User Profile**

The intended user for this application is an **Employee** of the organization who needs to record their daily attendance.

## **4\. Feature Requirements 📱**

### **4.1. Authentication and Login**

* **Login Screen:** The app will present a login screen with fields for **Email** and **Password**.  
* **Authentication:** Authentication will be handled by the existing Supabase auth service.  
* **Incorrect Credentials:** If a user enters an incorrect email or password, the app will display a generic error message: "**Invalid credentials. Please try again.**"

### **4.2. Single Device Enforcement**

* **Login Check:** Before attempting to log in a user, the app must query the profile table.  
* **Condition:** If the android_login column for that user is true, the login attempt must be blocked.  
* **Error Message:** The app will display the following error message: "**This account is already active on another device. To resolve this, please contact the administrator.**"  
* **Successful Login Update:** Upon a successful login, the app must immediately update the user's record in the profile table by setting android_login to true and saving the unique device ID to the device_id column.

### **4.3. Session Management**

* **Persistent Login:** Once a user has logged in, the app must keep them logged in indefinitely. The user should not have to log in again upon subsequent app launches.  
* **Session Invalidation:** If the app detects an invalid session for any reason (e.g., API call fails with an unauthorized error), it will automatically navigate the user to the login screen.  
* **Invalid Session Message:** Upon being redirected, the app will display the message: "**Contact Admin if Login is disabled.**"

### **4.4. Main Dashboard & Attendance Marking**

* **Screen Layout:** After login, the main screen will display three primary buttons: **'Mark IN'**, **'Mark OUT'**, and **'View Past Attendance'**.  
* **Button State Logic:** The state of the attendance buttons will be determined dynamically upon loading the screen:  
  * **If no in_time exists for the current date:** 'Mark IN' is **enabled**. 'Mark OUT' is **disabled**.  
  * **If in_time exists for the current date:** 'Mark IN' is **disabled** and its color is set to **green**. 'Mark OUT' is **enabled**. This is true even if an out_time already exists.  
* **Multiple 'Mark OUT'**: Users are allowed to press 'Mark OUT' multiple times throughout the day. Each press will **update** the out_time for that day in the attendance table.

### **4.5. Wi-Fi Geofencing**

* **BSSID Check:** Before recording any 'Mark IN' or 'Mark OUT' event, the app must get the BSSID of the currently connected Wi-Fi network.  
* **Validation:** The app will check if this BSSID exists in the bssid column of the wifi_allowed table.  
* **Failure Message:** If the BSSID is not found or the device is not connected to Wi-Fi, the action will fail and the app will show the error message: "**you are not connected to an approved Wi-Fi network.**"

### **4.6. 'Mark IN' & 'Mark OUT' Time Logic**

* **Mark IN:**  
  * A new row is created in the attendance table for the employee_id and the current attendance_date.  
  * If the current time (IST) is before 10:00, in_time is saved as **10:00 IST** for that day.  
  * If the current time (IST) is after 10:00, in_time is saved as the **current timestamp**.  
* **Mark OUT:**  
  * The existing row in the attendance table for the user and date is updated.  
  * If the current time (IST) is after 19:30, out_time is saved as **19:30 IST** for that day.  
  * If the current time (IST) is before 19:30, out_time is saved as the **current timestamp**.

### **4.7. View Past Attendance**

* **Calendar View:** Clicking 'View Past Attendance' navigates to a calendar screen.  
* **Month Navigation:** The view will default to the current month. The user can navigate between the **current month** and the **previous month** using **<** and **>** arrow icons.  
* **Date Coloring:** Each date on the calendar will be colored based on the status in the attendance table for that day:  
  * **Green:** Present  
  * **Yellow:** Late  
  * **Orange:** Half Day  
  * **Red:** Absent  
  * **Grey:** Holiday or Sunday.  
* **Daily Details:** Tapping on a specific date will open a **pop-up dialog** displaying the in_time, out_time, and status for that selected date.

## **5\. Backend Requirements (Supabase) ☁️**

### **5.1. New Table: holidays**

A new table named holidays is required to manage non-working days.

CREATE TABLE public.holidays (  
  holiday_date date NOT NULL,  
  description character varying(255) NOT NULL,  
  CONSTRAINT holidays_pkey PRIMARY KEY (holiday_date)  
);

### **5.2. Function: calculate_daily_attendance**

A PL/pgSQL function to calculate and update the daily attendance status.

CREATE OR REPLACE FUNCTION calculate_daily_attendance(p_attendance_date date)  
RETURNS void  
LANGUAGE plpgsql  
AS $$  
DECLARE  
    r record;  
    total_work_minutes integer;  
    calculated_status public.att_status;  
BEGIN  
    FOR r IN  
        SELECT id, in_time, out_time FROM attendance WHERE attendance_date = p_attendance_date AND in_time IS NOT NULL AND out_time IS NOT NULL  
    LOOP  
        total_work_minutes := EXTRACT(EPOCH FROM (r.out_time - r.in_time)) / 60;

        IF total_work_minutes > 500 THEN -- 8 hours 20 minutes  
            calculated\_status := 'Present';  
        ELSIF total_work_minutes > 380 THEN -- 6 hours 20 minutes  
            calculated\_status := 'Late';  
        ELSIF total_work_minutes > 240 THEN -- 4 hours  
            calculated_status := 'Half Day';  
        ELSE  
            calculated_status := 'Absent';  
        END IF;

        UPDATE attendance  
        SET  
          status = calculated_status,  
          total_minutes = total_work_minutes  
        WHERE id = r.id;  
    END LOOP;

    -- Mark users who did not mark IN time as Absent  
    UPDATE attendance  
    SET status = 'Absent', total_minutes = 0  
    WHERE attendance_date = p_attendance_date AND in_time IS NULL;  
END;  
$$;

### **5.3. Cron Job: Daily Status Update**

A cron job needs to be scheduled to run **every day at 20:00 IST**.

* **Schedule:** 0 20 \* \* \* (with appropriate timezone configuration in Supabase).  
* **Job Logic:** The job will execute SQL logic to perform the following steps in order:  
  1. Check if today is a Sunday or if today's date exists in the holidays table.  
  2. If true, INSERT or UPDATE the status for all employees to **'Holiday'** in the attendance table for the current date.  
  3. If false (it's a workday), call the calculate_daily_attendance(CURRENT_DATE) function to process the day's attendance records.

## **6\. Existing Data Schema 🗄️**

The app will utilize the following existing tables:

* public.profile  
* public.wifi_allowed  
* public.attendance (as defined in the initial prompt)  
* The public.att_status ENUM type.
