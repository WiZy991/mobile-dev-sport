package com.fitnessclub.app.ui.util

/**
 * Адрес клуба без города: «г. Владивосток, ТЦ …» → «ТЦ …».
 */
fun addressWithoutCity(address: String): String {
    val trimmed = address.trim()
    if (trimmed.isEmpty()) return trimmed

    val stripped = trimmed
        .replace(Regex("""^(г\.?\s*|город\s+)[^,]+,\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^(село|посёлок|поселок|пгт|деревня)\s+[^,]+,\s*""", RegexOption.IGNORE_CASE), "")
        .trim()

    return stripped.ifBlank { trimmed }
}
