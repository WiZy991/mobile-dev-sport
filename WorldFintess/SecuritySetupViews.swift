import LocalAuthentication
import SwiftUI

// MARK: - Блокировка приложения кодом / биометрией

struct AppSecurityLockOverlay: View {
    let onUnlocked: () -> Void

    @State private var pinInput = ""
    @State private var errorMessage: String?
    @State private var didFailBiometric = false

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 20) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(Theme.primary)
                Text("Введите код-пароль")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
                pinDots
                if let errorMessage {
                    Text(errorMessage)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.error)
                }
                pinKeypad
                if AppPinStore.isBiometricUnlockEnabled, BiometryLoginUX.canUseBiometrics {
                    Button {
                        Task { await unlockWithBiometrics() }
                    } label: {
                        Label(BiometryLoginUX.unlockButtonTitle(), systemImage: BiometryLoginUX.primaryIconName())
                            .font(FCTypography.labelLarge())
                            .foregroundStyle(Theme.primary)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 8)
                }
            }
            .padding(.horizontal, 24)
        }
        .onAppear {
            if AppPinStore.isBiometricUnlockEnabled, BiometryLoginUX.canUseBiometrics, !didFailBiometric {
                Task { await unlockWithBiometrics() }
            }
        }
    }

    private var pinDots: some View {
        HStack(spacing: 16) {
            ForEach(0..<6, id: \.self) { i in
                Circle()
                    .fill(i < pinInput.count ? Theme.primary : Theme.surfaceVariant)
                    .frame(width: 14, height: 14)
            }
        }
        .padding(.vertical, 8)
    }

    private var pinKeypad: some View {
        let rows: [[String]] = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["", "0", "⌫"]]
        return VStack(spacing: 12) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 12) {
                    ForEach(row, id: \.self) { key in
                        if key.isEmpty {
                            Color.clear.frame(maxWidth: .infinity).frame(height: 56)
                        } else {
                            Button { tapKey(key) } label: {
                                Text(key)
                                    .font(FCTypography.titleLarge())
                                    .fontWeight(.semibold)
                                    .foregroundStyle(Theme.onBackground)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 56)
                                    .background(Theme.surface)
                                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    private func tapKey(_ key: String) {
        errorMessage = nil
        if key == "⌫" {
            if !pinInput.isEmpty { pinInput.removeLast() }
            return
        }
        guard pinInput.count < 6 else { return }
        pinInput.append(key)
        if pinInput.count >= 4 {
            if AppPinStore.verifyPin(pinInput) {
                onUnlocked()
            } else if pinInput.count == 6 {
                errorMessage = "Неверный код"
                pinInput = ""
            }
        }
    }

    private func unlockWithBiometrics() async {
        let ctx = LAContext()
        let ok = await withCheckedContinuation { cont in
            ctx.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: "Разблокировать приложение"
            ) { success, _ in cont.resume(returning: success) }
        }
        await MainActor.run {
            if ok {
                onUnlocked()
            } else {
                didFailBiometric = true
            }
        }
    }
}

// MARK: - Настройка безопасности после регистрации

struct PostRegistrationSecuritySetupView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.dismiss) private var dismiss

    @State private var step = 0
    @State private var pin = ""
    @State private var pinConfirm = ""
    @State private var pinError: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer(minLength: 0)
                content
                Spacer(minLength: 0)
                buttons
            }
            .padding(24)
            .navigationTitle("Безопасность")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch step {
        case 0:
            Image(systemName: "shield.checkered")
                .font(.system(size: 56))
                .foregroundStyle(Theme.primary)
            Text("Защитите аккаунт")
                .font(FCTypography.titleLarge())
                .fontWeight(.bold)
            Text("По желанию установите код-пароль для блокировки приложения и включите биометрию для быстрого входа.")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        case 1:
            Text("Придумайте код-пароль")
                .font(FCTypography.titleLarge())
                .fontWeight(.bold)
            SecureField("4–6 цифр", text: $pin)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .multilineTextAlignment(.center)
                .font(FCTypography.titleMedium())
                .padding()
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
            SecureField("Повторите код", text: $pinConfirm)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .multilineTextAlignment(.center)
                .font(FCTypography.titleMedium())
                .padding()
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
            if let pinError {
                Text(pinError).font(FCTypography.bodySmall()).foregroundStyle(Theme.error)
            }
        case 2:
            Image(systemName: BiometryLoginUX.primaryIconName())
                .font(.system(size: 56))
                .foregroundStyle(Theme.primary)
            Text(BiometryLoginUX.saveToggleLabel())
                .font(FCTypography.titleMedium())
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
            Text("Быстрый вход без email и пароля на экране авторизации.")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        case 3:
            Image(systemName: BiometryLoginUX.primaryIconName())
                .font(.system(size: 56))
                .foregroundStyle(Theme.primary)
            Text(BiometryLoginUX.unlockToggleLabel())
                .font(FCTypography.titleMedium())
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
            Text("Разблокировка приложения \(BiometryLoginUX.biometryShortName()) вместо ввода кода.")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        default:
            EmptyView()
        }
    }

    @ViewBuilder
    private var buttons: some View {
        switch step {
        case 0:
            FCPrimaryButton(title: "Настроить") { step = 1 }
            Button("Пропустить") { finish() }
                .font(FCTypography.labelLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
        case 1:
            FCPrimaryButton(title: "Сохранить код") { savePin() }
            Button("Пропустить") { step = nextAfterPinStep() }
                .font(FCTypography.labelLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
        case 2:
            FCPrimaryButton(title: "Включить") { Task { await enableLoginBiometrics() } }
            Button("Пропустить") { step = nextAfterBiometricLoginStep() }
                .font(FCTypography.labelLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
        case 3:
            FCPrimaryButton(title: "Включить") {
                AppPinStore.setBiometricUnlockEnabled(true)
                finish()
            }
            Button("Пропустить") { finish() }
                .font(FCTypography.labelLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
        default:
            EmptyView()
        }
    }

    private func savePin() {
        pinError = nil
        guard pin == pinConfirm else {
            pinError = AppPinError.mismatch.localizedDescription
            return
        }
        do {
            try AppPinStore.setPin(pin)
            pin = ""
            pinConfirm = ""
            step = nextAfterPinStep()
        } catch {
            pinError = error.localizedDescription
        }
    }

    private func nextAfterPinStep() -> Int {
        BiometryLoginUX.canUseBiometrics ? 2 : finishStep()
    }

    private func nextAfterBiometricLoginStep() -> Int {
        AppPinStore.isPinEnabled && BiometryLoginUX.canUseBiometrics ? 3 : finishStep()
    }

    private func finishStep() -> Int {
        finish()
        return step
    }

    @MainActor
    private func enableLoginBiometrics() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        guard let refresh = KeychainStore.get(.refreshToken), !refresh.isEmpty else {
            step = nextAfterBiometricLoginStep()
            return
        }
        let ctx = LAContext()
        do {
            let ok = try await ctx.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: "Подтвердите включение биометрии"
            )
            guard ok else { return }
            try BiometricCredentialStore.save(refreshToken: refresh)
            step = nextAfterBiometricLoginStep()
        } catch {
            step = nextAfterBiometricLoginStep()
        }
    }

    private func finish() {
        app.pendingSecuritySetup = false
        dismiss()
    }
}

// MARK: - Настройка / смена PIN в Settings

struct AppPinSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var mode: Mode = .choose
    @State private var pin = ""
    @State private var pinConfirm = ""
    @State private var error: String?

    enum Mode { case choose, set, change, remove }

    var body: some View {
        NavigationStack {
            Form {
                switch mode {
                case .choose:
                    if AppPinStore.isPinEnabled {
                        Button("Изменить код-пароль") { mode = .change; pin = ""; pinConfirm = "" }
                        Button("Отключить код-пароль", role: .destructive) {
                            AppPinStore.clearPin()
                            dismiss()
                        }
                    } else {
                        Button("Установить код-пароль") { mode = .set }
                    }
                    if AppPinStore.isPinEnabled {
                        Toggle(BiometryLoginUX.unlockToggleLabel(), isOn: Binding(
                            get: { AppPinStore.isBiometricUnlockEnabled },
                            set: { AppPinStore.setBiometricUnlockEnabled($0) }
                        ))
                        .disabled(!BiometryLoginUX.canUseBiometrics)
                    }
                case .set, .change:
                    SecureField("Новый код (4–6 цифр)", text: $pin).keyboardType(.numberPad)
                    SecureField("Повторите код", text: $pinConfirm).keyboardType(.numberPad)
                    if let error { Text(error).foregroundStyle(Theme.error) }
                case .remove:
                    EmptyView()
                }
            }
            .navigationTitle("Код-пароль")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { dismiss() }
                }
                if mode == .set || mode == .change {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Сохранить") { save() }
                    }
                }
            }
        }
    }

    private func save() {
        error = nil
        guard pin == pinConfirm else {
            error = AppPinError.mismatch.localizedDescription
            return
        }
        do {
            try AppPinStore.setPin(pin)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
