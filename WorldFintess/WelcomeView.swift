import SwiftUI
import UIKit

/// Экран приветствия (`WelcomeScreen.kt`) — до первого входа.
struct WelcomeView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var onContinue: () -> Void

    @State private var bannerUrl: String?
    @State private var legalText: String?
    @State private var supportEmail: String?
    @State private var supportPhone: String?
    @State private var brandName = AppConfiguration.appDisplayName
    @State private var legalPdfSheet: LegalPdfAsset?

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Theme.primary, Theme.primaryVariant, Color(red: 0.70, green: 0.23, blue: 0.07)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            if let bannerUrl, let url = URL(string: bannerUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                            .ignoresSafeArea()
                    default:
                        EmptyView()
                    }
                }
            }

            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0),
                    .init(color: .clear, location: 0.45),
                    .init(color: Color(red: 0.10, green: 0.04, blue: 0).opacity(0.80), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer(minLength: 24)

                BrandHeader(
                    brandName: brandName,
                    subtitle: nil,
                    textColor: .white,
                    logoSize: 48
                )

                Spacer(minLength: 24)

                Button {
                    onContinue()
                } label: {
                    Text("Продолжить")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.primary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                }
                .buttonStyle(.plain)

                welcomeLegalText
                    .padding(.top, 16)

                Button(action: openSupport) {
                    Text("Обратиться в поддержку")
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Color.white.opacity(0.92))
                }
                .buttonStyle(.plain)
                .padding(.top, 8)

                Text("Версия \(AppConfiguration.appVersion)")
                    .font(FCTypography.labelSmall())
                    .foregroundStyle(Color.white.opacity(0.7))
                    .padding(.top, 4)
                    .padding(.bottom, 8)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 20)
        }
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $legalPdfSheet) { asset in
            LegalPdfSheet(asset: asset) { legalPdfSheet = nil }
        }
        .task {
            guard let info = try? await app.api.getClubInfo() else { return }
            brandName = info.resolvedBrandName
            bannerUrl = info.welcomeBannerUrl?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            legalText = info.welcomeLegalText?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            supportEmail = info.email.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            supportPhone = info.phone.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        }
    }

    private var welcomeLegalText: some View {
        let privacy = "legalpdf://\(LegalPdfAsset.privacyPolicy.rawValue)"
        let ua = "legalpdf://\(LegalPdfAsset.userAgreement.rawValue)"
        let custom = legalText?.trimmingCharacters(in: .whitespacesAndNewlines)
        // Как на Android Welcome: при custom — две ссылки; в дефолте тоже обе
        // (пользовательское соглашение + политика), а не одна «политиками и документами».
        let md: String = {
            if let custom, !custom.isEmpty {
                return "\(custom) [Пользовательское соглашение](\(ua)) · [Политика конфиденциальности](\(privacy))"
            }
            return "Нажимая «Продолжить», вы соглашаетесь с [пользовательским соглашением](\(ua)) и [политикой конфиденциальности](\(privacy))."
        }()
        return Text(fcHighlightedLegalLinks(markdown: md, linkColor: .white))
            .font(FCTypography.bodySmall())
            .multilineTextAlignment(.center)
            .foregroundStyle(Color.white.opacity(0.9))
            .tint(.white)
            .environment(\.openURL, OpenURLAction { url in
                if url.scheme == "legalpdf", let asset = LegalPdfAsset.fromLink(url.host) {
                    legalPdfSheet = asset
                } else {
                    UIApplication.shared.open(url)
                }
                return .handled
            })
            .frame(maxWidth: .infinity)
    }

    private func openSupport() {
        if let email = supportEmail, let u = URL(string: "mailto:\(email)") {
            UIApplication.shared.open(u)
        } else if let phone = supportPhone {
            dialPhoneRaw(phone)
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
