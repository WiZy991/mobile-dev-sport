import SwiftUI
import PDFKit

/// Встроенные PDF правовых документов (аналог `LegalPdfAsset.kt` на Android, `Legal/*.pdf`).
enum LegalPdfAsset: String, Identifiable, Hashable, CaseIterable {
    case dobrozalOffer
    case privacyPolicy
    case dobrozalPrivacy
    case consentUser
    case userAgreement
    case wcfClubOffer

    var id: String { rawValue }

    /// Имя файла в бандле (без расширения).
    var resourceName: String {
        switch self {
        case .dobrozalOffer: return "dobrozal_offer"
        case .privacyPolicy: return "privacy_policy"
        case .dobrozalPrivacy: return "dobrozal_privacy"
        case .consentUser: return "consent_user"
        case .userAgreement: return "user_agreement"
        case .wcfClubOffer: return "wcf_club_offer"
        }
    }

    var title: String {
        switch self {
        case .dobrozalOffer: return "Публичная оферта"
        case .privacyPolicy: return "Политика конфиденциальности"
        case .dobrozalPrivacy: return "Политика обработки и защиты персональных данных Клуба"
        case .consentUser: return "Согласие на обработку персональных данных"
        case .userAgreement: return "Пользовательское соглашение"
        case .wcfClubOffer: return "Оферта для клубов"
        }
    }

    var fileURL: URL? {
        Bundle.main.url(forResource: resourceName, withExtension: "pdf")
            ?? Bundle.main.url(forResource: resourceName, withExtension: "pdf", subdirectory: "Legal")
    }

    /// Для ссылок `legalpdf://<rawValue>` (URL приводит host к нижнему регистру).
    static func fromLink(_ value: String?) -> LegalPdfAsset? {
        guard let value else { return nil }
        return allCases.first { $0.rawValue.lowercased() == value.lowercased() }
    }
}

// MARK: - Просмотр PDF (`LegalPdfScreen.kt`)

struct LegalPdfView: View {
    let asset: LegalPdfAsset

    var body: some View {
        Group {
            if let url = asset.fileURL {
                PDFKitRepresentedView(url: url)
                    .ignoresSafeArea(edges: .bottom)
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "doc.questionmark")
                        .font(.system(size: 40))
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text("Документ недоступен")
                        .font(FCTypography.bodyLarge())
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Theme.background)
            }
        }
        .fcPrimaryNavigation(title: asset.title)
    }
}

/// PDF в модальном листе (для входа/регистрации/покупки, где нет `NavigationStack`).
struct LegalPdfSheet: View {
    let asset: LegalPdfAsset
    var onClose: () -> Void

    var body: some View {
        NavigationStack {
            Group {
                if let url = asset.fileURL {
                    PDFKitRepresentedView(url: url)
                } else {
                    Text("Документ недоступен")
                        .font(FCTypography.bodyLarge())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Theme.background)
                }
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(asset.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть", action: onClose)
                }
            }
        }
    }
}

struct PDFKitRepresentedView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.document = PDFDocument(url: url)
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.backgroundColor = .systemBackground
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        if uiView.document?.documentURL != url {
            uiView.document = PDFDocument(url: url)
        }
    }
}
