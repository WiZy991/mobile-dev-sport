import SwiftUI

struct RentalView: View {
    @Bindable var controller: RentalController
    let onBack: () -> Void
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if let label = controller.rentalPaidUntilLabel {
                        StaffInfoBanner(text: label, color: StaffColors.primary)
                    }
                    if let status = controller.statusMessage {
                        StaffInfoBanner(text: status, color: StaffColors.success)
                    }
                    StaffSectionTitle(title: "Выберите зал · \(controller.rentalDays) дней")
                    ForEach(controller.clubs) { club in
                        RentalClubSelectCard(
                            club: club,
                            selected: club.clubId == controller.selectedClubId
                        ) {
                            controller.selectClub(club.clubId)
                        }
                        if club.rentalActive, club.clubId != controller.activeClubId {
                            StaffSecondaryButton(text: "Сделать активным для QR") {
                                controller.setActive(club.clubId)
                            }
                        }
                    }

                    let selected = controller.clubs.first { $0.clubId == controller.selectedClubId }
                        ?? controller.clubs.first
                    let amount = selected?.amountRub ?? 0
                    StaffPrimaryButton(
                        text: controller.isPaying
                            ? "Создаём платёж..."
                            : String(format: "Оплатить %.0f ₽", amount),
                        action: controller.requestPay
                    )

                    if controller.clubs.isEmpty, !controller.isLoading {
                        StaffEmptyState(message: "Каталог залов пока пуст. Добавьте клуб в CRM (раздел клубов) и обновите экран.")
                    }

                    if let err = controller.errorMessage {
                        Text(err).font(.footnote).foregroundStyle(StaffColors.error)
                    }

                    StaffSectionTitle(title: "История платежей")
                    if controller.payments.isEmpty {
                        StaffEmptyState(message: "История платежей пока пуста.")
                    } else {
                        ForEach(controller.payments) { payment in
                            let clubPart = payment.clubName.map { " · \($0)" } ?? ""
                            StaffListCard(
                                item: ListCardUi(
                                    title: String(format: "%.0f ₽ · %d дн.%@", payment.amountRub, controller.rentalDays, clubPart),
                                    subtitle: UiLabels.paymentStatus(payment.status),
                                    meta: payment.paidAt ?? payment.createdAt ?? ""
                                )
                            )
                        }
                    }
                }
                .padding(16)
            }

            if controller.showConsent {
                Color.black.opacity(0.4).ignoresSafeArea()
                SpecialistPurchaseConsentDialog(
                    isLoading: controller.isPaying,
                    onDismiss: { controller.showConsent = false },
                    onConfirm: { controller.confirmPayFromConsent() },
                    onOpenPdf: { controller.openLegalPdf = $0 }
                )
            }
        }
        .background(StaffColors.background)
        .navigationTitle("Аренда клуба")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Назад", action: onBack)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Обновить", action: controller.reload)
            }
        }
        .staffToolbarStyle()
        .navigationDestination(item: $controller.openLegalPdf) { doc in
            LegalPdfView(doc: doc) {
                controller.openLegalPdf = nil
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
}
