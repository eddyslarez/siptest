package com.eddyslarez.siptest

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eddyslarez.siptest.screens.CallHistoryScreen
import com.eddyslarez.siptest.screens.CallScreen
import com.eddyslarez.siptest.screens.DialerScreen
import com.eddyslarez.siptest.screens.RegisterScreen
import com.eddyslarez.siptest.screens.SettingsScreen
import com.eddyslarez.siptest.viewmodel.SipViewModel
import com.eddyslarez.siptest.viewmodel.isCallActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SipTestApp(
    sipViewModel: SipViewModel
) {
    val navController = rememberNavController()
    val callState by sipViewModel.callState.collectAsState()

    // Si hay una llamada activa, navegar a la pantalla de llamada
    LaunchedEffect(callState) {
        if (callState.isCallActive()) {
            navController.navigate("call") {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SIP Test App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            if (!callState.isCallActive()) {
                SipTestBottomNavigation(navController = navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "register",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("register") {
                RegisterScreen(sipViewModel = sipViewModel)
            }
            composable("dialer") {
                DialerScreen(sipViewModel = sipViewModel)
            }
            composable("call") {
                CallScreen(
                    sipViewModel = sipViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable("history") {
                CallHistoryScreen(sipViewModel = sipViewModel)
            }
            composable("settings") {
                SettingsScreen(sipViewModel = sipViewModel)
            }
        }
    }
}

@Composable
fun SipTestBottomNavigation(
    navController: androidx.navigation.NavController
) {
    val items = listOf(
        BottomNavItem("register", "Register", Icons.Default.Person),
        BottomNavItem("dialer", "Dialer", Icons.Default.Call),
        BottomNavItem("history", "History", Icons.Default.History),
        BottomNavItem("settings", "Settings", Icons.Default.Settings)
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = {
                    navController.navigate(item.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)