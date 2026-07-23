package com.example.staffapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.phone.RussianPhoneVisualTransformation
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary

data class TrainerProfileUiState(
    val name: String = "",
    val specialization: String = "",
    val description: String = "",
    /** 10 национальных цифр без +7; на экране — маска `+7 (XXX) XXX-XX-XX`. */
    val phoneNationalDigits: String = "",
    val photoUrl: String? = null,
    val localPhotoUri: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerProfileScreen(
    state: TrainerProfileUiState,
    onNameChange: (String) -> Unit,
    onSpecializationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль тренера", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StaffPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Так вас увидят клиенты в разделе «Тренеры»",
                color = StaffOnSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(StaffPrimary.copy(alpha = 0.15f))
                    .clickable(enabled = !state.saving, onClick = onPickPhoto),
                contentAlignment = Alignment.Center,
            ) {
                val preview = state.localPhotoUri ?: state.photoUrl
                if (!preview.isNullOrBlank()) {
                    AsyncImage(
                        model = preview,
                        contentDescription = "Фото",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = "Добавить фото",
                        tint = StaffPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Text("Нажмите на фото, чтобы изменить", color = StaffOnSurfaceVariant)
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Имя") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saving && !state.loading,
            )
            OutlinedTextField(
                value = state.specialization,
                onValueChange = onSpecializationChange,
                label = { Text("Специализация") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saving && !state.loading,
            )
            OutlinedTextField(
                value = state.phoneNationalDigits,
                onValueChange = onPhoneChange,
                label = { Text("Телефон") },
                placeholder = { Text("+7 (___) ___-__-__") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saving && !state.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = remember { RussianPhoneVisualTransformation() },
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text("О себе") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                enabled = !state.saving && !state.loading,
            )
            state.statusMessage?.let { Text(it, color = StaffOnSurfaceVariant) }
            state.errorMessage?.let { StaffErrorState(message = it) }
            Spacer(modifier = Modifier.height(8.dp))
            StaffPrimaryButton(
                text = when {
                    state.loading -> "Загрузка..."
                    state.saving -> "Сохраняем..."
                    else -> "Сохранить"
                },
                onClick = onSave,
                enabled = !state.loading && !state.saving,
            )
        }
    }
}
