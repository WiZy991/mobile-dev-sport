package com.fitnessclub.app.ui.screens.qrcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.SubscriptionStatus
import com.fitnessclub.app.data.repository.AccessRepository
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrCodeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val memberId: String = "",
    val qrCodeData: String? = null,
    val isInsideGym: Boolean = false,
    val secondsRemaining: Int = 15,
    /** Почему QR для входа не показывается (лимит/нет абонемента). */
    val entryBlockedMessage: String? = null,
)

@HiltViewModel
class QrCodeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accessRepository: AccessRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrCodeUiState())
    val uiState: StateFlow<QrCodeUiState> = _uiState.asStateFlow()

    private var rotationJob: Job? = null
    private var statusJob: Job? = null
    private var sheetVisible: Boolean = false

    @Volatile
    private var cachedEntryBlock: String? = null
    @Volatile
    private var entryBlockCachedAtMs: Long = 0L

    /** Запускать только когда открыт QR-sheet — иначе фоновый опрос грузит сеть. */
    fun onSheetOpened() {
        if (sheetVisible) return
        sheetVisible = true
        viewModelScope.launch {
            coroutineScope {
                val accessDeferred = async { accessRepository.refreshAccessStatus(force = true) }
                val blockDeferred = async { entryBlockReason(force = true) }
                accessDeferred.await()
                blockDeferred.await()
            }
            _uiState.update { it.copy(isInsideGym = accessRepository.accessStatus.value.isInside) }
            startQrRotation()
            startAccessStatusPolling()
        }
    }

    fun onSheetClosed() {
        sheetVisible = false
        rotationJob?.cancel()
        statusJob?.cancel()
        rotationJob = null
        statusJob = null
    }

    fun refreshQrCode() {
        if (!sheetVisible) return
        viewModelScope.launch {
            accessRepository.refreshAccessStatus(force = true)
            rotationJob?.cancel()
            startQrRotation()
        }
    }

    private fun startAccessStatusPolling() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            while (isActive && sheetVisible) {
                delay(8_000)
                if (!sheetVisible) return@launch
                accessRepository.refreshAccessStatus(force = true)
                val isInside = accessRepository.accessStatus.value.isInside
                val wasInside = _uiState.value.isInsideGym
                if (isInside != wasInside) {
                    _uiState.update { it.copy(isInsideGym = isInside) }
                    rotationJob?.cancel()
                    startQrRotation()
                } else {
                    _uiState.update { it.copy(isInsideGym = isInside) }
                }
            }
        }
    }

    private suspend fun entryBlockReason(force: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!force && entryBlockCachedAtMs > 0L && now - entryBlockCachedAtMs < ENTRY_BLOCK_TTL_MS) {
            return cachedEntryBlock
        }
        val reason = when (val result = subscriptionRepository.getMySubscriptions().first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> {
                val active = result.data.filter { it.status == SubscriptionStatus.ACTIVE && !it.isFrozen }
                when {
                    active.isEmpty() -> "Нет активного абонемента. Оформите абонемент, чтобы войти в зал."
                    active.none { sub ->
                        val left = sub.visitsLeft
                        left == null || left > 0
                    } -> "Посещения по абонементу закончились. Купите новый или продлите текущий."
                    else -> null
                }
            }
            else -> null
        }
        cachedEntryBlock = reason
        entryBlockCachedAtMs = System.currentTimeMillis()
        return reason
    }

    private fun startQrRotation() {
        rotationJob = viewModelScope.launch {
            val user = authRepository.getCurrentUser().first()
            if (user == null) {
                _uiState.update {
                    it.copy(isLoading = false, qrCodeData = null, secondsRemaining = 0, entryBlockedMessage = null)
                }
                return@launch
            }

            val isInside = accessRepository.accessStatus.value.isInside
            _uiState.update { it.copy(isInsideGym = isInside) }

            if (!isInside) {
                val blocked = entryBlockReason()
                if (blocked != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userName = user.name,
                            memberId = user.id.takeLast(8).uppercase(),
                            qrCodeData = null,
                            secondsRemaining = 0,
                            entryBlockedMessage = blocked,
                        )
                    }
                    return@launch
                }
            }

            while (isActive && sheetVisible) {
                val insideNow = accessRepository.accessStatus.value.isInside
                if (!insideNow) {
                    val again = entryBlockReason()
                    if (again != null) {
                        _uiState.update {
                            it.copy(
                                isInsideGym = false,
                                qrCodeData = null,
                                secondsRemaining = 0,
                                entryBlockedMessage = again,
                            )
                        }
                        return@launch
                    }
                }

                val ts = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isInsideGym = insideNow,
                        userName = user.name,
                        memberId = user.id.takeLast(8).uppercase(),
                        qrCodeData = generateQrData(user.id, ts),
                        secondsRemaining = 15,
                        entryBlockedMessage = null,
                    )
                }
                repeat(15) {
                    delay(1_000)
                    if (!isActive || !sheetVisible) return@launch
                    _uiState.update { state -> state.copy(secondsRemaining = maxOf(0, state.secondsRemaining - 1)) }
                }
            }
        }
    }

    override fun onCleared() {
        onSheetClosed()
        super.onCleared()
    }

    private fun generateQrData(userId: String, timestamp: Long): String {
        val uid = if (userId.lowercase().startsWith("user-")) {
            userId.substring(5)
        } else {
            userId
        }
        val t = encodeTimestampBase62(timestamp)
        return "FITNESSCLUB:ENTRY:$uid:$t"
    }

    private fun encodeTimestampBase62(ms: Long): String {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        var v = ms.coerceAtLeast(0L)
        val sb = StringBuilder(7)
        repeat(7) {
            val idx = (v % 62L).toInt()
            sb.insert(0, alphabet[idx])
            v /= 62L
        }
        return sb.toString()
    }

    companion object {
        private const val ENTRY_BLOCK_TTL_MS = 60_000L
    }
}
