import SwiftUI

struct SpecialistPurchaseConsentDialog: View {
    var isLoading: Bool = false
    let onDismiss: () -> Void
    let onConfirm: () -> Void
    let onOpenPdf: (StaffLegalPdf) -> Void

    @State private var safetyBriefed = false
    @State private var sessionRules = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Подтвердите согласие перед покупкой")
                .font(.title3.weight(.bold))
                .padding(.bottom, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Вы приобретаете профессиональный доступ в Клуб.")
                    Text("Нажимая «Приобрести абонемент», вы подтверждаете, что ознакомились с тарифом и условиями доступа, а также со следующими документами:")
                        .font(.footnote)
                        .foregroundStyle(StaffColors.onSurfaceVariant)

                    Button("Оферта для специалистов") {
                        onOpenPdf(.proOffer)
                    }
                    .foregroundStyle(StaffColors.primary)

                    Button("Политика обработки и защиты персональных данных Клуба") {
                        onOpenPdf(.dobrozalPrivacy)
                    }
                    .foregroundStyle(StaffColors.primary)

                    Toggle(isOn: $safetyBriefed) {
                        Text("Я проинструктирован по технике безопасности в Клубе")
                            .font(.footnote)
                    }
                    .tint(StaffColors.primary)
                    .disabled(isLoading)

                    Toggle(isOn: $sessionRules) {
                        Text("Я ознакомлен с правилами проведения сессий с клиентами")
                            .font(.footnote)
                    }
                    .tint(StaffColors.primary)
                    .disabled(isLoading)
                }
            }

            VStack(spacing: 10) {
                StaffPrimaryButton(
                    text: isLoading ? "Оформляем…" : "Приобрести абонемент",
                    action: onConfirm,
                    enabled: safetyBriefed && sessionRules && !isLoading
                )
                StaffSecondaryButton(text: "Отмена", action: onDismiss, enabled: !isLoading)
            }
            .padding(.top, 16)
        }
        .padding(20)
        .background(StaffColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .padding(.horizontal, 16)
    }
}
