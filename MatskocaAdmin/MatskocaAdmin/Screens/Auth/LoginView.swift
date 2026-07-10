import SwiftUI

struct LoginView: View {
    @Bindable var controller: LoginController
    @FocusState private var focusedField: Field?

    private enum Field: Hashable {
        case email, name, password
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Доброзал.Админ")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .foregroundStyle(.white)
                Text("Вход для сотрудников")
                    .font(.title3)
                    .foregroundStyle(.white.opacity(0.9))

                VStack(spacing: 12) {
                    TextField("Email", text: $controller.email)
                        .textFieldStyle(.roundedBorder)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                        .textContentType(.username)
                        .focused($focusedField, equals: .email)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .password }

                    TextField("Имя (для регистрации)", text: $controller.name)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .textContentType(.name)
                        .focused($focusedField, equals: .name)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .password }

                    SecureField("Пароль", text: $controller.password)
                        .textFieldStyle(.roundedBorder)
                        .textContentType(.password)
                        .focused($focusedField, equals: .password)
                        .submitLabel(.go)
                        .onSubmit { submitLogin() }

                    rolePicker

                    if !controller.configSummary.isEmpty {
                        Text(controller.configSummary)
                            .font(.caption)
                            .foregroundStyle(StaffColors.onSurfaceVariant)
                    }

                    StaffPrimaryButton(
                        text: controller.isLoading && !controller.isCheckingSession ? "Вход..." : "Войти",
                        action: submitLogin,
                        enabled: !controller.isLoading || controller.isCheckingSession
                    )

                    StaffSecondaryButton(
                        text: "Зарегистрироваться",
                        action: submitRegister,
                        enabled: !controller.isLoading || controller.isCheckingSession
                    )

                    if controller.isLoading, let status = controller.statusMessage {
                        Text(status)
                            .font(.caption)
                            .foregroundStyle(StaffColors.onSurfaceVariant)
                    }

                    if let error = controller.errorMessage {
                        StaffErrorState(message: error)
                    }
                }
                .padding(20)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .shadow(color: .black.opacity(0.08), radius: 4, y: 2)
            }
            .padding(24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(StaffColors.primary)
        .onAppear { controller.tryAutoLogin() }
    }

    private func submitLogin() {
        focusedField = nil
        controller.login()
    }

    private func submitRegister() {
        focusedField = nil
        controller.register()
    }

    private var rolePicker: some View {
        Menu {
            ForEach(controller.roles) { role in
                Button(role.label) {
                    controller.selectedRole = role
                }
            }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Должность")
                        .font(.caption)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                    Text(controller.selectedRole?.label ?? "")
                        .foregroundStyle(StaffColors.onBackground)
                }
                Spacer()
                Image(systemName: "chevron.up.chevron.down")
                    .foregroundStyle(StaffColors.onSurfaceVariant)
            }
            .padding(12)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.gray.opacity(0.4)))
        }
    }
}
