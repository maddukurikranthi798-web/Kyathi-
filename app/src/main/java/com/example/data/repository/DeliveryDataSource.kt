package com.example.data.repository

import com.example.data.model.AccountStatus
import com.example.data.model.AdMobConfig
import com.example.data.model.CustomerAddress
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.VehicleType
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

interface DeliveryDataSource {
    val isDemoMode: Boolean
    fun observeProfile(userId: String): Flow<DeliveryBoyProfile?>
    fun observeAddresses(userId: String): Flow<List<CustomerAddress>>
    fun observeAdMobConfig(): Flow<AdMobConfig>

    suspend fun saveProfile(
        userId: String,
        fullName: String,
        mobileNumber: String,
        email: String?,
        vehicleType: VehicleType,
        zone: String
    ): Result<Unit>

    suspend fun addAddress(
        userId: String,
        customerName: String,
        mobileNumber: String,
        fullAddress: String,
        area: String,
        landmark: String,
        deliveryNotes: String,
        latitude: Double?,
        longitude: Double?
    ): Result<Unit>

    suspend fun updateAddress(
        userId: String,
        addressId: String,
        customerName: String,
        mobileNumber: String,
        fullAddress: String,
        area: String,
        landmark: String,
        deliveryNotes: String,
        latitude: Double?,
        longitude: Double?
    ): Result<Unit>

    suspend fun deleteAddress(userId: String, addressId: String): Result<Unit>
}

class DemoDeliveryRepository : DeliveryDataSource {
    override val isDemoMode: Boolean = true

    companion object {
        const val DEMO_USER_ID = "demo_partner_local"
    }

    private val _profile = MutableStateFlow<DeliveryBoyProfile?>(createInitialProfile())
    private val _addresses = MutableStateFlow(createSampleAddresses())
    private val _adMobConfig = MutableStateFlow(AdMobConfig(useTestAds = true))

    override fun observeProfile(userId: String): Flow<DeliveryBoyProfile?> = _profile.asStateFlow()

    override fun observeAddresses(userId: String): Flow<List<CustomerAddress>> = _addresses.asStateFlow()

    override fun observeAdMobConfig(): Flow<AdMobConfig> = _adMobConfig.asStateFlow()

    override suspend fun saveProfile(
        userId: String,
        fullName: String,
        mobileNumber: String,
        email: String?,
        vehicleType: VehicleType,
        zone: String
    ): Result<Unit> {
        val trimmedName = fullName.trim()
        val trimmedPhone = mobileNumber.trim()
        val trimmedZone = zone.trim()
        if (trimmedName.length !in 2..80) {
            return Result.failure(IllegalArgumentException("Name must be 2–80 characters."))
        }
        if (trimmedPhone.length !in 8..20) {
            return Result.failure(IllegalArgumentException("Mobile number must be 8–20 characters."))
        }
        val current = _profile.value ?: createInitialProfile()
        _profile.value = current.copy(
            fullName = trimmedName,
            mobileNumber = trimmedPhone,
            email = email?.trim()?.takeIf { it.isNotEmpty() } ?: current.email,
            vehicleType = vehicleType.wireValue,
            zone = trimmedZone,
            updatedAt = Timestamp.now()
        )
        return Result.success(Unit)
    }

    override suspend fun addAddress(
        userId: String,
        customerName: String,
        mobileNumber: String,
        fullAddress: String,
        area: String,
        landmark: String,
        deliveryNotes: String,
        latitude: Double?,
        longitude: Double?
    ): Result<Unit> {
        val name = customerName.trim()
        val phone = mobileNumber.trim()
        val address = fullAddress.trim()
        val areaStr = area.trim()
        if (name.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Customer name must be 2–100 characters."))
        }
        if (phone.length !in 8..20) {
            return Result.failure(IllegalArgumentException("Mobile number must be 8–20 characters."))
        }
        if (address.length !in 5..300) {
            return Result.failure(IllegalArgumentException("Full address must be 5–300 characters."))
        }
        if (areaStr.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Area must be 2–100 characters."))
        }

        val now = Timestamp.now()
        val newEntry = CustomerAddress(
            id = "demo_addr_${UUID.randomUUID().toString().take(8)}",
            userId = DEMO_USER_ID,
            customerName = name,
            mobileNumber = phone,
            fullAddress = address,
            area = areaStr,
            landmark = landmark.trim().ifBlank { null },
            deliveryNotes = deliveryNotes.trim().ifBlank { null },
            latitude = latitude,
            longitude = longitude,
            createdAt = now,
            updatedAt = now
        )
        val updatedList = listOf(newEntry) + _addresses.value
        _addresses.value = updatedList
        _profile.value = _profile.value?.copy(
            savedAddressCount = updatedList.size.toLong(),
            updatedAt = now
        )
        return Result.success(Unit)
    }

    override suspend fun updateAddress(
        userId: String,
        addressId: String,
        customerName: String,
        mobileNumber: String,
        fullAddress: String,
        area: String,
        landmark: String,
        deliveryNotes: String,
        latitude: Double?,
        longitude: Double?
    ): Result<Unit> {
        val name = customerName.trim()
        val phone = mobileNumber.trim()
        val address = fullAddress.trim()
        val areaStr = area.trim()
        if (addressId.isBlank()) {
            return Result.failure(IllegalArgumentException("Address ID required"))
        }
        if (name.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Customer name must be 2–100 characters."))
        }
        if (phone.length !in 8..20) {
            return Result.failure(IllegalArgumentException("Mobile number must be 8–20 characters."))
        }
        if (address.length !in 5..300) {
            return Result.failure(IllegalArgumentException("Full address must be 5–300 characters."))
        }
        if (areaStr.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Area must be 2–100 characters."))
        }

        val now = Timestamp.now()
        val currentList = _addresses.value
        if (currentList.none { it.id == addressId }) {
            return Result.failure(IllegalArgumentException("Demo address not found"))
        }
        _addresses.value = currentList.map { item ->
            if (item.id == addressId) {
                item.copy(
                    customerName = name,
                    mobileNumber = phone,
                    fullAddress = address,
                    area = areaStr,
                    landmark = landmark.trim().ifBlank { null },
                    deliveryNotes = deliveryNotes.trim().ifBlank { null },
                    latitude = latitude,
                    longitude = longitude,
                    updatedAt = now
                )
            } else {
                item
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteAddress(userId: String, addressId: String): Result<Unit> {
        val updatedList = _addresses.value.filterNot { it.id == addressId }
        _addresses.value = updatedList
        _profile.value = _profile.value?.copy(
            savedAddressCount = updatedList.size.toLong(),
            updatedAt = Timestamp.now()
        )
        return Result.success(Unit)
    }

    fun resetToSampleData() {
        _profile.value = createInitialProfile()
        _addresses.value = createSampleAddresses()
    }

    private fun createInitialProfile(): DeliveryBoyProfile {
        val now = Timestamp.now()
        return DeliveryBoyProfile(
            id = DEMO_USER_ID,
            userId = DEMO_USER_ID,
            fullName = "Demo Partner",
            mobileNumber = "+91 90000 00000",
            email = null,
            vehicleType = VehicleType.BIKE.wireValue,
            zone = "Madhapur (Demo)",
            accountStatus = AccountStatus.ACTIVE.wireValue,
            savedAddressCount = 3L,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun createSampleAddresses(): List<CustomerAddress> {
        val now = Timestamp.now()
        return listOf(
            CustomerAddress(
                id = "demo_1",
                userId = DEMO_USER_ID,
                customerName = "Lakshmi Narayana (Demo)",
                mobileNumber = "9000111222",
                fullAddress = "Flat 302, Sri Sai Towers, KPHB Phase 1",
                area = "Kukatpally",
                landmark = "Near JNTU Metro Station",
                deliveryNotes = "Temporary demo entry — call at gate 2",
                latitude = 17.4933,
                longitude = 78.3914,
                createdAt = now,
                updatedAt = now
            ),
            CustomerAddress(
                id = "demo_2",
                userId = DEMO_USER_ID,
                customerName = "Suresh Babu (Demo)",
                mobileNumber = "9888777666",
                fullAddress = "Plot 18, Kavuri Hills, Lane 4",
                area = "Madhapur",
                landmark = "Opposite Ratnadeep Supermarket",
                deliveryNotes = "Temporary demo entry — ring bell twice",
                latitude = 17.4401,
                longitude = 78.3955,
                createdAt = now,
                updatedAt = now
            ),
            CustomerAddress(
                id = "demo_3",
                userId = DEMO_USER_ID,
                customerName = "Anitha Devi (Demo)",
                mobileNumber = "9700554433",
                fullAddress = "H.No 8-2-293, Road No 36",
                area = "Jubilee Hills",
                landmark = "Near Peddamma Gudi Arch",
                deliveryNotes = "Temporary demo entry — leave with security if unavailable",
                latitude = 17.4311,
                longitude = 78.4071,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
