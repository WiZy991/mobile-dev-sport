package com.fitnessclub.app.ui.navigation

sealed class Screen(val route: String) {
    // Auth
    data object Login : Screen("login")
    data object Register : Screen("register")
    
    // Main
    data object Home : Screen("home")
    data object Schedule : Screen("schedule")
    data object MyTrainings : Screen("my_trainings")
    data object Profile : Screen("profile")
    
    // Details
    data object TrainingDetails : Screen("training/{trainingId}") {
        fun createRoute(trainingId: String) = "training/$trainingId"
    }
    
    // Subscriptions & Shop
    data object Subscriptions : Screen("subscriptions")
    data object SubscriptionPlans : Screen("subscription_plans")
    data object Shop : Screen("shop")
    
    // Profile related
    data object EditProfile : Screen("edit_profile")
    data object QrCode : Screen("qr_code")
    data object Referral : Screen("referral")
    data object Notifications : Screen("notifications")
    data object Settings : Screen("settings")
    
    // Club & Trainers
    data object ClubInfo : Screen("club_info")
    data object Trainers : Screen("trainers")
    data object PersonalTraining : Screen("personal_training")
}

object NavArgs {
    const val TRAINING_ID = "trainingId"
}
