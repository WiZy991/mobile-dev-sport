package com.example.staffapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.profile.TrainerProfileScreen
import com.example.staffapp.ui.profile.TrainerProfileUiState
import com.example.staffapp.ui.theme.StaffTheme
import kotlin.concurrent.thread

class TrainerProfileActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null

    private var uiState by mutableStateOf(TrainerProfileUiState())
    private var pendingPhotoUri: Uri? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        pendingPhotoUri = uri
        uiState = uiState.copy(localPhotoUri = uri.toString(), errorMessage = null)
        uploadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        if (session == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            StaffTheme {
                TrainerProfileScreen(
                    state = uiState,
                    onNameChange = { uiState = uiState.copy(name = it) },
                    onSpecializationChange = { uiState = uiState.copy(specialization = it) },
                    onDescriptionChange = { uiState = uiState.copy(description = it) },
                    onPhoneChange = { uiState = uiState.copy(phone = it) },
                    onPickPhoto = { pickPhoto.launch("image/*") },
                    onSave = { saveProfile() },
                    onBack = { finish() },
                )
            }
        }
        loadProfile()
    }

    private fun loadProfile() {
        uiState = uiState.copy(loading = true, errorMessage = null)
        thread {
            try {
                val profile = withRefresh { apiClient.loadTrainerProfile(it) }
                runOnUiThread {
                    uiState = uiState.copy(
                        name = profile.name,
                        specialization = profile.specialization,
                        description = profile.description,
                        phone = profile.phone,
                        photoUrl = profile.photoUrl,
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        loading = false,
                        errorMessage = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    private fun saveProfile() {
        uiState = uiState.copy(saving = true, errorMessage = null, statusMessage = null)
        thread {
            try {
                val profile = withRefresh {
                    apiClient.updateTrainerProfile(
                        token = it,
                        name = uiState.name.trim(),
                        specialization = uiState.specialization.trim(),
                        description = uiState.description.trim(),
                        phone = uiState.phone.trim(),
                    )
                }
                runOnUiThread {
                    uiState = uiState.copy(
                        name = profile.name,
                        specialization = profile.specialization,
                        description = profile.description,
                        phone = profile.phone,
                        photoUrl = profile.photoUrl ?: uiState.photoUrl,
                        saving = false,
                        statusMessage = "Сохранено. Профиль виден клиентам в разделе «Тренеры».",
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        saving = false,
                        errorMessage = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    private fun uploadPhoto(uri: Uri) {
        uiState = uiState.copy(saving = true, errorMessage = null, statusMessage = "Загружаем фото...")
        thread {
            try {
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Не удалось прочитать фото")
                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val profile = withRefresh {
                    apiClient.uploadTrainerPhoto(it, bytes, mime, "photo.$ext")
                }
                runOnUiThread {
                    pendingPhotoUri = null
                    uiState = uiState.copy(
                        photoUrl = profile.photoUrl,
                        localPhotoUri = null,
                        saving = false,
                        statusMessage = "Фото обновлено",
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        saving = false,
                        localPhotoUri = null,
                        errorMessage = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    private fun <T> withRefresh(action: (token: String) -> T): T {
        val current = session ?: throw IllegalStateException("Нет сессии")
        return try {
            action(current.accessToken)
        } catch (e: IllegalStateException) {
            if (!e.message.orEmpty().contains("401")) throw e
            val refreshed = apiClient.refresh(current.refreshToken)
            session = refreshed
            store.saveSession(refreshed)
            action(refreshed.accessToken)
        }
    }
}
