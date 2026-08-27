import Foundation
import Observation
import UIKit

@Observable
@MainActor
final class OnboardingController {
    var status = "pending_approval"
    var amountRub: Double = 0
    var rentalClubs: [RentalClubOption] = []
    var selectedClubId: Int?
    var rentalDays = 30
    var rentalPaidUntil: String?
    var isLoading = false
    var isPaying = false
    var showConsent = false
    var openLegalPdf: StaffLegalPdf?
    var statusMessage: String?
    var errorMessage: String?
    var offerUrl = "https://dobrozal.ru/doc/offer"
    var privacyUrl = "https://dobrozal.ru/doc/privacy"
    var lastPaymentId: Int?

    private let env: AppEnvironment
    var onFinished: (() -> Void)?
    var onLogout: (() -> Void)?

    init(env: AppEnvironment) {
        self.env = env
    }

    func onAppear() {
        refresh()
        if let id = lastPaymentId {
            pollPayment(id)
        }
    }

    func selectClub(_ clubId: Int) {
        selectedClubId = clubId
    }

    func refresh() {
        runAsync(status: "Обновляем статус...") {
            let onboarding = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadOnboarding(token: token)
            }
            self.apply(onboarding)
            if onboarding.status == "active" || onboarding.status == "needs_profile" {
                self.onFinished?()
            }
        }
    }

    func requestPay() {
        guard selectedClubId != nil || rentalClubs.first != nil else {
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
        guard let clubId = selectedClubId ?? rentalClubs.first?.clubId else {
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
            self.apply(result.onboarding)
            self.lastPaymentId = result.paymentId
            if let urlStr = result.paymentUrl, let url = URL(string: urlStr) {
                await UIApplication.shared.open(url)
            }
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
            self.apply(status.onboarding)
            if status.onboarding.status == "active" || status.onboarding.status == "needs_profile" {
                self.onFinished?()
            }
        }
    }

    func logout() {
        onLogout?()
    }

    private func apply(_ onboarding: StaffOnboarding) {
        status = onboarding.status
        amountRub = onboarding.rentalAmountRub
        rentalClubs = onboarding.rentalClubs
        rentalDays = onboarding.rentalDays
        rentalPaidUntil = onboarding.rentalPaidUntil
        offerUrl = onboarding.offerUrl
        privacyUrl = onboarding.privacyUrl
        if let current = selectedClubId, onboarding.rentalClubs.contains(where: { $0.clubId == current }) {
            // keep selection
        } else {
            selectedClubId = onboarding.activeClubId ?? onboarding.rentalClubs.first?.clubId
        }
    }

    private func runAsync(status: String, action: @escaping () async throws -> Void) {
        isLoading = true
        statusMessage = status
        errorMessage = nil
        Task { @MainActor in
            do {
                try await action()
                isLoading = false
                statusMessage = nil
            } catch {
                isLoading = false
                statusMessage = nil
                errorMessage = UserFacingError.message(error)
            }
        }
    }
}
