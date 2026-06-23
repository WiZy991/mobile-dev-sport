package com.fitnessclub.app.ui.screens.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.SupportTicketItem
import com.fitnessclub.app.data.api.SupportTicketRequest
import com.fitnessclub.app.data.local.TokenManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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

object SupportTicketStatuses {
    fun label(api: String): String = when (api) {
        "new" -> "Новое"
        "in_progress" -> "В работе"
        "done" -> "Закрыто"
        else -> api
    }
}

data class HelpUiState(
    val contactEmail: String = "",
    /** Есть сохранённый профиль — сервер примет обращение без email в теле. */
    val hasUserProfile: Boolean = false,
    val tickets: List<SupportTicketItem> = emptyList(),
    val isLoadingTickets: Boolean = false,
    val ticketsError: String? = null,
    val subject: String = "",
    val message: String = "",
    val categoryApi: String = SupportCategories.defaultApi,
    val categoryMenuExpanded: Boolean = false,
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class HelpViewModel @Inject constructor(
    private val api: FitnessApi,
    private val tokenManager: TokenManager,
    private val gson: Gson,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HelpUiState())
    val uiState: StateFlow<HelpUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = tokenManager.getUser().first()
            _uiState.update {
                it.copy(
                    contactEmail = user?.email?.trim().orEmpty(),
                    hasUserProfile = user != null,
                )
            }
            if (user != null) {
                loadTickets()
            }
        }
    }

    fun loadTickets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTickets = true, ticketsError = null) }
            try {
                val res = api.getSupportTickets()
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isLoadingTickets = false,
                            tickets = res.body()?.tickets.orEmpty(),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingTickets = false,
                            ticketsError = "Не удалось загрузить историю (${res.code()})",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingTickets = false,
                        ticketsError = e.message ?: "Ошибка сети",
                    )
                }
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
                    loadTickets()
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
            }
        }
    }
}
