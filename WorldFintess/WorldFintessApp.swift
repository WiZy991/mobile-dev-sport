//
//  WorldFintessApp.swift
//  WorldFintess
//
//  Created by Степан on 10.04.2026.
//

import SwiftUI

#if canImport(SIDSDK)
import SIDSDK
#endif

@main
struct WorldFintessApp: App {
    @StateObject private var appState = WorldFitnessAppState()

    init() {
        #if canImport(SIDSDK)
        SID.initializer.initialize(stand: .prom) { _ in }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
        }
    }
}
