package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.LocalLocalizedContext
import com.example.data.repository.AuthManager
import com.example.data.repository.AuthManager.findActivity
import com.example.ui.components.KyaathiBrandHeader
import com.example.ui.theme.KyaathiAmber
import com.example.ui.theme.KyaathiAmberContainer
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onToggleLanguage: (AppLanguage) -> Unit,
    onAuthSuccess: () -> Unit,
    onEnterTestMode: () -> Unit
) {
    val context = LocalContext.current
    val localizedContext = LocalLocalizedContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val configStatus = remember(context) { AuthManager.inspectFirebaseConfig(context) }

    var riderAuthTab by remember { mutableIntStateOf(0) } // 0 = Google, 1 = Mobile OTP
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Phone OTP state
    var mobileInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                KyaathiBrandHeader(
                    subtitle = localizedContext.getString(R.string.brand_tagline),
                    onToggleLanguage = onToggleLanguage
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hero Banner Art
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        Image(
                            painter = painterResource(id = R.drawable.img_delivery_hero),
                            contentDescription = localizedContext.getString(R.string.auth_welcome_title),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = localizedContext.getString(R.string.auth_welcome_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = localizedContext.getString(R.string.auth_welcome_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!configStatus.isReadyForLiveFirebase) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = KyaathiAmberContainer,
                        border = BorderStroke(1.dp, KyaathiAmber.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("firebase_config_warning")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = KyaathiAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = localizedContext.getString(R.string.auth_firebase_warning_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = KyaathiAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Delivery Partner Auth Card (No Admin Portal entry on public screen)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        TabRow(
                            selectedTabIndex = riderAuthTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = riderAuthTab == 0,
                                onClick = {
                                    riderAuthTab = 0
                                    errorMessage = null
                                },
                                modifier = Modifier.testTag("auth_tab_google"),
                                text = { Text(localizedContext.getString(R.string.auth_tab_google)) }
                            )
                            Tab(
                                selected = riderAuthTab == 1,
                                onClick = {
                                    riderAuthTab = 1
                                    errorMessage = null
                                },
                                modifier = Modifier.testTag("auth_tab_mobile"),
                                text = { Text(localizedContext.getString(R.string.auth_tab_mobile)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (riderAuthTab == 0) {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    isLoading = true
                                    AuthManager.onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isLoading = false
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isLoading = false
                                            errorMessage = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isLoading = false
                                            errorMessage = localizedContext.getString(R.string.auth_google_cancelled)
                                        }
                                    )
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("google_sign_in_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = localizedContext.getString(R.string.auth_google_button),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        } else {
                            if (verificationId == null) {
                                OutlinedTextField(
                                    value = mobileInput,
                                    onValueChange = {
                                        mobileInput = it
                                        errorMessage = null
                                    },
                                    label = { Text(localizedContext.getString(R.string.auth_mobile_label)) },
                                    placeholder = { Text(localizedContext.getString(R.string.auth_mobile_hint)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.PhoneAndroid, contentDescription = null)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("phone_number_input"),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        val digits = mobileInput.filter { it.isDigit() }
                                        if (digits.length < 10) {
                                            errorMessage = localizedContext.getString(R.string.auth_error_invalid_phone)
                                            return@Button
                                        }
                                        val activity = context.findActivity()
                                        if (activity == null) {
                                            errorMessage = "Activity context unavailable for OTP verification."
                                            return@Button
                                        }
                                        errorMessage = null
                                        isLoading = true
                                        AuthManager.startPhoneVerification(
                                            activity = activity,
                                            phoneNumber = mobileInput,
                                            onCodeSent = { vId, _ ->
                                                isLoading = false
                                                verificationId = vId
                                            },
                                            onAutoVerified = { credential ->
                                                scope.launch {
                                                    val res = AuthManager.signInWithPhoneCredential(credential)
                                                    isLoading = false
                                                    res.onSuccess { onAuthSuccess() }
                                                        .onFailure { errorMessage = it.localizedMessage }
                                                }
                                            },
                                            onFailed = { msg ->
                                                isLoading = false
                                                errorMessage = msg
                                            }
                                        )
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("send_otp_button"),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = localizedContext.getString(R.string.auth_send_otp),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = localizedContext.getString(R.string.auth_otp_sent, mobileInput),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = otpInput,
                                    onValueChange = {
                                        if (it.length <= 6) otpInput = it.filter { c -> c.isDigit() }
                                        errorMessage = null
                                    },
                                    label = { Text(localizedContext.getString(R.string.auth_otp_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_code_input"),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        val vId = verificationId ?: return@Button
                                        if (otpInput.length != 6) {
                                            errorMessage = localizedContext.getString(R.string.auth_error_invalid_otp)
                                            return@Button
                                        }
                                        isLoading = true
                                        errorMessage = null
                                        val credential = PhoneAuthProvider.getCredential(vId, otpInput)
                                        scope.launch {
                                            val res = AuthManager.signInWithPhoneCredential(credential)
                                            isLoading = false
                                            res.onSuccess { onAuthSuccess() }
                                                .onFailure { errorMessage = it.localizedMessage ?: "OTP verification failed" }
                                        }
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("verify_otp_button"),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(localizedContext.getString(R.string.auth_verify_otp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        verificationId = null
                                        otpInput = ""
                                        errorMessage = null
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text(localizedContext.getString(R.string.auth_change_number))
                                }
                            }
                        }

                        AnimatedVisibility(visible = errorMessage != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp)
                                    .testTag("auth_error_banner"),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = localizedContext.getString(R.string.auth_privacy_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clearly Labeled Development TEST MODE Login Option
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = KyaathiAmberContainer.copy(alpha = 0.55f)
                    ),
                    border = BorderStroke(1.dp, KyaathiAmber.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Science,
                                contentDescription = null,
                                tint = KyaathiAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = localizedContext.getString(R.string.test_mode_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = KyaathiAmber,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = localizedContext.getString(R.string.test_mode_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FilledTonalButton(
                            onClick = onEnterTestMode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_mode_login_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Science,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = localizedContext.getString(R.string.test_mode_button),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
