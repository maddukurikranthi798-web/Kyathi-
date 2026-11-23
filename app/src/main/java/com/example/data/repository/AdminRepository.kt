package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AccountStatus
import com.example.data.model.AdMobConfig
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.OperationType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Admin must be signed in before accessing Firestore.")
    }

    suspend fun verifyIsAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        return try {
            val tokenResult = user.getIdToken(true).await()
            if (tokenResult.claims["admin"] == true) {
                return true
            }
            val doc = db.collection("admins").document(user.uid).get().await()
            doc.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admins/${user.uid}")
            false
        }
    }

    fun observeAllDeliveryBoys(): Flow<List<DeliveryBoyProfile>> = flow {
        val uid = requireUserId()
        val path = "delivery_boys"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DeliveryBoyProfile::class.java,
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

    fun observeAdMobConfig(): Flow<AdMobConfig> = flow {
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

    suspend fun updateAccountStatus(targetUserId: String, status: AccountStatus): Result<Unit> {
        val docPath = "delivery_boys/$targetUserId"
        return try {
            requireUserId()
            require(targetUserId.isNotBlank()) { "Target user ID is required" }
            db.collection("delivery_boys")
                .document(targetUserId)
                .update(
                    mapOf(
                        "accountStatus" to status.wireValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRiderZone(targetUserId: String, newZone: String): Result<Unit> {
        val docPath = "delivery_boys/$targetUserId"
        return try {
            requireUserId()
            val trimmed = newZone.trim()
            require(trimmed.length <= 80) { "Zone must be at most 80 characters" }
            db.collection("delivery_boys")
                .document(targetUserId)
                .update(
                    mapOf(
                        "zone" to trimmed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDeliveryBoyAccount(targetUserId: String): Result<Unit> {
        val docPath = "delivery_boys/$targetUserId"
        return try {
            requireUserId()
            require(targetUserId.isNotBlank()) { "Target user ID is required" }
            db.collection("delivery_boys")
                .document(targetUserId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveAdMobConfig(
        useTestAds: Boolean,
        bannerAdUnitId: String,
        rewardedAdUnitId: String
    ): Result<Unit> {
        val docPath = "app_config/admob"
        return try {
            val uid = requireUserId()
            val banner = bannerAdUnitId.trim()
            val rewarded = rewardedAdUnitId.trim()
            require(banner.length <= 100) { "Banner Ad Unit ID too long" }
            require(rewarded.length <= 100) { "Rewarded Ad Unit ID too long" }

            val docRef = db.collection("app_config").document("admob")
            val existing = docRef.get().await()
            val payload = mapOf(
                "useTestAds" to useTestAds,
                "bannerAdUnitId" to banner,
                "rewardedAdUnitId" to rewarded,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existing.exists()) {
                docRef.update(payload).await()
            } else {
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
