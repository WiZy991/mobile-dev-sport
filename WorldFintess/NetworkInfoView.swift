import SwiftUI

/// Аналог Android `NetworkInfoScreen` — «О сети и контакты».
struct NetworkInfoView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.openURL) private var openURL

    @State private var isLoading = true
    @State private var clubName = AppConfiguration.appDisplayName
    @State private var aboutText = ""
    @State private var phone: String?
    @State private var email: String?
    @State private var website: String?
    @State private var workingHours: String?
    @State private var address: String?
    @State private var socialLinks: [ClubSocialLink] = []
    @State private var contactEmail = ""
    @State private var ticketSubject = ""
    @State private var ticketMessage = ""
    @State private var ticketCategory = "other"
    @State private var isSubmittingTicket = false
    @State private var ticketError: String?
    @State private var ticketSuccess: String?
    @State private var toast: String?

    private var hasProfile: Bool { app.currentUser != nil }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                BrandHeader(brandName: clubName, textColor: Theme.onBackground, logoSize: 48)
                    .padding(.top, 8)

                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 24)
                } else {
                    sectionCard(title: "О клубе") {
                        Text(aboutText.isEmpty ? "Сеть фитнес-клубов \(clubName)." : aboutText)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onSurface)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    sectionCard(title: "Контакты") {
                        contactRow(label: "Телефон", value: phone) {
                            if let phone, let url = URL(string: "tel:\(phone.filter { $0.isNumber || $0 == "+" })") {
                                openURL(url)
                            }
                        }
                        contactRow(label: "Email", value: email) {
                            if let email, let url = URL(string: "mailto:\(email)") {
                                openURL(url)
                            }
                        }
                        contactRow(label: "Сайт", value: website) {
                            if let website, let url = URL(string: website) {
                                openURL(url)
                            }
                        }
                        if let workingHours, !workingHours.isEmpty {
                            Text("Режим работы: \(workingHours)")
                                .font(FCTypography.bodyMedium())
                                .foregroundStyle(Theme.onSurface)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        if let address, !address.isEmpty {
                            Text(address)
                                .font(FCTypography.bodyMedium())
                                .foregroundStyle(Theme.onSurfaceVariant)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    if !socialLinks.isEmpty {
                        sectionCard(title: "Соцсети") {
                            ForEach(Array(socialLinks.enumerated()), id: \.offset) { index, link in
                                if let url = URL(string: link.url) {
                                    Button(link.displayTitle) { openURL(url) }
                                        .buttonStyle(.plain)
                                        .foregroundStyle(Theme.primary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.top, index == 0 ? 0 : 8)
                                }
                            }
                        }
                    }

                    sectionCard(title: "Обратная связь") {
                        supportTicketForm
                    }

                    sectionCard(title: "Оценить приложение") {
                        Button("App Store") { openURL(AppConfiguration.appStoreURL) }
                            .buttonStyle(.plain)
                            .font(FCTypography.bodyLarge())
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.onPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Theme.primary)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                    }
                }
            }
            .padding(16)
            .padding(.bottom, 24)
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "О сети и контакты")
        .task { await load() }
        .onAppear {
            if contactEmail.isEmpty {
                contactEmail = (app.currentUser?.email ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        .alert("", isPresented: Binding(get: { toast != nil }, set: { if !$0 { toast = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(toast ?? "") }
    }

    private var supportTicketForm: some View {
        VStack(alignment: .leading, spacing: 12) {
            supportField(
                title: hasProfile ? "Email для ответа (необязательно)" : "Email для ответа",
                subtitle: hasProfile ? "По умолчанию — из профиля" : nil,
                text: $contactEmail,
                email: true
            )

            Menu {
                ForEach(fcHelpTicketCategories, id: \.api) { pair in
                    Button(pair.label) { ticketCategory = pair.api }
                }
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Тематика")
                            .font(FCTypography.labelMedium())
                            .foregroundStyle(Theme.onSurfaceVariant)
                        Text(fcHelpCategoryLabel(for: ticketCategory))
                            .font(FCTypography.bodyLarge())
                            .foregroundStyle(Theme.onBackground)
                    }
                    Spacer()
                    Image(systemName: "chevron.down.circle.fill")
                        .foregroundStyle(Theme.primary)
                }
                .padding(12)
                .frame(maxWidth: .infinity)
                .overlay(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous).stroke(Theme.outlineVariant, lineWidth: 1))
            }

            supportField(title: "Тема", text: $ticketSubject)

            VStack(alignment: .leading, spacing: 6) {
                Text("Сообщение")
                    .font(FCTypography.labelMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                TextEditor(text: $ticketMessage)
                    .frame(minHeight: 110)
                    .padding(8)
                    .scrollContentBackground(.hidden)
                    .background(Theme.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous).stroke(Theme.outlineVariant, lineWidth: 1))
                    .font(FCTypography.bodyLarge())
            }

            if let ticketError {
                Text(ticketError)
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.error)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let ticketSuccess {
                Text(ticketSuccess)
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.primary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Button {
                submitTicket()
            } label: {
                Text(isSubmittingTicket ? "Отправка…" : "Отправить обращение")
                    .font(FCTypography.titleSmall())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(isSubmittingTicket ? Theme.primary.opacity(0.55) : Theme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(isSubmittingTicket)
        }
    }

    private func supportField(title: String, subtitle: String? = nil, text: Binding<String>, email: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(FCTypography.labelMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
            if let subtitle {
                Text(subtitle)
                    .font(FCTypography.labelSmall())
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
            }
            TextField("", text: text)
                .padding(12)
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous).stroke(Theme.outlineVariant, lineWidth: 1))
                .font(FCTypography.bodyLarge())
                .keyboardType(email ? .emailAddress : .default)
                .textInputAutocapitalization(email ? .never : .sentences)
                .autocorrectionDisabled(email)
        }
    }

    private func submitTicket() {
        ticketError = nil
        ticketSuccess = nil
        let subj = ticketSubject.trimmingCharacters(in: .whitespacesAndNewlines)
        let msg = ticketMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        let emailTrim = contactEmail.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !subj.isEmpty else {
            ticketError = "Укажите тему обращения"
            return
        }
        guard msg.count >= 5 else {
            ticketError = "Опишите проблему не менее чем в 5 символов"
            return
        }
        if !hasProfile {
            guard fcLooksLikeEmail(emailTrim) else {
                ticketError = "Укажите email для ответа поддержки"
                return
            }
        } else if !emailTrim.isEmpty && !fcLooksLikeEmail(emailTrim) {
            ticketError = "Некорректный email"
            return
        }

        isSubmittingTicket = true
        Task {
            do {
                _ = try await app.api.createSupportTicket(
                    SupportTicketRequest(
                        subject: subj,
                        message: msg,
                        category: ticketCategory,
                        contactEmail: emailTrim.isEmpty ? nil : emailTrim
                    )
                )
                await MainActor.run {
                    isSubmittingTicket = false
                    ticketSubject = ""
                    ticketMessage = ""
                    ticketSuccess = "Обращение отправлено. Ответ придёт на указанный email."
                }
            } catch {
                await MainActor.run {
                    isSubmittingTicket = false
                    ticketError = error.localizedDescription
                }
            }
        }
    }

    private func sectionCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(FCTypography.titleSmall())
                .fontWeight(.semibold)
                .foregroundStyle(Theme.primary)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private func contactRow(label: String, value: String?, action: @escaping () -> Void) -> some View {
        Group {
            if let value, !value.isEmpty {
                Button(action: action) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(label)
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                        Text(value)
                            .font(FCTypography.bodyLarge())
                            .foregroundStyle(Theme.primary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .padding(.bottom, 6)
            }
        }
    }

    @MainActor
    private func load() async {
        isLoading = true
        defer { isLoading = false }
        guard let club = try? await app.api.getClubInfo() else { return }
        clubName = club.resolvedBrandName
        aboutText = club.network?.about ?? ""
        phone = club.phone
        email = club.email
        website = club.network?.website
        workingHours = club.workingHours
        address = club.address
        socialLinks = club.network?.resolvedSocialLinks ?? []
    }
}
