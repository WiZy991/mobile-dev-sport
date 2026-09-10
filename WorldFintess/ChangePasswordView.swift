import SwiftUI

/// Смена пароля в приложении (`ChangePasswordScreen.kt`).
struct ChangePasswordView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.dismiss) private var dismiss

    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var showCurrent = false
    @State private var showNew = false
    @State private var showConfirm = false
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var successToast = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Если пароль ещё не задан (например, после регистрации), оставьте поле «Текущий пароль» пустым.")
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)

                passwordField("Текущий пароль", text: $currentPassword, visible: $showCurrent)
                passwordField("Новый пароль", text: $newPassword, visible: $showNew)
                passwordField("Подтвердите пароль", text: $confirmPassword, visible: $showConfirm)

                if let errorMessage {
                    Text(errorMessage)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.error)
                }

                Button {
                    Task { await submit() }
                } label: {
                    Group {
                        if isSaving {
                            ProgressView().tint(Theme.onPrimary)
                        } else {
                            Text("Сохранить")
                                .fontWeight(.semibold)
                        }
                    }
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(Theme.primary.opacity(isSaving ? 0.6 : 1))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(isSaving)
                .padding(.top, 8)
            }
            .padding(24)
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Изменить пароль")
        .alert("Готово", isPresented: $successToast) {
            Button("OK") { dismiss() }
        } message: {
            Text("Пароль успешно изменён")
        }
    }

    private func passwordField(_ title: String, text: Binding<String>, visible: Binding<Bool>) -> some View {
        HStack(spacing: 12) {
            Group {
                if visible.wrappedValue {
                    TextField(title, text: text)
                } else {
                    SecureField(title, text: text)
                }
            }
            .textContentType(.password)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            Button {
                visible.wrappedValue.toggle()
            } label: {
                Image(systemName: visible.wrappedValue ? "eye.slash.fill" : "eye.fill")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
    }

    @MainActor
    private func submit() async {
        errorMessage = nil
        if newPassword.count < 6 {
            errorMessage = "Пароль должен быть не менее 6 символов"
            return
        }
        if newPassword != confirmPassword {
            errorMessage = "Пароли не совпадают"
            return
        }
        if !currentPassword.isEmpty && currentPassword == newPassword {
            errorMessage = "Новый пароль должен отличаться от текущего"
            return
        }

        isSaving = true
        defer { isSaving = false }
        do {
            try await app.api.changePassword(currentPassword: currentPassword, newPassword: newPassword)
            successToast = true
        } catch let e as FitnessAPIError {
            errorMessage = e.localizedDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
