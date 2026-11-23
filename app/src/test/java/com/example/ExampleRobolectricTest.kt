package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.example.ads.AdMobManager
import com.example.ads.BannerAd
import com.example.data.model.AccountStatus
import com.example.data.model.AdMobConfig
import com.example.data.model.CustomerAddress
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.UiState
import com.example.data.model.VehicleType
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.LocalAppLanguage
import com.example.data.preferences.LocalLocalizedContext
import com.example.data.preferences.createLocalizedContext
import com.example.data.repository.AuthManager
import com.example.data.repository.DemoDeliveryRepository
import com.example.ui.screens.AddressesScreen
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.theme.KyaathiTheme
import com.example.ui.viewmodel.RiderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name and bilingual Telugu strings resolve properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("KYAATHI", context.getString(R.string.app_name))

        val teluguContext = context.createLocalizedContext(AppLanguage.TELUGU)
        assertEquals("ఖ్యాతి (KYAATHI)", teluguContext.getString(R.string.app_name))
        assertEquals("హోమ్", teluguContext.getString(R.string.nav_home))
        assertEquals("TEST MODE (డెవలప్‌మెంట్)", teluguContext.getString(R.string.test_mode_title))
    }

    @Test
    fun `phone normalization and safe enum fallbacks work`() {
        assertEquals("+919876543210", AuthManager.normalizeIndianOrIntlPhone("9876543210"))
        assertEquals("+919876543210", AuthManager.normalizeIndianOrIntlPhone("+91 98765-43210"))
        assertEquals(VehicleType.BIKE, VehicleType.fromWire("unknown_type"))
        assertEquals(AccountStatus.SUSPENDED, AccountStatus.fromWire("suspended"))
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromWire("invalid_status"))
    }

    @Test
    fun `admob manager switches cleanly between test and owner production IDs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testBanner = context.getString(R.string.admob_banner_test_id)
        val testRewarded = context.getString(R.string.admob_rewarded_test_id)

        val devConfig = AdMobConfig(
            useTestAds = true,
            bannerAdUnitId = "ca-app-pub-111/222",
            rewardedAdUnitId = "ca-app-pub-111/333"
        )
        assertEquals(testBanner, AdMobManager.resolveBannerAdUnitId(context, devConfig))
        assertEquals(testRewarded, AdMobManager.resolveRewardedAdUnitId(context, devConfig))

        val prodConfig = AdMobConfig(
            useTestAds = false,
            bannerAdUnitId = "ca-app-pub-999/888",
            rewardedAdUnitId = "ca-app-pub-999/777"
        )
        assertEquals("ca-app-pub-999/888", AdMobManager.resolveBannerAdUnitId(context, prodConfig))
        assertEquals("ca-app-pub-999/777", AdMobManager.resolveRewardedAdUnitId(context, prodConfig))
        assertTrue(AdMobManager.isUsingTestAdUnit(AdMobManager.TEST_BANNER_AD_UNIT_ID))
        assertTrue(AdMobManager.isUsingTestAdUnit(AdMobManager.TEST_REWARDED_AD_UNIT_ID))
        assertFalse(AdMobManager.isUsingTestAdUnit("ca-app-pub-999/888"))
        assertTrue(AdMobManager.resolveAdaptiveBannerAdSize(context, 412).width > 0)
    }

    @Test
    fun `banner ad composable renders test ad in dashboard and detaches lifecycle observer on dispose`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        fakeOwner.registry.currentState = Lifecycle.State.RESUMED
        var showBanner by mutableStateOf(true)

        composeTestRule.setContent {
            KyaathiTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides AppLanguage.ENGLISH,
                    LocalLocalizedContext provides context,
                    LocalLifecycleOwner provides fakeOwner
                ) {
                    if (showBanner) {
                        BannerAd(config = AdMobConfig(useTestAds = true))
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("banner_ad").assertIsDisplayed()
        composeTestRule.onNodeWithText("TEST AD").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        composeTestRule.waitForIdle()

        showBanner = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("banner_ad").assertDoesNotExist()
        assertEquals(0, fakeOwner.registry.observerCount)
    }

    @Test
    fun `public welcome screen hides Admin Portal, shows Test Mode, and surfaces clear Google config errors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var enteredTestMode = false

        composeTestRule.setContent {
            KyaathiTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides AppLanguage.ENGLISH,
                    LocalLocalizedContext provides context
                ) {
                    AuthScreen(
                        onToggleLanguage = {},
                        onAuthSuccess = {},
                        onEnterTestMode = { enteredTestMode = true }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Admin portal MUST NOT exist on the public welcome screen
        composeTestRule.onNodeWithTag("portal_admin_tab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("portal_mode_selector").assertDoesNotExist()

        // Tapping Google Sign-In when Firebase isn't initialized in Robolectric must recover and show error banner
        composeTestRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_error_banner")
            .performScrollTo()
            .assertIsDisplayed()

        // TEST MODE login button must work immediately
        composeTestRule.onNodeWithTag("test_mode_login_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(enteredTestMode)
    }

    @Test
    fun `protected admin login screen requires Google auth and has no test mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var returnedToRider = false

        composeTestRule.setContent {
            KyaathiTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides AppLanguage.ENGLISH,
                    LocalLocalizedContext provides context
                ) {
                    AdminLoginScreen(
                        onToggleLanguage = {},
                        onBackToRiderApp = { returnedToRider = true },
                        onAdminAuthSuccess = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("test_mode_login_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("admin_google_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_back_to_rider_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(returnedToRider)
    }

    @Test
    fun `home dashboard renders rider count, search bar, demo badge, and saved addresses`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = DeliveryBoyProfile(
            id = "u1",
            userId = "u1",
            fullName = "Kiran Reddy",
            mobileNumber = "+919876543210",
            savedAddressCount = 2L
        )
        val addresses = listOf(
            CustomerAddress(
                id = "a1",
                userId = "u1",
                customerName = "Lakshmi Narayana",
                mobileNumber = "9000111222",
                fullAddress = "Flat 302, Sri Sai Towers",
                area = "Kukatpally",
                landmark = "Near JNTU"
            ),
            CustomerAddress(
                id = "a2",
                userId = "u1",
                customerName = "Suresh Babu",
                mobileNumber = "9888777666",
                fullAddress = "H.No 12-4, Jubilee Hills",
                area = "Jubilee Hills"
            )
        )

        composeTestRule.setContent {
            KyaathiTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides AppLanguage.ENGLISH,
                    LocalLocalizedContext provides context
                ) {
                    HomeDashboardScreen(
                        profile = profile,
                        addressesState = UiState.Success(addresses),
                        searchQuery = "",
                        filteredAddresses = addresses,
                        adMobConfig = AdMobConfig(useTestAds = true),
                        onSearchQueryChange = {},
                        onToggleLanguage = {},
                        onAddNewAddress = {},
                        onViewAllAddresses = {},
                        onAddressClick = {},
                        isDemoMode = true
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("demo_mode_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("total_customers_count").assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_search_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasText("Lakshmi Narayana"))
        composeTestRule.onNodeWithText("Lakshmi Narayana").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasText("Suresh Babu"))
        composeTestRule.onNodeWithText("Suresh Babu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("banner_ad"))
        composeTestRule.onNodeWithTag("banner_ad").assertIsDisplayed()
        composeTestRule.onNodeWithText("TEST AD").assertIsDisplayed()
    }

    @Test
    fun `addresses screen triggers instant search callback by name or mobile`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var capturedQuery = ""
        val sample = listOf(
            CustomerAddress(
                id = "a1",
                userId = "u1",
                customerName = "Ramesh Kumar",
                mobileNumber = "9123456789",
                fullAddress = "Plot 18, Madhapur",
                area = "Madhapur"
            )
        )

        composeTestRule.setContent {
            KyaathiTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides AppLanguage.ENGLISH,
                    LocalLocalizedContext provides context
                ) {
                    AddressesScreen(
                        addressesState = UiState.Success(sample),
                        searchQuery = capturedQuery,
                        filteredAddresses = sample,
                        adMobConfig = AdMobConfig(useTestAds = true),
                        onSearchQueryChange = { capturedQuery = it },
                        onAddressClick = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("addresses_search_bar")
            .performTextInput("91234")
        composeTestRule.waitForIdle()
        assertEquals("91234", capturedQuery)
    }

    @Test
    fun `test mode end-to-end journey supports login, saving, searching, editing, and logout`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule.setContent {
            KyaathiTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides AppLanguage.ENGLISH,
                    LocalLocalizedContext provides context
                ) {
                    AppRoot(
                        isProtectedAdminRoute = false,
                        onExitProtectedAdminRoute = {},
                        onToggleLanguage = {}
                    )
                }
            }
        }

        // 1. Login via TEST MODE (without Firebase)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("test_mode_login_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // 2. Verify temporary demo banner and initial 3 sample addresses
        composeTestRule.onNodeWithTag("test_mode_top_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("total_customers_count").assertTextEquals("3")

        // 3. Add a new customer address
        composeTestRule.onNodeWithTag("home_add_address_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("form_customer_name").performTextInput("Vikram Rao")
        composeTestRule.onNodeWithTag("form_customer_mobile").performTextInput("9988776655")
        composeTestRule.onNodeWithTag("form_full_address").performTextInput("Flat 501, Cyber Towers")
        composeTestRule.onNodeWithTag("form_area").performTextInput("Hitech City")
        composeTestRule.onNodeWithTag("form_landmark").performTextInput("Near Mindspace Gate")
        composeTestRule.onNodeWithTag("form_save_address_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // Count updates to 4 and new customer appears
        composeTestRule.onNodeWithTag("total_customers_count").assertTextEquals("4")
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasText("Vikram Rao"))
        composeTestRule.onNodeWithText("Vikram Rao").assertIsDisplayed()

        // 4. Search by mobile number and open newly created address
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_search_bar"))
        composeTestRule.onNodeWithTag("home_search_bar")
            .performTextInput("998877")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Vikram Rao").assertIsDisplayed()

        // 5. Open detail and edit customer name
        composeTestRule.onNodeWithText("Vikram Rao").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_edit_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("form_customer_name").performTextClearance()
        composeTestRule.onNodeWithTag("form_customer_name").performTextInput("Vikram Rao Updated")
        composeTestRule.onNodeWithTag("form_save_address_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Vikram Rao Updated").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_back_button").performClick()
        composeTestRule.waitForIdle()

        // 6. Logout / Exit TEST MODE returns cleanly to public AuthScreen
        composeTestRule.onNodeWithTag("test_mode_exit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("test_mode_login_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `demo repository and RiderViewModel filter, add, update, and delete accurately`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = DemoDeliveryRepository()
            val vm = RiderViewModel(repo, DemoDeliveryRepository.DEMO_USER_ID)

            val addressesJob = backgroundScope.launch(testDispatcher) { vm.addressesState.collect() }
            val filteredJob = backgroundScope.launch(testDispatcher) { vm.filteredAddresses.collect() }

            assertTrue(vm.isDemoMode)
            assertEquals(3, (vm.addressesState.value as UiState.Success).data.size)

            vm.updateSearchQuery("9888777")
            assertEquals(1, vm.filteredAddresses.value.size)
            assertEquals("Suresh Babu (Demo)", vm.filteredAddresses.value.first().customerName)

            vm.updateSearchQuery("")
            var addOk = false
            vm.saveAddress(
                editingAddressId = null,
                customerName = "Meena Kumari",
                mobileNumber = "9012345678",
                fullAddress = "Door 4-5, Miyapur",
                area = "Miyapur",
                landmark = "Metro Pillar 610",
                deliveryNotes = "Ring bell",
                latitude = null,
                longitude = null
            ) { addOk = it.isSuccess }
            assertTrue(addOk)
            assertEquals(4, (vm.addressesState.value as UiState.Success).data.size)

            val createdId = (vm.addressesState.value as UiState.Success).data.first().id
            assertFalse(createdId.isBlank())

            var editOk = false
            vm.saveAddress(
                editingAddressId = createdId,
                customerName = "Meena Kumari Edited",
                mobileNumber = "9012345678",
                fullAddress = "Door 4-5, Miyapur",
                area = "Miyapur",
                landmark = "Metro Pillar 610",
                deliveryNotes = "Updated note",
                latitude = null,
                longitude = null
            ) { editOk = it.isSuccess }
            assertTrue(editOk)
            assertEquals(
                "Meena Kumari Edited",
                (vm.addressesState.value as UiState.Success).data.first { it.id == createdId }.customerName
            )

            var deleteOk = false
            vm.deleteAddress(createdId) { deleteOk = it.isSuccess }
            assertTrue(deleteOk)
            assertEquals(3, (vm.addressesState.value as UiState.Success).data.size)

            addressesJob.cancel()
            filteredJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
