import Foundation
import UIKit
import UserNotifications

/// Регистрация APNs-токена в CRM (`POST /api/v1/user/push-token`, platform=ios).
@MainActor
final class PushNotificationService: NSObject {
    static let shared = PushNotificationService()

    private weak var api: FitnessAPI?
    private var pendingDeviceToken: String?
    private var lastUploadedToken: String?
    private var isRequestingPermission = false

    func attach(api: FitnessAPI) {
        self.api = api
    }

    func configureOnLaunch() {
        UNUserNotificationCenter.current().delegate = self
    }

    /// Запросить разрешение и зарегистрировать устройство (после входа или включения push в настройках).
    func enablePushAfterLogin() {
        Task { await requestPermissionAndRegister() }
    }

    func syncWithServerPreference(pushEnabled: Bool) async {
        if pushEnabled {
            await requestPermissionAndRegister()
        } else {
            await unregisterOnServer()
            lastUploadedToken = nil
        }
    }

    func handleDeviceToken(_ deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        pendingDeviceToken = token
        Task { await uploadPendingTokenIfPossible() }
    }

    func handleRegistrationFailure(_ error: Error) {
        #if DEBUG
        print("APNs registration failed: \(error.localizedDescription)")
        #endif
    }

    private func requestPermissionAndRegister() async {
        guard !isRequestingPermission else { return }
        isRequestingPermission = true
        defer { isRequestingPermission = false }

        do {
            let granted = try await UNUserNotificationCenter.current().requestAuthorization(
                options: [.alert, .badge, .sound]
            )
            guard granted else { return }
            await MainActor.run {
                UIApplication.shared.registerForRemoteNotifications()
            }
            await uploadPendingTokenIfPossible()
        } catch {
            #if DEBUG
            print("Push permission error: \(error.localizedDescription)")
            #endif
        }
    }

    private func uploadPendingTokenIfPossible() async {
        guard let api, let token = pendingDeviceToken, !token.isEmpty else { return }
        guard token != lastUploadedToken else { return }
        do {
            try await api.registerPushToken(PushTokenRequest(token: token, platform: "ios"))
            lastUploadedToken = token
        } catch {
            #if DEBUG
            print("Push token upload failed: \(error.localizedDescription)")
            #endif
        }
    }

    private func unregisterOnServer() async {
        guard let api else { return }
        try? await api.unregisterPushToken()
    }
}

extension PushNotificationService: UNUserNotificationCenterDelegate {
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }
}

final class AppPushDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        Task { @MainActor in
            PushNotificationService.shared.configureOnLaunch()
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in
            PushNotificationService.shared.handleDeviceToken(deviceToken)
        }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Task { @MainActor in
            PushNotificationService.shared.handleRegistrationFailure(error)
        }
    }
}
