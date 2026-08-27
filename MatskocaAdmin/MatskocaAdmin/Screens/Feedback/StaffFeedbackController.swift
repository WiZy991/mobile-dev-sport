import Foundation
import Observation

@Observable
@MainActor
final class StaffFeedbackController {
    var subject = ""
    var message = ""
    var category = "question"
    var tickets: [SupportTicketItem] = []
    var isLoading = false
    var isSending = false
    var statusMessage: String?
    var errorMessage: String?

    static let categories: [(String, String)] = [
        ("question", "Вопрос"),
        ("suggestion", "Предложение"),
        ("technical", "Техника"),
        ("billing", "Оплата"),
        ("complaint", "Жалоба"),
        ("other", "Другое"),
    ]

    private let env: AppEnvironment

    init(env: AppEnvironment) {
        self.env = env
    }

    func onAppear() {
        reload()
    }

    func reload() {
        runAsync(status: "Загрузка...", sending: false) {
            self.tickets = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadMyFeedbackTickets(token: token)
            }
        }
    }

    func submit() {
        let subj = subject.trimmingCharacters(in: .whitespacesAndNewlines)
        let msg = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !subj.isEmpty else {
            errorMessage = "Укажите тему"
            return
        }
        guard msg.count >= 5 else {
            errorMessage = "Сообщение слишком короткое"
            return
        }
        runAsync(status: "Отправляем...", sending: true) {
            _ = try await self.env.withRefresh { token in
                try await self.env.apiClient.createFeedbackTicket(
                    token: token,
                    subject: subj,
                    message: msg,
                    category: self.category
                )
            }
            self.subject = ""
            self.message = ""
            self.statusMessage = "Обращение отправлено"
            self.tickets = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadMyFeedbackTickets(token: token)
            }
        }
    }

    private func runAsync(status: String, sending: Bool, action: @escaping () async throws -> Void) {
        isLoading = true
        isSending = sending
        statusMessage = status
        errorMessage = nil
        Task { @MainActor in
            do {
                try await action()
                isLoading = false
                isSending = false
                if statusMessage == status { statusMessage = nil }
            } catch {
                isLoading = false
                isSending = false
                statusMessage = nil
                errorMessage = UserFacingError.message(error)
            }
        }
    }
}
