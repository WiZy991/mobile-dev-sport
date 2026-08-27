import SwiftUI

struct OnboardingView: View {
    @Bindable var controller: OnboardingController
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Доброзал")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(.white)
                    Text(titleForStatus)
                        .font(.title3)
                        .foregroundStyle(.white.opacity(0.9))

                    VStack(alignment: .leading, spacing: 12) {
                        switch controller.status {
                        case "pending_approval":
                            Text("Заявка отправлена администратору CRM. После одобрения вы сможете оплатить доступ и начать работу.")
                                .foregroundStyle(StaffColors.onSurfaceVariant)
                            StaffPrimaryButton(
                                text: controller.isLoading ? "Проверяем..." : "Обновить статус",
                                action: controller.refresh
                            )
                        case "rejected":
                            Text("Администратор отклонил регистрацию. Обратитесь в клуб.")
                                .foregroundStyle(StaffColors.onSurfaceVariant)
                        case "needs_offer_payment":
                            paymentBlock
                        case "needs_profile":
                            Text("Осталось заполнить карточку специалиста: телефон и специализацию. Без этого клиенты не увидят вас в приложении.")
                                .foregroundStyle(StaffColors.onSurfaceVariant)
                            StaffPrimaryButton(
                                text: controller.isLoading ? "Открываем..." : "Заполнить профиль",
                                action: controller.refresh
                            )
                        default:
                            Text("Доступ открыт.")
                                .foregroundStyle(StaffColors.onSurfaceVariant)
                            StaffPrimaryButton(text: "Продолжить", action: controller.refresh)
                        }

                        if let err = controller.errorMessage {
                            Text(err)
                                .font(.footnote)
                                .foregroundStyle(StaffColors.error)
                        }
                        StaffSecondaryButton(text: "Выйти", action: controller.logout)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                }
                .padding(24)
            }

            if controller.showConsent {
                Color.black.opacity(0.45).ignoresSafeArea()
                SpecialistPurchaseConsentDialog(
                    isLoading: controller.isPaying,
                    onDismiss: { controller.showConsent = false },
                    onConfirm: { controller.confirmPayFromConsent() },
                    onOpenPdf: { controller.openLegalPdf = $0 }
                )
            }
        }
        .background(StaffColors.primary.ignoresSafeArea())
        .sheet(item: $controller.openLegalPdf) { doc in
            NavigationStack {
                LegalPdfView(doc: doc) {
                    controller.openLegalPdf = nil
                }
            }
        }
        .onAppear { controller.onAppear() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, let id = controller.lastPaymentId {
                controller.pollPayment(id)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .staffPaymentDeepLink)) { note in
            if let url = note.object as? URL {
                controller.handlePaymentURL(url)
            }
        }
    }

    private var titleForStatus: String {
        switch controller.status {
        case "pending_approval": return "Ожидание одобрения"
        case "needs_offer_payment": return "Оплата доступа специалиста"
        case "needs_profile": return "Заполнение профиля"
        case "rejected": return "Регистрация отклонена"
        default: return "Доступ"
        }
    }

    @ViewBuilder
    private var paymentBlock: some View {
        Text("Выберите зал")
            .font(.headline)
            .foregroundStyle(StaffColors.onSurface)
        Text("Аренда на \(controller.rentalDays) дней · можно докупить другие залы позже")
            .font(.caption)
            .foregroundStyle(StaffColors.onSurfaceVariant)

        if controller.rentalClubs.isEmpty {
            Text(String(format: "Доступ в клуб: %.0f ₽ / %d дн.", controller.amountRub, controller.rentalDays))
                .foregroundStyle(StaffColors.onSurface)
        } else {
            ForEach(controller.rentalClubs) { club in
                RentalClubSelectCard(
                    club: club,
                    selected: club.clubId == controller.selectedClubId
                ) {
                    controller.selectClub(club.clubId)
                }
            }
        }

        let selected = controller.rentalClubs.first { $0.clubId == controller.selectedClubId }
            ?? controller.rentalClubs.first
        let amount = selected?.amountRub ?? controller.amountRub
        Text("К оплате")
            .font(.caption.weight(.medium))
            .foregroundStyle(StaffColors.onSurfaceVariant)
        Text(String(format: "%.0f ₽", amount))
            .font(.title2.weight(.bold))
            .foregroundStyle(StaffColors.primary)
        Text("Без оплаты доступ к рабочим разделам закрыт. Перед оплатой нужно подтвердить оферту и инструктаж.")
            .font(.caption)
            .foregroundStyle(StaffColors.onSurfaceVariant)
        StaffPrimaryButton(
            text: controller.isPaying
                ? "Создаём платёж..."
                : String(format: "Оплатить %.0f ₽", amount),
            action: controller.requestPay
        )
        StaffSecondaryButton(text: "Проверить оплату", action: controller.refresh)
    }
}
