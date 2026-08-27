import SwiftUI

struct StaffFeedbackView: View {
    @Bindable var controller: StaffFeedbackController
    let onBack: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                StaffSectionTitle(title: "Написать в клуб")
                FlowLayout(spacing: 8) {
                    ForEach(StaffFeedbackController.categories, id: \.0) { key, label in
                        Button {
                            controller.category = key
                        } label: {
                            Text(label)
                                .font(.caption)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(controller.category == key ? StaffColors.primary : StaffColors.primary.opacity(0.12))
                                .foregroundStyle(controller.category == key ? StaffColors.onPrimary : StaffColors.primary)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                TextField("Тема", text: $controller.subject)
                    .textFieldStyle(.roundedBorder)
                TextField("Сообщение", text: $controller.message, axis: .vertical)
                    .lineLimit(4...8)
                    .textFieldStyle(.roundedBorder)
                StaffPrimaryButton(
                    text: controller.isSending ? "Отправка..." : "Отправить",
                    action: controller.submit,
                    enabled: !controller.isSending
                )
                if let err = controller.errorMessage {
                    Text(err).font(.footnote).foregroundStyle(StaffColors.error)
                }
                if let status = controller.statusMessage {
                    StaffInfoBanner(text: status, color: StaffColors.success)
                }
                StaffSectionTitle(title: "Мои обращения")
                if controller.tickets.isEmpty, !controller.isLoading {
                    StaffEmptyState(message: "Пока нет обращений.")
                } else {
                    ForEach(controller.tickets, id: \.id) { ticket in
                        StaffListCard(
                            item: ListCardUi(
                                title: ticket.subject,
                                subtitle: ticket.message,
                                meta: "\(UiLabels.ticketStatus(ticket.status)) · \(ticket.createdAt)",
                                badge: UiLabels.ticketCategory(ticket.category)
                            )
                        )
                    }
                }
            }
            .padding(16)
        }
        .background(StaffColors.background)
        .navigationTitle("Обратная связь")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Назад", action: onBack)
            }
        }
        .staffToolbarStyle()
        .onAppear { controller.onAppear() }
    }
}
