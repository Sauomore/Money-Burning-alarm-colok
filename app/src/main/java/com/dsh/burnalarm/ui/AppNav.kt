package com.dsh.burnalarm.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val LIST = "list"
    const val EDIT = "edit"
    const val SETTINGS = "settings"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: AlarmViewModel = viewModel()

    NavHost(navController = nav, startDestination = Routes.LIST) {

        composable(Routes.LIST) {
            AlarmListScreen(
                viewModel = vm,
                onAdd = { nav.navigate("${Routes.EDIT}?id=-1") },
                onEdit = { id -> nav.navigate("${Routes.EDIT}?id=$id") },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = "${Routes.EDIT}?id={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            AlarmEditScreen(
                viewModel = vm,
                alarmId = id,
                onDone = { nav.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
