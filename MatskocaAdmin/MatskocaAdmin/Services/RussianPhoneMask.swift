import Foundation

enum RussianPhoneMask {
    /// До 10 цифр после кода страны (без ведущей 7/8).
    static func normalizeNationalDigits(_ input: String) -> String {
        var d = input.filter(\.isNumber)
        if d.hasPrefix("8") || d.hasPrefix("7") {
            d = String(d.dropFirst())
        }
        return String(d.prefix(10))
    }

    static func formatMask(_ national10: String) -> String {
        let d = String(national10.prefix(10))
        if d.isEmpty { return "+7 (" }
        var sb = "+7 ("
        sb += String(d.prefix(3))
        if d.count < 3 { return sb }
        sb += ") "
        let midEnd = min(6, d.count)
        sb += String(d[d.index(d.startIndex, offsetBy: 3)..<d.index(d.startIndex, offsetBy: midEnd)])
        if d.count <= 6 { return sb }
        sb += "-"
        let partEnd = min(8, d.count)
        sb += String(d[d.index(d.startIndex, offsetBy: 6)..<d.index(d.startIndex, offsetBy: partEnd)])
        if d.count <= 8 { return sb }
        sb += "-"
        sb += String(d[d.index(d.startIndex, offsetBy: 8)...])
        return sb
    }

    static func phoneForApi(_ national10: String) -> String {
        if national10.isEmpty { return "" }
        return national10.count == 10 ? "+7\(national10)" : ""
    }
}
