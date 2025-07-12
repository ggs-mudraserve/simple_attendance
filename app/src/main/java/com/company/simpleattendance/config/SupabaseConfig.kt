package com.company.simpleattendance.config

object SupabaseConfig {
    const val SUPABASE_URL = "https://vxcdvuekhfdkccjhbrhz.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ4Y2R2dWVraGZka2Njamhicmh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDM0ODI4NjUsImV4cCI6MjA1OTA1ODg2NX0.6SnpBAEQRSYlegMFE-QMLs5tyUG1W31EGdzkLeYYI7k"
    
    const val AUTH_ENDPOINT = "$SUPABASE_URL/auth/v1"
    const val REST_ENDPOINT = "$SUPABASE_URL/rest/v1"
}