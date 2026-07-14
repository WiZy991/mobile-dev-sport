package com.fitnessclub.app.ui.screens.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.fitnessclub.app.ui.navigation.Screen
import com.fitnessclub.app.ui.screens.home.HomeScreen
import com.fitnessclub.app.ui.screens.mytrainings.MyTrainingsScreen
import com.fitnessclub.app.ui.screens.mytrainings.MyTrainingsViewModel
import com.fitnessclub.app.ui.screens.profile.ProfileScreen
import com.fitnessclub.app.ui.screens.profile.ProfileViewModel
import com.fitnessclub.app.ui.screens.qrcode.QrCodeViewModel
import com.fitnessclub.app.ui.screens.schedule.ScheduleScreen
import com.fitnessclub.app.ui.screens.schedule.ScheduleViewModel
import com.fitnessclub.app.ui.components.SecureScreenEffect
import com.fitnessclub.app.ui.theme.AppShapes
import com.fitnessclub.app.ui.theme.Primary
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color

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
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    var selectedTab by remember(startTab) { mutableStateOf(startTab) }
    LaunchedEffect(currentRoute) {
        val tab = when (currentRoute) {
            Screen.Home.route -> 0
            Screen.Schedule.route -> 1
            Screen.MyTrainings.route -> 2
            Screen.Profile.route -> 3
            else -> null
        }
        if (tab != null) selectedTab = tab
    }
    var showQrSheet by remember { mutableStateOf(false) }
    val qrViewModel: QrCodeViewModel = hiltViewModel()
    val qrUiState by qrViewModel.uiState.collectAsState()
    
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
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showQrSheet = true },
                    containerColor = Primary,
                    shape = AppShapes.medium,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 4.dp,
                        hoveredElevation = 8.dp,
                        focusedElevation = 6.dp
                    )
                ) {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = if (qrUiState.isInsideGym) "Выход из зала" else "Вход в зал"
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
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
                        label = {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            navController.navigate(item.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> {
                HomeScreen(
                    onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) },
                    onNavigateToPersonalTraining = { navController.navigate(Screen.PersonalTraining.route) },
                    onNavigateToShop = { navController.navigate(Screen.Shop.route) },
                    onNavigateToSubscriptionPlans = { navController.navigate(Screen.SubscriptionPlans.route) },
                    onNavigateToTrainers = { navController.navigate(Screen.Trainers.route) },
                    onNavigateToClubInfo = { navController.navigate(Screen.Clubs.route) },
                    onNavigateToLockers = { navController.navigate(Screen.Lockers.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToQrCode = { navController.navigate(Screen.QrCode.route) },
                    onNavigateToTrainingDetails = { trainingId ->
                        navController.navigate(Screen.TrainingDetails.createRoute(trainingId))
                    },
                    onNavigateToTrainingDiary = { navController.navigate(Screen.TrainingDiary.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                    onNavigateToReferral = { navController.navigate(Screen.Referral.route) }
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
                            popUpTo(Screen.Home.route) { inclusive = true }
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
                    onNavigateToGuestPass = {
                        navController.navigate(Screen.GuestPass.route)
                    },
                    onNavigateToNotifications = {
                        navController.navigate(Screen.Notifications.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToEditProfile = {
                        navController.navigate(Screen.EditProfile.route)
                    },
                    onNavigateToDocuments = {
                        navController.navigate(Screen.Documents.route)
                    },
                    onNavigateToPurchaseHistory = {
                        navController.navigate(Screen.PurchaseHistory.route)
                    },
                    onNavigateToHelp = {
                        navController.navigate(Screen.Help.route)
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    }
                )
            }
        }
    }
    
    if (showQrSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQrSheet = false },
            sheetState = sheetState
        ) {
            SecureScreenEffect()
            QrQuickAccessContent(
                viewModel = qrViewModel,
                onClose = { showQrSheet = false }
            )
        }
    }
}

@Composable
private fun QrQuickAccessContent(
    viewModel: QrCodeViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(24.dp)
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (uiState.isInsideGym) "Выход из зала" else "Вход в зал",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = uiState.userName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        when {
            uiState.qrCodeData != null -> {
                val qrBitmap = remember(uiState.qrCodeData) {
                    generateQrBitmap(uiState.qrCodeData!!, 300)
                }
                qrBitmap?.let {
                    Card(
                        modifier = Modifier.size(240.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR код",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            uiState.isLoading -> CircularProgressIndicator()
            else -> Text(
                text = "Войдите в аккаунт",
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.secondsRemaining > 0 && uiState.qrCodeData != null && !uiState.isInsideGym) {
            Text(
                text = "Обновление кода через ${uiState.secondsRemaining} сек",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (uiState.isInsideGym && uiState.qrCodeData != null) {
            Text(
                text = "Код для выхода из зала",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (uiState.isInsideGym) "Поднесите к сканеру на выходе" else "Поднесите к сканеру",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Закрыть")
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
