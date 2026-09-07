package com.fitnessclub.app.ui.screens.auth

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** До 10 цифр после кода страны (без ведущей 7/8). */
fun normalizeRussianNationalDigits(input: String): String {
    var d = input.filter { it.isDigit() }
    if (d.startsWith("8")) {
        d = d.drop(1)
    } else if (d.startsWith("7")) {
        d = d.drop(1)
    }
    return d.take(10)
}

/**
 * Разбор текста из поля с маской `+7 (XXX) XXX-XX-XX`.
 * Backspace по скобке/пробелу снимает последнюю цифру, а не «застревает» в префиксе.
 */
fun nationalDigitsFromPhoneField(raw: String, previousNational: String): String {
    val prevFormatted = formatRussianPhoneMask(previousNational)
    val normalized = normalizeRussianNationalDigits(raw)
    if (raw.length < prevFormatted.length &&
        normalized.length >= previousNational.length &&
        previousNational.isNotEmpty()
    ) {
        return previousNational.dropLast(1)
    }
    return normalized
}

/** Значение поля: маска на экране, курсор всегда после последней цифры. */
fun russianPhoneFieldValue(nationalDigits: String): TextFieldValue {
    val formatted = formatRussianPhoneMask(nationalDigits)
    return TextFieldValue(text = formatted, selection = TextRange(formatted.length))
}

/** Полный вид номера для поля ввода: `+7 (XXX) XXX-XX-XX` (в стейте — только 10 национальных цифр). */
fun formatRussianPhoneMask(national10: String): String {
    val d = national10.take(10)
    if (d.isEmpty()) return "+7 ("
    val sb = StringBuilder("+7 (")
    sb.append(d.take(3))
    if (d.length < 3) return sb.toString()
    sb.append(") ")
    sb.append(d.substring(3, minOf(6, d.length)))
    if (d.length <= 6) return sb.toString()
    sb.append("-")
    sb.append(d.substring(6, minOf(8, d.length)))
    if (d.length <= 8) return sb.toString()
    sb.append("-")
    sb.append(d.substring(8, d.length))
    return sb.toString()
}

fun phoneForApi(national10: String): String =
    if (national10.length == 10) "+7$national10" else ""
