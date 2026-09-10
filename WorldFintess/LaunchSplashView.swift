import SwiftUI

/// Загрузочный экран при старте (пока `sessionReady == false`), аналог Android bootstrap spinner + бренд.
struct LaunchSplashView: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Theme.primary, Theme.primaryVariant, Color(red: 0.70, green: 0.23, blue: 0.07)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer(minLength: 0)

                Image("BrandLogo")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 88, height: 88)
                    .clipShape(Circle())
                    .shadow(color: .black.opacity(0.2), radius: 12, y: 4)

                Text(AppConfiguration.appDisplayName)
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(.white)

                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(.white)
                    .scaleEffect(1.15)
                    .padding(.top, 8)

                Spacer(minLength: 0)
            }
            .padding(.horizontal, 32)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Загрузка \(AppConfiguration.appDisplayName)")
    }
}

#Preview {
    LaunchSplashView()
}
