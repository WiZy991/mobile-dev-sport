import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins

enum QRCodeGenerator {
    /// Тот же формат, что `QrCodeViewModel.generateQrData` на Android.
    static func entryPayload(userId: String, timestampMillis: Int64) -> String {
        "FITNESSCLUB:ENTRY:\(userId):\(timestampMillis)"
    }

    static func image(from string: String, dimension: CGFloat = 240) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scale = dimension / output.extent.width
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let cg = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
