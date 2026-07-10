import SwiftUI

struct StaffToolbarStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .toolbarBackground(StaffColors.primary, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
    }
}

extension View {
    func staffToolbarStyle() -> some View {
        modifier(StaffToolbarStyle())
    }
}
