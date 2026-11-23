package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class VehicleType(val wireValue: String) {
    BIKE("Bike"),
    SCOOTER("Scooter"),
    VAN("Van"),
    CYCLE("Cycle"),
    OTHER("Other");

    companion object {
        fun fromWire(value: String?): VehicleType =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: BIKE
    }
}

enum class AccountStatus(val wireValue: String) {
    ACTIVE("active"),
    SUSPENDED("suspended");

    companion object {
        fun fromWire(value: String?): AccountStatus =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class DeliveryBoyProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val mobileNumber: String = "",
    val email: String? = null,
    val vehicleType: String = VehicleType.BIKE.wireValue,
    val zone: String? = null,
    val accountStatus: String = AccountStatus.ACTIVE.wireValue,
    val savedAddressCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: AccountStatus
        get() = AccountStatus.fromWire(accountStatus)

    val vehicleEnum: VehicleType
        get() = VehicleType.fromWire(vehicleType)
}

@Keep
@IgnoreExtraProperties
data class CustomerAddress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val mobileNumber: String = "",
    val fullAddress: String = "",
    val area: String = "",
    val landmark: String? = null,
    val deliveryNotes: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdMobConfig(
    @DocumentId val id: String = "",
    val useTestAds: Boolean = true,
    val bannerAdUnitId: String = "",
    val rewardedAdUnitId: String = "",
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminRecord(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val grantedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
