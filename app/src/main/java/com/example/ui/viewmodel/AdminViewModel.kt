package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccountStatus
import com.example.data.model.AdMobConfig
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.UiState
import com.example.data.repository.AdminRepository
import com.example.data.repository.unwrapFirestoreError
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminStats(
    val totalRiders: Int = 0,
    val activeRiders: Int = 0,
    val suspendedRiders: Int = 0,
    val totalSavedCustomers: Long = 0L
)

class AdminViewModel(
    private val repository: AdminRepository,
    private val currentUser: FirebaseUser
) : ViewModel() {

    private val _adminAccessState = MutableStateFlow<UiState<Boolean>>(UiState.Loading)
    val adminAccessState: StateFlow<UiState<Boolean>> = _adminAccessState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        verifyAccess()
    }

    fun verifyAccess() {
        viewModelScope.launch {
            _adminAccessState.value = UiState.Loading
            try {
                val allowed = repository.verifyIsAdmin(currentUser)
                _adminAccessState.value = UiState.Success(allowed)
            } catch (e: Exception) {
                _adminAccessState.value = UiState.Error(e.message ?: "Admin verification failed")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val ridersState: StateFlow<UiState<List<DeliveryBoyProfile>>> =
        _adminAccessState
            .flatMapLatest { access ->
                if (access is UiState.Success && access.data) {
                    repository.observeAllDeliveryBoys()
                        .map<List<DeliveryBoyProfile>, UiState<List<DeliveryBoyProfile>>> {
                            UiState.Success(it)
                        }
                        .catch { error ->
                            val root = unwrapFirestoreError(error)
                            Log.w(TAG, "Error observing delivery boys", root)
                            emit(UiState.Error(root.message ?: "Failed to load delivery boys"))
                        }
                } else {
                    flowOf(UiState.Loading)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val stats: StateFlow<AdminStats> = ridersState
        .map { state ->
            val list = (state as? UiState.Success)?.data ?: emptyList()
            AdminStats(
                totalRiders = list.size,
                activeRiders = list.count { it.statusEnum == AccountStatus.ACTIVE },
                suspendedRiders = list.count { it.statusEnum == AccountStatus.SUSPENDED },
                totalSavedCustomers = list.sumOf { it.savedAddressCount.coerceAtLeast(0L) }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AdminStats()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val adMobConfigState: StateFlow<AdMobConfig> =
        _adminAccessState
            .flatMapLatest { access ->
                if (access is UiState.Success && access.data) {
                    repository.observeAdMobConfig()
                } else {
                    flowOf(AdMobConfig(useTestAds = true))
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AdMobConfig(useTestAds = true)
            )

    fun toggleRiderStatus(profile: DeliveryBoyProfile, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val next = if (profile.statusEnum == AccountStatus.ACTIVE) {
                AccountStatus.SUSPENDED
            } else {
                AccountStatus.ACTIVE
            }
            val result = repository.updateAccountStatus(profile.userId, next)
            _isBusy.value = false
            onResult(result)
        }
    }

    fun updateRiderZone(targetUserId: String, zone: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateRiderZone(targetUserId, zone)
            _isBusy.value = false
            onResult(result)
        }
    }

    fun deleteRider(targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteDeliveryBoyAccount(targetUserId)
            _isBusy.value = false
            onResult(result)
        }
    }

    fun saveAdMobConfig(
        useTestAds: Boolean,
        bannerAdUnitId: String,
        rewardedAdUnitId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveAdMobConfig(
                useTestAds = useTestAds,
                bannerAdUnitId = bannerAdUnitId,
                rewardedAdUnitId = rewardedAdUnitId
            )
            _isBusy.value = false
            onResult(result)
        }
    }

    private companion object {
        const val TAG = "AdminViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
