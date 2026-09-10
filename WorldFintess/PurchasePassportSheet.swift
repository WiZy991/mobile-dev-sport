import SwiftUI

/// Состояние шага сверки профиля перед согласием/оплатой (`PurchasePassportGate`).
struct PurchasePassportGate: Identifiable {
    let id = UUID()
    let plan: SubscriptionPlan
    let isReview: Bool
    /// После покупки абонемента сервер ставит `profile_locked` — правки только через поддержку.
    let profileLocked: Bool
    let name: String
    let email: String
    let emailVerified: Bool
    let phone: String
    let initialDobDisplay: String
    let series: String
    let number: String
    let issuedBy: String
    let issueDateDisplay: String
    let registrationAddress: String

    static func from(user: User, plan: SubscriptionPlan) -> PurchasePassportGate {
        let passportComplete = user.isPassportCompleteForPurchase
        let review = passportComplete
            && !user.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !user.email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return PurchasePassportGate(
            plan: plan,
            isReview: review,
            profileLocked: user.profileLocked,
            name: user.name,
            email: user.email,
            emailVerified: user.emailVerified,
            phone: user.phone,
            initialDobDisplay: isoDateToDisplay(user.dateOfBirth),
            series: user.passportSeries ?? "",
            number: user.passportNumber ?? "",
            issuedBy: user.passportIssuedBy ?? "",
            issueDateDisplay: isoDateToDisplay(user.passportIssueDate),
            registrationAddress: user.registrationAddress ?? ""
        )
    }

    private static func isoDateToDisplay(_ iso: String?) -> String {
        guard let iso, !iso.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return "" }
        let prefix = String(iso.prefix(10))
        let parts = prefix.split(separator: "-")
        guard parts.count == 3 else { return iso }
        return "\(parts[2]).\(parts[1]).\(parts[0])"
    }
}

struct PurchasePassportResult {
    let name: String
    let email: String
    let series: String
    let number: String
    let issuedBy: String
    let issueDateIso: String
    let registrationAddress: String
    let dateOfBirthIso: String?
}

/// Полноэкранный шаг сверки/заполнения профиля (`PurchasePassportDialog.kt`).
struct PurchasePassportSheet: View {
    let gate: PurchasePassportGate
    var isLoading: Bool = false
    var error: String? = nil
    var onDismiss: () -> Void
    var onConfirm: (PurchasePassportResult) -> Void
    var onResendEmail: () -> Void = {}
    var isResendingEmail: Bool = false
    var emailResendMessage: String? = nil
    var onConsumeEmailResendMessage: () -> Void = {}

    @State private var name = ""
    @State private var email = ""
    @State private var birthDisplay = ""
    @State private var series = ""
    @State private var number = ""
    @State private var issuedBy = ""
    @State private var issueDateDisplay = ""
    @State private var address = ""
    @State private var unlocked: Set<String> = []
    @State private var localError: String?
    @State private var showIssuePicker = false
    @State private var showBirthPicker = false

    private var phoneDisplay: String {
        formatRussianPhoneMask(normalizeRussianNationalDigits(gate.phone))
    }

    private var review: Bool { gate.isReview }
    /// Карандаш только в режиме сверки и пока профиль не заблокирован покупкой.
    private var allowUnlock: Bool { review && !gate.profileLocked }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text("Эти данные попадут в договор с клубом. Проверьте ФИО, дату рождения, паспорт, адрес и email. Телефон менять здесь нельзя.")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 8)

                if gate.profileLocked {
                    Text("Данные зафиксированы после покупки абонемента. Чтобы изменить их, обратитесь в поддержку.")
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                }

                ScrollView {
                    VStack(spacing: 10) {
                        profileField(
                            label: "ФИО",
                            text: $name,
                            key: "name",
                            filled: !gate.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        )

                        filledField(
                            label: "Телефон",
                            text: .constant(phoneDisplay),
                            enabled: false
                        )

                        profileField(
                            label: "Email",
                            text: $email,
                            key: "email",
                            filled: !gate.email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                            keyboard: .emailAddress
                        )

                        dateRow(
                            label: "Дата рождения",
                            text: birthDateBinding,
                            key: "dob",
                            filled: !gate.initialDobDisplay.isEmpty,
                            showPicker: $showBirthPicker
                        )

                        HStack(alignment: .top, spacing: 8) {
                            profileField(
                                label: "Серия",
                                text: Binding(
                                    get: { series },
                                    set: { series = String($0.filter { $0.isASCII && $0.isNumber }.prefix(4)) }
                                ),
                                key: "series",
                                filled: gate.series.filter { $0.isASCII && $0.isNumber }.count == 4,
                                keyboard: .numberPad
                            )
                            profileField(
                                label: "Номер",
                                text: Binding(
                                    get: { number },
                                    set: { number = String($0.filter { $0.isASCII && $0.isNumber }.prefix(6)) }
                                ),
                                key: "number",
                                filled: gate.number.filter { $0.isASCII && $0.isNumber }.count == 6,
                                keyboard: .numberPad
                            )
                        }

                        profileField(
                            label: "Кем выдан",
                            text: Binding(
                                get: { issuedBy },
                                set: { issuedBy = String($0.prefix(300)) }
                            ),
                            key: "issuedBy",
                            filled: !gate.issuedBy.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                            minLines: 2
                        )

                        dateRow(
                            label: "Дата выдачи",
                            text: issueDateBinding,
                            key: "issueDate",
                            filled: !gate.issueDateDisplay.isEmpty,
                            showPicker: $showIssuePicker
                        )

                        profileField(
                            label: "Адрес прописки",
                            text: $address,
                            key: "address",
                            filled: !gate.registrationAddress.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                            minLines: 2
                        )

                        if let msg = localError ?? error {
                            Text(msg)
                                .font(FCTypography.bodySmall())
                                .foregroundStyle(Theme.error)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }

                VStack(spacing: 10) {
                    Button(action: submit) {
                        HStack(spacing: 10) {
                            if isLoading {
                                ProgressView().tint(Theme.onPrimary)
                            }
                            Text(isLoading ? "Сохраняем…" : "Продолжить")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 48)
                        .background(Theme.primary)
                        .foregroundStyle(Theme.onPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(isLoading)

                    Button(action: onDismiss) {
                        Text("Отмена")
                            .fontWeight(.medium)
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 48)
                            .foregroundStyle(Theme.primary)
                            .background(
                                RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous)
                                    .stroke(Theme.outlineVariant, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                    .disabled(isLoading)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
            }
            .background(Theme.background)
            .navigationTitle(review ? "Проверьте данные профиля" : "Данные профиля")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Theme.primary, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .interactiveDismissDisabled(isLoading)
        .onAppear { seedFromGate() }
        .sheet(isPresented: $showBirthPicker) {
            passportDatePicker(title: "Дата рождения") {
                birthDisplay = RegisterDateParsing.displayFromDate($0)
                localError = nil
            }
        }
        .sheet(isPresented: $showIssuePicker) {
            passportDatePicker(title: "Дата выдачи") {
                issueDateDisplay = RegisterDateParsing.displayFromDate($0)
                localError = nil
            }
        }
        .alert("Письмо отправлено", isPresented: Binding(
            get: { emailResendMessage != nil },
            set: { if !$0 { onConsumeEmailResendMessage() } }
        )) {
            Button("Хорошо", role: .cancel) { onConsumeEmailResendMessage() }
        } message: {
            Text(emailResendMessage ?? "")
        }
    }

    private var birthDateBinding: Binding<String> {
        Binding(
            get: { birthDisplay },
            set: {
                birthDisplay = RegisterDateParsing.applyDotsMask($0)
                localError = nil
            }
        )
    }

    private var issueDateBinding: Binding<String> {
        Binding(
            get: { issueDateDisplay },
            set: {
                issueDateDisplay = RegisterDateParsing.applyDotsMask($0)
                localError = nil
            }
        )
    }

    private func seedFromGate() {
        name = gate.name
        email = gate.email
        birthDisplay = gate.initialDobDisplay
        series = String(gate.series.filter { $0.isASCII && $0.isNumber }.prefix(4))
        number = String(gate.number.filter { $0.isASCII && $0.isNumber }.prefix(6))
        issuedBy = gate.issuedBy
        issueDateDisplay = gate.issueDateDisplay
        address = gate.registrationAddress
        unlocked = []
        localError = nil
    }

    private func editable(_ key: String, filled: Bool) -> Bool {
        if gate.profileLocked { return false }
        if !review { return true }
        return !filled || unlocked.contains(key)
    }

    private func submit() {
        if gate.profileLocked {
            // Уже сохранено на сервере — только подтверждаем и идём к согласию.
            localError = nil
            onConfirm(
                PurchasePassportResult(
                    name: name.trimmingCharacters(in: .whitespacesAndNewlines),
                    email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                    series: series,
                    number: number,
                    issuedBy: issuedBy.trimmingCharacters(in: .whitespacesAndNewlines),
                    issueDateIso: RegisterDateParsing.toIsoDate(issueDateDisplay) ?? "",
                    registrationAddress: address.trimmingCharacters(in: .whitespacesAndNewlines),
                    dateOfBirthIso: RegisterDateParsing.toIsoDate(birthDisplay)
                )
            )
            return
        }

        let issueIso = RegisterDateParsing.toIsoDate(issueDateDisplay)
        let birthIso = RegisterDateParsing.toIsoDate(birthDisplay)
        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            localError = "Укажите ФИО как в паспорте"
            return
        }
        let emailTrim = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if emailTrim.isEmpty || !RegisterValidation.isValidEmail(emailTrim) {
            localError = "Укажите корректный email"
            return
        }
        if birthIso == nil {
            localError = "Укажите дату рождения"
            return
        }
        if series.count != 4 || number.count != 6
            || issuedBy.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || issueIso == nil {
            localError = "Заполните все поля паспорта корректно"
            return
        }
        if address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            localError = "Укажите адрес прописки"
            return
        }
        localError = nil
        onConfirm(
            PurchasePassportResult(
                name: name.trimmingCharacters(in: .whitespacesAndNewlines),
                email: emailTrim,
                series: series,
                number: number,
                issuedBy: issuedBy.trimmingCharacters(in: .whitespacesAndNewlines),
                issueDateIso: issueIso!,
                registrationAddress: address.trimmingCharacters(in: .whitespacesAndNewlines),
                dateOfBirthIso: birthIso
            )
        )
    }

    @ViewBuilder
    private func profileField(
        label: String,
        text: Binding<String>,
        key: String,
        filled: Bool,
        keyboard: UIKeyboardType = .default,
        minLines: Int = 1
    ) -> some View {
        let canEdit = editable(key, filled: filled)
        HStack(alignment: .center, spacing: 4) {
            filledField(
                label: label,
                text: text,
                enabled: !isLoading && canEdit,
                keyboard: keyboard,
                minLines: minLines
            )
            if allowUnlock && !canEdit {
                Button { unlocked.insert(key) } label: {
                    Image(systemName: "pencil")
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .disabled(isLoading)
            }
        }
        .onChange(of: text.wrappedValue) { _, _ in localError = nil }
    }

    @ViewBuilder
    private func dateRow(
        label: String,
        text: Binding<String>,
        key: String,
        filled: Bool,
        showPicker: Binding<Bool>
    ) -> some View {
        let canEdit = editable(key, filled: filled)
        HStack(alignment: .center, spacing: 4) {
            filledField(
                label: label,
                text: text,
                enabled: !isLoading && canEdit,
                keyboard: .numberPad,
                placeholder: "дд.мм.гггг"
            )
            if allowUnlock && !canEdit {
                Button { unlocked.insert(key) } label: {
                    Image(systemName: "pencil")
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .disabled(isLoading)
            } else if canEdit {
                Button { showPicker.wrappedValue = true } label: {
                    Image(systemName: "calendar")
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .disabled(isLoading)
            }
        }
    }

    /// Filled TextField как Material3 на Android (`shape = RoundedCornerShape(12)`).
    private func filledField(
        label: String,
        text: Binding<String>,
        enabled: Bool,
        keyboard: UIKeyboardType = .default,
        minLines: Int = 1,
        placeholder: String? = nil
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(FCTypography.labelSmall())
                .foregroundStyle(enabled ? Theme.onSurfaceVariant : Theme.onSurfaceVariant.opacity(0.55))

            Group {
                if minLines > 1 {
                    TextField("", text: text, prompt: Text(placeholder ?? "").foregroundStyle(Theme.onSurfaceVariant.opacity(0.45)), axis: .vertical)
                        .lineLimit(minLines...4)
                } else {
                    TextField("", text: text, prompt: Text(placeholder ?? "").foregroundStyle(Theme.onSurfaceVariant.opacity(0.45)))
                        .keyboardType(keyboard)
                        .textInputAutocapitalization(keyboard == .emailAddress ? .never : .sentences)
                        .autocorrectionDisabled(keyboard == .emailAddress)
                }
            }
            .disabled(!enabled)
            .foregroundStyle(enabled ? Theme.onSurface : Theme.onSurface.opacity(0.55))
            .font(FCTypography.bodyLarge())
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func passportDatePicker(title: String, onPick: @escaping (Date) -> Void) -> some View {
        PassportDatePickerSheet(title: title, onPick: onPick)
    }
}

private struct PassportDatePickerSheet: View {
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
