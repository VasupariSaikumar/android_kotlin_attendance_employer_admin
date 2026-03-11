package com.pragament.employerreportsviewer.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseAttendanceRecord(
    val id: String? = null,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("punch_in_time") val punchInTime: String? = null,
    @SerialName("punch_out_time") val punchOutTime: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("punch_out_image_url") val punchOutImageUrl: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    // New fields for personal-phone punch flow
    @SerialName("punch_source") val punchSource: String? = null,       // "shared_device" | "personal_phone"
    @SerialName("google_email") val googleEmail: String? = null,
    @SerialName("device_id_hash") val deviceIdHash: String? = null,
    @SerialName("intranet_verified") val intranetVerified: Boolean? = null
)

/**
 * Local model for displaying attendance records in the UI.
 */
data class AttendanceDisplayRecord(
    val id: String?,
    val employeeId: String,
    val punchInTime: String?,
    val punchOutTime: String?,
    val imageUrl: String?,
    val date: String,
    val duration: String
)

/**
 * Summary of an employee's attendance for a day.
 */
data class EmployeeDailySummary(
    val employeeId: String,
    val date: String,
    val firstPunchIn: String?,
    val lastPunchOut: String?,
    val totalHours: Double,
    val recordCount: Int
)

// ─── Approval Access Tables ──────────────────────────────────────────────────

/**
 * Maps to the `employee_google_access` Supabase table.
 * Tracks employer approval of an employee's Google email for personal-phone punch.
 */
@Serializable
data class EmployeeGoogleAccess(
    val id: String,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("google_email") val googleEmail: String,
    val status: String,                          // "pending" | "approved" | "rejected"
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null
)

/**
 * Maps to the `employee_device_access` Supabase table.
 * Tracks employer approval of an employee's device ID hash for personal-phone punch.
 */
@Serializable
data class EmployeeDeviceAccess(
    val id: String,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("device_id_hash") val deviceIdHash: String,
    val status: String,                          // "pending" | "approved" | "rejected"
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null
)
