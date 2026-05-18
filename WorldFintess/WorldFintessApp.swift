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
    @StateObject private var appState = WorldFitnessAppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
        }
    }
}
