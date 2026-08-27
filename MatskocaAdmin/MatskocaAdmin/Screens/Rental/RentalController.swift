import Foundation
import Observation
import UIKit

@Observable
@MainActor
final class RentalController {
    var rentalPaidUntilLabel: String?
    var clubs: [RentalClubOption] = []
    var payments: [RentalPaymentItem] = []
    var rentalDays = 30
    var selectedClubId: Int?
    var activeClubId: Int?
    var offerUrl = "https://dobrozal.ru/doc/offer"
    var isLoading = false
    var isPaying = false
    var showConsent = false
    var openLegalPdf: StaffLegalPdf?
    var statusMessage: String?
    var errorMessage: String?
    var lastPaymentId: Int?

    private let env: AppEnvironment

    init(env: AppEnvironment) {
        self.env = env
    }

    func onAppear() {
        reload()
        if let id = lastPaymentId {
            pollPayment(id)
        }
    }

    func selectClub(_ clubId: Int) {
        selectedClubId = clubId
    }

    func setActive(_ clubId: Int) {
        runAsync(status: "Меняем зал...") {
            let onboarding = try await self.env.withRefresh { token in
                try await self.env.apiClient.setActiveRentalClub(token: token, clubId: clubId)
            }
            self.applyOnboarding(onboarding)
        }
    }

    func requestPay() {
        guard selectedClubId != nil || clubs.first != nil else {
            errorMessage = "Выберите зал"
            return
        }
        showConsent = true
    }

    func confirmPayFromConsent() {
        showConsent = false
        pay()
    }

    func pay() {
        guard let clubId = selectedClubId ?? clubs.first?.clubId else {
            errorMessage = "Выберите зал"
            return
        }
        isPaying = true
        runAsync(status: "Создаём оплату...") {
            defer { self.isPaying = false }
            let result = try await self.env.withRefresh { token in
                try await self.env.apiClient.initRentalPayment(
                    token: token,
                    offerAccepted: true,
                    clubId: clubId
                )
            }
            self.applyOnboarding(result.onboarding)
            self.lastPaymentId = result.paymentId
            if let urlStr = result.paymentUrl, let url = URL(string: urlStr) {
                await UIApplication.shared.open(url)
            }
            try await self.loadPayments()
        }
    }

    func handlePaymentURL(_ url: URL) {
        guard url.host == "payment" else { return }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems
        let paymentId = items?.first(where: { $0.name == "payment_id" })?.value.flatMap(Int.init)
        if let paymentId, paymentId > 0 {
            lastPaymentId = paymentId
            pollPayment(paymentId)
        }
    }

    func pollPayment(_ paymentId: Int) {
        runAsync(status: "Проверяем оплату...") {
            let status = try await self.env.withRefresh { token in
                try await self.env.apiClient.rentalPaymentStatus(token: token, paymentId: paymentId)
            }
            self.applyOnboarding(status.onboarding)
            try await self.loadPayments()
            if status.status == "paid" || status.status == "succeeded" || status.status == "success" {
                self.statusMessage = "Оплата получена"
                self.lastPaymentId = nil
            } else {
                self.statusMessage = "Статус оплаты: \(status.status)"
            }
        }
    }

    func reload() {
        runAsync(status: "Загрузка...") {
            let onboarding = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadOnboarding(token: token)
            }
            self.applyOnboarding(onboarding)
            try await self.loadPayments()
        }
    }

    private func loadPayments() async throws {
        payments = try await env.withRefresh { token in
            try await env.apiClient.loadRentalPayments(token: token)
        }
    }

    private func applyOnboarding(_ onboarding: StaffOnboarding) {
        clubs = onboarding.rentalClubs
        rentalDays = onboarding.rentalDays
        activeClubId = onboarding.activeClubId
        offerUrl = onboarding.offerUrl
        if let current = selectedClubId, onboarding.rentalClubs.contains(where: { $0.clubId == current }) {
            // keep selection
        } else {
            selectedClubId = onboarding.activeClubId ?? onboarding.rentalClubs.first?.clubId
        }
        if let club = onboarding.activeClub {
            rentalPaidUntilLabel = formatUntil(club.paidUntil, title: club.title)
        } else {
            rentalPaidUntilLabel = formatUntil(onboarding.rentalPaidUntil, title: nil)
        }
    }

    private func formatUntil(_ iso: String?, title: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        let day = String(iso.prefix(10))
        if let title, !title.isEmpty {
            return "Активен: \(title) · до \(day)"
        }
        return "Оплачено до \(day)"
    }

    private func runAsync(status: String, action: @escaping () async throws -> Void) {
        isLoading = true
        statusMessage = status
        errorMessage = nil
        Task { @MainActor in
            do {
                try await action()
                isLoading = false
                if statusMessage == status { statusMessage = nil }
            } catch {
                isLoading = false
                statusMessage = nil
                errorMessage = UserFacingError.message(error)
            }
        }
    }
}
