package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdMobConfig
import com.example.data.model.CustomerAddress
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.UiState
import com.example.data.model.VehicleType
import com.example.data.repository.DeliveryDataSource
import com.example.data.repository.unwrapFirestoreError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RiderViewModel(
    private val repository: DeliveryDataSource,
    private val currentUserId: String
) : ViewModel() {

    val isDemoMode: Boolean = repository.isDemoMode

    val profileState: StateFlow<UiState<DeliveryBoyProfile?>> =
        repository.observeProfile(currentUserId)
            .map<DeliveryBoyProfile?, UiState<DeliveryBoyProfile?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing rider profile", root)
                emit(UiState.Error(root.message ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val addressesState: StateFlow<UiState<List<CustomerAddress>>> =
        repository.observeAddresses(currentUserId)
            .map<List<CustomerAddress>, UiState<List<CustomerAddress>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing customer addresses", root)
                emit(UiState.Error(root.message ?: "Failed to load addresses"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val adMobConfig: StateFlow<AdMobConfig> =
        repository.observeAdMobConfig()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AdMobConfig(useTestAds = true)
            )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredAddresses: StateFlow<List<CustomerAddress>> =
        combine(addressesState, _searchQuery) { state, query ->
            val list = (state as? UiState.Success)?.data ?: emptyList()
            val q = query.trim().lowercase()
            if (q.isEmpty()) {
                list
            } else {
                val digitsQuery = q.filter { it.isDigit() }
                list.filter { item ->
                    item.customerName.lowercase().contains(q) ||
                        item.mobileNumber.lowercase().contains(q) ||
                        (digitsQuery.isNotEmpty() && item.mobileNumber.filter { it.isDigit() }.contains(digitsQuery)) ||
                        item.area.lowercase().contains(q) ||
                        (item.landmark?.lowercase()?.contains(q) == true)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveProfile(
        fullName: String,
        mobileNumber: String,
        email: String?,
        vehicleType: VehicleType,
        zone: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveProfile(
                userId = currentUserId,
                fullName = fullName,
                mobileNumber = mobileNumber,
                email = email,
                vehicleType = vehicleType,
                zone = zone
            )
            _isSaving.value = false
            onResult(result)
        }
    }

    fun saveAddress(
        editingAddressId: String?,
        customerName: String,
        mobileNumber: String,
        fullAddress: String,
        area: String,
        landmark: String,
        deliveryNotes: String,
        latitude: Double?,
        longitude: Double?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = if (editingAddressId.isNullOrBlank()) {
                repository.addAddress(
                    userId = currentUserId,
                    customerName = customerName,
                    mobileNumber = mobileNumber,
                    fullAddress = fullAddress,
                    area = area,
                    landmark = landmark,
                    deliveryNotes = deliveryNotes,
                    latitude = latitude,
                    longitude = longitude
                )
            } else {
                repository.updateAddress(
                    userId = currentUserId,
                    addressId = editingAddressId,
                    customerName = customerName,
                    mobileNumber = mobileNumber,
                    fullAddress = fullAddress,
                    area = area,
                    landmark = landmark,
                    deliveryNotes = deliveryNotes,
                    latitude = latitude,
                    longitude = longitude
                )
            }
            _isSaving.value = false
            onResult(result)
        }
    }

    fun deleteAddress(addressId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteAddress(currentUserId, addressId)
            _isSaving.value = false
            onResult(result)
        }
    }

    private companion object {
        const val TAG = "RiderViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
