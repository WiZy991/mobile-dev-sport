package com.fitnessclub.app.ui.screens.club

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubInfoScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FitnessClub") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+74991234567"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Позвонить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Club image placeholder
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Primary.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Primary
                    )
                }
            }
            
            // Rating
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Primary)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(4) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Icon(
                        Icons.Default.StarHalf,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Status message
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentBlue.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Добро пожаловать! Работаем ежедневно с 7:00 до 23:00",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // Contact items
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        ContactItem(
                            icon = Icons.Default.LocationOn,
                            text = "г. Москва, ул. Примерная, д. 123",
                            iconColor = Primary,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Москва,+ул.+Примерная,+123"))
                                context.startActivity(intent)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        ContactItem(
                            icon = Icons.Default.Phone,
                            text = "+7 (499) 123-45-67",
                            iconColor = Primary,
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+74991234567"))
                                context.startActivity(intent)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        ContactItem(
                            icon = Icons.Default.Email,
                            text = "info@fitnessclub.ru",
                            iconColor = Primary,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@fitnessclub.ru"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
            
            // Social links
            item {
                Text(
                    text = "Мы в социальных сетях",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        SocialItem(
                            name = "ВКонтакте",
                            color = Color(0xFF4C75A3),
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://vk.com/fitnessclub"))
                                context.startActivity(intent)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        SocialItem(
                            name = "Telegram",
                            color = Color(0xFF0088CC),
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/fitnessclub"))
                                context.startActivity(intent)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        SocialItem(
                            name = "WhatsApp",
                            color = Color(0xFF25D366),
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/74991234567"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
            
            // Working hours
            item {
                Text(
                    text = "Режим работы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        WorkingHoursRow("Пн-Пт", "7:00 - 23:00")
                        WorkingHoursRow("Сб", "8:00 - 22:00")
                        WorkingHoursRow("Вс", "9:00 - 21:00")
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ContactItem(
    icon: ImageVector,
    text: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SocialItem(
    name: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.first().toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkingHoursRow(day: String, hours: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = day, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = hours,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
