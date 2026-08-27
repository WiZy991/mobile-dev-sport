import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

/// Генерация staff QR: ascii `FITNESSCLUB:STAFF` или Wiegand-7 по `entry_qr_format` клуба.
enum StaffEntryQrCodec {
    private static let base62Alphabet = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
    private static let ciContext = CIContext(options: nil)
    private static let wiegandSlotMs: Int64 = 15_000
    private static let wiegandSlotMod = 100
    private static let wiegandUserMod = 10_000

    static func buildPayload(staffUserId: Int, entryQrFormat: String?, timestampMillis: Int64) -> String {
        if usesWiegandNumeric(entryQrFormat: entryQrFormat) {
            return wiegandPayload(staffUserId: staffUserId, timestampMillis: timestampMillis)
        }
        let t = encodeTimestampBase62(ms: max(0, timestampMillis))
        return "FITNESSCLUB:STAFF:\(staffUserId):\(t)"
    }

    static func usesWiegandNumeric(entryQrFormat: String?) -> Bool {
        guard let raw = entryQrFormat?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !raw.isEmpty else {
            return false
        }
        return raw == "wiegand"
    }

    static func wiegandPayload(staffUserId: Int, timestampMillis: Int64) -> String {
        let userPart = max(0, staffUserId) % wiegandUserMod
        let slot = Int(max(0, timestampMillis) / wiegandSlotMs) % wiegandSlotMod
        let body = String(format: "%04d%02d", userPart, slot)
        return body + String(luhnCheckDigit(bodyDigits: body))
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

    static func imageAsync(from string: String, dimension: CGFloat = 240) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            image(from: string, dimension: dimension)
        }.value
    }
}
