package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.BannerAd
import com.example.data.model.AdMobConfig
import com.example.data.model.CustomerAddress
import com.example.data.model.UiState
import com.example.data.preferences.LocalLocalizedContext
import com.example.ui.components.CustomerAddressCard
import com.example.ui.components.DemoModeBadge
import com.example.ui.components.PrivateAccountBadge
import com.example.util.MapsLauncher

@Composable
fun AddressesScreen(
    addressesState: UiState<List<CustomerAddress>>,
    searchQuery: String,
    filteredAddresses: List<CustomerAddress>,
    adMobConfig: AdMobConfig,
    onSearchQueryChange: (String) -> Unit,
    onAddressClick: (String) -> Unit,
    isDemoMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localizedContext = LocalLocalizedContext.current
    var selectedArea by remember { mutableStateOf<String?>(null) }

    val allAreas = remember(addressesState) {
        val list = (addressesState as? UiState.Success)?.data ?: emptyList()
        list.map { it.area.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val finalResults = remember(filteredAddresses, selectedArea) {
        if (selectedArea == null) filteredAddresses
        else filteredAddresses.filter { it.area.equals(selectedArea, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = localizedContext.getString(R.string.nav_addresses),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (isDemoMode) {
                DemoModeBadge()
            } else {
                PrivateAccountBadge()
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(localizedContext.getString(R.string.search_placeholder)) },
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
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("addresses_search_bar")
        )

        if (allAreas.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedArea == null,
                        onClick = { selectedArea = null },
                        label = { Text(localizedContext.getString(R.string.view_all)) }
                    )
                }
                items(allAreas) { area ->
                    FilterChip(
                        selected = selectedArea == area,
                        onClick = { selectedArea = if (selectedArea == area) null else area },
                        label = { Text(area) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (addressesState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = addressesState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (finalResults.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        localizedContext.getString(R.string.no_search_results, searchQuery)
                                    } else {
                                        localizedContext.getString(R.string.no_addresses_title)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                    } else {
                        items(finalResults, key = { it.id }) { item ->
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

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        BannerAd(config = adMobConfig)
                    }
                }
            }
        }
    }
}
