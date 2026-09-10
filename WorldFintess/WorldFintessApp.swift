//
//  WorldFintessApp.swift
//  WorldFintess
//
//  Created by Степан on 10.04.2026.
//

import SwiftUI

/// Вход через Сбер ID — PKCE и `ASWebAuthenticationSession` в `SberIDAuthService` (без нативного SID XCFramework:
/// он собран под другую версию Swift и даёт ошибку совместимости компилятора на текущем Xcode).

@main
struct WorldFintessApp: App {
    @UIApplicationDelegateAdaptor(AppPushDelegate.self) private var pushDelegate
    @StateObject private var appState = WorldFitnessAppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .onAppear {
                    PushNotificationService.shared.attach(api: appState.api)
                    if appState.isLoggedIn {
                        PushNotificationService.shared.enablePushAfterLogin()
                    }
                }
                .onChange(of: appState.isLoggedIn) { _, loggedIn in
                    if loggedIn {
                        PushNotificationService.shared.enablePushAfterLogin()
                    }
                }
                .onOpenURL { url in
                    if let paymentId = PaymentDeepLinkBus.parsePaymentId(from: url) {
                        PaymentDeepLinkBus.publish(paymentId: paymentId)
                    }
                }
        }
    }
}
