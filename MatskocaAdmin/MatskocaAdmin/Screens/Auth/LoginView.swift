import SwiftUI

struct LoginView: View {
    @Bindable var controller: LoginController
    @FocusState private var focusedField: Field?
    @State private var passwordVisible = false
    @State private var openLegalPdf: StaffLegalPdf?

    private enum Field: Hashable {
        case email, name, password
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Доброзал")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .foregroundStyle(.white)
                Text("Приложение для специалистов и сотрудников Клуба")
                    .font(.title3)
                    .foregroundStyle(.white.opacity(0.9))

                VStack(alignment: .leading, spacing: 12) {
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

                    HStack {
                        Group {
                            if passwordVisible {
                                TextField("Пароль", text: $controller.password)
                            } else {
                                SecureField("Пароль", text: $controller.password)
                            }
                        }
                        .textContentType(.password)
                        .focused($focusedField, equals: .password)
                        .submitLabel(.go)
                        .onSubmit { submitLogin() }
                        Button {
                            passwordVisible.toggle()
                        } label: {
                            Image(systemName: passwordVisible ? "eye.slash" : "eye")
                                .foregroundStyle(StaffColors.onSurfaceVariant)
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(Color(uiColor: .systemBackground))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.3)))

                    Text("Войдите в приложение или создайте новый аккаунт, чтобы получить доступ к функциям для специалистов или сотрудников Клуба.")
                        .font(.footnote)
                        .foregroundStyle(StaffColors.onSurfaceVariant)

                    Text("Продолжая использовать приложение, Вы принимаете условия")
                        .font(.caption2)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                    Button("Пользовательского соглашения") { openLegalPdf = .userAgreement }
                        .font(.caption2)
                    Text("и подтверждаете ознакомление с")
                        .font(.caption2)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                    Button("Политикой конфиденциальности") { openLegalPdf = .privacy }
                        .font(.caption2)

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
        .sheet(item: $openLegalPdf) { doc in
            NavigationStack {
                LegalPdfView(doc: doc) { openLegalPdf = nil }
            }
        }
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
}
