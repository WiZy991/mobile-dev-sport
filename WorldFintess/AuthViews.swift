import LocalAuthentication
import SwiftUI
import UIKit

// MARK: - Login (`LoginScreen.kt`)

struct LoginView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var onRegister: () -> Void

    @State private var phoneNationalDigits = ""
    @State private var credentialsStep = false
    @State private var email = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var isLoading = false
    @State private var phoneError: String?
    @State private var emailError: String?
    @State private var passwordError: String?
    @State private var errorMessage: String?
    @State private var sberLoading = false

    @FocusState private var focusedPhone: Bool
    @FocusState private var focusedEmail: Bool
    @FocusState private var focusedPassword: Bool

    private let surface = Theme.loginOnBackground

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("ВХОД")
                    .font(FCTypography.labelLarge())
                    .tracking(2)
                    .fontWeight(.semibold)
                    .foregroundStyle(surface.opacity(0.9))

                Text("GYMroom")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(surface)
                    .padding(.top, 8)

                Text("Какой ваш номер телефона?")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(surface)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 28)

                Text("Вы уже являетесь клиентом клуба? Тогда просто подтвердите ваш номер телефона. Если нет, то пройдите регистрацию.")
                    .font(FCTypography.bodyMedium())
                    .multilineTextAlignment(.center)
                    .foregroundStyle(surface.opacity(0.92))
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)

                phoneRow
                    .padding(.top, 32)

                if credentialsStep {
                    credentialBlock
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }

                Button("Регистрация") {
                    onRegister()
                }
                .buttonStyle(.plain)
                .font(FCTypography.titleSmall())
                .foregroundStyle(surface)
                .underline()
                .padding(.vertical, 8)
                .padding(.top, 28)
                .frame(maxWidth: .infinity)

                loginLegalParagraph
                    .padding(.top, 24)

                primaryActionButton
                    .padding(.top, 20)

                biometricBlock
                    .padding(.top, 20)

                sberLoginButton
                    .padding(.top, 16)

                if let errorMessage {
                    Text(errorMessage)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(surface)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 12)
                }

                Spacer(minLength: 24)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 20)
            .animation(.easeInOut(duration: 0.25), value: credentialsStep)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.loginBackground.ignoresSafeArea())
    }

    private var phoneRow: some View {
        HStack(alignment: .bottom, spacing: 0) {
            Text("🇷🇺")
                .font(.system(size: 22))
                .padding(.trailing, 8)
                .padding(.bottom, 10)

            VStack(alignment: .leading, spacing: 6) {
                TextField(
                    "",
                    text: Binding(
                        get: { formatRussianPhoneMask(phoneNationalDigits) },
                        set: { phoneNationalDigits = normalizeRussianNationalDigits($0); phoneError = nil }
                    ),
                    prompt: Text("+7 (___) ___-__-__")
                        .font(FCTypography.bodyLarge())
                        .foregroundStyle(surface.opacity(0.45))
                )
                .font(FCTypography.bodyLarge())
                .foregroundStyle(surface)
                .tint(surface)
                .keyboardType(.phonePad)
                .focused($focusedPhone)

                Rectangle()
                    .fill(surface.opacity(phoneUnderlineOpacity))
                    .frame(height: 1)

                if let phoneError {
                    Text(phoneError)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(surface)
                }
            }
        }
    }

    private var phoneUnderlineOpacity: Double {
        if phoneError != nil { return 1 }
        return focusedPhone ? 1 : 0.55
    }

    private var credentialBlock: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Вход по email и паролю, указанным при регистрации.")
                .font(FCTypography.bodySmall())
                .foregroundStyle(surface.opacity(0.88))
                .padding(.top, 8)
                .padding(.bottom, 8)

            VStack(alignment: .leading, spacing: 6) {
                Text("E-mail")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(surface.opacity(0.85))
                TextField("", text: $email, axis: .vertical)
                    .lineLimit(1)
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(surface)
                    .tint(surface)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focusedEmail)
                    .submitLabel(.next)
                    .onChange(of: email) { _, _ in emailError = nil }

                Rectangle()
                    .fill(surface.opacity(emailUnderlineOpacity))
                    .frame(height: 1)
            }
            if let emailError {
                credentialError(emailError)
            }

            underlinePasswordRow
                .padding(.top, 8)

            if let passwordError {
                credentialError(passwordError)
            }
        }
    }

    private func credentialError(_ s: String) -> some View {
        Text(s)
            .font(FCTypography.bodySmall())
            .foregroundStyle(surface)
            .padding(.top, 4)
    }

    private var emailUnderlineOpacity: Double {
        if emailError != nil { return 1 }
        return focusedEmail ? 1 : 0.55
    }

    private var passwordUnderlineOpacity: Double {
        if passwordError != nil { return 1 }
        return focusedPassword ? 1 : 0.55
    }

    private var underlinePasswordRow: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Пароль")
                .font(FCTypography.bodySmall())
                .foregroundStyle(surface.opacity(0.85))
            HStack {
                Group {
                    if passwordVisible {
                        TextField("", text: $password, axis: .vertical)
                            .lineLimit(1)
                    } else {
                        SecureField("", text: $password)
                    }
                }
                .font(FCTypography.bodyLarge())
                .foregroundStyle(surface)
                .tint(surface)
                .focused($focusedPassword)
                .textContentType(.password)
                .submitLabel(.done)
                .onChange(of: password) { _, _ in passwordError = nil }

                Button {
                    passwordVisible.toggle()
                } label: {
                    Image(systemName: passwordVisible ? "eye.slash.fill" : "eye.fill")
                        .foregroundStyle(surface)
                }
                .buttonStyle(.plain)
                .padding(.leading, 4)
            }

            Rectangle()
                .fill(surface.opacity(passwordUnderlineOpacity))
                .frame(height: 1)
        }
    }

    private var loginLegalParagraph: some View {
        let terms = AppConfiguration.termsURL.absoluteString
        let privacy = AppConfiguration.privacyURL.absoluteString
        let md = "Продолжая, вы принимаете [Правила использования](\(terms)), [Политику конфиденциальности](\(privacy)) и соглашаетесь на обработку персональных данных"
        let astr = (try? AttributedString(markdown: md, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)))
            ?? AttributedString(md)
        return Text(astr)
            .font(FCTypography.bodySmall())
            .multilineTextAlignment(.center)
            .tint(surface)
            .foregroundStyle(surface.opacity(0.88))
            .environment(\.openURL, OpenURLAction { url in
                UIApplication.shared.open(url)
                return .handled
            })
            .frame(maxWidth: .infinity)
    }

    private var primaryActionButton: some View {
        Button {
            if !credentialsStep {
                continueFromPhone()
            } else {
                loginWithPassword()
            }
        } label: {
            Group {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: surface))
                } else {
                    Text(credentialsStep ? "ВОЙТИ" : "ПРОДОЛЖИТЬ")
                        .fontWeight(.bold)
                }
            }
            .foregroundStyle(surface)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(Theme.loginButton.opacity(isLoading ? 0.65 : 1))
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isLoading || sberLoading)
    }

    private func continueFromPhone() {
        focusedPhone = false
        phoneError = nil
        errorMessage = nil
        guard normalizeRussianNationalDigits(phoneNationalDigits).count == 10 else {
            phoneError = "Введите номер полностью"
            return
        }
        credentialsStep = true
    }

    private func loginWithPassword() {
        focusedEmail = false
        focusedPassword = false
        phoneError = nil
        emailError = nil
        passwordError = nil
        errorMessage = nil

        let em = email.trimmingCharacters(in: .whitespacesAndNewlines)

        var eErr: String?
        var pErr: String?

        if em.isEmpty {
            eErr = "Введите email"
        } else if !isValidRuEmail(em) {
            eErr = "Неверный формат email"
        }

        if password.isEmpty {
            pErr = "Введите пароль"
        } else if password.count < 6 {
            pErr = "Пароль не менее 6 символов"
        }

        if let eErr { emailError = eErr }
        if let pErr { passwordError = pErr }

        guard eErr == nil, pErr == nil else { return }

        isLoading = true
        Task {
            defer { isLoading = false }
            do {
                let r = try await app.api.login(email: em, password: password)
                app.applyAuth(r)
            } catch let e as FitnessAPIError {
                errorMessage = e.localizedDescription
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private var biometricBlock: some View {
        let configured = BiometricCredentialStore.hasSavedLogin
        let hwReady = BiometryLoginUX.canUseBiometrics

        return VStack(spacing: 6) {
            Button {
                biometricTap(configured: configured)
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "touchid")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(surface)
                    Text("Войти по отпечатку пальца")
                        .font(FCTypography.bodyLarge())
                        .fontWeight(.semibold)
                        .foregroundStyle(surface)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.radius14)
                        .stroke(surface.opacity(isLoading ? 0.35 : 0.92), lineWidth: 1)
                )
            }
            .buttonStyle(.plain)
            .disabled(isLoading || sberLoading)

            Text(biometricSubtitle(configured: configured, hardwareReady: hwReady))
                .font(FCTypography.bodySmall())
                .foregroundStyle(surface.opacity(0.82))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
    }

    private var sberLoginButton: some View {
        Button {
            Task { await loginWithSberID() }
        } label: {
            Group {
                if sberLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: surface))
                } else {
                    Text("Войти через Сбер ID")
                        .fontWeight(.semibold)
                }
            }
            .font(FCTypography.bodyLarge())
            .foregroundStyle(surface)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radius14)
                    .stroke(surface.opacity(0.92), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(isLoading || sberLoading)
    }

    private func loginWithSberID() async {
        errorMessage = nil
        sberLoading = true
        defer { sberLoading = false }
        do {
            let r = try await SberIDAuthService.shared.loginCompleting(api: app.api, attachToLoggedInSession: false)
            app.applyAuth(r)
        } catch SberIDAuthError.userCanceled {
            errorMessage = nil
        } catch let e as FitnessAPIError {
            errorMessage = e.localizedDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func biometricSubtitle(configured: Bool, hardwareReady: Bool) -> String {
        if !configured {
            return "После первого входа: Настройки → Безопасность → включите биометрию."
        }
        if configured && !hardwareReady {
            return "Добавьте отпечаток в системных настройках телефона."
        }
        return "Быстрый вход без пароля, если биометрия уже включена в приложении."
    }

    private func biometricTap(configured: Bool) {
        errorMessage = nil
        if !configured {
            errorMessage = "Сначала войдите в приложение (номер телефона и пароль). Затем в Настройки → Безопасность включите «Биометрию»."
            return
        }
        guard BiometryLoginUX.canUseBiometrics else {
            errorMessage = "Добавьте отпечаток в настройках телефона (раздел «Безопасность» / «Отпечаток пальца»)."
            return
        }
        isLoading = true
        Task {
            defer { isLoading = false }
            let ctx = LAContext()
            do {
                let creds = try BiometricCredentialStore.loadCredentials(context: ctx)
                let r = try await app.api.login(email: creds.email, password: creds.password)
                app.applyAuth(r)
            } catch let e as FitnessAPIError {
                errorMessage = e.localizedDescription
            } catch BiometricCredentialStoreError.notAvailable {
                errorMessage = "Биометрия недоступна"
            } catch BiometricCredentialStoreError.decode {
                errorMessage = "Не удалось прочитать сохранённые данные"
            } catch BiometricCredentialStoreError.keychain(let status) where status == errSecUserCanceled {
                errorMessage = nil
            } catch BiometricCredentialStoreError.keychain {
                errorMessage = "Не удалось выполнить вход по биометрии"
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}

private func isValidRuEmail(_ raw: String) -> Bool {
    let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !s.isEmpty else { return false }
    let rx = #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#
    return s.range(of: rx, options: .regularExpression) != nil
}

// MARK: - Выбор клуба при регистрации (`RegisterClubPickScreen.kt`)

private enum RegistrationVenueCardIOS: String, Identifiable, CaseIterable {
    case kupera
    case mallFormat
    case mallDeFriz

    var id: String { rawValue }

    var clubId: String {
        switch self {
        case .kupera: return "3"
        case .mallFormat: return "1"
        case .mallDeFriz: return "2"
        }
    }

    var title: String {
        switch self {
        case .kupera: return "ул. Купера, 2"
        case .mallFormat: return "ТЦ Формат"
        case .mallDeFriz: return "ТЦ Новый де Фриз"
        }
    }

    var addressLines: String {
        switch self {
        case .kupera: return "Основной зал"
        case .mallFormat: return "ул. Центральная, 18, 2 этаж"
        case .mallDeFriz: return "ул. Купера, 2, 2 этаж"
        }
    }
}

/// Прайс как `LocalSubscriptionCatalog.PLANS_PRICELIST_ORDER`.
private enum RegistrationPriceRow: String, Identifiable {
    case plan1
    case plan2
    case plan4
    case plan3
    case plan5
    case plan6

    var id: String { rawValue }

    var safeName: String {
        switch self {
        case .plan1: return "На 12 месяцев"
        case .plan2: return "На 6 месяцев"
        case .plan3: return "На 4 месяца"
        case .plan4: return "На 3 месяца"
        case .plan5: return "На 1 месяц"
        case .plan6: return "Разовое посещение"
        }
    }

    var price: Double {
        switch self {
        case .plan1: return 38_000
        case .plan2: return 25_000
        case .plan3: return 18_000
        case .plan4: return 16_500
        case .plan5: return 6_000
        case .plan6: return 990
        }
    }

    var freezeLine: String? {
        switch self {
        case .plan1: return "+30 дней заморозки"
        case .plan2: return "+20 дней заморозки"
        case .plan4: return "+14 дней заморозки"
        case .plan3: return "+15 дней заморозки"
        default: return nil
        }
    }

    static let pricelist: [RegistrationPriceRow] = [.plan1, .plan2, .plan4, .plan3, .plan5, .plan6]

    func formattedRuPrice() -> String {
        let nf = NumberFormatter()
        nf.numberStyle = .decimal
        nf.groupingSeparator = " "
        nf.locale = Locale(identifier: "ru_RU")
        return nf.string(from: NSNumber(value: Int(price))) ?? "\(Int(price))"
    }
}

struct RegisterClubPickView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var selectedClubId: String?
    @State private var expandedIds: Set<String> = []
    @State private var showSberDialog = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Theme.primary, Theme.primaryVariant],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Button {
                            dismiss()
                        } label: {
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

                    Text("Клуб привяжется к вашему аккаунту")
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.loginOnBackground.opacity(0.85))
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                        .padding(.bottom, 16)

                    ForEach(RegistrationVenueCardIOS.allCases) { card in
                        venueCard(card)
                    }

                    Button {
                        showSberDialog = true
                    } label: {
                        Text("Выбрать зал")
                            .font(FCTypography.titleMedium())
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.onPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Theme.primary)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .opacity(selectedClubId == nil ? 0.55 : 1)
                    .disabled(selectedClubId == nil)
                    .padding(.top, 6)
                    .padding(.bottom, 36)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
        }
        .alert("Зарегистрироваться с помощью Сбер ID", isPresented: $showSberDialog) {
            Button("Продолжить") {
                _ = selectedClubId
            }
            Button("Позже", role: .cancel) {}
        } message: {
            Text(
                """
                После подключения Сбер ID данные будут подставляться автоматически. \
                Сейчас выбор зала уже сохранён.
                """
            )
        }
    }

    private func venueCard(_ card: RegistrationVenueCardIOS) -> some View {
        let selected = card.clubId == selectedClubId
        let expanded = expandedIds.contains(card.clubId)

        return VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 10) {
                Button {
                    selectedClubId = card.clubId
                } label: {
                    ImagePlaceholderTile()
                        .frame(maxWidth: .infinity)
                        .frame(height: 140)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                }
                .buttonStyle(.plain)

                HStack(alignment: .center) {
                    Button {
                        selectedClubId = card.clubId
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(card.title)
                                .font(FCTypography.titleMedium())
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.loginOnBackground)
                                .multilineTextAlignment(.leading)
                            Text(card.addressLines)
                                .font(FCTypography.bodySmall())
                                .foregroundStyle(Theme.loginOnBackground.opacity(0.88))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)

                    Button {
                        if expandedIds.contains(card.clubId) {
                            expandedIds.remove(card.clubId)
                        } else {
                            expandedIds.insert(card.clubId)
                        }
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(Theme.loginOnBackground)
                            .rotationEffect(.degrees(expanded ? 180 : 0))
                    }
                    .buttonStyle(.plain)
                    .frame(width: 44, height: 44)

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
                        .padding(.top, 8)
                        .padding(.bottom, 10)

                    Text("Абонементы")
                        .font(FCTypography.titleSmall())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.loginOnBackground)
                        .padding(.bottom, 8)

                    ForEach(RegistrationPriceRow.pricelist) { plan in
                        pricePane(plan)
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
        }
    }

    private func pricePane(_ plan: RegistrationPriceRow) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(plan.safeName)
                .font(FCTypography.bodyLarge())
                .fontWeight(.medium)
                .foregroundStyle(Theme.loginOnBackground)
            Text("Цена: \(plan.formattedRuPrice()) ₽")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.loginOnBackground.opacity(0.95))
            if let line = plan.freezeLine {
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
}

private struct ImagePlaceholderTile: View {
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
