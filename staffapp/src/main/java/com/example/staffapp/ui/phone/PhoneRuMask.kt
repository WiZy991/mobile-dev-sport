package com.example.staffapp.ui.phone

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

/** API: `+7XXXXXXXXXX` или пустая строка, если номер неполный / пустой. */
fun phoneForApi(national10: String): String =
    if (national10.isEmpty()) "" else if (national10.length == 10) "+7$national10" else ""
