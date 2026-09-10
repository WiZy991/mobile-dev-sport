import SwiftUI
import UIKit

// MARK: - Модель регистрации (`RegisterViewModel.kt`)

enum RegistrationTypeOption: String, CaseIterable, Identifiable {
    case client
    case coach

    var id: String { rawValue }

    var label: String {
        switch self {
        case .client: return "Я клиент"
        case .coach: return "Я тренер"
        }
    }
}

enum GenderOption: String, CaseIterable, Identifiable {
    case male
    case female

    var id: String { rawValue }

    var label: String {
        switch self {
        case .male: return "Муж"
        case .female: return "Жен"
        }
    }
}

/// Шаги формы регистрации (как `RegisterFormStep` на Android).
enum RegisterFormStep: Int, CaseIterable, Identifiable {
    case personal = 1
    case account = 2

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .personal: return "Личные данные"
        case .account: return "Пароль и согласие"
        }
    }

    static var last: RegisterFormStep { .account }
}

enum ReferralSourceOptions {
    static let otherKey = "other"

    static let items: [(key: String, label: String)] = [
        ("friends_family", "От друзей/родственников"),
        ("friends_in_gymroom", "Друзья/родственники уже ходят в Доброзал"),
        ("social", "Из социальных сетей"),
        ("2gis", "2ГИС"),
        ("yandex", "Яндекс"),
        ("internet_ads", "Из рекламы в интернете"),
        ("mall", "Увидел в торговом центре"),
        ("event", "Посещал мероприятие"),
        (otherKey, "Другое (свой вариант)"),
    ]
}

struct PassportDraft: Equatable {
    var series = ""
    var number = ""
    var issuedBy = ""
    var issuedDateDisplay = ""
    var region = ""
    var city = ""
    var streetHouse = ""

    /// Российский паспорт: серия 4 цифры, номер 6 цифр.
    var isCompleteForRegister: Bool {
        series.count == 4
            && number.count == 6
            && !issuedBy.isEmpty
            && !issuedDateDisplay.isEmpty
            && !region.isEmpty
            && !city.isEmpty
            && !streetHouse.isEmpty
    }

    var summary: String {
        if isCompleteForRegister { return "Паспорт заполнен" }
        if !series.isEmpty || !number.isEmpty { return "Заполнение…" }
        return "Заполните паспорт"
    }
}

private struct RegisterStepErrors {
    var lastNameError: String?
    var firstNameError: String?
    var birthDateError: String?
    var phoneError: String?
    var emailError: String?
    var genderError: String?
    var passportError: String?
    var passwordError: String?
    var confirmPasswordError: String?
    var clubError: String?
    var legalTermsError: String?

    var hasError: Bool {
        allMessages.contains { $0 != nil }
    }

    var firstMessage: String? {
        allMessages.compactMap { $0 }.first
    }

    private var allMessages: [String?] {
        [
            clubError,
            lastNameError,
            firstNameError,
            birthDateError,
            phoneError,
            emailError,
            genderError,
            passportError,
            passwordError,
            confirmPasswordError,
            legalTermsError,
        ]
    }

    func merging(_ other: RegisterStepErrors) -> RegisterStepErrors {
        RegisterStepErrors(
            lastNameError: lastNameError ?? other.lastNameError,
            firstNameError: firstNameError ?? other.firstNameError,
            birthDateError: birthDateError ?? other.birthDateError,
            phoneError: phoneError ?? other.phoneError,
            emailError: emailError ?? other.emailError,
            genderError: genderError ?? other.genderError,
            passportError: passportError ?? other.passportError,
            passwordError: passwordError ?? other.passwordError,
            confirmPasswordError: confirmPasswordError ?? other.confirmPasswordError,
            clubError: clubError ?? other.clubError,
            legalTermsError: legalTermsError ?? other.legalTermsError
        )
    }
}

@MainActor
final class RegisterFlowModel: ObservableObject {
    /// Регистрация после phone OTP (`phoneRegistration` на Android).
    var phoneOtpMode = false

    @Published var selectedClub: ClubItem?
    @Published var registrationType: RegistrationTypeOption = .client
    @Published var lastName = ""
    @Published var firstName = ""
    @Published var middleName = ""
    @Published var birthDateDisplay = ""
    @Published var phoneNationalDigits = ""
    @Published var email = ""
    @Published var gender: GenderOption?
    @Published var passport = PassportDraft()
    @Published var promoCode = ""
    @Published var password = ""
    @Published var confirmPassword = ""
    @Published var acceptedLegalTerms = true
    @Published var referralSource: String?
    @Published var referralSourceOther = ""
    @Published var referralError: String?

    @Published var formStep: RegisterFormStep = .personal
    @Published var validationSummary: String?
    @Published var showValidationErrors = false

    @Published var lastNameError: String?
    @Published var firstNameError: String?
    @Published var birthDateError: String?
    @Published var phoneError: String?
    @Published var emailError: String?
    @Published var genderError: String?
    @Published var passportError: String?
    @Published var passwordError: String?
    @Published var confirmPasswordError: String?
    @Published var clubError: String?
    @Published var legalTermsError: String?
    @Published var isLoading = false
    @Published var error: String?

    @Published var emailCheckLoading = false
    @Published var emailTakenMaskedPhone: String?
    @Published var supportEmail: String?
    @Published var supportPhone: String?
    @Published var showUnder18Dialog = false

    var pendingAfterUnder18: Under18Continue?
    var phoneLocked: Bool { phoneOtpMode }

    enum Under18Continue {
        case needClubPick
        case register
    }

    var primaryButtonTitle: String {
        if phoneOtpMode && formStep == .personal {
            return "Далее"
        }
        return formStep == .account ? "Зарегистрироваться" : "Далее"
    }

    var isCurrentStepValid: Bool {
        !validateStep(formStep).hasError
    }

    func applyPhoneOtpPrefill() {
        guard phoneOtpMode else { return }
        if let phone = AuthFlowStore.peekOtpPhone() {
            phoneNationalDigits = normalizeRussianNationalDigits(phone)
        }
    }

    func selectClub(from card: RegistrationVenueCard) {
        selectedClub = RegistrationVenues.clubItem(for: card)
        clubError = nil
        validationSummary = nil
    }

    func fieldErr(_ message: String?) -> String? {
        guard showValidationErrors else { return nil }
        return message
    }

    /// Опросник «Откуда вы о нас узнали» — первый шаг регистрации (как `RegisterSurveyScreen.kt`).
    func submitSurvey() -> Bool {
        if referralSource == nil || referralSource?.isEmpty == true {
            referralError = "Выберите вариант"
            return false
        }
        if referralSource == ReferralSourceOptions.otherKey,
           referralSourceOther.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            referralError = "Опишите ваш вариант"
            return false
        }
        referralError = nil
        return true
    }

    func submitRegisterEmail(api: FitnessAPI) async -> Bool {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || !RegisterValidation.isValidEmail(trimmed) {
            emailError = "Введите корректный email"
            return false
        }
        emailCheckLoading = true
        emailTakenMaskedPhone = nil
        emailError = nil
        defer { emailCheckLoading = false }
        do {
            let res = try await api.checkRegisterEmail(trimmed)
            if res.exists {
                emailTakenMaskedPhone = res.maskedPhone ?? "••••"
                return false
            }
            email = trimmed
            return true
        } catch {
            emailError = error.localizedDescription
            return false
        }
    }

    func dismissEmailTaken() {
        emailTakenMaskedPhone = nil
    }

    func validatePassportDraft() -> Bool {
        // Нормализуем серию/номер: только ASCII-цифры (без пробелов и «красивых» юникод-цифр).
        var p = passport
        p.series = Self.passportDigits(p.series, maxLen: 4)
        p.number = Self.passportDigits(p.number, maxLen: 6)
        p.issuedBy = p.issuedBy.trimmingCharacters(in: .whitespacesAndNewlines)
        p.issuedDateDisplay = p.issuedDateDisplay.trimmingCharacters(in: .whitespacesAndNewlines)
        p.region = p.region.trimmingCharacters(in: .whitespacesAndNewlines)
        p.city = p.city.trimmingCharacters(in: .whitespacesAndNewlines)
        p.streetHouse = p.streetHouse.trimmingCharacters(in: .whitespacesAndNewlines)
        passport = p

        if p.series.count != 4 {
            passportError = "Серия паспорта должна состоять из 4 цифр"
            return false
        }
        if p.number.count != 6 {
            passportError = "Номер паспорта должен состоять из 6 цифр"
            return false
        }
        if p.issuedBy.isEmpty {
            passportError = "Укажите, кем выдан паспорт"
            return false
        }
        if RegisterDateParsing.toIsoDate(p.issuedDateDisplay) == nil {
            passportError = "Укажите дату выдачи в формате дд.мм.гггг"
            return false
        }
        if p.region.isEmpty || p.city.isEmpty || p.streetHouse.isEmpty {
            passportError = "Заполните адрес прописки (регион, город, улица и дом)"
            return false
        }

        passportError = nil
        showValidationErrors = false
        validationSummary = nil
        return true
    }

    private static func passportDigits(_ value: String, maxLen: Int) -> String {
        String(value.filter { $0.isASCII && $0.isNumber }.prefix(maxLen))
    }

    func goToPreviousFormStep() {
        switch formStep {
        case .personal:
            return
        case .account:
            formStep = .personal
        }
        showValidationErrors = false
        validationSummary = nil
        error = nil
    }

    @discardableResult
    func advanceFormStep() -> Bool {
        let errors = validateStep(formStep)
        if errors.hasError {
            applyValidationErrors(errors, summary: summaryForStep(formStep, errors), targetStep: formStep)
            return false
        }
        if phoneOtpMode && formStep == .personal {
            return true
        }
        if formStep == .last { return true }
        switch formStep {
        case .personal: formStep = .account
        case .account: break
        }
        showValidationErrors = false
        validationSummary = nil
        error = nil
        return true
    }

    /// Результат primary action: `needClubPick` для phone-path после personal.
    enum PrimaryFormOutcome {
        case advanced
        case needClubPick
        case registered(AuthResponse)
        case failed
    }

    func onPrimaryFormAction(api: FitnessAPI) async -> PrimaryFormOutcome {
        if phoneOtpMode && formStep == .personal {
            guard advanceFormStep() else { return .failed }
            if shouldShowUnder18Prompt() {
                pendingAfterUnder18 = .needClubPick
                showUnder18Dialog = true
                return .failed
            }
            return .needClubPick
        }
        if formStep == .account {
            if shouldShowUnder18Prompt() {
                pendingAfterUnder18 = .register
                showUnder18Dialog = true
                return .failed
            }
            if let response = await register(api: api) {
                return .registered(response)
            }
            return .failed
        }
        return advanceFormStep() ? .advanced : .failed
    }

    func shouldShowUnder18Prompt() -> Bool {
        guard let iso = RegisterDateParsing.toIsoDate(birthDateDisplay) else { return false }
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(secondsFromGMT: 0)
        f.dateFormat = "yyyy-MM-dd"
        guard let born = f.date(from: String(iso.prefix(10))) else { return false }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let today = cal.startOfDay(for: Date())
        guard let adultDay = cal.date(byAdding: .year, value: 18, to: cal.startOfDay(for: born)) else { return false }
        // Как Android: born.plusYears(18).isAfter(today)
        return adultDay > today
    }

    func dismissUnder18AndContinue(api: FitnessAPI) async -> PrimaryFormOutcome {
        showUnder18Dialog = false
        let pending = pendingAfterUnder18
        pendingAfterUnder18 = nil
        switch pending {
        case .needClubPick:
            return .needClubPick
        case .register:
            if let response = await register(api: api) {
                return .registered(response)
            }
            return .failed
        case nil:
            return .failed
        }
    }

    func dismissUnder18() {
        showUnder18Dialog = false
        pendingAfterUnder18 = nil
    }

    @discardableResult
    func register(api: FitnessAPI, onSuccess: ((AuthResponse) -> Void)? = nil) async -> AuthResponse? {
        let personal = validateStep(.personal)
        let account = validateStep(.account)
        let merged = personal.merging(account)
        if merged.hasError {
            let target: RegisterFormStep = personal.hasError ? .personal : .account
            applyValidationErrors(merged, summary: merged.firstMessage ?? "Заполните обязательные поля", targetStep: target)
            return nil
        }

        guard let birthIso = RegisterDateParsing.toIsoDate(birthDateDisplay),
              let gender
        else { return nil }

        let fullName = [lastName, firstName, middleName]
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")

        let otpTicket = AuthFlowStore.peekOtpTicket()
        let phoneApi: String = {
            let fromField = phoneForApi(phoneNationalDigits)
            if !fromField.isEmpty { return fromField }
            if let otpPhone = AuthFlowStore.peekOtpPhone() {
                return phoneForApi(normalizeRussianNationalDigits(otpPhone))
            }
            return ""
        }()

        let request = RegisterRequest(
            email: email,
            password: (phoneOtpMode || otpTicket != nil) ? "" : password,
            phone: phoneApi,
            name: fullName,
            registrationType: "client",
            dateOfBirth: birthIso,
            gender: gender.rawValue,
            passportSeries: nil,
            passportNumber: nil,
            passportIssuedBy: nil,
            passportIssueDate: nil,
            registrationAddress: nil,
            promoCode: promoCode.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
            newsletter: false,
            clubId: selectedClub?.id,
            clubName: selectedClub?.name,
            clubAddress: selectedClub?.address,
            referralSource: referralSource,
            referralSourceOther: referralSourceOther.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? nil
                : String(referralSourceOther.prefix(255)),
            otpTicket: otpTicket
        )

        isLoading = true
        error = nil
        defer { isLoading = false }

        do {
            let response: AuthResponse
            if otpTicket != nil {
                response = try await api.registerPhone(payload: request)
                AuthFlowStore.clearOtpRegistration()
            } else {
                response = try await api.register(payload: request)
            }
            onSuccess?(response)
            return response
        } catch let e as FitnessAPIError {
            error = e.localizedDescription
            return nil
        } catch {
            self.error = error.localizedDescription
            return nil
        }
    }

    func passportBinding<T>(_ keyPath: WritableKeyPath<PassportDraft, T>) -> Binding<T> {
        Binding(
            get: { self.passport[keyPath: keyPath] },
            set: { newValue in
                var draft = self.passport
                draft[keyPath: keyPath] = newValue
                self.passport = draft
                self.passportError = nil
                self.validationSummary = nil
            }
        )
    }

    private func validateStep(_ step: RegisterFormStep) -> RegisterStepErrors {
        var errors = RegisterStepErrors()
        switch step {
        case .personal:
            if !phoneOtpMode && selectedClub == nil {
                errors.clubError = "Выберите клуб на предыдущем шаге"
            }
            if lastName.trimmingCharacters(in: .whitespaces).isEmpty {
                errors.lastNameError = "Введите фамилию"
            }
            if firstName.trimmingCharacters(in: .whitespaces).isEmpty {
                errors.firstNameError = "Введите имя"
            }
            if RegisterDateParsing.toIsoDate(birthDateDisplay) == nil {
                errors.birthDateError = "Укажите дату рождения (дд.мм.гггг)"
            }
            if !phoneOtpMode {
                if phoneForApi(phoneNationalDigits).isEmpty {
                    errors.phoneError = "Введите полный номер телефона"
                }
                if email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    errors.emailError = "Введите email"
                } else if !RegisterValidation.isValidEmail(email) {
                    errors.emailError = "Неверный формат email"
                }
            }
            if gender == nil {
                errors.genderError = "Выберите пол"
            }
        case .account:
            if phoneOtpMode || AuthFlowStore.peekOtpTicket() != nil {
                break
            }
            if password.isEmpty {
                errors.passwordError = "Введите пароль"
            } else if password.count < 6 {
                errors.passwordError = "Пароль не менее 6 символов"
            }
            if confirmPassword != password {
                errors.confirmPasswordError = "Пароли не совпадают"
            }
            if !acceptedLegalTerms {
                errors.legalTermsError = "Подтвердите согласие с условиями"
            }
        }
        return errors
    }

    private func summaryForStep(_ step: RegisterFormStep, _ errors: RegisterStepErrors) -> String {
        let detail = errors.firstMessage
        switch step {
        case .personal: return detail ?? "Заполните личные данные"
        case .account: return detail ?? "Заполните пароль и подтвердите согласие"
        }
    }

    private func applyValidationErrors(_ errors: RegisterStepErrors, summary: String, targetStep: RegisterFormStep) {
        formStep = targetStep
        showValidationErrors = true
        validationSummary = summary
        lastNameError = errors.lastNameError
        firstNameError = errors.firstNameError
        birthDateError = errors.birthDateError
        phoneError = errors.phoneError
        emailError = errors.emailError
        genderError = errors.genderError
        passportError = errors.passportError
        passwordError = errors.passwordError
        confirmPasswordError = errors.confirmPasswordError
        clubError = errors.clubError
        legalTermsError = errors.legalTermsError
        error = nil
    }
}

enum RegisterDateParsing {
    /// Только цифры → `дд.мм.гггг` (как `DateDotsVisualTransformation` на Android).
    static func applyDotsMask(_ raw: String) -> String {
        let digits = String(raw.filter(\.isNumber).prefix(8))
        var out = ""
        for (i, c) in digits.enumerated() {
            if i == 2 || i == 4 { out.append(".") }
            out.append(c)
        }
        return out
    }

    static func toIsoDate(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let df = DateFormatter()
        // POSIX + GMT: стабильный разбор «дд.мм.гггг» без сюрпризов локали/DST.
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        df.isLenient = false

        if trimmed.contains("-") {
            df.dateFormat = "yyyy-MM-dd"
            if let d = df.date(from: String(trimmed.prefix(10))) {
                return df.string(from: d)
            }
        }

        let dotted = applyDotsMask(trimmed)
        guard dotted.count == 10 else { return nil }
        df.dateFormat = "dd.MM.yyyy"
        guard let d = df.date(from: dotted) else { return nil }
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: d)
    }

    static func displayFromDate(_ date: Date) -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone.current
        df.dateFormat = "dd.MM.yyyy"
        return df.string(from: date)
    }
}

enum RegisterValidation {
    static func isValidEmail(_ email: String) -> Bool {
        let pred = NSPredicate(format: "SELF MATCHES %@", #"^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#)
        return pred.evaluate(with: email)
    }
}

// MARK: - Навигация регистрации

private enum RegisterRoute: Hashable {
    case emailCheck
    case clubPick
    case form
}

struct RegisterFlowView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.dismiss) private var dismiss
    var phoneOtpMode: Bool = false
    @StateObject private var model = RegisterFlowModel()
    @State private var path: [RegisterRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if phoneOtpMode {
                    RegisterEmailCheckStepView(
                        model: model,
                        onBack: { dismiss() },
                        onContinue: { path.append(.form) }
                    )
                } else {
                    RegisterSurveyStepView(
                        model: model,
                        onBack: { dismiss() },
                        onSubmit: {
                            if model.submitSurvey() {
                                path.append(.clubPick)
                            }
                        }
                    )
                }
            }
            .navigationDestination(for: RegisterRoute.self) { route in
                switch route {
                case .emailCheck:
                    RegisterEmailCheckStepView(
                        model: model,
                        onBack: { dismiss() },
                        onContinue: { path.append(.form) }
                    )
                case .clubPick:
                    RegisterClubPickStepView(
                        selectedClubId: model.selectedClub?.id,
                        isSubmitting: model.isLoading,
                        submitError: model.error,
                        phoneRegistration: phoneOtpMode,
                        onClubSelected: { model.selectClub(from: $0) },
                        onContinue: {
                            if phoneOtpMode {
                                Task {
                                    if let response = await model.register(api: app.api) {
                                        applyRegistered(response)
                                    }
                                }
                            } else {
                                path.append(.form)
                            }
                        },
                        onBack: {
                            if phoneOtpMode {
                                // Phone: club ← form
                                if path.isEmpty { dismiss() } else { path.removeLast() }
                            } else {
                                // Email: club ← Login (как Android)
                                dismiss()
                            }
                        }
                    )
                case .form:
                    RegisterFormView(
                        model: model,
                        onBackToLogin: {
                            dismiss()
                        },
                        onChangeClub: {
                            model.formStep = .personal
                            model.showValidationErrors = false
                            model.validationSummary = nil
                            if phoneOtpMode {
                                path.append(.clubPick)
                            } else {
                                path = [.clubPick]
                            }
                        },
                        onNeedClubPick: {
                            path.append(.clubPick)
                        },
                        onRegistered: { response in
                            applyRegistered(response)
                        }
                    )
                }
            }
        }
        .onAppear {
            model.phoneOtpMode = phoneOtpMode
            model.applyPhoneOtpPrefill()
        }
        .task {
            if let info = try? await app.api.getClubInfo() {
                model.supportEmail = info.email.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                model.supportPhone = info.phone.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            }
        }
    }

    private func applyRegistered(_ response: AuthResponse) {
        app.applyAuth(response, markReturningCustomerOnDevice: false)
        app.markReturningCustomerOnDeviceAfterSuccessfulSignupFlow()
        app.pendingSecuritySetup = true
        dismiss()
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

// MARK: - Email check (`RegisterEmailScreen.kt`)

private struct RegisterEmailCheckStepView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @ObservedObject var model: RegisterFlowModel
    let onBack: () -> Void
    let onContinue: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(Theme.onBackground)
                        .frame(width: 44, height: 44)
                }
                Text("Давайте познакомимся!")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)

            Text("Введите email — так мы поймём, не регистрировались ли вы раньше.")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
                .padding(.horizontal, 20)
                .padding(.top, 8)

            TextField("Email", text: $model.email)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous)
                        .stroke(model.emailError != nil ? Theme.error : Theme.outlineVariant, lineWidth: 1)
                )
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .onChange(of: model.email) { _, _ in model.emailError = nil }

            if let err = model.emailError {
                Text(err)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.error)
                    .padding(.horizontal, 20)
                    .padding(.top, 6)
            }

            Spacer()

            Button {
                Task {
                    if await model.submitRegisterEmail(api: app.api) {
                        onContinue()
                    }
                }
            } label: {
                Group {
                    if model.emailCheckLoading {
                        ProgressView().tint(Theme.onPrimary)
                    } else {
                        Text("Далее")
                            .fontWeight(.bold)
                    }
                }
                .foregroundStyle(Theme.onPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Theme.primary)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(model.emailCheckLoading)
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationBarHidden(true)
        .alert("Аккаунт уже есть", isPresented: Binding(
            get: { model.emailTakenMaskedPhone != nil },
            set: { if !$0 { model.dismissEmailTaken() } }
        )) {
            Button("Связаться с поддержкой") {
                if let email = model.supportEmail, let u = URL(string: "mailto:\(email)") {
                    UIApplication.shared.open(u)
                } else if let phone = model.supportPhone {
                    dialPhoneRaw(phone)
                }
            }
            Button("Ввести другой email", role: .cancel) {
                model.dismissEmailTaken()
            }
        } message: {
            let phone = model.emailTakenMaskedPhone ?? "••••"
            Text(
                "Аккаунт с этой почтой уже есть. Телефон в профиле: \(phone). Обратитесь в поддержку, чтобы восстановить доступ."
            )
        }
    }
}

// MARK: - Опросник (`RegisterSurveyScreen.kt`)

private struct RegisterSurveyStepView: View {
    @ObservedObject var model: RegisterFlowModel
    let onBack: () -> Void
    let onSubmit: () -> Void

    private let surface = Color.white

    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.primary, Theme.primaryVariant], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Button(action: onBack) {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 20, weight: .semibold))
                                    .foregroundStyle(surface)
                                    .frame(width: 44, height: 44)
                            }
                            Spacer()
                        }

                        Text("Выбрать пункт, откуда вы о нас узнали")
                            .font(FCTypography.titleLarge())
                            .fontWeight(.bold)
                            .foregroundStyle(surface)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .padding(.bottom, 16)

                        ForEach(ReferralSourceOptions.items, id: \.key) { item in
                            surveyOptionRow(
                                label: item.label,
                                selected: model.referralSource == item.key
                            ) {
                                model.referralSource = item.key
                                model.referralError = nil
                            }
                        }

                        if model.referralSource == ReferralSourceOptions.otherKey {
                            TextField(
                                "",
                                text: $model.referralSourceOther,
                                prompt: Text("Ваш вариант").foregroundStyle(surface.opacity(0.78))
                            )
                            .foregroundStyle(surface)
                            .font(FCTypography.bodyLarge())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 14)
                            .overlay(alignment: .bottom) {
                                Rectangle()
                                    .fill(surface.opacity(0.55))
                                    .frame(height: 1)
                            }
                            .padding(.top, 8)
                            .onChange(of: model.referralSourceOther) { _, newValue in
                                if newValue.count > 255 {
                                    model.referralSourceOther = String(newValue.prefix(255))
                                }
                                model.referralError = nil
                            }
                        }

                        if let err = model.referralError {
                            Text(err)
                                .font(FCTypography.bodyMedium())
                                .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
                                .padding(.top, 8)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                }

                Button(action: onSubmit) {
                    Text("ОТПРАВИТЬ")
                        .font(FCTypography.titleSmall())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.primary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(surface)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
            }
        }
        .navigationBarHidden(true)
    }

    private func surveyOptionRow(label: String, selected: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(surface.opacity(selected ? 1 : 0.7))
                Text(label)
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(surface)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 8)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(surface.opacity(selected ? 0.16 : 0.06))
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.vertical, 2)
    }
}

// MARK: - Выбор клуба (`RegisterClubPickScreen.kt`)

private struct RegisterClubPickStepView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    let selectedClubId: String?
    var isSubmitting: Bool = false
    var submitError: String? = nil
    var phoneRegistration: Bool = false
    let onClubSelected: (RegistrationVenueCard) -> Void
    let onContinue: () -> Void
    let onBack: () -> Void

    @State private var expandedIds: Set<String> = []
    @State private var cards: [RegistrationVenueCard] = []
    @State private var loading = true
    @State private var loadError: String?

    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.primary, Theme.primaryVariant], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Button(action: onBack) {
                            Image(systemName: "arrow.left")
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(Theme.loginOnBackground)
                                .frame(width: 44, height: 44, alignment: .leading)
                                .contentShape(Rectangle())
                        }
                        Spacer()
                    }
                    .padding(.top, 4)

                    Text("Выберите зал")
                        .font(FCTypography.headlineSmall())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.loginOnBackground)
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                        .padding(.bottom, 8)

                    BrandHeader(
                        brandName: AppConfiguration.appDisplayName,
                        subtitle: "Клуб привяжется к вашему аккаунту",
                        textColor: Theme.loginOnBackground,
                        logoSize: 40
                    )
                    .padding(.bottom, 16)

                    if loading {
                        ProgressView()
                            .tint(Theme.loginOnBackground)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 24)
                    } else if let loadError {
                        Text(loadError)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.loginOnBackground)
                            .frame(maxWidth: .infinity)
                            .multilineTextAlignment(.center)
                            .padding(.vertical, 16)
                        Button("Повторить") {
                            Task { await loadClubs() }
                        }
                        .foregroundStyle(Theme.loginOnBackground)
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, 16)
                    } else if cards.isEmpty {
                        Text("Нет залов. Отметьте «Показывать в приложении» у залов в CRM.")
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.loginOnBackground)
                            .frame(maxWidth: .infinity)
                            .multilineTextAlignment(.center)
                            .padding(.vertical, 16)
                    } else {
                        ForEach(cards) { card in
                            venueCard(card)
                        }
                    }

                    if let submitError {
                        Text(submitError)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
                            .frame(maxWidth: .infinity)
                            .multilineTextAlignment(.center)
                            .padding(.vertical, 8)
                    }

                    Button(action: onContinue) {
                        Group {
                            if isSubmitting {
                                ProgressView().tint(Theme.onPrimary)
                            } else {
                                Text(phoneRegistration ? "Зарегистрироваться" : "Продолжить регистрацию")
                                    .font(FCTypography.titleMedium())
                                    .fontWeight(.semibold)
                            }
                        }
                        .foregroundStyle(Theme.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .opacity(selectedClubId == nil || isSubmitting ? 0.55 : 1)
                    .disabled(selectedClubId == nil || isSubmitting)
                    .padding(.top, 6)
                    .padding(.bottom, 36)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
        }
        .navigationBarHidden(true)
        .task { await loadClubs() }
    }

    private func loadClubs() async {
        loading = true
        loadError = nil
        defer { loading = false }
        do {
            let clubs = try await app.api.getClubs()
            if clubs.isEmpty {
                cards = RegistrationVenues.orderedCards
                if cards.isEmpty {
                    loadError = nil
                }
            } else {
                cards = clubs.map(RegistrationVenueCard.init(from:))
            }
        } catch {
            cards = RegistrationVenues.orderedCards
            if cards.isEmpty {
                loadError = "Не удалось загрузить список клубов"
            } else {
                loadError = nil
            }
        }
    }

    private func venueCard(_ card: RegistrationVenueCard) -> some View {
        let selected = card.clubId == selectedClubId
        let expanded = expandedIds.contains(card.clubId)

        return VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 10) {
                Button { onClubSelected(card) } label: {
                    venueImage(card)
                        .frame(maxWidth: .infinity)
                        .frame(height: 140)
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                HStack(alignment: .center, spacing: 8) {
                    Button { onClubSelected(card) } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(card.title)
                                .font(FCTypography.titleMedium())
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.loginOnBackground)
                            Text(card.addressLines)
                                .font(FCTypography.bodySmall())
                                .foregroundStyle(Theme.loginOnBackground.opacity(0.88))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    Button {
                        togglePriceList(for: card.clubId)
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(Theme.loginOnBackground)
                            .rotationEffect(.degrees(expanded ? 180 : 0))
                            .frame(width: 48, height: 48)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.borderless)

                    if selected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(Theme.loginOnBackground)
                    }
                }

                if expanded {
                    Rectangle()
                        .fill(Theme.loginOnBackground.opacity(0.25))
                        .frame(height: 1)
                        .padding(.vertical, 10)

                    Text("Абонементы")
                        .font(FCTypography.titleSmall())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.loginOnBackground)
                        .padding(.bottom, 8)

                    ForEach(LocalSubscriptionCatalog.pricelistOrder, id: \.safeId) { plan in
                        registrationPricePane(plan)
                    }
                }
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius16)
                    .fill(Theme.loginOnBackground.opacity(0.12))
            )
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radius16)
                    .stroke(selected ? Theme.loginOnBackground : Color.clear, lineWidth: 2)
            )
            .padding(.bottom, 12)
            .animation(.easeInOut(duration: 0.22), value: expanded)
        }
    }

    @ViewBuilder
    private func venueImage(_ card: RegistrationVenueCard) -> some View {
        if let urlStr = card.imageUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
           !urlStr.isEmpty,
           let url = URL(string: urlStr) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                case .failure:
                    fallbackVenueImage(card)
                default:
                    ZStack {
                        RegisterImagePlaceholderTile()
                        ProgressView().tint(Theme.loginOnBackground)
                    }
                }
            }
        } else {
            fallbackVenueImage(card)
        }
    }

    @ViewBuilder
    private func fallbackVenueImage(_ card: RegistrationVenueCard) -> some View {
        if let asset = card.imageAssetName, UIImage(named: asset) != nil {
            Image(asset).resizable().scaledToFill()
        } else {
            RegisterImagePlaceholderTile()
        }
    }

    private func togglePriceList(for clubId: String) {
        // Переприсваиваем Set целиком — in-place insert/remove иногда не обновляет SwiftUI.
        withAnimation(.easeInOut(duration: 0.22)) {
            if expandedIds.contains(clubId) {
                expandedIds = expandedIds.subtracting([clubId])
            } else {
                expandedIds = expandedIds.union([clubId])
            }
        }
    }

    private func registrationPricePane(_ plan: SubscriptionPlan) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(plan.safeName)
                .font(FCTypography.bodyLarge())
                .fontWeight(.medium)
                .foregroundStyle(Theme.loginOnBackground)
            Text("Цена: \(formattedRuPrice(plan.price)) ₽")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.loginOnBackground.opacity(0.95))
            if let line = LocalSubscriptionCatalog.freezeSubtitle(planId: plan.safeId) {
                Text(line)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.loginOnBackground.opacity(0.8))
                    .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.black.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .padding(.bottom, 8)
    }

    private func formattedRuPrice(_ price: Double) -> String {
        let nf = NumberFormatter()
        nf.numberStyle = .decimal
        nf.groupingSeparator = " "
        nf.locale = Locale(identifier: "ru_RU")
        return nf.string(from: NSNumber(value: Int(price))) ?? "\(Int(price))"
    }
}

private struct RegisterImagePlaceholderTile: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Theme.radius12)
                .fill(Theme.loginOnBackground.opacity(0.18))
            Image(systemName: "building.2.fill")
                .font(.system(size: 52))
                .foregroundStyle(Theme.loginOnBackground.opacity(0.5))
        }
    }
}

// MARK: - Форма регистрации (`RegisterScreen.kt`)

private struct RegisterFormView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @ObservedObject var model: RegisterFlowModel
    let onBackToLogin: () -> Void
    let onChangeClub: () -> Void
    let onNeedClubPick: () -> Void
    let onRegistered: (AuthResponse) -> Void

    @State private var passwordVisible = false
    @State private var confirmPasswordVisible = false
    @State private var showBirthPicker = false
    @State private var legalPdfSheet: LegalPdfAsset?

    private let surface = Theme.loginOnBackground

    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.primary, Theme.primaryVariant], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            HStack {
                                Button {
                                    if model.formStep == .personal {
                                        onBackToLogin()
                                    } else {
                                        model.goToPreviousFormStep()
                                    }
                                } label: {
                                    Image(systemName: "arrow.left")
                                        .font(.title3.weight(.semibold))
                                        .foregroundStyle(surface)
                                }
                                Spacer()
                            }

                            registerStepIndicator
                                .padding(.top, 4)
                                .padding(.bottom, 12)

                            BrandHeader(
                                brandName: AppConfiguration.appDisplayName,
                                subtitle: model.formStep.title,
                                textColor: surface,
                                logoSize: 40
                            )
                            .padding(.bottom, 12)

                            if !model.phoneOtpMode {
                                selectedClubSummary
                            }

                            if model.formStep == .personal {
                                Text("Пожалуйста, вводите данные точно также, как они указаны в вашем паспорте. Иначе вы не сможете приобрести абонемент.")
                                    .font(FCTypography.bodySmall())
                                    .foregroundStyle(surface.opacity(0.9))
                                    .padding(.bottom, 8)
                            }

                            Group {
                                switch model.formStep {
                                case .personal:
                                    personalStepContent
                                case .account:
                                    accountStepContent
                                }
                            }

                            if let summary = model.validationSummary {
                                Text(summary)
                                    .id("validationSummary")
                                    .font(FCTypography.bodyMedium())
                                    .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(14)
                                    .background(Color(red: 0.36, green: 0.18, blue: 0).opacity(0.55))
                                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                                    .padding(.top, 16)
                            }

                            Spacer(minLength: 100)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                    }
                    .onChange(of: model.validationSummary) { _, summary in
                        guard summary != nil else { return }
                        withAnimation {
                            proxy.scrollTo("validationSummary", anchor: .center)
                        }
                    }
                }

                VStack(spacing: 8) {
                    Button {
                        Task {
                            let outcome = await model.onPrimaryFormAction(api: app.api)
                            switch outcome {
                            case .advanced:
                                break
                            case .needClubPick:
                                onNeedClubPick()
                            case .registered(let response):
                                onRegistered(response)
                            case .failed:
                                break
                            }
                        }
                    } label: {
                        Group {
                            if model.isLoading {
                                ProgressView().tint(Theme.primary)
                            } else {
                                Text(model.primaryButtonTitle.uppercased())
                                    .fontWeight(.bold)
                            }
                        }
                        .foregroundStyle(Theme.primary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(
                            surface.opacity(
                                model.isLoading || model.validationSummary != nil || !model.isCurrentStepValid
                                    ? 0.35
                                    : 0.92
                            )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius28, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    // Не блокируем при невалидной форме — иначе `applyValidationErrors` не вызывается.
                    .disabled(model.isLoading)

                    Button("Уже есть аккаунт? Войти", action: onBackToLogin)
                        .buttonStyle(.plain)
                        .font(FCTypography.titleSmall())
                        .foregroundStyle(surface)
                        .underline()
                        .padding(.top, 4)

                    if let error = model.error {
                        Text(error)
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(surface)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .background(
                    LinearGradient(colors: [Theme.primary.opacity(0.01), Theme.primaryVariant], startPoint: .top, endPoint: .bottom)
                )
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showBirthPicker) {
            RegisterDatePickerSheet(
                title: "Дата рождения",
                onPick: {
                    model.birthDateDisplay = RegisterDateParsing.displayFromDate($0)
                    model.birthDateError = nil
                    model.validationSummary = nil
                }
            )
        }
        .sheet(item: $legalPdfSheet) { asset in
            LegalPdfSheet(asset: asset) { legalPdfSheet = nil }
        }
        .alert("Внимание!", isPresented: $model.showUnder18Dialog) {
            Button("Хорошо") {
                Task {
                    let outcome = await model.dismissUnder18AndContinue(api: app.api)
                    switch outcome {
                    case .needClubPick:
                        onNeedClubPick()
                    case .registered(let response):
                        onRegistered(response)
                    default:
                        break
                    }
                }
            }
        } message: {
            Text("Лица младше 18 лет могут посещать наши спортзалы только в сопровождении родителя, опекуна или профессионального тренера.")
        }
    }

    private var registerStepIndicator: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ForEach(RegisterFormStep.allCases) { step in
                    Capsule()
                        .fill(step.rawValue <= model.formStep.rawValue ? surface : surface.opacity(0.28))
                        .frame(height: 4)
                }
            }
            Text("Шаг \(model.formStep.rawValue) из \(RegisterFormStep.last.rawValue)")
                .font(FCTypography.bodySmall())
                .foregroundStyle(surface.opacity(0.85))
                .frame(maxWidth: .infinity)
        }
    }

    private var birthDateBinding: Binding<String> {
        Binding(
            get: { model.birthDateDisplay },
            set: { model.birthDateDisplay = RegisterDateParsing.applyDotsMask($0) }
        )
    }

    @ViewBuilder
    private var personalStepContent: some View {
        orangeField(
            "Фамилия",
            text: $model.lastName,
            error: model.fieldErr(model.lastNameError),
            onEdit: { model.lastNameError = nil }
        )
        orangeField(
            "Имя",
            text: $model.firstName,
            error: model.fieldErr(model.firstNameError),
            onEdit: { model.firstNameError = nil }
        )
        orangeField("Отчество", text: $model.middleName, error: nil)

        HStack(alignment: .top, spacing: 8) {
            orangeField(
                "Дата рождения",
                text: birthDateBinding,
                error: model.fieldErr(model.birthDateError),
                placeholder: "дд.мм.гггг",
                keyboard: .numberPad,
                onEdit: { model.birthDateError = nil }
            )
            Button { showBirthPicker = true } label: {
                Image(systemName: "calendar")
                    .font(.title3)
                    .foregroundStyle(surface)
                    .frame(width: 44, height: 44)
                    .padding(.top, 10)
            }
            .buttonStyle(.plain)
        }

        if !model.phoneOtpMode {
            phoneField
            orangeField(
                "E-mail",
                text: $model.email,
                error: model.fieldErr(model.emailError),
                keyboard: .emailAddress,
                email: true,
                onEdit: { model.emailError = nil }
            )
        }

        genderPicker
    }

    @ViewBuilder
    private var accountStepContent: some View {
        Text("Придумайте пароль для входа в приложение")
            .font(FCTypography.bodyMedium())
            .foregroundStyle(surface.opacity(0.9))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.bottom, 4)

        orangeSecureField(
            "Пароль",
            text: $model.password,
            visible: $passwordVisible,
            error: model.fieldErr(model.passwordError),
            onEdit: { model.passwordError = nil }
        )
        orangeSecureField(
            "Подтвердите пароль",
            text: $model.confirmPassword,
            visible: $confirmPasswordVisible,
            error: model.fieldErr(model.confirmPasswordError),
            onEdit: { model.confirmPasswordError = nil }
        )

        orangeField("Промокод (необязательно)", text: $model.promoCode, error: nil)
            .padding(.top, 4)

        registerLegalParagraph
            .padding(.top, 16)

        if let err = model.fieldErr(model.legalTermsError) {
            Text(err)
                .font(FCTypography.bodySmall())
                .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
                .padding(.top, 4)
        }
    }

    private var selectedClubSummary: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let club = model.selectedClub {
                Text(club.name)
                    .font(FCTypography.titleSmall())
                    .fontWeight(.semibold)
                    .foregroundStyle(surface)
                Text(club.address)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface.opacity(0.88))
                Button("Изменить", action: onChangeClub)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface)
                    .underline()
            }
            if let err = model.fieldErr(model.clubError) {
                Text(err)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 8)
    }

    private var registrationTypePicker: some View {
        Menu {
            ForEach(RegistrationTypeOption.allCases) { opt in
                Button(opt.label) { model.registrationType = opt }
            }
        } label: {
            HStack {
                Text(model.registrationType.label)
                    .foregroundStyle(Theme.loginBackground)
                Spacer()
                Image(systemName: "chevron.down")
                    .foregroundStyle(Theme.loginBackground.opacity(0.7))
            }
            .font(FCTypography.bodyLarge())
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
        }
        .overlay(alignment: .topLeading) {
            Text("Тип регистрации")
                .font(FCTypography.labelSmall())
                .foregroundStyle(surface.opacity(0.78))
                .padding(.horizontal, 12)
                .padding(.vertical, 2)
                .background(Theme.primary)
                .offset(x: 8, y: -8)
        }
    }

    private var phoneField: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                RussianPhoneTextField(
                    nationalDigits: $model.phoneNationalDigits,
                    textColor: UIColor(Theme.loginBackground),
                    font: UIFont.preferredFont(forTextStyle: .body),
                    placeholder: "+7 (999) 123-45-67"
                )
                .frame(maxWidth: .infinity)
                .frame(height: 22)
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))

                Image(systemName: "keyboard")
                    .foregroundStyle(surface.opacity(0.7))
            }
            if let err = model.fieldErr(model.phoneError) {
                Text(err)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
            }
        }
        .overlay(alignment: .topLeading) {
            Text("Номер телефона")
                .font(FCTypography.labelSmall())
                .foregroundStyle(surface.opacity(0.78))
                .padding(.horizontal, 8)
                .padding(.vertical, 2)
                .background(Theme.primary)
                .offset(x: 8, y: -8)
        }
        .padding(.top, 10)
        .onChange(of: model.phoneNationalDigits) { _, _ in
            model.phoneError = nil
            model.validationSummary = nil
        }
    }

    private var phoneFieldLocked: some View {
        VStack(alignment: .leading, spacing: 4) {
            TextField(
                "",
                text: .constant(formatRussianPhoneMask(model.phoneNationalDigits)),
                prompt: Text("+7 (999) 123-45-67").foregroundStyle(Theme.loginBackground.opacity(0.45))
            )
            .disabled(true)
            .foregroundStyle(Theme.loginBackground.opacity(0.75))
            .font(FCTypography.bodyLarge())
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(surface.opacity(0.85))
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
        }
        .overlay(alignment: .topLeading) {
            Text("Номер телефона")
                .font(FCTypography.labelSmall())
                .foregroundStyle(surface.opacity(0.78))
                .padding(.horizontal, 8)
                .padding(.vertical, 2)
                .background(Theme.primary)
                .offset(x: 8, y: -8)
        }
        .padding(.top, 10)
    }

    private var genderPicker: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Пол")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(surface.opacity(0.85))
                .padding(.top, 8)

            HStack(spacing: 16) {
                ForEach(GenderOption.allCases) { g in
                    Button {
                        model.gender = g
                        model.genderError = nil
                        model.validationSummary = nil
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: model.gender == g ? "largecircle.fill.circle" : "circle")
                                .foregroundStyle(surface)
                            Text(g.label)
                                .foregroundStyle(surface)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            if let err = model.fieldErr(model.genderError) {
                Text(err)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
            }
        }
    }

    private var registerLegalParagraph: some View {
        let privacy = "legalpdf://\(LegalPdfAsset.privacyPolicy.rawValue)"
        let ua = "legalpdf://\(LegalPdfAsset.userAgreement.rawValue)"
        let md = "Нажимая «Зарегистрироваться», я подтверждаю, что ознакомился с [Политикой конфиденциальности](\(privacy)) и принимаю условия [Пользовательского соглашения](\(ua))."
        return Text(fcHighlightedLegalLinks(markdown: md, linkColor: surface))
            .font(FCTypography.bodySmall())
            .multilineTextAlignment(.leading)
            .tint(surface)
            .foregroundStyle(surface.opacity(0.9))
            .environment(\.openURL, OpenURLAction { url in
                if url.scheme == "legalpdf", let asset = LegalPdfAsset.fromLink(url.host) {
                    legalPdfSheet = asset
                } else {
                    UIApplication.shared.open(url)
                }
                return .handled
            })
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func orangeField(
        _ label: String,
        text: Binding<String>,
        error: String?,
        placeholder: String? = nil,
        keyboard: UIKeyboardType = .default,
        email: Bool = false,
        onEdit: (() -> Void)? = nil
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            TextField(
                "",
                text: text,
                prompt: Text(placeholder ?? label).foregroundStyle(Theme.loginBackground.opacity(0.6))
            )
            .keyboardType(keyboard)
            .textInputAutocapitalization(email ? .never : .words)
            .autocorrectionDisabled(email)
            .foregroundStyle(Theme.loginBackground)
            .font(FCTypography.bodyLarge())
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous)
                    .stroke(error != nil ? Color(red: 0.85, green: 0.35, blue: 0.2) : Color.clear, lineWidth: 2)
            )

            if let error {
                Text(error)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
            }
        }
        .padding(.top, 10)
        .onChange(of: text.wrappedValue) { _, _ in
            model.validationSummary = nil
            onEdit?()
        }
    }

    private func orangeSecureField(
        _ label: String,
        text: Binding<String>,
        visible: Binding<Bool>,
        error: String?,
        onEdit: (() -> Void)? = nil
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Group {
                    if visible.wrappedValue {
                        TextField("", text: text, prompt: Text(label).foregroundStyle(Theme.loginBackground.opacity(0.6)))
                    } else {
                        SecureField("", text: text, prompt: Text(label).foregroundStyle(Theme.loginBackground.opacity(0.6)))
                    }
                }
                .textInputAutocapitalization(.never)
                .foregroundStyle(Theme.loginBackground)

                Button { visible.wrappedValue.toggle() } label: {
                    Image(systemName: visible.wrappedValue ? "eye.slash.fill" : "eye.fill")
                        .foregroundStyle(Theme.loginBackground.opacity(0.75))
                }
                .buttonStyle(.plain)
            }
            .font(FCTypography.bodyLarge())
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous)
                    .stroke(error != nil ? Color(red: 0.85, green: 0.35, blue: 0.2) : Color.clear, lineWidth: 2)
            )

            if let error {
                Text(error)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Color(red: 1, green: 0.88, blue: 0.7))
            }
        }
        .padding(.top, 10)
        .onChange(of: text.wrappedValue) { _, _ in
            model.validationSummary = nil
            onEdit?()
        }
    }
}

// MARK: - Паспорт (`RegisterPassportScreen.kt`)

private struct RegisterPassportView: View {
    @ObservedObject var model: RegisterFlowModel
    let onBack: () -> Void

    @State private var showIssuePicker = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(Theme.loginOnBackground)
                }
                Text("Мой паспорт")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.loginOnBackground)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Theme.primary)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let err = model.passportError {
                        Text(err)
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.error)
                    }

                    HStack(spacing: 8) {
                        passportField("Серия паспорта", text: model.passportBinding(\.series), limit: 4, digitsOnly: true)
                        passportField("Номер паспорта", text: model.passportBinding(\.number), limit: 6, digitsOnly: true)
                    }

                    passportField("Кем выдан", text: model.passportBinding(\.issuedBy), limit: 300, multiline: true)

                    HStack(spacing: 8) {
                        passportField(
                            "Выдан",
                            text: Binding(
                                get: { model.passport.issuedDateDisplay },
                                set: {
                                    model.passport.issuedDateDisplay = RegisterDateParsing.applyDotsMask($0)
                                    model.passportError = nil
                                }
                            ),
                            placeholder: "дд.мм.гггг"
                        )
                        Button { showIssuePicker = true } label: {
                            Image(systemName: "calendar")
                                .font(.title3)
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                    }

                    Text("Адрес прописки")
                        .font(FCTypography.titleMedium())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.top, 12)

                    passportField("Регион/область", text: model.passportBinding(\.region))
                    passportField("Город", text: model.passportBinding(\.city))
                    passportField("Улица и дом", text: model.passportBinding(\.streetHouse))

                    Button {
                        if model.validatePassportDraft() {
                            onBack()
                        }
                    } label: {
                        Text("СОХРАНИТЬ")
                            .fontWeight(.bold)
                            .foregroundStyle(Theme.loginOnBackground)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(Color.black)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 24)
                }
                .padding(20)
            }
            .background(Theme.background)
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showIssuePicker) {
            RegisterDatePickerSheet(
                title: "Дата выдачи",
                onPick: {
                    var draft = model.passport
                    draft.issuedDateDisplay = RegisterDateParsing.displayFromDate($0)
                    model.passport = draft
                    model.passportError = nil
                }
            )
        }
    }

    private func passportField(
        _ label: String,
        text: Binding<String>,
        limit: Int? = nil,
        placeholder: String? = nil,
        multiline: Bool = false,
        digitsOnly: Bool = false
    ) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Group {
                if multiline {
                    TextField(
                        "",
                        text: Binding(
                            get: { text.wrappedValue },
                            set: { new in
                                var v = new
                                if let limit { v = String(v.prefix(limit)) }
                                text.wrappedValue = v
                            }
                        ),
                        prompt: Text(label).foregroundStyle(Theme.onSurfaceVariant.opacity(0.85)),
                        axis: .vertical
                    )
                    .lineLimit(2...4)
                } else {
                    TextField(
                        "",
                        text: Binding(
                            get: { text.wrappedValue },
                            set: { new in
                                var v = new
                                if digitsOnly {
                                    v = v.filter { $0.isASCII && $0.isNumber }
                                }
                                if let limit {
                                    v = String(v.prefix(limit))
                                }
                                text.wrappedValue = v
                            }
                        ),
                        prompt: Text(placeholder ?? label).foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                    )
                    .keyboardType(digitsOnly ? .numberPad : .default)
                }
            }
            .foregroundStyle(Theme.onBackground)
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous)
                    .stroke(Theme.outlineVariant, lineWidth: 1)
            )

            if let limit {
                Text("\(text.wrappedValue.count)/\(limit)")
                    .font(FCTypography.labelSmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
    }
}

private struct RegisterDatePickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    let onPick: (Date) -> Void

    @State private var date = Date()

    var body: some View {
        NavigationStack {
            VStack {
                DatePicker(title, selection: $date, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .environment(\.locale, Locale(identifier: "ru_RU"))
                    .padding()
                Spacer()
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("OK") {
                        onPick(date)
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
