package com.fitnessclub.app.ui.screens.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.fitnessclub.app.ui.navigation.Screen
import com.fitnessclub.app.ui.screens.home.HomeScreen
import com.fitnessclub.app.ui.screens.mytrainings.MyTrainingsScreen
import com.fitnessclub.app.ui.screens.mytrainings.MyTrainingsViewModel
import com.fitnessclub.app.ui.screens.profile.ProfileScreen
import com.fitnessclub.app.ui.screens.profile.ProfileViewModel
import com.fitnessclub.app.ui.screens.schedule.ScheduleScreen
import com.fitnessclub.app.ui.screens.schedule.ScheduleViewModel

data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    startTab: Int = 0
) {
    var selectedTab by remember { mutableIntStateOf(startTab) }
    
    val navItems = listOf(
        BottomNavItem(
            title = "Главная",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            route = Screen.Home.route
        ),
        BottomNavItem(
            title = "Расписание",
            selectedIcon = Icons.Filled.CalendarMonth,
            unselectedIcon = Icons.Outlined.CalendarMonth,
            route = Screen.Schedule.route
        ),
        BottomNavItem(
            title = "Мои записи",
            selectedIcon = Icons.Filled.FitnessCenter,
            unselectedIcon = Icons.Outlined.FitnessCenter,
            route = Screen.MyTrainings.route
        ),
        BottomNavItem(
            title = "Профиль",
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            route = Screen.Profile.route
        )
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) 
                                    item.selectedIcon 
                                else 
                                    item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> {
                HomeScreen(
                    onNavigateToSchedule = { selectedTab = 1 },
                    onNavigateToPersonalTraining = { navController.navigate(Screen.PersonalTraining.route) },
                    onNavigateToShop = { navController.navigate(Screen.Shop.route) },
                    onNavigateToTrainers = { navController.navigate(Screen.Trainers.route) },
                    onNavigateToClubInfo = { navController.navigate(Screen.ClubInfo.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) }
                )
            }
            1 -> {
                val viewModel: ScheduleViewModel = hiltViewModel()
                ScheduleScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(paddingValues),
                    onTrainingClick = { trainingId ->
                        navController.navigate(Screen.TrainingDetails.createRoute(trainingId))
                    }
                )
            }
            2 -> {
                val viewModel: MyTrainingsViewModel = hiltViewModel()
                MyTrainingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(paddingValues),
                    onTrainingClick = { trainingId ->
                        navController.navigate(Screen.TrainingDetails.createRoute(trainingId))
                    }
                )
            }
            3 -> {
                val viewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(paddingValues),
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToQrCode = {
                        navController.navigate(Screen.QrCode.route)
                    },
                    onNavigateToSubscriptionPlans = {
                        navController.navigate(Screen.SubscriptionPlans.route)
                    },
                    onNavigateToReferral = {
                        navController.navigate(Screen.Referral.route)
                    },
                    onNavigateToNotifications = {
                        navController.navigate(Screen.Notifications.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToEditProfile = {
                        navController.navigate(Screen.EditProfile.route)
                    }
                )
            }
        }
    }
}
