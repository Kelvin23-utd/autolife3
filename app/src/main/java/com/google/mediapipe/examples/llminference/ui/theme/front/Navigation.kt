package com.google.mediapipe.examples.llminference.ui.theme.front

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mediapipe.examples.llminference.MainScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateToTests = {
                    navController.navigate("tests")
                }
            )
        }
        composable("tests") {
            TestFunctionsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}