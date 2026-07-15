package com.fitnessclub.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
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
import com.fitnessclub.app.data.auth.PaymentDeepLinkBus
import com.fitnessclub.app.ui.screens.auth.LoginScreen
import com.fitnessclub.app.ui.screens.auth.LoginViewModel
import com.fitnessclub.app.ui.screens.auth.RegisterClubPickScreen
import com.fitnessclub.app.ui.screens.auth.RegisterPassportScreen
import com.fitnessclub.app.ui.screens.auth.RegisterScreen
import com.fitnessclub.app.ui.screens.auth.RegisterSurveyScreen
import com.fitnessclub.app.ui.screens.auth.RegisterViewModel
import com.fitnessclub.app.ui.screens.club.ClubInfoScreen
import com.fitnessclub.app.ui.screens.clubs.ClubsScreen
import com.fitnessclub.app.ui.screens.documents.DocumentsScreen
import com.fitnessclub.app.ui.screens.diary.TrainingDiaryScreen
import com.fitnessclub.app.ui.screens.purchases.PurchaseHistoryScreen
import com.fitnessclub.app.ui.screens.guestpass.GuestPassScreen
import com.fitnessclub.app.ui.screens.main.MainScreen
import com.fitnessclub.app.ui.screens.notifications.NotificationsScreen
import com.fitnessclub.app.ui.screens.personal.PersonalTrainingScreen
import com.fitnessclub.app.ui.screens.profile.EditProfileScreen
import com.fitnessclub.app.ui.screens.qrcode.QrCodeScreen
import com.fitnessclub.app.ui.screens.referral.ReferralScreen
import com.fitnessclub.app.ui.screens.lockers.LockerScreen
import com.fitnessclub.app.ui.screens.settings.ChangePasswordScreen
import com.fitnessclub.app.ui.screens.settings.SettingsScreen
import com.fitnessclub.app.ui.screens.help.HelpScreen
import com.fitnessclub.app.ui.screens.about.AboutScreen
import com.fitnessclub.app.ui.screens.networkinfo.NetworkInfoScreen
import com.fitnessclub.app.ui.screens.legal.LegalDocumentScreen
import com.fitnessclub.app.ui.screens.legal.LegalPdfScreen
import com.fitnessclub.app.data.config.LegalDocumentType
import com.fitnessclub.app.data.config.LegalPdfAsset
import com.fitnessclub.app.ui.screens.shop.ShopScreen
import com.fitnessclub.app.ui.screens.subscriptions.PaymentPendingScreen
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

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        navController.navigate(Screen.Home.route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
        PaymentDeepLinkBus.events.collect { uri ->
            val paymentId = uri.getQueryParameter("payment_id")?.toIntOrNull()
            if (paymentId != null && paymentId > 0) {
                navController.navigate(Screen.PaymentPending.createRoute(paymentId)) {
                    launchSingleTop = true
                }
            }
        }
    }
    
    val openLegalPdf: (LegalPdfAsset) -> Unit = { asset ->
        navController.navigate(Screen.LegalPdf.createRoute(asset))
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Auth screens
        composable(
            route = Screen.Login.ROUTE_WITH_ARG,
            arguments = listOf(
                navArgument(Screen.Login.ARG_START_SBER) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val startWithSber = backStackEntry.arguments
                ?.getBoolean(Screen.Login.ARG_START_SBER) ?: false
            val viewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = viewModel,
                startWithSber = startWithSber,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onOpenLegalPdf = openLegalPdf,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        
        navigation(
            route = Screen.Register.route,
            startDestination = RegisterRoutes.SURVEY
        ) {
            composable(RegisterRoutes.SURVEY) {
                val parentEntry = remember {
                    navController.getBackStackEntry(Screen.Register.route)
                }
                val viewModel: RegisterViewModel = hiltViewModel(parentEntry)
                RegisterSurveyScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack(Screen.Login.route, inclusive = false)
                    },
                    onSubmit = {
                        navController.navigate(RegisterRoutes.CLUB_PICK)
                    },
                )
            }
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
                    onContinueToRegister = {
                        navController.navigate(RegisterRoutes.FORM)
                    },
                    onRequestSberRegistration = {
                        navController.navigate(Screen.Login.createRoute(startSber = true)) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(RegisterRoutes.FORM) {
                val parentEntry = remember {
                    navController.getBackStackEntry(Screen.Register.route)
                }
                val viewModel: RegisterViewModel = hiltViewModel(parentEntry)
                RegisterScreen(
                    viewModel = viewModel,
                    onNavigateToLogin = {
                        navController.popBackStack(Screen.Login.route, inclusive = false)
                    },
                    onNavigateToPassport = {
                        navController.navigate(RegisterRoutes.PASSPORT)
                    },
                    onOpenLegalPdf = openLegalPdf,
                    onRegisterSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onChangeClub = {
                        navController.popBackStack(RegisterRoutes.CLUB_PICK, inclusive = false)
                    },
                )
            }
            composable(RegisterRoutes.PASSPORT) {
                val parentEntry = remember {
                    navController.getBackStackEntry(Screen.Register.route)
                }
                val viewModel: RegisterViewModel = hiltViewModel(parentEntry)
                RegisterPassportScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
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
                onNavigateToPayment = { paymentId ->
                    navController.navigate(Screen.PaymentPending.createRoute(paymentId))
                },
                onPurchaseSuccess = { navController.popBackStack() },
                onOpenLegalDocument = { type ->
                    navController.navigate(Screen.LegalDocument.createRoute(type))
                },
                onOpenLegalPdf = openLegalPdf,
            )
        }

        composable(
            route = Screen.PaymentPending.route,
            arguments = listOf(
                navArgument("paymentId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val paymentId = backStackEntry.arguments?.getInt("paymentId") ?: 0
            PaymentPendingScreen(
                paymentId = paymentId,
                onPaymentSuccess = {
                    navController.navigate(Screen.Profile.route) {
                        popUpTo(Screen.SubscriptionPlans.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onPaymentFailed = { _ ->
                    navController.popBackStack()
                },
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChangePassword = {
                    navController.navigate(Screen.ChangePassword.route)
                },
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
                onNavigateToNetworkInfo = { navController.navigate(Screen.NetworkInfo.route) },
                onNavigateToChangePassword = {
                    navController.navigate(Screen.ChangePassword.route)
                },
                onOpenLegalDocument = { type ->
                    navController.navigate(Screen.LegalDocument.createRoute(type))
                },
                onOpenLegalPdf = openLegalPdf,
            )
        }

        composable(Screen.ChangePassword.route) {
            ChangePasswordScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.LegalDocument.route,
            arguments = listOf(
                navArgument(NavArgs.LEGAL_DOC) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val docType = backStackEntry.arguments
                ?.getString(NavArgs.LEGAL_DOC)
                ?.let { LegalDocumentType.fromRouteArg(it) }
            if (docType != null) {
                LegalDocumentScreen(
                    document = docType,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Screen.LegalPdf.route,
            arguments = listOf(
                navArgument(NavArgs.LEGAL_PDF) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val pdfAsset = backStackEntry.arguments
                ?.getString(NavArgs.LEGAL_PDF)
                ?.let { LegalPdfAsset.fromAnnotation(it) }
            if (pdfAsset != null) {
                LegalPdfScreen(
                    asset = pdfAsset,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
        
        // Help & About
        composable(Screen.Help.route) {
            HelpScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.NetworkInfo.route) {
            NetworkInfoScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.About.route) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
        
        // Shop
        composable(Screen.Shop.route) {
            ShopScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenPurchaseHistory = { navController.navigate(Screen.PurchaseHistory.route) },
                onNavigateToPayment = { paymentId ->
                    navController.navigate(Screen.PaymentPending.createRoute(paymentId))
                },
                onOpenLegalDocument = { type ->
                    navController.navigate(Screen.LegalDocument.createRoute(type))
                },
                onOpenLegalPdf = openLegalPdf,
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

        // Training diary
        composable(Screen.TrainingDiary.route) {
            TrainingDiaryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
