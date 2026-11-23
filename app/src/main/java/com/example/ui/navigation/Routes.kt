package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Addresses : AppRoute

    @Serializable
    data object Profile : AppRoute

    @Serializable
    data class AddressForm(val addressId: String? = null) : AppRoute

    @Serializable
    data class AddressDetail(val addressId: String) : AppRoute
}
