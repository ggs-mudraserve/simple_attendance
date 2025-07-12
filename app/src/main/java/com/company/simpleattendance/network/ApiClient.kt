package com.company.simpleattendance.network

import com.company.simpleattendance.config.SupabaseConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun makeAuthRequest(endpoint: String, json: String, callback: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("${SupabaseConfig.AUTH_ENDPOINT}$endpoint")
            .post(json.toRequestBody("application/json".toMediaType()))
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                callback(response.isSuccessful, body)
            }
        })
    }

    fun makeRestRequest(
        endpoint: String,
        method: String = "GET",
        json: String? = null,
        token: String? = null,
        callback: (Boolean, String) -> Unit
    ) {
        val requestBuilder = Request.Builder().url("${SupabaseConfig.REST_ENDPOINT}$endpoint")

        when (method) {
            "POST" -> requestBuilder.post(json?.toRequestBody("application/json".toMediaType()) ?: "".toRequestBody())
            "PATCH" -> requestBuilder.patch(json?.toRequestBody("application/json".toMediaType()) ?: "".toRequestBody())
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        requestBuilder.addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
        requestBuilder.addHeader("Content-Type", "application/json")
        requestBuilder.addHeader("Prefer", "return=minimal")

        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                callback(response.isSuccessful, body)
            }
        })
    }
}