import Foundation

// MARK: - Parity с `PhoneRuMask.kt`

/// До 10 цифр после кода страны (без ведущей 7/8).
func normalizeRussianNationalDigits(_ input: String) -> String {
    var d = String(input.filter { $0.isNumber })
    if d.first == Character("8") {
        d.removeFirst()
    } else if d.first == Character("7") {
        d.removeFirst()
    }
    return String(d.prefix(10))
}

/// Вид номера для поля: `+7 (XXX) XXX-XX-XX` (в состоянии — только 10 национальных цифр).
func formatRussianPhoneMask(_ national10: String) -> String {
    let d = String(normalizeRussianNationalDigits(national10).prefix(10))
    if d.isEmpty { return "+7 (" }

    var sb = "+7 ("
    let take3 = String(d.prefix(3))
    sb += take3
    if d.count < 3 { return sb }

    sb += ") "
    let rest = String(d.dropFirst(3))
    sb += String(rest.prefix(min(3, rest.count)))

    if d.count <= 6 { return sb }
    sb += "-"
    let mid = String(d.dropFirst(6))
    sb += String(mid.prefix(min(2, mid.count)))

    if d.count <= 8 { return sb }
    sb += "-"
    sb += String(d.dropFirst(8))
    return sb
}

func phoneForApi(_ national10: String) -> String {
    let n = normalizeRussianNationalDigits(national10)
    return n.count == 10 ? "+7\(n)" : ""
}
