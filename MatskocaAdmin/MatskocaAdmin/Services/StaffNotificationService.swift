import Foundation
import UserNotifications

@MainActor
final class StaffNotificationService: NSObject, UNUserNotificationCenterDelegate {
    static let shared = StaffNotificationService()

    var onOpenSupport: (() -> Void)?

    private var notificationId = 1000

    private override init() {
        super.init()
    }

    func requestPermission() {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    func showSupportNotification(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "staff_support_\(notificationId)",
            content: content,
            trigger: nil
        )
        notificationId += 1
        UNUserNotificationCenter.current().add(request)
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        Task { @MainActor in
            if response.notification.request.identifier.hasPrefix("staff_support_") {
                onOpenSupport?()
            }
            completionHandler()
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
