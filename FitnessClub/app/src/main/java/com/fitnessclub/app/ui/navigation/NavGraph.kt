package com.fitnessclub.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnessclub.app.ui.screens.auth.LoginScreen
import com.fitnessclub.app.ui.screens.auth.LoginViewModel
import com.fitnessclub.app.ui.screens.auth.RegisterScreen
import com.fitnessclub.app.ui.screens.auth.RegisterViewModel
import com.fitnessclub.app.ui.screens.club.ClubInfoScreen
import com.fitnessclub.app.ui.screens.home.HomeScreen
import com.fitnessclub.app.ui.screens.main.MainScreen
import com.fitnessclub.app.ui.screens.notifications.NotificationsScreen
import com.fitnessclub.app.ui.screens.personal.PersonalTrainingScreen
import com.fitnessclub.app.ui.screens.profile.EditProfileScreen
import com.fitnessclub.app.ui.screens.qrcode.QrCodeScreen
import com.fitnessclub.app.ui.screens.referral.ReferralScreen
import com.fitnessclub.app.ui.screens.settings.SettingsScreen
import com.fitnessclub.app.ui.screens.shop.ShopScreen
import com.fitnessclub.app.ui.screens.subscriptions.SubscriptionPlansScreen
import com.fitnessclub.app.ui.screens.trainers.TrainersScreen
import com.fitnessclub.app.ui.screens.training.TrainingDetailsScreen
import com.fitnessclub.app.ui.screens.training.TrainingDetailsViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    isLoggedIn: Boolean
) {
    val startDestination = if (isLoggedIn) Screen.Schedule.route else Screen.Login.route
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Auth screens
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Schedule.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Register.route) {
            val viewModel: RegisterViewModel = hiltViewModel()
            RegisterScreen(
                viewModel = viewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.Schedule.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Main screens with bottom navigation
        composable(Screen.Schedule.route) {
            MainScreen(
                navController = navController,
                startTab = 0
            )
        }
        
        composable(Screen.MyTrainings.route) {
            MainScreen(
                navController = navController,
                startTab = 1
            )
        }
        
        composable(Screen.Profile.route) {
            MainScreen(
                navController = navController,
                startTab = 2
            )
        }
        
        // Training details
        composable(
            route = Screen.TrainingDetails.route,
            arguments = listOf(
                navArgument(NavArgs.TRAINING_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val trainingId = backStackEntry.arguments?.getString(NavArgs.TRAINING_ID) ?: ""
            val viewModel: TrainingDetailsViewModel = hiltViewModel()
            TrainingDetailsScreen(
                viewModel = viewModel,
                trainingId = trainingId,
                onBackClick = { navController.popBackStack() }
            )
        }
        
        // Subscription Plans
        composable(Screen.SubscriptionPlans.route) {
            SubscriptionPlansScreen(
                onNavigateBack = { navController.popBackStack() },
                onPromoCode = { /* Handled in screen */ }
            )
        }
        
        // QR Code
        composable(Screen.QrCode.route) {
            QrCodeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Edit Profile
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Referral
        composable(Screen.Referral.route) {
            ReferralScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Notifications
        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Settings
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Home
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) },
                onNavigateToPersonalTraining = { navController.navigate(Screen.PersonalTraining.route) },
                onNavigateToShop = { navController.navigate(Screen.Shop.route) },
                onNavigateToTrainers = { navController.navigate(Screen.Trainers.route) },
                onNavigateToClubInfo = { navController.navigate(Screen.ClubInfo.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) }
            )
        }
        
        // Shop
        composable(Screen.Shop.route) {
            ShopScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Club Info
        composable(Screen.ClubInfo.route) {
            ClubInfoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Trainers
        composable(Screen.Trainers.route) {
            TrainersScreen(
                onNavigateBack = { navController.popBackStack() },
                onTrainerClick = { /* Navigate to trainer details */ }
            )
        }
        
        // Personal Training
        composable(Screen.PersonalTraining.route) {
            PersonalTrainingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
