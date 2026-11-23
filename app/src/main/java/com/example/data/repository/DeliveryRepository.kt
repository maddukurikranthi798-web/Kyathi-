package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AccountStatus
import com.example.data.model.AdMobConfig
import com.example.data.model.CustomerAddress
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.OperationType
import com.example.data.model.VehicleType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DeliveryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : DeliveryDataSource {
    override val isDemoMode: Boolean = false

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    override fun observeProfile(userId: String): Flow<DeliveryBoyProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user profile observation" }
        val path = "delivery_boys/$userId"
        emitAll(
            db.collection("delivery_boys").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            DeliveryBoyProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override fun observeAddresses(userId: String): Flow<List<CustomerAddress>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user address observation" }
        val path = "delivery_boys/$userId/addresses"
        emitAll(
            db.collection("delivery_boys")
                .document(userId)
                .collection("addresses")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomerAddress::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeAdMobConfig(): Flow<AdMobConfig> = flow {
        requireUserId()
        val path = "app_config/admob"
        emitAll(
            db.collection("app_config").document("admob")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            AdMobConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AdMobConfig(useTestAds = true)
                    } else {
                        AdMobConfig(useTestAds = true)
                    }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    handleFirestoreError(root, OperationType.GET, path)
                    emit(AdMobConfig(useTestAds = true))
                }
        )
    }

    override suspend fun saveProfile(
        userId: String,
        fullName: String,
        mobileNumber: String,
        email: String?,
        vehicleType: VehicleType,
        zone: String
    ): Result<Unit> {
        val docPath = "delivery_boys/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user profile write" }
            val trimmedName = fullName.trim()
            val trimmedPhone = mobileNumber.trim()
            val trimmedZone = zone.trim()
            val trimmedEmail = email?.trim()?.takeIf { it.isNotEmpty() }

            require(trimmedName.length in 2..80) { "Name must be 2–80 characters." }
            require(trimmedPhone.length in 8..20) { "Mobile number must be 8–20 characters." }
            require(trimmedZone.length <= 80) { "Zone must be at most 80 characters." }

            val docRef = db.collection("delivery_boys").document(userId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "fullName" to trimmedName,
                    "mobileNumber" to trimmedPhone,
                    "vehicleType" to vehicleType.wireValue,
                    "zone" to trimmedZone,
                    "accountStatus" to AccountStatus.ACTIVE.wireValue,
                    "savedAddressCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (trimmedEmail != null) {
                    createPayload["email"] = trimmedEmail.take(120)
                }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "fullName" to trimmedName,
                    "mobileNumber" to trimmedPhone,
                    "vehicleType" to vehicleType.wireValue,
                    "zone" to trimmedZone,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (trimmedEmail != null) {
                    updatePayload["email"] = trimmedEmail.take(120)
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
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
        var docPath = "delivery_boys/$userId/addresses"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user address write" }

            val name = customerName.trim()
            val phone = mobileNumber.trim()
            val address = fullAddress.trim()
            val areaStr = area.trim()
            val landmarkStr = landmark.trim()
            val notesStr = deliveryNotes.trim()

            require(name.length in 2..100) { "Customer name must be 2–100 characters." }
            require(phone.length in 8..20) { "Mobile number must be 8–20 characters." }
            require(address.length in 5..300) { "Full address must be 5–300 characters." }
            require(areaStr.length in 2..100) { "Area must be 2–100 characters." }
            require(landmarkStr.length <= 150) { "Landmark must be at most 150 characters." }
            require(notesStr.length <= 300) { "Delivery notes must be at most 300 characters." }

            val profileRef = db.collection("delivery_boys").document(uid)
            val addrRef = profileRef.collection("addresses").document()
            docPath = addrRef.path

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "customerName" to name,
                "mobileNumber" to phone,
                "fullAddress" to address,
                "area" to areaStr,
                "landmark" to landmarkStr,
                "deliveryNotes" to notesStr,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (latitude != null && longitude != null) {
                payload["latitude"] = latitude
                payload["longitude"] = longitude
            }

            db.runBatch { batch ->
                batch.set(addrRef, payload)
                batch.update(
                    profileRef,
                    mapOf(
                        "savedAddressCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
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
        val docPath = "delivery_boys/$userId/addresses/$addressId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user address update" }
            require(addressId.isNotBlank()) { "Address ID required" }

            val name = customerName.trim()
            val phone = mobileNumber.trim()
            val address = fullAddress.trim()
            val areaStr = area.trim()
            val landmarkStr = landmark.trim()
            val notesStr = deliveryNotes.trim()

            require(name.length in 2..100) { "Customer name must be 2–100 characters." }
            require(phone.length in 8..20) { "Mobile number must be 8–20 characters." }
            require(address.length in 5..300) { "Full address must be 5–300 characters." }
            require(areaStr.length in 2..100) { "Area must be 2–100 characters." }
            require(landmarkStr.length <= 150) { "Landmark must be at most 150 characters." }
            require(notesStr.length <= 300) { "Delivery notes must be at most 300 characters." }

            val payload = mutableMapOf<String, Any>(
                "customerName" to name,
                "mobileNumber" to phone,
                "fullAddress" to address,
                "area" to areaStr,
                "landmark" to landmarkStr,
                "deliveryNotes" to notesStr,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (latitude != null && longitude != null) {
                payload["latitude"] = latitude
                payload["longitude"] = longitude
            }

            db.collection("delivery_boys")
                .document(uid)
                .collection("addresses")
                .document(addressId)
                .update(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteAddress(userId: String, addressId: String): Result<Unit> {
        val docPath = "delivery_boys/$userId/addresses/$addressId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user address deletion" }
            require(addressId.isNotBlank()) { "Address ID required" }

            val profileRef = db.collection("delivery_boys").document(uid)
            val addrRef = profileRef.collection("addresses").document(addressId)

            db.runTransaction { transaction ->
                val profileSnap = transaction.get(profileRef)
                val currentCount = profileSnap.getLong("savedAddressCount") ?: 0L
                val newCount = (currentCount - 1L).coerceAtLeast(0L)
                transaction.delete(addrRef)
                if (profileSnap.exists()) {
                    transaction.update(
                        profileRef,
                        mapOf(
                            "savedAddressCount" to newCount,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
