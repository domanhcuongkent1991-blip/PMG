package com.example.devicetracker.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Search : NavRoutes("search")
    data object SyncStatus : NavRoutes("sync-status")
    data object HgtChecks : NavRoutes("hgt-checks")
    data object Edit : NavRoutes("edit")
    data object Detail : NavRoutes("detail/{recordId}") {
        fun create(recordId: String) = "detail/$recordId"
    }
    data object RepairDate : NavRoutes("repair-date/{recordId}") {
        fun create(recordId: String) = "repair-date/$recordId"
    }
}
