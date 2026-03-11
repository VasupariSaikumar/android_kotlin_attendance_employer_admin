package com.pragament.employerreportsviewer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pragament.employerreportsviewer.data.model.EmployeeDeviceAccess
import com.pragament.employerreportsviewer.data.model.EmployeeGoogleAccess
import com.pragament.employerreportsviewer.data.model.SupabaseAttendanceRecord
import com.pragament.employerreportsviewer.ui.theme.PunchInColor
import com.pragament.employerreportsviewer.ui.theme.PunchOutColor
import com.pragament.employerreportsviewer.ui.theme.WorkingColor
import com.pragament.employerreportsviewer.viewmodel.DateFilter
import com.pragament.employerreportsviewer.viewmodel.ReportsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsDashboard(
    viewModel: ReportsViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val pendingCount = uiState.pendingGoogleApprovals.size + uiState.pendingDeviceApprovals.size

    // Show attendance error
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Show approvals action feedback (approve / reject toast)
    LaunchedEffect(uiState.approvalActionMessage) {
        uiState.approvalActionMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearApprovalActionMessage()
        }
    }

    // Show approvals error
    LaunchedEffect(uiState.approvalsError) {
        uiState.approvalsError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.clearApprovalsError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Employee Reports")
                        uiState.lastRefresh?.let { time ->
                            Text(
                                text = "Last updated: $time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isLoading && !uiState.approvalsLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (!uiState.isConfigured) {
            // Show configuration needed message
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Supabase Not Configured",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Please configure Supabase in Settings to view reports",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Button(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Go to Settings")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Tab Row ──────────────────────────────────────────────────
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Attendance") },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Approvals")
                                if (pendingCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pendingCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onError,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.HowToReg, contentDescription = null) }
                    )
                }

                // ── Tab Content ───────────────────────────────────────────────
                when (selectedTabIndex) {
                    0 -> AttendanceTab(uiState = uiState, viewModel = viewModel)
                    1 -> PendingApprovalsTab(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

// ─── Attendance Tab (existing logic, extracted into its own composable) ───────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceTab(
    uiState: com.pragament.employerreportsviewer.viewmodel.ReportsUiState,
    viewModel: ReportsViewModel
) {
    var showEmployeeDropdown by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<SupabaseAttendanceRecord?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filters Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Employee Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Employee:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    ExposedDropdownMenuBox(
                        expanded = showEmployeeDropdown,
                        onExpandedChange = { showEmployeeDropdown = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedEmployeeId ?: "All Employees",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showEmployeeDropdown)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        ExposedDropdownMenu(
                            expanded = showEmployeeDropdown,
                            onDismissRequest = { showEmployeeDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Employees") },
                                onClick = {
                                    viewModel.selectEmployee(null)
                                    showEmployeeDropdown = false
                                }
                            )
                            uiState.employeeIds.forEach { empId ->
                                DropdownMenuItem(
                                    text = { Text(empId) },
                                    onClick = {
                                        viewModel.selectEmployee(empId)
                                        showEmployeeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Date Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateFilter.values().forEach { filter ->
                        FilterChip(
                            selected = uiState.selectedDateFilter == filter,
                            onClick = { viewModel.selectDateFilter(filter) },
                            label = {
                                Text(
                                    when (filter) {
                                        DateFilter.ALL -> "All Time"
                                        DateFilter.TODAY -> "Today"
                                        DateFilter.THIS_WEEK -> "This Week"
                                        DateFilter.THIS_MONTH -> "This Month"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        // Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Total Records",
                value = uiState.filteredRecords.size.toString(),
                icon = Icons.Default.Person,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Employees",
                value = uiState.filteredRecords.map { it.employeeId }.distinct().size.toString(),
                icon = Icons.Default.Groups,
                color = WorkingColor,
                modifier = Modifier.weight(1f)
            )
        }

        // Records List
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "No records found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredRecords) { record ->
                    AttendanceRecordCard(
                        record = record,
                        onClick = { selectedRecord = record }
                    )
                }
            }
        }

        if (selectedRecord != null) {
            android.util.Log.d("ReportsDashboard", "Opening dialog for record: ${selectedRecord!!.id}. PunchOutImg: ${selectedRecord!!.punchOutImageUrl}")
            AttendanceDetailsDialog(
                record = selectedRecord!!,
                onDismiss = { selectedRecord = null }
            )
        }
    }
}

// ─── Pending Approvals Tab ────────────────────────────────────────────────────

@Composable
private fun PendingApprovalsTab(
    uiState: com.pragament.employerreportsviewer.viewmodel.ReportsUiState,
    viewModel: ReportsViewModel
) {
    val googleList = uiState.pendingGoogleApprovals
    val deviceList = uiState.pendingDeviceApprovals

    if (uiState.approvalsLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (googleList.isEmpty() && deviceList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = WorkingColor
                )
                Text(
                    text = "No pending approvals",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "All requests have been reviewed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Google Email Approvals ───────────────────────────────────────────
        if (googleList.isNotEmpty()) {
            item {
                ApprovalSectionHeader(
                    title = "Google Email Requests",
                    count = googleList.size,
                    icon = Icons.Default.Email
                )
            }
            items(googleList, key = { it.id }) { item ->
                GoogleApprovalCard(
                    item = item,
                    onApprove = { viewModel.approveGoogleAccess(item.id) },
                    onReject  = { viewModel.rejectGoogleAccess(item.id) }
                )
            }
        }

        // ── Device ID Approvals ──────────────────────────────────────────────
        if (deviceList.isNotEmpty()) {
            item {
                ApprovalSectionHeader(
                    title = "Device ID Requests",
                    count = deviceList.size,
                    icon = Icons.Default.PhoneAndroid,
                    modifier = if (googleList.isNotEmpty()) Modifier.padding(top = 8.dp) else Modifier
                )
            }
            items(deviceList, key = { it.id }) { item ->
                DeviceApprovalCard(
                    item = item,
                    onApprove = { viewModel.approveDeviceAccess(item.id) },
                    onReject  = { viewModel.rejectDeviceAccess(item.id) }
                )
            }
        }
    }
}

@Composable
private fun ApprovalSectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun GoogleApprovalCard(
    item: EmployeeGoogleAccess,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    ApprovalCard(
        employeeId = item.employeeId,
        primaryLabel = "Google Email",
        primaryValue = item.googleEmail,
        requestedAt = item.requestedAt,
        icon = Icons.Default.Email,
        onApprove = onApprove,
        onReject = onReject
    )
}

@Composable
private fun DeviceApprovalCard(
    item: EmployeeDeviceAccess,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    ApprovalCard(
        employeeId = item.employeeId,
        primaryLabel = "Device ID (hash)",
        primaryValue = item.deviceIdHash,
        requestedAt = item.requestedAt,
        icon = Icons.Default.PhoneAndroid,
        onApprove = onApprove,
        onReject = onReject
    )
}

@Composable
private fun ApprovalCard(
    employeeId: String,
    primaryLabel: String,
    primaryValue: String,
    requestedAt: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf<Boolean?>(null) } // true=approve, false=reject

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: employee ID + type icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = employeeId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            text = "PENDING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary value (email or device hash)
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Timestamp
            requestedAt?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Requested: ${formatApprovalTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject")
                }
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WorkingColor
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }

    // Confirmation dialog
    showConfirmDialog?.let { isApprove ->
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            icon = {
                Icon(
                    if (isApprove) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isApprove) WorkingColor else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (isApprove) "Confirm Approval" else "Confirm Rejection") },
            text = {
                Text(
                    if (isApprove)
                        "Approve this $primaryLabel request for employee $employeeId?"
                    else
                        "Reject this $primaryLabel request for employee $employeeId? The employee will need to re-submit."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isApprove) onApprove() else onReject()
                        showConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isApprove) WorkingColor else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (isApprove) "Approve" else "Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) { Text("Cancel") }
            }
        )
    }
}

// ─── Attendance Detail Dialog (unchanged) ────────────────────────────────────

@Composable
fun AttendanceDetailsDialog(
    record: SupabaseAttendanceRecord,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attendance Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // PUNCH IN SECTION
                    SectionHeader(title = "Punch In", time = record.punchInTime)

                    if (record.imageUrl != null) {
                        AttendanceImage(
                            model = record.imageUrl,
                            label = "Punch In Selfie"
                        )
                    } else {
                        StatusMessage(text = "No punch in image available", isError = true)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // PUNCH OUT SECTION
                    SectionHeader(title = "Punch Out", time = record.punchOutTime ?: "Pending")

                    if (record.punchOutImageUrl != null) {
                        AttendanceImage(
                            model = record.punchOutImageUrl,
                            label = "Punch Out Selfie"
                        )
                    } else {
                        if (record.punchOutTime == null) {
                            StatusMessage(
                                text = "User is still working",
                                icon = Icons.Default.Timer,
                                color = WorkingColor
                            )
                        } else {
                            StatusMessage(text = "No punch out image available", isError = true)
                        }
                    }

                    // Source badge (shared device / personal phone)
                    record.punchSource?.let { source ->
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (source == "personal_phone") Icons.Default.PhoneAndroid else Icons.Default.Devices,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = if (source == "personal_phone") "Personal Phone" else "Shared Office Device",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            record.googleEmail?.let { email ->
                                Text(
                                    text = "• $email",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, time: String?) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        time?.let {
            Text(
                text = formatTime(it) + " - " + formatDate(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AttendanceImage(model: Any, label: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        coil.compose.AsyncImage(
            model = model,
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

@Composable
fun StatusMessage(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Info,
    isError: Boolean = false,
    color: Color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AttendanceRecordCard(
    record: SupabaseAttendanceRecord,
    onClick: () -> Unit
) {
    val hasCheckedOut = record.punchOutTime != null
    val statusColor = if (hasCheckedOut) PunchOutColor else PunchInColor
    val statusText = if (hasCheckedOut) "Checked Out" else "Working"

    // Calculate duration if both times are present
    val duration = if (record.punchInTime != null && record.punchOutTime != null) {
        try {
            val inTime = LocalDateTime.parse(record.punchInTime.replace(" ", "T").take(19))
            val outTime = LocalDateTime.parse(record.punchOutTime.replace(" ", "T").take(19))
            val minutes = ChronoUnit.MINUTES.between(inTime, outTime)
            val hours = minutes / 60
            val mins = minutes % 60
            "${hours}h ${mins}m"
        } catch (e: Exception) {
            null
        }
    } else null

    // Format punch times
    val punchInFormatted = record.punchInTime?.let { formatTime(it) } ?: "N/A"
    val punchOutFormatted = record.punchOutTime?.let { formatTime(it) } ?: "Still working..."
    val dateFormatted = record.punchInTime?.let { formatDate(it) } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = record.employeeId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Punch source badge
                    record.punchSource?.let { src ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = if (src == "personal_phone") "📱" else "🖥",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = statusColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date
            Text(
                text = dateFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Time details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Punch In",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PunchInColor
                        )
                        Text(
                            text = punchInFormatted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Punch Out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (hasCheckedOut) PunchOutColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            text = punchOutFormatted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (hasCheckedOut) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Duration
            if (duration != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WorkingColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Duration: $duration",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = WorkingColor
                    )
                }
            }
        }
    }
}

// ─── Private helpers ──────────────────────────────────────────────────────────

private fun formatTime(isoTime: String): String {
    return try {
        val dateTime = LocalDateTime.parse(isoTime.replace(" ", "T").take(19))
        dateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
    } catch (e: Exception) {
        isoTime.take(8)
    }
}

private fun formatDate(isoTime: String): String {
    return try {
        val dateTime = LocalDateTime.parse(isoTime.replace(" ", "T").take(19))
        dateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
    } catch (e: Exception) {
        isoTime.take(10)
    }
}

private fun formatApprovalTime(isoTime: String): String {
    return try {
        // Handle ISO-8601 with optional Z or offset
        val normalised = isoTime.replace("Z", "").replace(" ", "T").take(19)
        val dateTime = LocalDateTime.parse(normalised)
        dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy  hh:mm a"))
    } catch (e: Exception) {
        isoTime
    }
}
