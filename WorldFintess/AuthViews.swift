import SwiftUI
import UIKit

// MARK: - Login (`LoginScreen.kt`) — phone OTP primary; email collapsed. Без Sber / биометрии.

private enum LoginOtpStep {
    case phone
    case code
}

struct LoginView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var onRegister: () -> Void
    var onPhoneRegister: () -> Void = {}

    @State private var otpStep: LoginOtpStep = .phone
    @State private var phoneNationalDigits = ""
    /// E.164 номер, на который ушёл OTP — verify не зависит от маски поля.
    @State private var otpPhoneE164 = ""
    @State private var phoneError: String?
    @State private var otpCode = ""
    @State private var otpError: String?
    @State private var otpInstruction: String?
    @State private var otpDevCode: String?
    @State private var resendSecondsLeft = 0
    @State private var isVerifyingOtp = false
    @State private var showEmailLogin = false

    @State private var email = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var emailError: String?
    @State private var passwordError: String?
    @State private var validationSummary: String?
    @State private var loginHintCode: String?
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var legalPdfSheet: LegalPdfAsset?
    @State private var brandName = AppConfiguration.appDisplayName
    @State private var supportEmail: String?
    @State private var supportPhone: String?
    @FocusState private var focusedField: LoginField?

    private let surface = Theme.loginOnBackground
    private let accent = Theme.loginBackground

    private enum LoginField: Hashable {
        case phone, otp, email, password
    }

    private var hasCompletedRegistration: Bool {
        AuthFlowStore.hasCompletedRegistration
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("ВХОД")
                    .font(FCTypography.labelLarge())
                    .tracking(2)
                    .fontWeight(.semibold)
                    .foregroundStyle(surface.opacity(0.9))

                BrandHeader(
                    brandName: brandName,
                    subtitle: hasCompletedRegistration ? "Вход в аккаунт" : "Вход или регистрация",
                    textColor: surface,
                    logoSize: 48
                )
                .padding(.top, 12)

                Text("Вход по номеру телефона")
                    .font(FCTypography.bodyMedium())
                    .multilineTextAlignment(.center)
                    .foregroundStyle(surface.opacity(0.92))
                    .frame(maxWidth: .infinity)
                    .padding(.top, 16)

                if otpStep == .phone {
                    phoneStepContent
                } else {
                    otpCodeStepContent
                }

                emailCollapseSection
                    .padding(.top, 20)

                if let errorMessage {
                    Text(errorMessage)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(surface)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                }

                Text(hasCompletedRegistration ? "Нужен другой аккаунт?" : "Впервые в \(brandName)?")
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(surface.opacity(0.88))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 20)

                Button("Зарегистрироваться") {
                    errorMessage = nil
                    validationSummary = nil
                    onRegister()
                }
                .buttonStyle(.plain)
                .font(FCTypography.titleSmall())
                .fontWeight(.semibold)
                .foregroundStyle(surface)
                .underline()
                .padding(.vertical, 8)

                Spacer(minLength: 24)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 20)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.loginBackground.ignoresSafeArea())
        .sheet(item: $legalPdfSheet) { asset in
            LegalPdfSheet(asset: asset) { legalPdfSheet = nil }
        }
        .task {
            if let info = try? await app.api.getClubInfo() {
                brandName = info.resolvedBrandName
                supportEmail = info.email.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                supportPhone = info.phone.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            }
        }
        .task(id: resendSecondsLeft) {
            guard resendSecondsLeft > 0 else { return }
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            if resendSecondsLeft > 0 { resendSecondsLeft -= 1 }
        }
    }

    // MARK: Phone OTP (SMS на номер)

    private var phoneStepContent: some View {
        VStack(spacing: 0) {
            loginPhoneField
                .padding(.top, 20)

            Text("Код подтверждения придёт в SMS на этот номер")
                .font(FCTypography.bodySmall())
                .foregroundStyle(surface.opacity(0.88))
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 12)

            Button {
                Task { await requestOtp() }
            } label: {
                Group {
                    if isLoading {
                        ProgressView().tint(accent)
                    } else {
                        Text("Получить код для входа")
                            .fontWeight(.bold)
                    }
                }
                .font(FCTypography.bodyLarge())
                .foregroundStyle(accent)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(surface.opacity(isLoading ? 0.5 : 1))
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(isLoading)
            .padding(.top, 16)

            Button("Связаться с поддержкой") {
                openSupport()
            }
            .buttonStyle(.plain)
            .font(FCTypography.titleSmall())
            .foregroundStyle(surface.opacity(0.92))
            .padding(.top, 8)
        }
    }

    private var otpCodeStepContent: some View {
        VStack(spacing: 0) {
            Text("Код из SMS")
                .font(FCTypography.titleSmall())
                .fontWeight(.semibold)
                .foregroundStyle(surface)
                .padding(.top, 20)

            if let otpInstruction {
                Text(otpInstruction)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .padding(.top, 6)
            } else {
                Text("Введите код из SMS, отправленного на ваш номер")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .padding(.top, 6)
            }
            if let otpDevCode {
                Text("Debug: \(otpDevCode)")
                    .font(FCTypography.labelSmall())
                    .foregroundStyle(surface.opacity(0.7))
            }

            loginOtpField
                .padding(.top, 12)

            Button {
                Task { await verifyOtp() }
            } label: {
                Group {
                    if isLoading || isVerifyingOtp {
                        ProgressView().tint(accent)
                    } else {
                        Text("Подтвердить")
                            .fontWeight(.bold)
                    }
                }
                .font(FCTypography.bodyLarge())
                .foregroundStyle(accent)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(surface.opacity((isLoading || isVerifyingOtp || otpCode.count < 6) ? 0.5 : 1))
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(isLoading || isVerifyingOtp || otpCode.count < 6)
            .padding(.top, 16)

            if resendSecondsLeft > 0 {
                Text("Отправить повторно (\(resendSecondsLeft))")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface.opacity(0.7))
                    .padding(.top, 12)
            } else {
                Button("Отправить повторно") {
                    Task { await requestOtp() }
                }
                .buttonStyle(.plain)
                .font(FCTypography.titleSmall())
                .foregroundStyle(surface)
                .padding(.top, 12)
                .disabled(isLoading)
            }

            Button("Изменить номер") {
                otpStep = .phone
                otpCode = ""
                otpError = nil
                otpPhoneE164 = ""
                isVerifyingOtp = false
            }
            .buttonStyle(.plain)
            .font(FCTypography.titleSmall())
            .foregroundStyle(surface.opacity(0.92))
            .padding(.top, 8)
        }
    }

    private var loginPhoneField: some View {
        VStack(alignment: .leading, spacing: 4) {
            RussianPhoneTextField(
                nationalDigits: $phoneNationalDigits,
                textColor: UIColor(accent),
                font: UIFont.preferredFont(forTextStyle: .body),
                placeholder: "Телефон",
                onSubmit: { Task { await requestOtp() } }
            )
            .frame(maxWidth: .infinity)
            .frame(height: 22)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            .onChange(of: phoneNationalDigits) { _, _ in phoneError = nil }

            if let phoneError {
                Text(phoneError)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface)
            }
        }
    }

    private var loginOtpField: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Как Android: обычный TextField (не SecureField) — иначе код не вводится/не автозаполняется.
            TextField(
                "",
                text: $otpCode,
                prompt: Text("Код").foregroundStyle(accent.opacity(0.6))
            )
            .keyboardType(.numberPad)
            .textContentType(.oneTimeCode)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .foregroundStyle(accent)
            .focused($focusedField, equals: .otp)
            .disabled(isLoading || isVerifyingOtp)
            .onChange(of: otpCode) { _, newValue in
                let digits = String(newValue.filter(\.isNumber).prefix(6))
                if digits != newValue {
                    otpCode = digits
                    return
                }
                otpError = nil
                if digits.count == 6, !isVerifyingOtp, !isLoading {
                    Task { await verifyOtp() }
                }
            }
            .font(FCTypography.bodyLarge())
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))

            if let otpError {
                Text(otpError)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface)
            }
        }
    }

    // MARK: Email (collapsed)

    private var emailCollapseSection: some View {
        VStack(spacing: 0) {
            Button {
                withAnimation { showEmailLogin.toggle() }
            } label: {
                Text(showEmailLogin ? "Скрыть вход по почте" : "Войти по почте")
                    .font(FCTypography.titleSmall())
                    .foregroundStyle(surface)
                    .underline()
            }
            .buttonStyle(.plain)

            if showEmailLogin {
                loginCredentialField(
                    label: "Email",
                    text: $email,
                    error: emailError,
                    icon: "envelope.fill",
                    keyboard: .emailAddress,
                    email: true,
                    field: .email,
                    submitLabel: .next
                )
                .padding(.top, 16)
                .onChange(of: email) { _, _ in
                    emailError = nil
                    validationSummary = nil
                    loginHintCode = nil
                }

                loginCredentialField(
                    label: "Пароль",
                    text: $password,
                    error: passwordError,
                    icon: "lock.fill",
                    secure: true,
                    field: .password,
                    submitLabel: .go
                )
                .padding(.top, 12)
                .onChange(of: password) { _, _ in
                    passwordError = nil
                    validationSummary = nil
                    loginHintCode = nil
                }

                Button {
                    Task { await loginWithEmailPassword() }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView().tint(accent)
                        } else {
                            Text("Войти")
                                .fontWeight(.bold)
                        }
                    }
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(accent)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(surface.opacity(isLoading ? 0.5 : 1))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(isLoading)
                .padding(.top, 16)

                if let validationSummary {
                    Text(validationSummary)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(surface)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(surface.opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                        .padding(.top, 12)
                }

                loginLegalParagraph
                    .padding(.top, 16)
            }
        }
    }

    // MARK: Actions

    private func requestOtp() async {
        let phone = phoneForApi(phoneNationalDigits)
        guard !phone.isEmpty else {
            phoneError = "Введите полный номер телефона"
            return
        }
        isLoading = true
        errorMessage = nil
        phoneError = nil
        defer { isLoading = false }
        do {
            let res = try await app.api.requestOtp(phone: phone, channel: "sms")
            guard res.ok || res.error == nil else {
                errorMessage = res.error ?? "Не удалось отправить код"
                return
            }
            otpPhoneE164 = phone
            otpStep = .code
            otpInstruction = res.instruction ?? "Код отправлен в SMS на ваш номер телефона"
            otpDevCode = res.devCode
            resendSecondsLeft = max(20, res.resendAfterSec)
            otpCode = ""
            otpError = nil
            focusedField = .otp
        } catch let e as FitnessAPIError {
            errorMessage = otpSendFailureMessage(e)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func otpSendFailureMessage(_ error: FitnessAPIError) -> String {
        if case .http(let code, let body) = error {
            let mapped = FitnessAPIError.userMessage(from: body, httpCode: code)
            if code == 404 || code == 405 {
                return "На сервере нет ручки отправки кода (HTTP \(code)). Пока войдите по почте."
            }
            if let body, body.localizedCaseInsensitiveContains("<html") || body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return "Сервер вернул ошибку HTTP \(code) без JSON. Пока войдите по почте."
            }
            if mapped.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return "Не удалось отправить SMS с кодом. Войдите по почте."
            }
            return mapped
        }
        return error.localizedDescription
    }

    private func verifyOtp() async {
        let phone = otpPhoneE164.isEmpty ? phoneForApi(phoneNationalDigits) : otpPhoneE164
        let code = String(otpCode.filter(\.isNumber).prefix(6))
        guard code.count == 6 else { return }
        guard !phone.isEmpty else {
            otpError = "Сначала укажите номер телефона"
            return
        }
        guard !isVerifyingOtp else { return }
        isVerifyingOtp = true
        isLoading = true
        otpError = nil
        defer {
            isVerifyingOtp = false
            isLoading = false
        }
        do {
            let body = try await app.api.verifyOtp(phone: phone, code: code)
            // Как Android AuthRepository.verifyOtp + LoginViewModel:
            if body.registrationRequired, let ticket = body.otpTicket?.trimmingCharacters(in: .whitespacesAndNewlines), !ticket.isEmpty {
                AuthFlowStore.saveOtpRegistration(ticket: ticket, phone: body.phone ?? phone)
                onPhoneRegister()
                return
            }
            if let user = body.user {
                let token = body.token?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let refresh = body.refreshToken?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if !token.isEmpty, !refresh.isEmpty {
                    app.applyAuth(AuthResponse(token: token, refreshToken: refresh, user: user))
                    return
                }
            }
            otpError = "Не удалось войти"
            otpCode = ""
        } catch {
            otpError = error.localizedDescription
            otpCode = ""
        }
    }

    private func openSupport() {
        if let email = supportEmail, let u = URL(string: "mailto:\(email)") {
            UIApplication.shared.open(u)
        } else if let phone = supportPhone {
            dialPhoneRaw(phone)
        }
    }

    private func loginCredentialField(
        label: String,
        text: Binding<String>,
        error: String?,
        icon: String,
        keyboard: UIKeyboardType = .default,
        email: Bool = false,
        secure: Bool = false,
        field: LoginField,
        submitLabel: SubmitLabel
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .foregroundStyle(accent)
                    .frame(width: 22)

                Group {
                    if secure {
                        if passwordVisible {
                            TextField("", text: text, prompt: Text(label).foregroundStyle(accent.opacity(0.6)))
                        } else {
                            SecureField("", text: text, prompt: Text(label).foregroundStyle(accent.opacity(0.6)))
                        }
                    } else {
                        TextField("", text: text, prompt: Text(label).foregroundStyle(accent.opacity(0.6)))
                    }
                }
                .textContentType(secure ? .password : (email ? .emailAddress : nil))
                .keyboardType(keyboard)
                .textInputAutocapitalization(email ? .never : .none)
                .autocorrectionDisabled(email)
                .foregroundStyle(accent)
                .focused($focusedField, equals: field)
                .submitLabel(submitLabel)
                .onSubmit {
                    switch field {
                    case .email:
                        focusedField = .password
                    case .password:
                        focusedField = nil
                        Task { await loginWithEmailPassword() }
                    default:
                        break
                    }
                }

                if secure {
                    Button { passwordVisible.toggle() } label: {
                        Image(systemName: passwordVisible ? "eye.slash.fill" : "eye.fill")
                            .foregroundStyle(accent)
                    }
                    .buttonStyle(.plain)
                }
            }
            .font(FCTypography.bodyLarge())
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))

            if let error {
                Text(error)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface)
                    .padding(.leading, 4)
            }
        }
    }

    private func loginWithEmailPassword() async {
        errorMessage = nil
        emailError = nil
        passwordError = nil
        validationSummary = nil
        loginHintCode = nil

        var hasError = false
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmedEmail.isEmpty {
            emailError = "Введите email"
            hasError = true
        } else if !RegisterValidation.isValidEmail(trimmedEmail) {
            emailError = "Неверный формат email"
            hasError = true
        }

        if !password.isEmpty && password.count < 6 {
            passwordError = "Пароль не менее 6 символов"
            hasError = true
        }

        if hasError {
            validationSummary = emailError ?? passwordError
            return
        }

        if password.isEmpty {
            await requestLoginHint(email: trimmedEmail)
            return
        }

        isLoading = true
        defer { isLoading = false }

        do {
            let response = try await app.api.login(email: trimmedEmail, password: password)
            app.applyAuth(response)
        } catch let e as FitnessAPIError {
            applyAuthFailure(e)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func requestLoginHint(email: String) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let hint = try await app.api.loginHint(email: email)
            applyLoginHint(message: humanizeLoginHint(hint.message, code: hint.code), code: hint.code)
        } catch let e as FitnessAPIError {
            if e.apiCode == "password_not_set" {
                applyLoginHint(message: Self.passwordNotSetHintMessage(), code: "password_not_set")
            } else {
                do {
                    _ = try await app.api.login(email: email, password: "")
                    applyLoginHint(message: "Введите пароль", code: "password_required")
                } catch let fallback as FitnessAPIError {
                    applyAuthFailure(fallback)
                } catch {
                    passwordError = "Введите пароль"
                }
            }
        } catch {
            passwordError = "Введите пароль"
        }
    }

    /// Как Android `LoginViewModel`: почти любая ошибка логина → «Введите пароль».
    private func applyAuthFailure(_ error: FitnessAPIError) {
        let code = error.apiCode ?? ""
        if code == "password_not_set" {
            applyLoginHint(message: Self.passwordNotSetHintMessage(), code: code)
        } else {
            applyLoginHint(message: "Введите пароль", code: "password_required")
        }
    }

    private func humanizeLoginHint(_ message: String, code: String) -> String {
        switch code.trimmingCharacters(in: .whitespacesAndNewlines) {
        case "password_not_set":
            return Self.passwordNotSetHintMessage()
        case "password_required", "missing_password":
            return "Введите пароль"
        case "email_unknown":
            let t = message.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty
                ? "Введите пароль. Если аккаунта ещё нет — пройдите регистрацию."
                : t
        default:
            let t = message.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? "Введите пароль" : t
        }
    }

    private static func passwordNotSetHintMessage() -> String {
        "Аккаунт с этим email уже есть в клубе, но пароль ещё не задан.\n\nПройдите регистрацию с этим email и придумайте пароль — вход откроется сразу."
    }

    private func applyLoginHint(message: String, code: String) {
        switch code {
        case "password_not_set":
            validationSummary = message
            loginHintCode = code
            passwordError = nil
            errorMessage = nil
        case "email_unknown":
            validationSummary = "Аккаунт не найден. Зарегистрируйтесь."
            loginHintCode = code
            passwordError = nil
            errorMessage = nil
        default:
            validationSummary = nil
            loginHintCode = code.isEmpty ? nil : code
            passwordError = message.isEmpty ? "Введите пароль" : message
            errorMessage = nil
        }
    }

    private var loginLegalParagraph: some View {
        let ua = "legalpdf://\(LegalPdfAsset.userAgreement.rawValue)"
        let privacy = "legalpdf://\(LegalPdfAsset.privacyPolicy.rawValue)"
        let md = "Продолжая использовать приложение, Вы принимаете условия [Пользовательского соглашения](\(ua)) и подтверждаете ознакомление с [Политикой конфиденциальности](\(privacy))"
        return Text(fcHighlightedLegalLinks(markdown: md, linkColor: surface))
            .font(FCTypography.bodySmall())
            .multilineTextAlignment(.center)
            .tint(surface)
            .foregroundStyle(surface.opacity(0.88))
            .environment(\.openURL, OpenURLAction { url in
                if url.scheme == "legalpdf", let asset = LegalPdfAsset.fromLink(url.host) {
                    legalPdfSheet = asset
                } else {
                    UIApplication.shared.open(url)
                }
                return .handled
            })
            .frame(maxWidth: .infinity)
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

/// Markdown-параграф с подчёркнутыми, выделенными ссылками (видно, что кликабельно).
func fcHighlightedLegalLinks(markdown: String, linkColor: Color) -> AttributedString {
    var astr = (try? AttributedString(markdown: markdown, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)))
        ?? AttributedString(markdown)
    for run in astr.runs where run.link != nil {
        astr[run.range].underlineStyle = .single
        astr[run.range].foregroundColor = linkColor
        astr[run.range].font = FCTypography.titleSmall().weight(.semibold)
    }
    return astr
}
