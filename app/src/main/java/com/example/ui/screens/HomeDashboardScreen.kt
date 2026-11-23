package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.AdMobManager
import com.example.ads.BannerAd
import com.example.ads.RewardedAdController
import com.example.data.model.AdMobConfig
import com.example.data.model.CustomerAddress
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.UiState
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.LocalLocalizedContext
import com.example.ui.components.CustomerAddressCard
import com.example.ui.components.DemoModeBadge
import com.example.ui.components.KyaathiBrandHeader
import com.example.ui.components.PrivateAccountBadge
import com.example.util.MapsLauncher

@Composable
fun HomeDashboardScreen(
    profile: DeliveryBoyProfile,
    addressesState: UiState<List<CustomerAddress>>,
    searchQuery: String,
    filteredAddresses: List<CustomerAddress>,
    adMobConfig: AdMobConfig,
    onSearchQueryChange: (String) -> Unit,
    onToggleLanguage: (AppLanguage) -> Unit,
    onAddNewAddress: () -> Unit,
    onViewAllAddresses: () -> Unit,
    onAddressClick: (String) -> Unit,
    isDemoMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localizedContext = LocalLocalizedContext.current
    val rewardedController = remember(context) { RewardedAdController(context) }
    val rewardedUnitId = remember(adMobConfig, context) {
        AdMobManager.resolveRewardedAdUnitId(context, adMobConfig)
    }

    LaunchedEffect(rewardedUnitId) {
        rewardedController.preload(rewardedUnitId)
    }

    DisposableEffect(rewardedController) {
        onDispose {
            rewardedController.release()
        }
    }

    val totalCount = when (addressesState) {
        is UiState.Success -> addressesState.data.size
        else -> profile.savedAddressCount.toInt()
    }

    val displayedAddresses = remember(filteredAddresses, searchQuery) {
        if (searchQuery.isBlank()) filteredAddresses.take(5) else filteredAddresses
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            KyaathiBrandHeader(
                subtitle = localizedContext.getString(R.string.greeting_rider, profile.fullName),
                onToggleLanguage = onToggleLanguage
            )
        }

        // Purple Hero Summary Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedContext.getString(R.string.total_customers_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = totalCount.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("total_customers_count")
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Contacts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDemoMode) {
                            DemoModeBadge()
                        } else {
                            PrivateAccountBadge()
                        }
                        Button(
                            onClick = onAddNewAddress,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("home_add_address_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddLocationAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = localizedContext.getString(R.string.add_new_address),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Instant Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(localizedContext.getString(R.string.search_placeholder))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = localizedContext.getString(R.string.clear_search)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_bar")
            )
        }

        // Section Header: Recently Saved Addresses
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = localizedContext.getString(R.string.recent_saves_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (totalCount > 0) {
                    TextButton(
                        onClick = onViewAllAddresses,
                        modifier = Modifier.testTag("home_view_all_button")
                    ) {
                        Text(localizedContext.getString(R.string.view_all))
                    }
                }
            }
        }

        when (addressesState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = addressesState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (addressesState.data.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_addresses),
                                    contentDescription = localizedContext.getString(R.string.no_addresses_title),
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = localizedContext.getString(R.string.no_addresses_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = localizedContext.getString(R.string.no_addresses_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onAddNewAddress,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.AddLocationAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(localizedContext.getString(R.string.add_new_address))
                                }
                            }
                        }
                    }
                } else if (displayedAddresses.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = localizedContext.getString(R.string.no_search_results, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(displayedAddresses, key = { it.id }) { item ->
                        CustomerAddressCard(
                            address = item,
                            onClick = { onAddressClick(item.id) },
                            onNavigateClick = {
                                MapsLauncher.startNavigation(context, localizedContext, item)
                            },
                            onCallClick = {
                                MapsLauncher.dialCustomer(context, item.mobileNumber)
                            }
                        )
                    }
                }
            }
        }

        // Rewarded Ad Action Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localizedContext.getString(R.string.rewarded_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = localizedContext.getString(R.string.rewarded_card_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = {
                                val activity = AdMobManager.findActivity(context)
                                if (activity != null) {
                                    rewardedController.show(
                                        activity = activity,
                                        adUnitId = rewardedUnitId,
                                        onRewardEarned = { amount, type ->
                                            Toast.makeText(
                                                context,
                                                localizedContext.getString(R.string.reward_earned_msg, amount, type),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        onUnavailable = {
                                            Toast.makeText(
                                                context,
                                                localizedContext.getString(R.string.ad_unavailable_msg),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("watch_rewarded_ad_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (rewardedController.isLoading) {
                                    localizedContext.getString(R.string.ad_loading_msg)
                                } else {
                                    localizedContext.getString(R.string.btn_watch_rewarded)
                                }
                            )
                        }
                    }
                }
            }
        }

        // AdMob Banner Slot
        item {
            BannerAd(config = adMobConfig)
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
