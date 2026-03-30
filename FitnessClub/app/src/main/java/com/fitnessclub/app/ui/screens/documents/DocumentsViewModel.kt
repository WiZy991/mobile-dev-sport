package com.fitnessclub.app.ui.screens.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiDocument
import com.fitnessclub.app.data.api.FitnessApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class DocumentsUiState(
    val isLoading: Boolean = true,
    val documents: List<ApiDocument> = emptyList(),
    val isUploading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val api: FitnessApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val res = api.getDocuments()
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(isLoading = false, documents = res.body() ?: emptyList())
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Ошибка загрузки")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка сети")
                }
            }
        }
    }

    fun uploadFile(file: File, displayName: String?, category: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            try {
                val requestFile = file.asRequestBody(
                    (file.extension.let { ext ->
                        when (ext.lowercase()) {
                            "pdf" -> "application/pdf"
                            "jpg", "jpeg" -> "image/jpeg"
                            "png" -> "image/png"
                            else -> "application/octet-stream"
                        }
                    }).toMediaTypeOrNull()
                )
                val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val namePart = (displayName ?: file.name).toRequestBody("text/plain".toMediaTypeOrNull())
                val categoryPart = category?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

                val res = api.uploadDocument(filePart, namePart, categoryPart)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(isUploading = false) }
                    load()
                    onSuccess()
                } else {
                    val err = res.errorBody()?.string() ?: "Ошибка загрузки"
                    _uiState.update { it.copy(isUploading = false, error = err) }
                    onError(err)
                }
            } catch (e: Exception) {
                val err = e.message ?: "Ошибка"
                _uiState.update { it.copy(isUploading = false, error = err) }
                onError(err)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun downloadDocument(docId: String, docName: String, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.downloadDocument(docId)
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body != null) {
                        val ext = when {
                            docName.contains("pdf", true) -> "pdf"
                            docName.contains("jpg", true) -> "jpg"
                            docName.contains("png", true) -> "png"
                            else -> "pdf"
                        }
                        val file = File.createTempFile(docName.take(20).filter { it.isLetterOrDigit() }, ".$ext")
                        body.byteStream().use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }
                        onResult(file)
                    } else {
                        onResult(null)
                    }
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
}
