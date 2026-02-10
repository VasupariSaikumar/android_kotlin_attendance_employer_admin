package com.pragament.employerreportsviewer.data.network

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * API client to fetch Supabase config from the Pragament Nameserver.
 * Endpoint: POST https://expressjs-api-intranet-nameserver.onrender.com/api/config/get
 */
object NameserverApi {

    private const val TAG = "NameserverApi"
    private const val API_URL = "https://expressjs-api-intranet-nameserver.onrender.com/api/config/get"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ConfigRequest(
        val uuid: String,
        val pin: String = ""
    )

    @Serializable
    data class ConfigResponse(
        val success: Boolean = false,
        val config: ConfigData? = null,
        val message: String? = null,
        val error: String? = null
    )

    @Serializable
    data class ConfigData(
        // Fields matching the nameserver's Configuration JSON format
        val url: String? = null,
        val anonKey: String? = null,
        val projectId: String? = null,
        // Alternative field names the server might return
        val supabaseUrl: String? = null,
        val supabaseKey: String? = null,
        val supabase_url: String? = null,
        val supabase_key: String? = null,
        val supabaseAnonKey: String? = null,
        val supabase_anon_key: String? = null,
        val name: String? = null
    ) {
        /** Resolve the URL from whichever field the server returns */
        fun resolvedUrl(): String = url ?: supabaseUrl ?: supabase_url ?: ""

        /** Resolve the key from whichever field the server returns */
        fun resolvedKey(): String = anonKey ?: supabaseKey ?: supabase_key ?: supabaseAnonKey ?: supabase_anon_key ?: ""
    }


    /**
     * Fetch Supabase config from the nameserver API.
     * Returns ConfigResponse on success, or a ConfigResponse with error message on failure.
     */
    suspend fun fetchConfig(uuid: String, pin: String): ConfigResponse {
        return try {
            Log.d(TAG, "Fetching config for UUID: $uuid")

            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // Write request body
            val requestBody = json.encodeToString(ConfigRequest.serializer(), ConfigRequest(uuid = uuid, pin = pin))
            Log.d(TAG, "Request body: $requestBody")

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")

            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }

            Log.d(TAG, "Response body: $responseBody")
            connection.disconnect()

            if (responseCode in 200..299) {
                json.decodeFromString(ConfigResponse.serializer(), responseBody)
            } else {
                // Try to parse error response
                try {
                    json.decodeFromString(ConfigResponse.serializer(), responseBody)
                } catch (e: Exception) {
                    ConfigResponse(success = false, error = "Server error ($responseCode): $responseBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch config", e)
            ConfigResponse(success = false, error = "Network error: ${e.message}")
        }
    }
}
