package com.example.devicetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.devicetracker.ui.detail.DetailScreen
import com.example.devicetracker.ui.edit.EditLogScreen
import com.example.devicetracker.ui.hgt.HgtCheckScreen
import com.example.devicetracker.ui.repair.UpdateRepairDateScreen
import com.example.devicetracker.ui.search.SearchScreen
import com.example.devicetracker.ui.sync.SyncStatusScreen

@Composable
fun DeviceTrackerNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Search.route
    ) {
        composable(NavRoutes.Search.route) {
            SearchScreen(
                onOpenSyncStatus = { navController.navigate(NavRoutes.SyncStatus.route) },
                onOpenHgtChecks = { navController.navigate(NavRoutes.HgtChecks.route) },
                onAddNew = { navController.navigate(NavRoutes.Edit.route) },
                onOpenDetail = { recordId -> navController.navigate(NavRoutes.Detail.create(recordId)) }
            )
        }
        composable(NavRoutes.SyncStatus.route) {
            SyncStatusScreen(onBack = { navController.safePopBackStack() })
        }
        composable(NavRoutes.HgtChecks.route) {
            HgtCheckScreen(onBack = { navController.safePopBackStack() })
        }
        composable(NavRoutes.Edit.route) {
            EditLogScreen(onBack = { navController.safePopBackStack() })
        }
        composable(NavRoutes.Detail.route) { backStackEntry ->
            DetailScreen(
                recordId = backStackEntry.arguments?.getString("recordId").orEmpty(),
                onBack = { navController.safePopBackStack() },
                onUpdateRepairDate = { recordId ->
                    navController.navigate(NavRoutes.RepairDate.create(recordId))
                }
            )
        }
        composable(NavRoutes.RepairDate.route) {
            UpdateRepairDateScreen(onBack = { navController.safePopBackStack() })
        }
    }
}

private fun NavHostController.safePopBackStack() {
    val currentRoute = currentBackStackEntry?.destination?.route
    if (currentRoute == NavRoutes.Search.route) return

    val popped = popBackStack()
    if (!popped || currentBackStackEntry == null) {
        navigate(NavRoutes.Search.route) {
            popUpTo(graph.startDestinationId) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }
}
