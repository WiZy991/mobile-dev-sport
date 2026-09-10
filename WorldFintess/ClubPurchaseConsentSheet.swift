import SwiftUI

/// Согласие перед покупкой абонемента (`ClubPurchaseConsentDialog.kt`).
struct ClubPurchaseConsentSheet: View {
    let clubName: String
    var visitingRulesUrl: String? = nil
    var safetyRulesUrl: String? = nil
    var isLoading: Bool = false
    var onConfirm: () -> Void
    var onDismiss: () -> Void
    var onOpenURL: (URL) -> Void

    @State private var legalPdfSheet: LegalPdfAsset?
    @State private var safetyBriefed = false
    @State private var dataAccurate = false

    private var canConfirm: Bool {
        safetyBriefed && dataAccurate && !isLoading
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Подтвердите согласие перед покупкой")
                            .font(FCTypography.titleMedium())
                            .fontWeight(.bold)
                            .foregroundStyle(Theme.onBackground)

                        Text("Вы покупаете абонемент у клуба «\(clubName)».")
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onBackground)

                        Text("Нажимая «Согласен, приобрести абонемент», Вы подтверждаете, что ознакомились с тарифом и условиями приобретения абонемента, согласны с условиями нижеуказанных документов, а также прочитали Правила техники безопасности в Клубе и Правила использования тренажёров.")
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)

                        consentLink("Публичная оферта", asset: .dobrozalOffer)
                        consentLink("Политика обработки и защиты персональных данных Клуба", asset: .dobrozalPrivacy)

                        if let urlStr = visitingRulesUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
                           !urlStr.isEmpty,
                           let url = URL(string: urlStr) {
                            externalLink("Правила посещения", url: url)
                        }
                        if let urlStr = safetyRulesUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
                           !urlStr.isEmpty,
                           let url = URL(string: urlStr) {
                            externalLink("Правила техники безопасности", url: url)
                        }

                        Text("Приобретая абонемент, Вы заключаете договор напрямую с Клубом.")
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    .padding(20)
                }

                consentFooter
            }
            .background(Theme.background)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть", action: onDismiss)
                        .disabled(isLoading)
                }
            }
            .interactiveDismissDisabled(isLoading)
        }
        .presentationDetents([.large, .medium])
        .sheet(item: $legalPdfSheet) { asset in
            LegalPdfSheet(asset: asset) { legalPdfSheet = nil }
        }
    }

    private var consentFooter: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: $safetyBriefed) {
                Text("Я проинструктирован по технике безопасности в Клубе")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onBackground)
            }
            .tint(Theme.primary)
            .disabled(isLoading)

            Toggle(isOn: $dataAccurate) {
                Text("Подтверждаю достоверность предоставленных данных")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onBackground)
            }
            .tint(Theme.primary)
            .disabled(isLoading)

            Button(action: onConfirm) {
                HStack(spacing: 10) {
                    if isLoading {
                        ProgressView().tint(Theme.onPrimary)
                    }
                    Text(isLoading ? "Оформляем…" : "Согласен, приобрести абонемент")
                        .font(FCTypography.titleSmall())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onPrimary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(canConfirm ? Theme.primary : Theme.primary.opacity(0.4))
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(!canConfirm)

            Button(action: onDismiss) {
                Text("Назад")
                    .font(FCTypography.titleSmall())
                    .foregroundStyle(Theme.primary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(
                        RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous)
                            .stroke(Theme.outlineVariant, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .disabled(isLoading)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 12)
        .background(
            Theme.background
                .shadow(color: Color.black.opacity(0.06), radius: 8, y: -2)
                .ignoresSafeArea(edges: .bottom)
        )
    }

    private func consentLink(_ title: String, asset: LegalPdfAsset) -> some View {
        Button {
            legalPdfSheet = asset
        } label: {
            Text(title)
                .font(FCTypography.bodySmall())
                .underline()
                .foregroundStyle(Theme.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, 4)
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
    }

    private func externalLink(_ title: String, url: URL) -> some View {
        Button {
            onOpenURL(url)
        } label: {
            Text(title)
                .font(FCTypography.bodySmall())
                .underline()
                .foregroundStyle(Theme.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, 4)
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
    }
}
