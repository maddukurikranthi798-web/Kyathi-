package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.CustomerAddress
import com.example.data.preferences.LocalLocalizedContext
import com.example.util.MapsLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressFormScreen(
    existingAddress: CustomerAddress?,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (
        customerName: String,
        mobileNumber: String,
        fullAddress: String,
        area: String,
        landmark: String,
        deliveryNotes: String,
        latitude: Double?,
        longitude: Double?,
        onError: (String) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val localizedContext = LocalLocalizedContext.current
    val isEditing = existingAddress != null

    var customerName by remember(existingAddress?.id) {
        mutableStateOf(existingAddress?.customerName.orEmpty())
    }
    var mobileNumber by remember(existingAddress?.id) {
        mutableStateOf(existingAddress?.mobileNumber.orEmpty())
    }
    var fullAddress by remember(existingAddress?.id) {
        mutableStateOf(existingAddress?.fullAddress.orEmpty())
    }
    var area by remember(existingAddress?.id) {
        mutableStateOf(existingAddress?.area.orEmpty())
    }
    var landmark by remember(existingAddress?.id) {
        mutableStateOf(existingAddress?.landmark.orEmpty())
    }
    var deliveryNotes by remember(existingAddress?.id) {
        mutableStateOf(existingAddress?.deliveryNotes.orEmpty())
    }
    var latitude by remember(existingAddress?.id) { mutableStateOf(existingAddress?.latitude) }
    var longitude by remember(existingAddress?.id) { mutableStateOf(existingAddress?.longitude) }
    var isFetchingGps by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isFetchingGps = true
            MapsLauncher.fetchCurrentCoordinates(
                context = context,
                onResult = { lat, lng ->
                    latitude = lat
                    longitude = lng
                    isFetchingGps = false
                },
                onError = {
                    isFetchingGps = false
                }
            )
        } else {
            errorMessage = localizedContext.getString(R.string.gps_permission_denied)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = localizedContext.getString(
                            if (isEditing) R.string.title_edit_address else R.string.title_add_address
                        ),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("address_form_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = localizedContext.getString(R.string.btn_cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = {
                        if (it.length <= 100) customerName = it
                        errorMessage = null
                    },
                    label = { Text(localizedContext.getString(R.string.field_customer_name)) },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_customer_name")
                )

                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = {
                        if (it.length <= 20) mobileNumber = it
                        errorMessage = null
                    },
                    label = { Text(localizedContext.getString(R.string.field_customer_mobile)) },
                    leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_customer_mobile")
                )

                OutlinedTextField(
                    value = fullAddress,
                    onValueChange = {
                        if (it.length <= 300) fullAddress = it
                        errorMessage = null
                    },
                    label = { Text(localizedContext.getString(R.string.field_full_address)) },
                    leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_full_address")
                )

                OutlinedTextField(
                    value = area,
                    onValueChange = {
                        if (it.length <= 100) area = it
                        errorMessage = null
                    },
                    label = { Text(localizedContext.getString(R.string.field_area)) },
                    leadingIcon = { Icon(Icons.Rounded.LocationCity, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_area")
                )

                OutlinedTextField(
                    value = landmark,
                    onValueChange = { if (it.length <= 150) landmark = it },
                    label = { Text(localizedContext.getString(R.string.field_landmark)) },
                    leadingIcon = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_landmark")
                )

                OutlinedTextField(
                    value = deliveryNotes,
                    onValueChange = { if (it.length <= 300) deliveryNotes = it },
                    label = { Text(localizedContext.getString(R.string.field_delivery_notes)) },
                    leadingIcon = { Icon(Icons.Rounded.NoteAlt, contentDescription = null) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_delivery_notes")
                )

                OutlinedButton(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasFine) {
                            isFetchingGps = true
                            MapsLauncher.fetchCurrentCoordinates(
                                context = context,
                                onResult = { lat, lng ->
                                    latitude = lat
                                    longitude = lng
                                    isFetchingGps = false
                                },
                                onError = { isFetchingGps = false }
                            )
                        } else {
                            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    enabled = !isFetchingGps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("form_pin_gps_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isFetchingGps) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(localizedContext.getString(R.string.btn_pin_gps))
                }

                if (latitude != null && longitude != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = localizedContext.getString(R.string.gps_pinned, latitude!!, longitude!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val cName = customerName.trim()
                        val cPhone = mobileNumber.trim()
                        val cAddr = fullAddress.trim()
                        val cArea = area.trim()

                        when {
                            cName.length < 2 -> errorMessage = localizedContext.getString(R.string.validation_name)
                            cPhone.length !in 8..20 -> errorMessage = localizedContext.getString(R.string.validation_mobile)
                            cAddr.length < 5 -> errorMessage = localizedContext.getString(R.string.validation_address)
                            cArea.length < 2 -> errorMessage = localizedContext.getString(R.string.validation_area)
                            else -> onSave(
                                cName,
                                cPhone,
                                cAddr,
                                cArea,
                                landmark.trim(),
                                deliveryNotes.trim(),
                                latitude,
                                longitude
                            ) { err -> errorMessage = err }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("form_save_address_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = localizedContext.getString(
                                if (isEditing) R.string.btn_update_address else R.string.btn_save_address
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
