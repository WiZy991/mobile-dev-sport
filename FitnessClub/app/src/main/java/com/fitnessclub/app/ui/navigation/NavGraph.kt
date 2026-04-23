package com.fitnessclub.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.fitnessclub.app.ui.navigation.NavArgs
import androidx.navigation.navArgument
import com.fitnessclub.app.ui.screens.auth.LoginScreen
import com.fitnessclub.app.ui.screens.auth.LoginViewModel
import com.fitnessclub.app.ui.screens.auth.RegisterClubPickScreen
import com.fitnessclub.app.ui.screens.auth.RegisterViewModel
import com.fitnessclub.app.ui.screens.club.ClubInfoScreen
import com.fitnessclub.app.ui.screens.clubs.ClubsScreen
import com.fitnessclub.app.ui.screens.documents.DocumentsScreen
import com.fitnessclub.app.ui.screens.purchases.PurchaseHistoryScreen
import com.fitnessclub.app.ui.screens.guestpass.GuestPassScreen
import com.fitnessclub.app.ui.screens.main.MainScreen
import com.fitnessclub.app.ui.screens.notifications.NotificationsScreen
import com.fitnessclub.app.ui.screens.personal.PersonalTrainingScreen
import com.fitnessclub.app.ui.screens.profile.EditProfileScreen
import com.fitnessclub.app.ui.screens.qrcode.QrCodeScreen
import com.fitnessclub.app.ui.screens.referral.ReferralScreen
import com.fitnessclub.app.ui.screens.lockers.LockerScreen
import com.fitnessclub.app.ui.screens.settings.SettingsScreen
import com.fitnessclub.app.ui.screens.help.HelpScreen
import com.fitnessclub.app.ui.screens.about.AboutScreen
import com.fitnessclub.app.ui.screens.shop.ShopScreen
import com.fitnessclub.app.ui.screens.subscriptions.SubscriptionPlansScreen
import com.fitnessclub.app.ui.screens.trainers.TrainerDetailsScreen
import com.fitnessclub.app.ui.screens.trainers.TrainersScreen
import com.fitnessclub.app.ui.screens.training.TrainingDetailsScreen
import com.fitnessclub.app.ui.screens.training.TrainingDetailsViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    isLoggedIn: Boolean
) {
    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route
    
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
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        navigation(
            route = Screen.Register.route,
            startDestination = RegisterRoutes.CLUB_PICK
        ) {
            composable(RegisterRoutes.CLUB_PICK) {
                val parentEntry = remember {
                    navController.getBackStackEntry(Screen.Register.route)
                }
                val viewModel: RegisterViewModel = hiltViewModel(parentEntry)
                val registerState by viewModel.uiState.collectAsState()
                RegisterClubPickScreen(
                    selectedClubId = registerState.selectedClub?.id,
                    onBack = {
                        navController.popBackStack(Screen.Login.route, inclusive = false)
                    },
                    onPicked = { club ->
                        viewModel.onClubSelected(club)
                    },
                    onRequestSberRegistration = {
                        // Пока Сбер ID не подключен: оставляем пользователя на этом шаге.
                    },
                )
            }
        }
        
        // Main screens with bottom navigation (tabs: 0=Home, 1=Schedule, 2=MyTrainings, 3=Profile)
        composable(Screen.Home.route) {
            MainScreen(
                navController = navController,
                startTab = 0
            )
        }
        
        composable(Screen.Schedule.route) {
            MainScreen(
                navController = navController,
                startTab = 1
            )
        }
        
        composable(Screen.MyTrainings.route) {
            MainScreen(
                navController = navController,
                startTab = 2
            )
        }
        
        composable(Screen.Profile.route) {
            MainScreen(
                navController = navController,
                startTab = 3
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
                onPromoCode = { /* Handled in screen */ },
                onPurchaseSuccess = { navController.popBackStack() }
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }
        
        // Help & About
        composable(Screen.Help.route) {
            HelpScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.About.route) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
        
        // Shop
        composable(Screen.Shop.route) {
            ShopScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenPurchaseHistory = { navController.navigate(Screen.PurchaseHistory.route) },
                onNavigateToSubscriptionPlans = { navController.navigate(Screen.SubscriptionPlans.route) }
            )
        }
        
        // Clubs list
        composable(Screen.Clubs.route) {
            ClubsScreen(
                onNavigateBack = { navController.popBackStack() },
                onClubClick = { clubId -> navController.navigate(Screen.ClubDetails.createRoute(clubId)) }
            )
        }

        // Club Info (single club)
        composable(Screen.ClubInfo.route) {
            ClubInfoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Club Details (by id)
        composable(
            route = Screen.ClubDetails.route,
            arguments = listOf(navArgument(NavArgs.CLUB_ID) { type = NavType.StringType })
        ) {
            ClubInfoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Trainers
        composable(Screen.Trainers.route) {
            TrainersScreen(
                onNavigateBack = { navController.popBackStack() },
                onTrainerClick = { trainerId ->
                    navController.navigate(Screen.TrainerDetails.createRoute(trainerId))
                }
            )
        }

        composable(
            route = Screen.TrainerDetails.route,
            arguments = listOf(navArgument(NavArgs.TRAINER_ID) { type = NavType.StringType })
        ) {
            TrainerDetailsScreen(
                onNavigateBack = { navController.popBackStack() },
                onBookPersonalTraining = {
                    navController.navigate(Screen.PersonalTraining.route)
                }
            )
        }

        // Personal Training
        composable(Screen.PersonalTraining.route) {
            PersonalTrainingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Lockers
        composable(Screen.Lockers.route) {
            LockerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Guest pass
        composable(Screen.GuestPass.route) {
            GuestPassScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Documents
        composable(Screen.Documents.route) {
            DocumentsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Purchase history
        composable(Screen.PurchaseHistory.route) {
            PurchaseHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
