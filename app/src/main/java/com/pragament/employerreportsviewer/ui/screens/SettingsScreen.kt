package com.pragament.employerreportsviewer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pragament.employerreportsviewer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showKey by remember { mutableStateOf(false) }
    var fetchUuid by remember { mutableStateOf("") }
    var fetchPin by remember { mutableStateOf("") }
    
    // Show toast messages
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessStates()
        }
    }
    
    LaunchedEffect(uiState.testSuccess) {
        uiState.testSuccess?.let { success ->
            val message = if (success) "Connection successful!" else "Connection failed"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    // Show fetch config success toast
    LaunchedEffect(uiState.showFetchDialog) {
        // When dialog closes and we are not fetching, it means success if no error
        if (!uiState.showFetchDialog && !uiState.isFetchingConfig && uiState.fetchError == null && uiState.supabaseUrl.isNotBlank()) {
            // Only show if it was a fetch action (URL was just populated)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Supabase Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect to your Supabase instance to view employee attendance reports",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Fetch Config Button
            OutlinedButton(
                onClick = {
                    fetchUuid = ""
                    fetchPin = ""
                    viewModel.showFetchDialog()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF1565C0)
                )
            ) {
                Text("\uD83D\uDD11 Fetch Config from Server")
            }
            
            // Supabase URL Field
            OutlinedTextField(
                value = uiState.supabaseUrl,
                onValueChange = { viewModel.updateSupabaseUrl(it) },
                label = { Text("Supabase URL") },
                placeholder = { Text("https://your-project.supabase.co") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("Enter your Supabase project URL")
                }
            )
            
            // Anon Key Field
            OutlinedTextField(
                value = uiState.supabaseKey,
                onValueChange = { viewModel.updateSupabaseKey(it) },
                label = { Text("Anon Key") },
                placeholder = { Text("eyJhbGciOi...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "Hide" else "Show")
                    }
                },
                supportingText = {
                    Text("Your Supabase anonymous key")
                }
            )
            
            // Connection Status
            if (uiState.testSuccess != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.testSuccess == true) 
                            Color(0xFF4CAF50).copy(alpha = 0.2f) 
                        else 
                            Color(0xFFE53935).copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.testSuccess == true) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (uiState.testSuccess == true) Color(0xFF4CAF50) else Color(0xFFE53935)
                            )
                            Text(
                                text = if (uiState.testSuccess == true) 
                                    "Connection successful" 
                                else 
                                    "Connection failed",
                                color = if (uiState.testSuccess == true) Color(0xFF4CAF50) else Color(0xFFE53935),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Show error details if connection failed
                        if (uiState.testSuccess == false && uiState.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE53935).copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Test Connection Button
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isTesting && !uiState.isSaving
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Test")
                }
                
                // Save Button
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSaving && !uiState.isTesting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Help Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How to get these values",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Go to your Supabase Dashboard\n" +
                               "2. Select your project\n" +
                               "3. Go to Settings → API\n" +
                               "4. Copy the Project URL and anon public key",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // --- FETCH CONFIG DIALOG ---
    if (uiState.showFetchDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isFetchingConfig) viewModel.dismissFetchDialog()
            },
            title = { Text("Fetch Supabase Config") },
            text = {
                Column {
                    Text(
                        "Enter the UUID and PIN from your nameserver config.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Create config at: pragament.github.io/html_intranet_nameserver",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = fetchUuid,
                        onValueChange = { fetchUuid = it },
                        label = { Text("UUID *") },
                        placeholder = { Text("e.g. g3kfI3sfWHB8Hbe0cGlu") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.fetchError != null && fetchUuid.isBlank(),
                        enabled = !uiState.isFetchingConfig
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = fetchPin,
                        onValueChange = { fetchPin = it },
                        label = { Text("PIN (Optional)") },
                        placeholder = { Text("e.g. 123456") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !uiState.isFetchingConfig
                    )

                    if (uiState.fetchError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.fetchError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (uiState.isFetchingConfig) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Fetching config...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.fetchConfigFromServer(fetchUuid, fetchPin)
                    },
                    enabled = !uiState.isFetchingConfig
                ) {
                    Text("Fetch")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissFetchDialog() },
                    enabled = !uiState.isFetchingConfig
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
