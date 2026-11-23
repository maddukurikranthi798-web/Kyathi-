package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.R
import com.example.data.model.CustomerAddress
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object MapsLauncher {

    fun openInGoogleMaps(
        context: Context,
        localizedContext: Context,
        address: CustomerAddress
    ) {
        val query = buildAddressQuery(address)
        val geoUri = if (address.latitude != null && address.longitude != null) {
            val label = Uri.encode("${address.customerName} (${address.area})")
            Uri.parse("geo:${address.latitude},${address.longitude}?q=${address.latitude},${address.longitude}($label)")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        }

        val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (mapsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapsIntent)
                return
            }
            val genericGeo = Intent(Intent.ACTION_VIEW, geoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (genericGeo.resolveActivity(context.packageManager) != null) {
                context.startActivity(genericGeo)
                return
            }
            val webUri = if (address.latitude != null && address.longitude != null) {
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${address.latitude},${address.longitude}")
            } else {
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(
                context,
                localizedContext.getString(R.string.maps_not_found),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun startNavigation(
        context: Context,
        localizedContext: Context,
        address: CustomerAddress
    ) {
        val destination = if (address.latitude != null && address.longitude != null) {
            "${address.latitude},${address.longitude}"
        } else {
            Uri.encode(buildAddressQuery(address))
        }

        val navIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$destination")
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (navIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(navIntent)
                return
            }
            val webDirUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=$destination"
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW, webDirUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(
                context,
                localizedContext.getString(R.string.maps_not_found),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun dialCustomer(context: Context, mobileNumber: String) {
        val cleaned = mobileNumber.trim()
        if (cleaned.isEmpty()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(cleaned)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun buildAddressQuery(address: CustomerAddress): String {
        return listOf(address.fullAddress, address.area, address.landmark.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentCoordinates(
        context: Context,
        onResult: (Double, Double) -> Unit,
        onError: () -> Unit
    ) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        onResult(loc.latitude, loc.longitude)
                    } else {
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) onResult(last.latitude, last.longitude)
                                else onError()
                            }
                            .addOnFailureListener { onError() }
                    }
                }
                .addOnFailureListener { onError() }
        } catch (_: Exception) {
            onError()
        }
    }
}
