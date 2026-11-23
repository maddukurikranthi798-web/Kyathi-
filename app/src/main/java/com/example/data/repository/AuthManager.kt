package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class FirebaseConfigStatus(
    val isFirebaseInitialized: Boolean,
    val hasWebClientId: Boolean,
    val hasFirestoreDatabaseId: Boolean
) {
    val isReadyForGoogleSignIn: Boolean
        get() = isFirebaseInitialized && hasWebClientId

    val isReadyForLiveFirebase: Boolean
        get() = isFirebaseInitialized && hasWebClientId && hasFirestoreDatabaseId
}

object AuthManager {
    private const val TAG = "Auth"

    fun inspectFirebaseConfig(context: Context): FirebaseConfigStatus {
        val isInitialized = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
        val webClientId = try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (_: Exception) {
            ""
        }
        val dbId = try {
            context.getString(R.string.firestore_database_id).trim()
        } catch (_: Exception) {
            ""
        }
        val hasWebClient = webClientId.isNotBlank() &&
            !webClientId.contains("placeholder", ignoreCase = true) &&
            !webClientId.contains("YOUR_", ignoreCase = true)
        val hasDbId = dbId.isNotBlank() &&
            !dbId.contains("remixed-project-id", ignoreCase = true)

        return FirebaseConfigStatus(
            isFirebaseInitialized = isInitialized,
            hasWebClientId = hasWebClient,
            hasFirestoreDatabaseId = hasDbId
        )
    }

    tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        val status = inspectFirebaseConfig(context)
        if (!status.isReadyForGoogleSignIn) {
            onUnauthenticated()
            return
        }
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val status = inspectFirebaseConfig(context)
        if (!status.isFirebaseInitialized) {
            onAuthError(context.getString(R.string.auth_error_firebase_unconfigured))
            return
        }
        if (!status.hasWebClientId) {
            onAuthError(context.getString(R.string.auth_error_oauth_missing))
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (_: Exception) {
            onAuthError(context.getString(R.string.auth_error_oauth_missing))
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Unable to open Google Sign-In dialog from this screen context.")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned by Google Sign-In.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.w(TAG, "No Google credentials available on device: ${e.message}", e)
                onAuthError(context.getString(R.string.auth_error_no_google_account))
            } catch (e: GetCredentialProviderConfigurationException) {
                Log.e(TAG, "Credential Manager provider configuration error", e)
                onAuthError(context.getString(R.string.auth_error_play_services_unavailable))
            } catch (e: GetCredentialUnsupportedException) {
                Log.e(TAG, "Credential Manager unsupported on device", e)
                onAuthError(context.getString(R.string.auth_error_play_services_unavailable))
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google Credential Manager failed: ${e.type}", e)
                onAuthError(e.localizedMessage ?: context.getString(R.string.auth_error_oauth_missing))
            } catch (e: FirebaseAuthException) {
                Log.e(TAG, "Firebase Auth sign-in failed: ${e.errorCode}", e)
                onAuthError(mapFirebaseAuthError(context, e))
            } catch (e: CancellationException) {
                onAuthCancelled()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed. Check Firebase & Google Play Services.")
            }
        }
    }

    private fun mapFirebaseAuthError(context: Context, e: FirebaseAuthException): String {
        return when (e.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Google Sign-In is disabled in Firebase Console (Authentication > Sign-in method)."
            "ERROR_USER_DISABLED" ->
                "This account has been disabled in Firebase Authentication."
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "Network error while contacting Firebase Authentication. Check internet connection."
            "ERROR_INVALID_CREDENTIAL" ->
                context.getString(R.string.auth_error_oauth_missing)
            else -> e.localizedMessage ?: "Authentication failed (${e.errorCode})."
        }
    }

    fun startPhoneVerification(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (String, PhoneAuthProvider.ForceResendingToken) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (FirebaseApp.getApps(activity).isEmpty()) {
            onFailed(activity.getString(R.string.auth_error_firebase_unconfigured))
            return
        }
        val normalized = normalizeIndianOrIntlPhone(phoneNumber)
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                onAutoVerified(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "Phone verification failed: ${e.message}", e)
                onFailed(e.localizedMessage ?: "Phone verification failed. Ensure Phone Auth is enabled in Firebase Console.")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId, token)
            }
        }

        try {
            val options = PhoneAuthOptions.newBuilder(Firebase.auth)
                .setPhoneNumber(normalized)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate phone verification", e)
            onFailed(e.localizedMessage ?: "Unable to start mobile OTP verification.")
        }
    }

    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<Unit> {
        return try {
            Firebase.auth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OTP sign-in failed", e)
            Result.failure(e)
        }
    }

    fun normalizeIndianOrIntlPhone(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[\\s\\-()]"), "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.length == 10 && cleaned.all { it.isDigit() } -> "+91$cleaned"
            else -> "+$cleaned"
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    try {
                        Firebase.auth.signOut()
                    } catch (e: Exception) {
                        Log.w(TAG, "Firebase signOut skipped: ${e.message}")
                    }
                    onSignOutComplete()
                }
            }
        }
    }
}
