package com.example.staffapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.phone.normalizeRussianNationalDigits
import com.example.staffapp.ui.phone.phoneForApi
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
    private var requiredMode = false

    // Системный Photo Picker: не требует разрешений на доступ к медиа
    // и работает одинаково на всех оболочках (пункт 20 репорта).
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
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
        requiredMode = intent.getBooleanExtra(EXTRA_REQUIRED, false)
        uiState = uiState.copy(requiredMode = requiredMode)
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
                    onToggleSpecialization = { label ->
                        val current = uiState.selectedSpecializations.toMutableList()
                        if (label in current) {
                            current.remove(label)
                        } else if (current.size < TrainerSpecializationCatalog.MAX_SELECTED) {
                            current.add(label)
                        }
                        uiState = uiState.copy(
                            selectedSpecializations = current,
                            errorMessage = null,
                        )
                    },
                    onDescriptionChange = { uiState = uiState.copy(description = it) },
                    onPhoneChange = {
                        uiState = uiState.copy(
                            phoneNationalDigits = normalizeRussianNationalDigits(it),
                            errorMessage = null,
                        )
                    },
                    onPickPhoto = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onSave = { saveProfile() },
                    onBack = {
                        if (!requiredMode) finish()
                    },
                )
            }
        }
        loadProfile()
    }

    private fun applyProfile(profile: TrainerPublicProfile) {
        uiState = uiState.copy(
            name = profile.name,
            selectedSpecializations = profile.specializations.ifEmpty {
                TrainerSpecializationCatalog.parseSelected(
                    profile.specialization,
                    profile.specializationsCatalog,
                )
            },
            specializationsCatalog = profile.specializationsCatalog,
            description = profile.description,
            phoneNationalDigits = normalizeRussianNationalDigits(profile.phone),
            photoUrl = profile.photoUrl ?: uiState.photoUrl,
            publicationStatus = profile.publicationStatus,
            publicationStatusLabel = profile.publicationStatusLabel,
            loading = false,
            saving = false,
        )
    }

    private fun loadProfile() {
        uiState = uiState.copy(loading = true, errorMessage = null)
        thread {
            try {
                val profile = withRefresh { apiClient.loadTrainerProfile(it) }
                runOnUiThread { applyProfile(profile) }
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
        val digits = uiState.phoneNationalDigits
        if (digits.length != 10) {
            uiState = uiState.copy(errorMessage = "Введите полный номер телефона")
            return
        }
        if (uiState.selectedSpecializations.isEmpty()) {
            uiState = uiState.copy(errorMessage = "Выберите хотя бы одну специализацию")
            return
        }
        if (uiState.name.isBlank()) {
            uiState = uiState.copy(errorMessage = "Укажите имя")
            return
        }
        val phoneApi = phoneForApi(digits)
        uiState = uiState.copy(saving = true, errorMessage = null, statusMessage = null)
        thread {
            try {
                val profile = withRefresh {
                    apiClient.updateTrainerProfile(
                        token = it,
                        name = uiState.name.trim(),
                        specialization = uiState.specialization,
                        description = uiState.description.trim(),
                        phone = phoneApi,
                    )
                }
                runOnUiThread {
                    applyProfile(profile)
                    uiState = uiState.copy(
                        statusMessage = when {
                            profile.publicationStatus == "moderation" || profile.needsModeration ->
                                "Сохранено. Профиль на модерации — клиенты увидят его после проверки сотрудником."
                            else -> "Сохранено."
                        },
                    )
                    if (requiredMode && profile.profileComplete) {
                        startActivity(Intent(this, WorkActivity::class.java))
                        finish()
                    }
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
                val bytes = preparePhotoForUpload(uri)
                val profile = withRefresh {
                    apiClient.uploadTrainerPhoto(it, bytes, "image/jpeg", "photo.jpg")
                }
                runOnUiThread {
                    pendingPhotoUri = null
                    applyProfile(profile)
                    uiState = uiState.copy(
                        localPhotoUri = null,
                        statusMessage = when {
                            profile.publicationStatus == "moderation" || profile.needsModeration ->
                                "Фото загружено. Профиль на модерации."
                            else -> "Фото обновлено"
                        },
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        saving = false,
                        localPhotoUri = null,
                        statusMessage = null,
                        errorMessage = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    /**
     * Уменьшает фото до [MAX_PHOTO_SIDE] px по большей стороне, поворачивает
     * по EXIF и кодирует в JPEG — иначе загрузка падает на больших оригиналах.
     */
    private fun preparePhotoForUpload(uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IllegalStateException("Не удалось прочитать фото")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Файл не похож на изображение")
        }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_PHOTO_SIDE || bounds.outHeight / (sample * 2) >= MAX_PHOTO_SIDE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("Не удалось обработать фото")

        if (bitmap.width > MAX_PHOTO_SIDE || bitmap.height > MAX_PHOTO_SIDE) {
            val scale = MAX_PHOTO_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }

        val rotationDegrees = contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
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

    companion object {
        const val EXTRA_REQUIRED = "extra_required_profile"
        private const val MAX_PHOTO_SIDE = 1600
    }
}
