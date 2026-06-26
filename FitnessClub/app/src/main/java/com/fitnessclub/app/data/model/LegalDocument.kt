package com.fitnessclub.app.data.model

import com.google.gson.annotations.SerializedName

data class LegalDocumentResponse(
    @SerializedName("title")
    val title: String,

    /** Текст документа (договор, политика и т.д.). */
    @SerializedName("body")
    val body: String? = null,

    /** Структурированные поля (реквизиты). */
    @SerializedName("fields")
    val fields: List<LegalDocumentField>? = null,
)

data class LegalDocumentField(
    @SerializedName("label")
    val label: String,

    @SerializedName("value")
    val value: String,
)
