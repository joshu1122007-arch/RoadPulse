package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.HazardAiRepository
import com.example.data.HazardRepository
import com.example.data.LocalPreferencesDataStore
import com.example.data.UserRepository
import com.example.data.WeatherRepository
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.authStateFlow
import com.example.model.IncidentRecord
import com.example.viewmodel.RoadPulseViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object RoadPulseRoutes {
    const val HOME = "home"
    const val MAP = "map"
    const val REPORT = "report"
    const val ALERTS = "alerts"
    const val PROFILE = "profile"
    const val ADMIN = "admin"
}

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = this
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedRoadPulseShell(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedRoadPulseShell(currentUserId: String) {
    val viewModel: RoadPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RoadPulseViewModel(
                    currentUserId = currentUserId,
                    hazardRepo = HazardRepository(db),
                    userRepo = UserRepository(db),
                    weatherRepo = WeatherRepository(),
                    aiRepo = HazardAiRepository(),
                    localPrefs = LocalPreferencesDataStore(app)
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: RoadPulseRoutes.HOME
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadCount = (notificationsState as? UiState.Success)?.data?.count { !it.read } ?: 0
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var inspectedIncident by remember { mutableStateOf<IncidentRecord?>(null) }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            launch { snackbarHostState.showSnackbar(msg) }
            viewModel.clearActionMessage()
        }
    }

    // If an incident detail overlay is active, render full detail screen with BackHandler
    val detailTarget = inspectedIncident
    if (detailTarget != null) {
        IncidentDetailScreen(
            incident = detailTarget,
            viewModel = viewModel,
            onBack = { inspectedIncident = null }
        )
        return
    }

    val tabs = remember(isAdmin) {
        buildList {
            add(NavTab(RoadPulseRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home))
            add(NavTab(RoadPulseRoutes.MAP, "Map", Icons.Filled.Map, Icons.Outlined.Map))
            add(NavTab(RoadPulseRoutes.REPORT, "Report", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline))
            add(NavTab(RoadPulseRoutes.ALERTS, "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications))
            add(NavTab(RoadPulseRoutes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person))
            if (isAdmin) {
                add(NavTab(RoadPulseRoutes.ADMIN, "Admin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings))
            }
        }
    }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    val iconVec = if (selected) tab.selectedIcon else tab.unselectedIcon
                                    if (tab.route == RoadPulseRoutes.ALERTS && unreadCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$unreadCount") } }) {
                                            Icon(iconVec, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(iconVec, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    val iconVec = if (selected) tab.selectedIcon else tab.unselectedIcon
                                    if (tab.route == RoadPulseRoutes.ALERTS && unreadCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$unreadCount") } }) {
                                            Icon(iconVec, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(iconVec, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = RoadPulseRoutes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(RoadPulseRoutes.HOME) {
                        HomeDashboardScreen(
                            viewModel = viewModel,
                            onOpenMap = { navigateToTab(RoadPulseRoutes.MAP) },
                            onOpenReport = { navigateToTab(RoadPulseRoutes.REPORT) },
                            onOpenNotifications = { navigateToTab(RoadPulseRoutes.ALERTS) },
                            onOpenIncident = { inspectedIncident = it }
                        )
                    }
                    composable(RoadPulseRoutes.MAP) {
                        HazardMapScreen(
                            viewModel = viewModel,
                            onOpenIncidentDetail = { inspectedIncident = it }
                        )
                    }
                    composable(RoadPulseRoutes.REPORT) {
                        ReportHazardScreen(
                            viewModel = viewModel,
                            onTrackReports = { navigateToTab(RoadPulseRoutes.PROFILE) }
                        )
                    }
                    composable(RoadPulseRoutes.ALERTS) {
                        NotificationsScreen(
                            viewModel = viewModel,
                            onOpenIncident = { inspectedIncident = it }
                        )
                    }
                    composable(RoadPulseRoutes.PROFILE) {
                        ProfileScreen(
                            viewModel = viewModel,
                            onOpenIncident = { inspectedIncident = it },
                            onOpenAdmin = { navigateToTab(RoadPulseRoutes.ADMIN) }
                        )
                    }
                    composable(RoadPulseRoutes.ADMIN) {
                        AdminDashboardScreen(
                            viewModel = viewModel,
                            onOpenIncident = { inspectedIncident = it }
                        )
                    }
                }
            }
        }
    }
}
