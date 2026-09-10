import Combine
import Foundation

/// События возврата из Альфа-Банка: `worldfitness://payment/callback?payment_id=N`.
enum PaymentDeepLinkBus {
    static let notification = Notification.Name("wf.paymentDeepLink")

    static var publisher: NotificationCenter.Publisher {
        NotificationCenter.default.publisher(for: notification)
    }

    static func publish(paymentId: Int) {
        NotificationCenter.default.post(name: notification, object: paymentId)
    }

    static func parsePaymentId(from url: URL) -> Int? {
        guard url.scheme?.lowercased() == "worldfitness",
              url.host?.lowercased() == "payment"
        else { return nil }

        let path = url.path.isEmpty ? "/" : url.path
        guard path == "/callback" || path.hasSuffix("/callback") else { return nil }

        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let idStr = components.queryItems?.first(where: { $0.name == "payment_id" })?.value,
              let id = Int(idStr),
              id > 0
        else { return nil }

        return id
    }
}
