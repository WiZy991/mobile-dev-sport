import SwiftUI

// MARK: - Стиль как Android `TopAppBar`: primary фон, светлый заголовок (через `.toolbarColorScheme(.dark)`)

extension View {
    func fcPrimaryNavigation(title: String) -> some View {
        navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Theme.primary, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
    }
}
