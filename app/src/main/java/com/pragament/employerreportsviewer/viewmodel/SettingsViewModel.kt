package com.pragament.employerreportsviewer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pragament.employerreportsviewer.data.AppPreferences
import com.pragament.employerreportsviewer.data.network.NameserverApi
import com.pragament.employerreportsviewer.data.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val isFetchingConfig: Boolean = false,
    val showFetchDialog: Boolean = false,
    val saveSuccess: Boolean = false,
    val testSuccess: Boolean? = null,
    val errorMessage: String? = null,
    val fetchError: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefs = AppPreferences(application)
    private val repository = AttendanceRepository()
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            val config = prefs.supabaseConfig.first()
            _uiState.value = _uiState.value.copy(
                supabaseUrl = config.first,
                supabaseKey = config.second
            )
        }
    }
    
    fun updateSupabaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            supabaseUrl = url,
            saveSuccess = false,
            testSuccess = null,
            errorMessage = null
        )
    }
    
    fun updateSupabaseKey(key: String) {
        _uiState.value = _uiState.value.copy(
            supabaseKey = key,
            saveSuccess = false,
            testSuccess = null,
            errorMessage = null
        )
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                prefs.saveSupabaseConfig(
                    _uiState.value.supabaseUrl.trim(),
                    _uiState.value.supabaseKey.trim()
                )
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Failed to save settings"
                )
            }
        }
    }
    
    fun testConnection() {
        val url = _uiState.value.supabaseUrl.trim()
        val key = _uiState.value.supabaseKey.trim()
        
        if (url.isBlank() || key.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter both URL and Key"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTesting = true, 
                errorMessage = null,
                testSuccess = null
            )
            
            val result = repository.testConnection(url, key)
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        testSuccess = true
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isTesting = false,
                        testSuccess = false,
                        errorMessage = e.message ?: "Connection failed"
                    )
                }
            )
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun clearSuccessStates() {
        _uiState.value = _uiState.value.copy(
            saveSuccess = false,
            testSuccess = null
        )
    }

    fun showFetchDialog() {
        _uiState.value = _uiState.value.copy(
            showFetchDialog = true,
            fetchError = null
        )
    }

    fun dismissFetchDialog() {
        _uiState.value = _uiState.value.copy(
            showFetchDialog = false,
            fetchError = null
        )
    }

    fun fetchConfigFromServer(uuid: String, pin: String) {
        if (uuid.isBlank()) {
            _uiState.value = _uiState.value.copy(fetchError = "UUID is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetchingConfig = true, fetchError = null)

            val response = withContext(Dispatchers.IO) {
                NameserverApi.fetchConfig(uuid.trim(), pin.trim())
            }

            if (response.success && response.config != null) {
                val url = response.config.resolvedUrl()
                val anonKey = response.config.resolvedKey()

                _uiState.value = _uiState.value.copy(
                    supabaseUrl = url.ifBlank { _uiState.value.supabaseUrl },
                    supabaseKey = anonKey.ifBlank { _uiState.value.supabaseKey },
                    isFetchingConfig = false,
                    showFetchDialog = false,
                    fetchError = null
                )

                // Auto-save the fetched config
                prefs.saveSupabaseConfig(
                    _uiState.value.supabaseUrl,
                    _uiState.value.supabaseKey
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isFetchingConfig = false,
                    fetchError = response.error ?: response.message ?: "Failed to fetch config"
                )
            }
        }
    }
}
