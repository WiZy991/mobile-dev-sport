import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins

enum QRCodeGenerator {
    private static let base62Alphabet = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
    private static let ciContext = CIContext(options: nil)
    private static let wiegandSlotMs: Int64 = 15_000
    private static let wiegandSlotMod = 100
    private static let wiegandUserMod = 10_000
    /// Клубы со считывателями PERCo в режиме Wiegand (только цифры).
    private static let wiegandClubIds: Set<String> = ["11"]

    /// Формат QR: ASCII для обычных залов, 7 цифр для Wiegand-26 (PERCo).
    static func entryPayload(userId: String, clubId: String?, timestampMillis: Int64) -> String {
        if usesWiegandNumeric(clubId: clubId) {
            return wiegandEntryPayload(userId: userId, timestampMillis: timestampMillis)
        }
        return asciiEntryPayload(userId: userId, timestampMillis: timestampMillis)
    }

    static func usesWiegandNumeric(clubId: String?) -> Bool {
        guard let raw = clubId?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return false
        }
        return wiegandClubIds.contains(raw)
    }

    /// Тот же формат, что `QrCodeViewModel.generateQrData` на Android / CRM `FitnessClubEntryQrTimestamp`.
    static func asciiEntryPayload(userId: String, timestampMillis: Int64) -> String {
        let uid = normalizedUserId(userId)
        let t = encodeTimestampBase62(ms: max(0, timestampMillis))
        return "FITNESSCLUB:ENTRY:\(uid):\(t)"
    }

    /// 7 цифр: UUUUTTC — всегда ≤ 2^24, см. `WiegandEntryQrCodec` в CRM.
    static func wiegandEntryPayload(userId: String, timestampMillis: Int64) -> String {
        let uid = Int(normalizedUserId(userId)) ?? 0
        let userPart = uid % wiegandUserMod
        let slot = Int(max(0, timestampMillis) / wiegandSlotMs) % wiegandSlotMod
        let body = String(format: "%04d%02d", userPart, slot)
        let check = luhnCheckDigit(bodyDigits: body)
        return body + String(check)
    }

    static func luhnCheckDigit(bodyDigits: String) -> Int {
        guard !bodyDigits.isEmpty, bodyDigits.allSatisfy(\.isNumber) else { return 0 }
        var sum = 0
        let rev = Array(bodyDigits.reversed())
        for (i, ch) in rev.enumerated() {
            var n = Int(String(ch)) ?? 0
            if i % 2 == 0 {
                n *= 2
                if n > 9 { n -= 9 }
            }
            sum += n
        }
        return (10 - sum % 10) % 10
    }

    private static func normalizedUserId(_ userId: String) -> String {
        if userId.lowercased().hasPrefix("user-") {
            return String(userId.dropFirst(5))
        }
        return userId
    }

    /// Тот же формат, что `QrCodeViewModel.generateQrData` на Android / CRM `FitnessClubEntryQrTimestamp`.
    static func entryPayload(userId: String, timestampMillis: Int64) -> String {
        asciiEntryPayload(userId: userId, timestampMillis: timestampMillis)
    }

    /// 7 символов base62 — компактная метка времени под лимит PERCo (~32 символа на строку).
    static func encodeTimestampBase62(ms: Int64) -> String {
        var v = UInt64(bitPattern: max(0, ms))
        var chars = [Character]()
        chars.reserveCapacity(7)
        for _ in 0..<7 {
            let idx = Int(v % 62)
            chars.insert(base62Alphabet[idx], at: 0)
            v /= 62
        }
        return String(chars)
    }

    static func image(from string: String, dimension: CGFloat = 240) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scale = dimension / output.extent.width
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let cg = ciContext.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }

    /// Генерация вне main — чтобы UI не подвисал на ротации QR.
    static func imageAsync(from string: String, dimension: CGFloat = 240) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            image(from: string, dimension: dimension)
        }.value
    }
}
