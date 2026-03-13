package com.pragament.employerreportsviewer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pragament.employerreportsviewer.data.AppPreferences
import com.pragament.employerreportsviewer.data.model.EmployeeDeviceAccess
import com.pragament.employerreportsviewer.data.model.EmployeeGoogleAccess
import com.pragament.employerreportsviewer.data.model.SupabaseAttendanceRecord
import com.pragament.employerreportsviewer.data.repository.AttendanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class DateFilter {
    ALL,
    TODAY,
    THIS_WEEK,
    THIS_MONTH
}

data class ReportsUiState(
    val isLoading: Boolean = false,
    val isConfigured: Boolean = false,
    val records: List<SupabaseAttendanceRecord> = emptyList(),
    val filteredRecords: List<SupabaseAttendanceRecord> = emptyList(),
    val employeeIds: List<String> = emptyList(),
    val selectedEmployeeId: String? = null,
    val selectedDateFilter: DateFilter = DateFilter.ALL,
    val errorMessage: String? = null,
    val lastRefresh: String? = null,
    // Approvals state
    val pendingGoogleApprovals: List<EmployeeGoogleAccess> = emptyList(),
    val pendingDeviceApprovals: List<EmployeeDeviceAccess> = emptyList(),
    val approvalsLoading: Boolean = false,
    val approvalsError: String? = null,
    val approvalActionMessage: String? = null  // toast-style one-shot feedback
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val repository = AttendanceRepository()

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var supabaseUrl: String = ""
    private var supabaseKey: String = ""

    init {
        checkConfiguration()
    }

    fun checkConfiguration() {
        viewModelScope.launch {
            val config = prefs.supabaseConfig.first()
            supabaseUrl = config.first
            supabaseKey = config.second
            val configured = supabaseUrl.isNotBlank() && supabaseKey.isNotBlank()
            _uiState.value = _uiState.value.copy(isConfigured = configured)

            if (configured) {
                loadRecords()
                loadPendingApprovals()
            }
        }
    }

    fun loadRecords() {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isConfigured = false,
                errorMessage = "Please configure Supabase in Settings"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = repository.getAllRecords(supabaseUrl, supabaseKey)

            result.fold(
                onSuccess = { records ->
                    val employeeIds = records.map { it.employeeId }.distinct().sorted()
                    val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        records = records,
                        filteredRecords = applyFilters(records, null, _uiState.value.selectedDateFilter),
                        employeeIds = employeeIds,
                        lastRefresh = now
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load records"
                    )
                }
            )
        }
    }

    fun selectEmployee(employeeId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedEmployeeId = employeeId,
            filteredRecords = applyFilters(
                _uiState.value.records,
                employeeId,
                _uiState.value.selectedDateFilter
            )
        )
    }

    fun selectDateFilter(filter: DateFilter) {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = filter,
            filteredRecords = applyFilters(
                _uiState.value.records,
                _uiState.value.selectedEmployeeId,
                filter
            )
        )
    }

    private fun parseSupabaseTime(isoTime: String): LocalDateTime {
        return try {
            var cleanTime = isoTime.replace(" ", "T")
            if (!cleanTime.contains("Z", ignoreCase = true) && !cleanTime.drop(11).contains("+") && !cleanTime.drop(11).contains("-")) {
                cleanTime += "Z" // Default to UTC
            }
            java.time.OffsetDateTime.parse(cleanTime)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(isoTime.replace(" ", "T").take(19))
            } catch (e2: Exception) {
                LocalDateTime.now()
            }
        }
    }

    private fun applyFilters(
        records: List<SupabaseAttendanceRecord>,
        employeeId: String?,
        dateFilter: DateFilter
    ): List<SupabaseAttendanceRecord> {
        var filtered = records

        // Filter by employee
        if (employeeId != null) {
            filtered = filtered.filter { it.employeeId == employeeId }
        }

        // Filter by date
        val today = LocalDate.now()
        filtered = when (dateFilter) {
            DateFilter.ALL -> filtered
            DateFilter.TODAY -> filtered.filter { record ->
                record.punchInTime?.let { time ->
                    try {
                        val recordDate = parseSupabaseTime(time).toLocalDate()
                        recordDate == today
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            }
            DateFilter.THIS_WEEK -> filtered.filter { record ->
                record.punchInTime?.let { time ->
                    try {
                        val recordDate = parseSupabaseTime(time).toLocalDate()
                        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
                        recordDate >= weekStart && recordDate <= today
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            }
            DateFilter.THIS_MONTH -> filtered.filter { record ->
                record.punchInTime?.let { time ->
                    try {
                        val recordDate = parseSupabaseTime(time).toLocalDate()
                        recordDate.month == today.month && recordDate.year == today.year
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            }
        }

        return filtered
    }

    fun refresh() {
        loadRecords()
        loadPendingApprovals()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ─── Approval Actions ─────────────────────────────────────────────────────

    /** Load both Google and device pending approvals from Supabase. */
    fun loadPendingApprovals() {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(approvalsLoading = true, approvalsError = null)

            val googleResult = repository.fetchPendingGoogleApprovals(supabaseUrl, supabaseKey)
            val deviceResult = repository.fetchPendingDeviceApprovals(supabaseUrl, supabaseKey)

            val googleList = googleResult.getOrElse {
                _uiState.value = _uiState.value.copy(
                    approvalsError = "Failed to load email approvals: ${it.message}"
                )
                emptyList()
            }
            val deviceList = deviceResult.getOrElse {
                _uiState.value = _uiState.value.copy(
                    approvalsError = "Failed to load device approvals: ${it.message}"
                )
                emptyList()
            }

            _uiState.value = _uiState.value.copy(
                approvalsLoading = false,
                pendingGoogleApprovals = googleList,
                pendingDeviceApprovals = deviceList
            )
        }
    }

    fun approveGoogleAccess(id: String) {
        viewModelScope.launch {
            val result = repository.approveGoogleAccess(supabaseUrl, supabaseKey, id)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        approvalActionMessage = "Email request approved ✓",
                        pendingGoogleApprovals = _uiState.value.pendingGoogleApprovals.filter { it.id != id }
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        approvalsError = "Approve failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun rejectGoogleAccess(id: String) {
        viewModelScope.launch {
            val result = repository.rejectGoogleAccess(supabaseUrl, supabaseKey, id)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        approvalActionMessage = "Email request rejected",
                        pendingGoogleApprovals = _uiState.value.pendingGoogleApprovals.filter { it.id != id }
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        approvalsError = "Reject failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun approveDeviceAccess(id: String) {
        viewModelScope.launch {
            val result = repository.approveDeviceAccess(supabaseUrl, supabaseKey, id)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        approvalActionMessage = "Device request approved ✓",
                        pendingDeviceApprovals = _uiState.value.pendingDeviceApprovals.filter { it.id != id }
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        approvalsError = "Approve failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun rejectDeviceAccess(id: String) {
        viewModelScope.launch {
            val result = repository.rejectDeviceAccess(supabaseUrl, supabaseKey, id)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        approvalActionMessage = "Device request rejected",
                        pendingDeviceApprovals = _uiState.value.pendingDeviceApprovals.filter { it.id != id }
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        approvalsError = "Reject failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun clearApprovalActionMessage() {
        _uiState.value = _uiState.value.copy(approvalActionMessage = null)
    }

    fun clearApprovalsError() {
        _uiState.value = _uiState.value.copy(approvalsError = null)
    }
}
