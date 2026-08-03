package com.example.staffapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
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
import com.example.staffapp.TrainerSpecializationCatalog
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffInfoBanner
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.phone.RussianPhoneVisualTransformation
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary

data class TrainerProfileUiState(
    val name: String = "",
    val selectedSpecializations: List<String> = emptyList(),
    val specializationsCatalog: List<String> = TrainerSpecializationCatalog.DEFAULT,
    val description: String = "",
    /** 10 национальных цифр без +7; на экране — маска `+7 (XXX) XXX-XX-XX`. */
    val phoneNationalDigits: String = "",
    val photoUrl: String? = null,
    val localPhotoUri: String? = null,
    /** Онбординг: нельзя уйти, пока не заполнены обязательные поля. */
    val requiredMode: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
) {
    val specialization: String
        get() = TrainerSpecializationCatalog.join(selectedSpecializations)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrainerProfileScreen(
    state: TrainerProfileUiState,
    onNameChange: (String) -> Unit,
    onToggleSpecialization: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.requiredMode) "Заполните профиль" else "Профиль тренера",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (!state.requiredMode) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
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
            if (state.requiredMode) {
                StaffInfoBanner(
                    "Чтобы начать работу, укажите телефон и специализацию — " +
                        "так клиенты смогут найти вас в приложении.",
                )
            } else {
                Text(
                    "Так вас увидят клиенты в разделе «Тренеры»",
                    color = StaffOnSurfaceVariant,
                )
            }
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
            Text(
                "Специализация (до ${TrainerSpecializationCatalog.MAX_SELECTED})",
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Выбрано: ${state.selectedSpecializations.size} из ${TrainerSpecializationCatalog.MAX_SELECTED}",
                modifier = Modifier.fillMaxWidth(),
                color = StaffOnSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.specializationsCatalog.forEach { label ->
                    val selected = label in state.selectedSpecializations
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleSpecialization(label) },
                        enabled = !state.saving && !state.loading &&
                            (selected || state.selectedSpecializations.size < TrainerSpecializationCatalog.MAX_SELECTED),
                        label = { Text(label) },
                    )
                }
            }
            OutlinedTextField(
                value = state.phoneNationalDigits,
                onValueChange = onPhoneChange,
                label = { Text("Телефон *") },
                placeholder = { Text("+7 (___) ___-__-__") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.saving && !state.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = remember { RussianPhoneVisualTransformation() },
                isError = state.errorMessage?.contains("телефон", ignoreCase = true) == true,
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
                    state.requiredMode -> "Сохранить и продолжить"
                    else -> "Сохранить"
                },
                onClick = onSave,
                enabled = !state.loading && !state.saving,
            )
        }
    }
}
