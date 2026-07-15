package com.fitnessclub.app.ui.navigation

import com.fitnessclub.app.data.config.LegalDocumentType
import com.fitnessclub.app.data.config.LegalPdfAsset

sealed class Screen(val route: String) {
    // Auth
    data object Login : Screen("login") {
        const val ARG_START_SBER = "startSber"
        const val ROUTE_WITH_ARG = "login?startSber={startSber}"
        fun createRoute(startSber: Boolean) = "login?startSber=$startSber"
    }
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
    data object PaymentPending : Screen("payment_pending/{paymentId}") {
        fun createRoute(paymentId: Int) = "payment_pending/$paymentId"
    }
    data object Shop : Screen("shop")
    
    // Profile related
    data object EditProfile : Screen("edit_profile")
    data object QrCode : Screen("qr_code")
    data object Referral : Screen("referral")
    data object Notifications : Screen("notifications")
    data object Settings : Screen("settings")
    data object ChangePassword : Screen("change_password")
    data object Help : Screen("help")
    data object About : Screen("about")
    data object NetworkInfo : Screen("network_info")

    data object LegalDocument : Screen("legal/{legalDoc}") {
        fun createRoute(type: LegalDocumentType) = "legal/${type.name}"
    }

    data object LegalPdf : Screen("legal_pdf/{pdfAsset}") {
        fun createRoute(asset: LegalPdfAsset) = "legal_pdf/${asset.name}"
    }
    
    // Club & Trainers
    data object ClubInfo : Screen("club_info")
    data object ClubDetails : Screen("club/{clubId}") {
        fun createRoute(clubId: String) = "club/$clubId"
    }
    data object Clubs : Screen("clubs")
    data object Lockers : Screen("lockers")
    data object GuestPass : Screen("guest_pass")
    data object Documents : Screen("documents")
    data object PurchaseHistory : Screen("purchase_history")
    data object TrainingDiary : Screen("training_diary")
    data object Trainers : Screen("trainers")

    data object TrainerDetails : Screen("trainer_details/{trainerId}") {
        fun createRoute(trainerId: String) = "trainer_details/$trainerId"
    }

    data object PersonalTraining : Screen("personal_training")
}

object NavArgs {
    const val TRAINING_ID = "trainingId"
    const val CLUB_ID = "clubId"
    const val TRAINER_ID = "trainerId"
    const val LEGAL_DOC = "legalDoc"
    const val LEGAL_PDF = "pdfAsset"
}
