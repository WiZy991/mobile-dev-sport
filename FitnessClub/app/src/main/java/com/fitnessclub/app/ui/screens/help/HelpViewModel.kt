package com.fitnessclub.app.ui.screens.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.SupportTicketRequest
<<<<<<< HEAD
import com.fitnessclub.app.data.local.TokenManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
=======
>>>>>>> a188090 (update)
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
<<<<<<< HEAD
import kotlinx.coroutines.flow.first
=======
>>>>>>> a188090 (update)
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

<<<<<<< HEAD
private data class ApiErrorJson(
    @SerializedName("error") val error: String? = null,
)

object SupportCategories {
    const val defaultApi = "other"
    val options: List<Pair<String, String>> = listOf(
        "question" to "Вопрос",
        "complaint" to "Жалоба",
        "suggestion" to "Пожелание",
        "technical" to "Техническая проблема",
        "billing" to "Оплата / абонемент",
        "other" to "Другое",
    )

    fun label(api: String): String =
        options.find { it.first == api }?.second ?: "Другое"
}

data class HelpUiState(
    val contactEmail: String = "",
    /** Есть сохранённый профиль — сервер примет обращение без email в теле. */
    val hasUserProfile: Boolean = false,
    val subject: String = "",
    val message: String = "",
    val categoryApi: String = SupportCategories.defaultApi,
    val categoryMenuExpanded: Boolean = false,
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
=======
data class HelpUiState(
    val isSending: Boolean = false,
    val sendError: String? = null,
    val sendSuccess: Boolean = false
>>>>>>> a188090 (update)
)

@HiltViewModel
class HelpViewModel @Inject constructor(
<<<<<<< HEAD
    private val api: FitnessApi,
    private val tokenManager: TokenManager,
    private val gson: Gson,
=======
    private val api: FitnessApi
>>>>>>> a188090 (update)
) : ViewModel() {

    private val _uiState = MutableStateFlow(HelpUiState())
    val uiState: StateFlow<HelpUiState> = _uiState.asStateFlow()

<<<<<<< HEAD
    init {
        viewModelScope.launch {
            val user = tokenManager.getUser().first()
            _uiState.update {
                it.copy(
                    contactEmail = user?.email?.trim().orEmpty(),
                    hasUserProfile = user != null,
                )
            }
        }
    }

    fun setContactEmail(value: String) {
        _uiState.update { it.copy(contactEmail = value, errorMessage = null, successMessage = null) }
    }

    fun setSubject(value: String) {
        _uiState.update { it.copy(subject = value, errorMessage = null, successMessage = null) }
    }

    fun setMessage(value: String) {
        _uiState.update { it.copy(message = value, errorMessage = null, successMessage = null) }
    }

    fun setCategory(api: String) {
        _uiState.update {
            it.copy(
                categoryApi = api,
                categoryMenuExpanded = false,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun setCategoryMenuExpanded(expanded: Boolean) {
        _uiState.update { it.copy(categoryMenuExpanded = expanded) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun submit() {
        val st = _uiState.value
        val subject = st.subject.trim()
        val message = st.message.trim()
        val email = st.contactEmail.trim()

        if (subject.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Укажите тему обращения") }
            return
        }
        if (message.length < 5) {
            _uiState.update { it.copy(errorMessage = "Опишите проблему не менее чем в 5 символов") }
            return
        }
        if (!st.hasUserProfile) {
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _uiState.update { it.copy(errorMessage = "Укажите email для ответа поддержки") }
                return
            }
        } else if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Некорректный email") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
            try {
                val body = SupportTicketRequest(
                    subject = subject,
                    message = message,
                    category = st.categoryApi,
                    contactEmail = email.takeIf { it.isNotEmpty() },
                )
                val res = api.createSupportTicket(body)
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            subject = "",
                            message = "",
                            successMessage = "Обращение отправлено. Ответ придёт на указанный email.",
                        )
                    }
                } else {
                    val raw = res.errorBody()?.string().orEmpty()
                    val parsed = runCatching { gson.fromJson(raw, ApiErrorJson::class.java) }.getOrNull()
                    val msg = parsed?.error?.takeIf { it.isNotBlank() }
                        ?: raw.takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }
                        ?: "Не удалось отправить (${res.code()})"
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = msg) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = e.message ?: "Ошибка сети")
                }
=======
    fun consumeSuccess() {
        _uiState.update { it.copy(sendSuccess = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(sendError = null) }
    }

    fun submitSupportTicket(
        subject: String,
        message: String,
        category: String,
        contactEmail: String?,
        onDone: () -> Unit,
        onFail: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, sendError = null, sendSuccess = false) }
            try {
                val res = api.createSupportTicket(
                    SupportTicketRequest(
                        subject = subject.trim(),
                        message = message.trim(),
                        category = category,
                        contactEmail = contactEmail?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    _uiState.update { it.copy(isSending = false, sendSuccess = true) }
                    onDone()
                } else {
                    val err = res.errorBody()?.string()?.takeIf { it.isNotBlank() }
                        ?: "Не удалось отправить (${res.code()})"
                    _uiState.update { it.copy(isSending = false, sendError = err) }
                    onFail(err)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Ошибка сети"
                _uiState.update { it.copy(isSending = false, sendError = msg) }
                onFail(msg)
>>>>>>> a188090 (update)
            }
        }
    }
}
