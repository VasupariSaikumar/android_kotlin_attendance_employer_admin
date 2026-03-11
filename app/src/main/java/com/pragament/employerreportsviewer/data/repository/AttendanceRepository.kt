package com.pragament.employerreportsviewer.data.repository

import com.pragament.employerreportsviewer.data.SupabaseManager
import com.pragament.employerreportsviewer.data.model.EmployeeDeviceAccess
import com.pragament.employerreportsviewer.data.model.EmployeeGoogleAccess
import com.pragament.employerreportsviewer.data.model.SupabaseAttendanceRecord
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AttendanceRepository {

    // ─── Attendance Records ───────────────────────────────────────────────────

    suspend fun getAllRecords(url: String, key: String): Result<List<SupabaseAttendanceRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                val records = client.postgrest["attendance"]
                    .select {
                        order("punch_in_time", Order.DESCENDING)
                    }
                    .decodeList<SupabaseAttendanceRecord>()
                Result.success(records)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRecordsByEmployee(
        url: String,
        key: String,
        employeeId: String
    ): Result<List<SupabaseAttendanceRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                val records = client.postgrest["attendance"]
                    .select {
                        filter {
                            eq("employee_id", employeeId)
                        }
                        order("punch_in_time", Order.DESCENDING)
                    }
                    .decodeList<SupabaseAttendanceRecord>()
                Result.success(records)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRecordsByDateRange(
        url: String,
        key: String,
        startDate: String,
        endDate: String
    ): Result<List<SupabaseAttendanceRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                val records = client.postgrest["attendance"]
                    .select {
                        filter {
                            gte("punch_in_time", startDate)
                            lte("punch_in_time", endDate)
                        }
                        order("punch_in_time", Order.DESCENDING)
                    }
                    .decodeList<SupabaseAttendanceRecord>()
                Result.success(records)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun testConnection(url: String, key: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("AttendanceRepo", "Testing connection to: $url")
                val client = SupabaseManager.getClient(url, key)
                android.util.Log.d("AttendanceRepo", "Got client, attempting query...")

                // Try to fetch just one record to test connection
                client.postgrest["attendance"]
                    .select {
                        limit(1)
                    }
                    .decodeList<SupabaseAttendanceRecord>()

                android.util.Log.d("AttendanceRepo", "Connection test successful!")
                Result.success(true)
            } catch (e: Exception) {
                android.util.Log.e("AttendanceRepo", "Connection test failed", e)
                android.util.Log.e("AttendanceRepo", "Error type: ${e.javaClass.simpleName}")
                android.util.Log.e("AttendanceRepo", "Error message: ${e.message}")
                // Return the actual error message to help debug
                Result.failure(Exception("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    suspend fun getUniqueEmployeeIds(url: String, key: String): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                val records = client.postgrest["attendance"]
                    .select {
                        // Select all and get unique employee_ids locally
                    }
                    .decodeList<SupabaseAttendanceRecord>()
                val uniqueIds = records.map { it.employeeId }.distinct().sorted()
                Result.success(uniqueIds)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ─── Google Email Approvals ───────────────────────────────────────────────

    /** Fetches all rows with status = 'pending' from employee_google_access. */
    suspend fun fetchPendingGoogleApprovals(
        url: String,
        key: String
    ): Result<List<EmployeeGoogleAccess>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                val records = client.postgrest["employee_google_access"]
                    .select {
                        filter { eq("status", "pending") }
                        order("requested_at", Order.DESCENDING)
                    }
                    .decodeList<EmployeeGoogleAccess>()
                Result.success(records)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Sets status = 'approved' on employee_google_access row by id. */
    suspend fun approveGoogleAccess(
        url: String,
        key: String,
        id: String,
        reviewedBy: String = "admin"
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                client.postgrest["employee_google_access"]
                    .update(
                        buildJsonObject {
                            put("status", "approved")
                            put("reviewed_by", reviewedBy)
                            put("reviewed_at", java.time.Instant.now().toString())
                        }
                    ) {
                        filter { eq("id", id) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Sets status = 'rejected' on employee_google_access row by id. */
    suspend fun rejectGoogleAccess(
        url: String,
        key: String,
        id: String,
        reviewedBy: String = "admin"
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                client.postgrest["employee_google_access"]
                    .update(
                        buildJsonObject {
                            put("status", "rejected")
                            put("reviewed_by", reviewedBy)
                            put("reviewed_at", java.time.Instant.now().toString())
                        }
                    ) {
                        filter { eq("id", id) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ─── Device ID Approvals ──────────────────────────────────────────────────

    /** Fetches all rows with status = 'pending' from employee_device_access. */
    suspend fun fetchPendingDeviceApprovals(
        url: String,
        key: String
    ): Result<List<EmployeeDeviceAccess>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                val records = client.postgrest["employee_device_access"]
                    .select {
                        filter { eq("status", "pending") }
                        order("requested_at", Order.DESCENDING)
                    }
                    .decodeList<EmployeeDeviceAccess>()
                Result.success(records)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Sets status = 'approved' on employee_device_access row by id. */
    suspend fun approveDeviceAccess(
        url: String,
        key: String,
        id: String,
        reviewedBy: String = "admin"
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                client.postgrest["employee_device_access"]
                    .update(
                        buildJsonObject {
                            put("status", "approved")
                            put("reviewed_by", reviewedBy)
                            put("reviewed_at", java.time.Instant.now().toString())
                        }
                    ) {
                        filter { eq("id", id) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Sets status = 'rejected' on employee_device_access row by id. */
    suspend fun rejectDeviceAccess(
        url: String,
        key: String,
        id: String,
        reviewedBy: String = "admin"
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val client = SupabaseManager.getClient(url, key)
                client.postgrest["employee_device_access"]
                    .update(
                        buildJsonObject {
                            put("status", "rejected")
                            put("reviewed_by", reviewedBy)
                            put("reviewed_at", java.time.Instant.now().toString())
                        }
                    ) {
                        filter { eq("id", id) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
