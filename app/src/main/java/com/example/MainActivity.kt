package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ads.AdMobManager
import com.example.data.model.AccountStatus
import com.example.data.model.DeliveryBoyProfile
import com.example.data.model.UiState
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.LanguagePreferences
import com.example.data.preferences.LocalAppLanguage
import com.example.data.preferences.LocalLocalizedContext
import com.example.data.preferences.createLocalizedContext
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthManager
import com.example.data.repository.DeliveryRepository
import com.example.data.repository.DemoDeliveryRepository
import com.example.ui.components.TestModeBanner
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.AddressDetailScreen
import com.example.ui.screens.AddressFormScreen
import com.example.ui.screens.AddressesScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.RiderOnboardingScreen
import com.example.ui.screens.SuspendedAccountScreen
import com.example.ui.theme.KyaathiTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.RiderViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var protectedAdminRouteRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdMobManager.initializeSafely(this)
        protectedAdminRouteRequested = isAdminDeepLink(intent)
        val languagePrefs = LanguagePreferences(applicationContext)

        setContent {
            val baseContext = LocalContext.current
            val appLanguage by languagePrefs.languageFlow.collectAsStateWithLifecycle(
                initialValue = AppLanguage.ENGLISH
            )
            val localizedContext = remember(baseContext, appLanguage) {
                baseContext.createLocalizedContext(appLanguage)
            }
            val scope = rememberCoroutineScope()

            KyaathiTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides appLanguage,
                    LocalLocalizedContext provides localizedContext
                ) {
                    AppRoot(
                        isProtectedAdminRoute = protectedAdminRouteRequested,
                        onExitProtectedAdminRoute = { protectedAdminRouteRequested = false },
                        onToggleLanguage = { next ->
                            scope.launch { languagePrefs.setLanguage(next) }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isAdminDeepLink(intent)) {
            protectedAdminRouteRequested = true
        }
    }

    private fun isAdminDeepLink(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return data.scheme == "kyaathi" && data.host == "admin"
    }
}

private fun FirebaseAuth?.authStateFlow(): Flow<FirebaseUser?> {
    if (this == null) return flowOf(null)
    return callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        addAuthStateListener(listener)
        awaitClose { removeAuthStateListener(listener) }
    }
}

@Composable
fun AppRoot(
    isProtectedAdminRoute: Boolean,
    onExitProtectedAdminRoute: () -> Unit,
    onToggleLanguage: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val firebaseAuth: FirebaseAuth? = remember(context) {
        if (FirebaseApp.getApps(context).isNotEmpty()) Firebase.auth else null
    }
    val currentUser by firebaseAuth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = firebaseAuth?.currentUser
    )

    var isTestModeActive by rememberSaveable { mutableStateOf(false) }
    val demoRepository = remember { DemoDeliveryRepository() }

    val user = currentUser

    when {
        // 1. Separate Protected Admin Route (kyaathi://admin only; never exposed to delivery partners)
        isProtectedAdminRoute -> {
            if (user == null) {
                AdminLoginScreen(
                    onToggleLanguage = onToggleLanguage,
                    onBackToRiderApp = onExitProtectedAdminRoute,
                    onAdminAuthSuccess = {}
                )
            } else {
                key("admin_${user.uid}") {
                    val adminViewModel: AdminViewModel = viewModel(
                        key = "admin_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val dbId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(dbId)
                                AdminViewModel(AdminRepository(db, checkNotNull(firebaseAuth)), user)
                            }
                        }
                    )
                    AdminDashboardScreen(
                        viewModel = adminViewModel,
                        userEmail = user.email.orEmpty(),
                        onToggleLanguage = onToggleLanguage,
                        onSwitchToRiderPortal = onExitProtectedAdminRoute,
                        onLogout = {
                            AuthManager.signOut(credentialManager, scope) {
                                onExitProtectedAdminRoute()
                            }
                        }
                    )
                }
            }
        }

        // 2. Live Authenticated Delivery Partner Flow (Firebase Auth + Firestore)
        user != null -> {
            key("rider_${user.uid}") {
                val riderViewModel: RiderViewModel = viewModel(
                    key = "rider_${user.uid}",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            val dbId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(dbId)
                            RiderViewModel(
                                DeliveryRepository(db, checkNotNull(firebaseAuth)),
                                user.uid
                            )
                        }
                    }
                )
                RiderAuthenticatedFlow(
                    userDisplayName = user.displayName.orEmpty(),
                    userPhone = user.phoneNumber.orEmpty(),
                    userEmail = user.email,
                    viewModel = riderViewModel,
                    onToggleLanguage = onToggleLanguage,
                    onLogout = {
                        isTestModeActive = false
                        AuthManager.signOut(credentialManager, scope)
                    }
                )
            }
        }

        // 3. Development TEST MODE (Local Demo Delivery Partner, temporary in-memory data)
        isTestModeActive -> {
            key("demo_${DemoDeliveryRepository.DEMO_USER_ID}") {
                val demoViewModel: RiderViewModel = viewModel(
                    key = "demo_${DemoDeliveryRepository.DEMO_USER_ID}",
                    factory = viewModelFactory {
                        initializer {
                            RiderViewModel(demoRepository, DemoDeliveryRepository.DEMO_USER_ID)
                        }
                    }
                )
                RiderAuthenticatedFlow(
                    userDisplayName = "Demo Partner",
                    userPhone = "+91 90000 00000",
                    userEmail = null,
                    viewModel = demoViewModel,
                    onToggleLanguage = onToggleLanguage,
                    onLogout = {
                        demoRepository.resetToSampleData()
                        isTestModeActive = false
                    }
                )
            }
        }

        // 4. Public Welcome / Delivery Partner Auth Screen (NO Admin Portal entry)
        else -> {
            AuthScreen(
                onToggleLanguage = onToggleLanguage,
                onAuthSuccess = {},
                onEnterTestMode = {
                    demoRepository.resetToSampleData()
                    isTestModeActive = true
                }
            )
        }
    }
}

@Composable
private fun RiderAuthenticatedFlow(
    userDisplayName: String,
    userPhone: String,
    userEmail: String?,
    viewModel: RiderViewModel,
    onToggleLanguage: (AppLanguage) -> Unit,
    onLogout: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    when (val state = profileState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            SuspendedAccountScreen(
                onToggleLanguage = onToggleLanguage,
                onLogout = onLogout
            )
        }

        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                RiderOnboardingScreen(
                    initialName = userDisplayName,
                    initialPhone = userPhone,
                    email = userEmail,
                    isSaving = isSaving,
                    onToggleLanguage = onToggleLanguage,
                    onSaveProfile = { name, phone, vehicle, zone, onError ->
                        viewModel.saveProfile(name, phone, userEmail, vehicle, zone) { res ->
                            res.onFailure { onError(it.localizedMessage ?: "Failed to save profile") }
                        }
                    },
                    onLogout = onLogout
                )
            } else if (profile.statusEnum == AccountStatus.SUSPENDED) {
                SuspendedAccountScreen(
                    onToggleLanguage = onToggleLanguage,
                    onLogout = onLogout
                )
            } else {
                RiderMainShell(
                    userEmail = userEmail,
                    profile = profile,
                    viewModel = viewModel,
                    onToggleLanguage = onToggleLanguage,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun RiderMainShell(
    userEmail: String?,
    profile: DeliveryBoyProfile,
    viewModel: RiderViewModel,
    onToggleLanguage: (AppLanguage) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val localizedContext = LocalLocalizedContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val addressesState by viewModel.addressesState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredAddresses by viewModel.filteredAddresses.collectAsStateWithLifecycle()
    val adMobConfig by viewModel.adMobConfig.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isDemoMode = viewModel.isDemoMode

    val showTopLevelNav = currentDest?.let { dest ->
        dest.hasRoute<AppRoute.Home>() ||
            dest.hasRoute<AppRoute.Addresses>() ||
            dest.hasRoute<AppRoute.Profile>()
    } ?: true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        val navigateTopLevel: (AppRoute) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (showTopLevelNav && !isWideScreen) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        val isHome = currentDest?.hasRoute<AppRoute.Home>() == true
                        val isAddresses = currentDest?.hasRoute<AppRoute.Addresses>() == true
                        val isProfile = currentDest?.hasRoute<AppRoute.Profile>() == true

                        NavigationBarItem(
                            selected = isHome,
                            onClick = { navigateTopLevel(AppRoute.Home) },
                            icon = {
                                Icon(
                                    imageVector = if (isHome) Icons.Rounded.Home else Icons.Outlined.Home,
                                    contentDescription = localizedContext.getString(R.string.nav_home)
                                )
                            },
                            label = { Text(localizedContext.getString(R.string.nav_home)) },
                            modifier = Modifier.testTag("nav_home")
                        )
                        NavigationBarItem(
                            selected = isAddresses,
                            onClick = { navigateTopLevel(AppRoute.Addresses) },
                            icon = {
                                Icon(
                                    imageVector = if (isAddresses) Icons.Rounded.Contacts else Icons.Outlined.Contacts,
                                    contentDescription = localizedContext.getString(R.string.nav_addresses)
                                )
                            },
                            label = { Text(localizedContext.getString(R.string.nav_addresses)) },
                            modifier = Modifier.testTag("nav_addresses")
                        )
                        NavigationBarItem(
                            selected = isProfile,
                            onClick = { navigateTopLevel(AppRoute.Profile) },
                            icon = {
                                Icon(
                                    imageVector = if (isProfile) Icons.Rounded.Person else Icons.Outlined.Person,
                                    contentDescription = localizedContext.getString(R.string.nav_profile)
                                )
                            },
                            label = { Text(localizedContext.getString(R.string.nav_profile)) },
                            modifier = Modifier.testTag("nav_profile")
                        )
                    }
                }
            },
            floatingActionButton = {
                if (showTopLevelNav) {
                    FloatingActionButton(
                        onClick = { navController.navigate(AppRoute.AddressForm(null)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_address")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = localizedContext.getString(R.string.add_new_address)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isDemoMode) {
                    TestModeBanner(onExitTestMode = onLogout)
                }

                Row(modifier = Modifier.weight(1f)) {
                    if (showTopLevelNav && isWideScreen) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            val isHome = currentDest?.hasRoute<AppRoute.Home>() == true
                            val isAddresses = currentDest?.hasRoute<AppRoute.Addresses>() == true
                            val isProfile = currentDest?.hasRoute<AppRoute.Profile>() == true

                            NavigationRailItem(
                                selected = isHome,
                                onClick = { navigateTopLevel(AppRoute.Home) },
                                icon = {
                                    Icon(
                                        imageVector = if (isHome) Icons.Rounded.Home else Icons.Outlined.Home,
                                        contentDescription = localizedContext.getString(R.string.nav_home)
                                    )
                                },
                                label = { Text(localizedContext.getString(R.string.nav_home)) }
                            )
                            NavigationRailItem(
                                selected = isAddresses,
                                onClick = { navigateTopLevel(AppRoute.Addresses) },
                                icon = {
                                    Icon(
                                        imageVector = if (isAddresses) Icons.Rounded.Contacts else Icons.Outlined.Contacts,
                                        contentDescription = localizedContext.getString(R.string.nav_addresses)
                                    )
                                },
                                label = { Text(localizedContext.getString(R.string.nav_addresses)) }
                            )
                            NavigationRailItem(
                                selected = isProfile,
                                onClick = { navigateTopLevel(AppRoute.Profile) },
                                icon = {
                                    Icon(
                                        imageVector = if (isProfile) Icons.Rounded.Person else Icons.Outlined.Person,
                                        contentDescription = localizedContext.getString(R.string.nav_profile)
                                    )
                                },
                                label = { Text(localizedContext.getString(R.string.nav_profile)) }
                            )
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Home,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable<AppRoute.Home> {
                            HomeDashboardScreen(
                                profile = profile,
                                addressesState = addressesState,
                                searchQuery = searchQuery,
                                filteredAddresses = filteredAddresses,
                                adMobConfig = adMobConfig,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onToggleLanguage = onToggleLanguage,
                                onAddNewAddress = { navController.navigate(AppRoute.AddressForm(null)) },
                                onViewAllAddresses = { navigateTopLevel(AppRoute.Addresses) },
                                onAddressClick = { id -> navController.navigate(AppRoute.AddressDetail(id)) },
                                isDemoMode = isDemoMode
                            )
                        }

                        composable<AppRoute.Addresses> {
                            AddressesScreen(
                                addressesState = addressesState,
                                searchQuery = searchQuery,
                                filteredAddresses = filteredAddresses,
                                adMobConfig = adMobConfig,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onAddressClick = { id -> navController.navigate(AppRoute.AddressDetail(id)) },
                                isDemoMode = isDemoMode
                            )
                        }

                        composable<AppRoute.Profile> {
                            ProfileSettingsScreen(
                                profile = profile,
                                isSaving = isSaving,
                                onToggleLanguage = onToggleLanguage,
                                onUpdateProfile = { name, phone, vehicle, zone, onSuccess ->
                                    viewModel.saveProfile(name, phone, userEmail, vehicle, zone) { res ->
                                        res.onSuccess { onSuccess() }
                                            .onFailure {
                                                Toast.makeText(context, it.localizedMessage, Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                },
                                onLogout = onLogout,
                                isDemoMode = isDemoMode
                            )
                        }

                        composable<AppRoute.AddressForm> { entry ->
                            val args = entry.toRoute<AppRoute.AddressForm>()
                            val allItems = (addressesState as? UiState.Success)?.data ?: emptyList()
                            val editing = args.addressId?.let { id -> allItems.firstOrNull { it.id == id } }

                            AddressFormScreen(
                                existingAddress = editing,
                                isSaving = isSaving,
                                onBack = { navController.popBackStack() },
                                onSave = { name, phone, fullAddr, area, landmark, notes, lat, lng, onErr ->
                                    viewModel.saveAddress(
                                        editingAddressId = args.addressId,
                                        customerName = name,
                                        mobileNumber = phone,
                                        fullAddress = fullAddr,
                                        area = area,
                                        landmark = landmark,
                                        deliveryNotes = notes,
                                        latitude = lat,
                                        longitude = lng
                                    ) { res ->
                                        res.onSuccess { navController.popBackStack() }
                                            .onFailure { onErr(it.localizedMessage ?: "Failed to save address") }
                                    }
                                }
                            )
                        }

                        composable<AppRoute.AddressDetail> { entry ->
                            val args = entry.toRoute<AppRoute.AddressDetail>()
                            val allItems = (addressesState as? UiState.Success)?.data ?: emptyList()
                            val address = allItems.firstOrNull { it.id == args.addressId }

                            AddressDetailScreen(
                                address = address,
                                onBack = { navController.popBackStack() },
                                onEdit = { navController.navigate(AppRoute.AddressForm(args.addressId)) },
                                onDeleteConfirmed = {
                                    viewModel.deleteAddress(args.addressId) { res ->
                                        res.onSuccess { navController.popBackStack() }
                                            .onFailure {
                                                Toast.makeText(context, it.localizedMessage, Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
