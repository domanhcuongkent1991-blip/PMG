package com.example.devicetracker.ui.navigation

import androidx.compose.runtime.Composable
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
            SyncStatusScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.HgtChecks.route) {
            HgtCheckScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.Edit.route) {
            EditLogScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.Detail.route) { backStackEntry ->
            DetailScreen(
                recordId = backStackEntry.arguments?.getString("recordId").orEmpty(),
                onBack = { navController.popBackStack() },
                onUpdateRepairDate = { recordId ->
                    navController.navigate(NavRoutes.RepairDate.create(recordId))
                }
            )
        }
        composable(NavRoutes.RepairDate.route) {
            UpdateRepairDateScreen(onBack = { navController.popBackStack() })
        }
    }
}
