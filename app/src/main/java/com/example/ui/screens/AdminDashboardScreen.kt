package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AccountStatus
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.UiState
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.LocalLocalizedContext
import com.example.ui.components.LanguageToggleChip
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    userEmail: String,
    onToggleLanguage: (AppLanguage) -> Unit,
    onSwitchToRiderPortal: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val localizedContext = LocalLocalizedContext.current

    val accessState by viewModel.adminAccessState.collectAsStateWithLifecycle()
    val ridersState by viewModel.ridersState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val adMobConfig by viewModel.adMobConfigState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var riderToDelete by remember { mutableStateOf<DeliveryBoyProfile?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (val access = accessState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                AdminDeniedView(
                    email = userEmail,
                    message = access.message,
                    onSwitchToRider = onSwitchToRiderPortal,
                    onLogout = onLogout
                )
            }

            is UiState.Success -> {
                if (!access.data) {
                    AdminDeniedView(
                        email = userEmail,
                        message = localizedContext.getString(R.string.auth_admin_denied_msg, userEmail.ifBlank { "Unknown" }),
                        onSwitchToRider = onSwitchToRiderPortal,
                        onLogout = onLogout
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 820.dp)
                            .fillMaxSize()
                    ) {
                        // Top Admin Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Column {
                                    Text(
                                        text = localizedContext.getString(R.string.admin_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = localizedContext.getString(R.string.admin_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LanguageToggleChip(onToggleLanguage = onToggleLanguage)
                                IconButton(
                                    onClick = onLogout,
                                    modifier = Modifier.testTag("admin_logout_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = localizedContext.getString(R.string.btn_logout),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Stats Grid (2x2)
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AdminStatCard(
                                    title = localizedContext.getString(R.string.admin_stat_total_riders),
                                    value = stats.totalRiders.toString(),
                                    icon = Icons.Rounded.Group,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                AdminStatCard(
                                    title = localizedContext.getString(R.string.admin_stat_active_riders),
                                    value = stats.activeRiders.toString(),
                                    icon = Icons.Rounded.CheckCircle,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    accentColor = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AdminStatCard(
                                    title = localizedContext.getString(R.string.admin_stat_suspended_riders),
                                    value = stats.suspendedRiders.toString(),
                                    icon = Icons.Rounded.PauseCircle,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    accentColor = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                AdminStatCard(
                                    title = localizedContext.getString(R.string.admin_stat_total_addresses),
                                    value = stats.totalSavedCustomers.toString(),
                                    icon = Icons.Rounded.Contacts,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.testTag("admin_tab_riders"),
                                text = { Text(localizedContext.getString(R.string.admin_tab_riders)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.testTag("admin_tab_ads"),
                                text = { Text(localizedContext.getString(R.string.admin_tab_ads)) }
                            )
                        }

                        if (selectedTab == 0) {
                            when (val riders = ridersState) {
                                is UiState.Loading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }

                                is UiState.Error -> {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = riders.message,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }

                                is UiState.Success -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (riders.data.isEmpty()) {
                                            item {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = localizedContext.getString(R.string.admin_no_riders),
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(24.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            items(riders.data, key = { it.userId }) { rider ->
                                                AdminRiderCard(
                                                    rider = rider,
                                                    isBusy = isBusy,
                                                    onToggleStatus = {
                                                        viewModel.toggleRiderStatus(rider) { res ->
                                                            res.onFailure {
                                                                Toast.makeText(context, it.localizedMessage, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    onDelete = { riderToDelete = rider }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // AdMob Setup Panel
                            var useTestAds by remember(adMobConfig) { mutableStateOf(adMobConfig.useTestAds) }
                            var bannerId by remember(adMobConfig) { mutableStateOf(adMobConfig.bannerAdUnitId) }
                            var rewardedId by remember(adMobConfig) { mutableStateOf(adMobConfig.rewardedAdUnitId) }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Text(
                                                text = localizedContext.getString(R.string.admin_admob_title),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = localizedContext.getString(R.string.admin_admob_subtitle),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = localizedContext.getString(R.string.admin_use_test_ads),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Switch(
                                                    checked = useTestAds,
                                                    onCheckedChange = { useTestAds = it },
                                                    modifier = Modifier.testTag("admin_admob_test_switch")
                                                )
                                            }

                                            OutlinedTextField(
                                                value = bannerId,
                                                onValueChange = { bannerId = it },
                                                label = { Text(localizedContext.getString(R.string.admin_banner_id_label)) },
                                                placeholder = { Text("ca-app-pub-xxxxxxxxxxxxxxxx/xxxxxxxxxx") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("admin_banner_id_input")
                                            )

                                            OutlinedTextField(
                                                value = rewardedId,
                                                onValueChange = { rewardedId = it },
                                                label = { Text(localizedContext.getString(R.string.admin_rewarded_id_label)) },
                                                placeholder = { Text("ca-app-pub-xxxxxxxxxxxxxxxx/xxxxxxxxxx") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("admin_rewarded_id_input")
                                            )

                                            Button(
                                                onClick = {
                                                    viewModel.saveAdMobConfig(
                                                        useTestAds = useTestAds,
                                                        bannerAdUnitId = bannerId,
                                                        rewardedAdUnitId = rewardedId
                                                    ) { res ->
                                                        res.onSuccess {
                                                            Toast.makeText(
                                                                context,
                                                                localizedContext.getString(R.string.admin_admob_saved),
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }.onFailure {
                                                            Toast.makeText(
                                                                context,
                                                                it.localizedMessage,
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(50.dp)
                                                    .testTag("admin_save_admob_button"),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text(localizedContext.getString(R.string.admin_save_admob))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    riderToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { riderToDelete = null },
            title = { Text("${localizedContext.getString(R.string.admin_action_remove)}: ${target.fullName}") },
            text = { Text("Remove ${target.fullName} (${target.mobileNumber}) from KYAATHI partner directory?") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.userId
                        riderToDelete = null
                        viewModel.deleteRider(id) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(localizedContext.getString(R.string.admin_action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { riderToDelete = null }) {
                    Text(localizedContext.getString(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AdminRiderCard(
    rider: DeliveryBoyProfile,
    isBusy: Boolean,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val localizedContext = LocalLocalizedContext.current
    val isActive = rider.statusEnum == AccountStatus.ACTIVE

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rider.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${rider.mobileNumber} · ${rider.vehicleType}" +
                            if (!rider.zone.isNullOrBlank()) " · ${rider.zone}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${localizedContext.getString(R.string.admin_stat_total_addresses)}: ${rider.savedAddressCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isActive) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = localizedContext.getString(
                            if (isActive) R.string.admin_status_active else R.string.admin_status_suspended
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onToggleStatus,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = localizedContext.getString(
                            if (isActive) R.string.admin_action_suspend else R.string.admin_action_activate
                        )
                    )
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isBusy,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(localizedContext.getString(R.string.admin_action_remove))
                }
            }
        }
    }
}

@Composable
private fun AdminDeniedView(
    email: String,
    message: String,
    onSwitchToRider: () -> Unit,
    onLogout: () -> Unit
) {
    val localizedContext = LocalLocalizedContext.current
    Column(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = localizedContext.getString(R.string.auth_admin_denied_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onSwitchToRider,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("switch_to_rider_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(localizedContext.getString(R.string.auth_mode_rider))
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(localizedContext.getString(R.string.btn_logout))
        }
    }
}
