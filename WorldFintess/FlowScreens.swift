import SwiftUI
import SafariServices
import Security
import UIKit
import UniformTypeIdentifiers

// MARK: - UI helpers (`PurchaseHistoryScreen` date, dial)

/// `formatDate()` в Kotlin: `yyyy-MM-dd` или ISO → `дд.мм.гггг`.
fileprivate func fcFormatRussianDate(fromIso iso: String) -> String {
    guard let sep = iso.firstIndex(of: "T") ?? iso.firstIndex(of: " ") else { return iso }
    let datePart = String(iso[..<sep])
    let parts = datePart.split(separator: "-")
    guard parts.count == 3 else { return iso }
    return "\(parts[2]).\(parts[1]).\(parts[0])"
}

fileprivate func dialPhoneRaw(_ raw: String?) {
    guard let raw, !raw.isEmpty else { return }
    let cleaned = raw.filter { $0.isNumber || $0 == "+" }
    guard !cleaned.isEmpty, let u = URL(string: "tel:\(cleaned)") else { return }
    UIApplication.shared.open(u)
}

/// Первый день недели (понедельник) для недельной ленты календаря, как на Android (`PersonalTrainingScreen.kt`).
fileprivate func fcStartOfWeekMonday(containing date: Date) -> Date {
    var cal = Calendar(identifier: .gregorian)
    cal.firstWeekday = 2
    cal.locale = Locale(identifier: "ru_RU")
    return cal.dateInterval(of: .weekOfYear, for: date)?.start ?? date
}

fileprivate func fcLockerEndsAtFormatted(_ iso: String) -> String {
    guard iso.count >= 16 else { return iso }
    let prefix = String(iso.prefix(16))
    return prefix.replacingOccurrences(of: "T", with: " ")
}

/// Как `SupportCategories` в `HelpViewModel.kt`.
fileprivate let fcHelpTicketCategories: [(api: String, label: String)] = [
    ("question", "Вопрос"),
    ("complaint", "Жалоба"),
    ("suggestion", "Пожелание"),
    ("technical", "Техническая проблема"),
    ("billing", "Оплата / абонемент"),
    ("other", "Другое"),
]

fileprivate func fcHelpCategoryLabel(for api: String) -> String {
    fcHelpTicketCategories.first { $0.api == api }?.label ?? "Другое"
}

fileprivate func fcLooksLikeEmail(_ s: String) -> Bool {
    let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
    let parts = t.split(separator: "@")
    guard parts.count == 2 else { return false }
    return !parts[0].isEmpty && parts[1].contains(".")
}

// MARK: - Subscription plans

struct SubscriptionPlansView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.dismiss) private var dismiss
    @State private var plans: [SubscriptionPlan] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var showPromoSheet = false
    @State private var promoField = ""
    @State private var appliedPromo: String?
    @State private var discountPct = 0
    @State private var isApplyingPromo = false
    @State private var snackMessage: String?
    @State private var safariSheet: SafariIdent?
    @State private var purchaseSheetPlan: PurchaseSheetPlan?
    @State private var purchaseError: String?
    @State private var isPurchasing = false

    /// Обёртка для `.sheet(item:)`: у `SubscriptionPlan` уже есть поле `id` из API — второй `id` для `Identifiable` недопустим.
    private struct PurchaseSheetPlan: Identifiable {
        let id: String
        let plan: SubscriptionPlan
        init(_ plan: SubscriptionPlan) {
            self.plan = plan
            id = plan.safeId
        }
    }

    private struct SafariIdent: Identifiable {
        let id = UUID()
        let url: URL
    }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            if isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let err = loadError {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(Theme.error)
                    Text(err)
                        .font(FCTypography.bodyLarge())
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    Button("Повторить") { Task { await load() } }
                        .font(FCTypography.labelLarge())
                        .foregroundStyle(Theme.onPrimary)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        if let code = appliedPromo {
                            promoAppliedCard(code: code, discount: discountPct)
                        }
                        ForEach(plans, id: \.safeId) { plan in
                            subscriptionPlanCard(plan) {
                                purchaseError = nil
                                purchaseSheetPlan = PurchaseSheetPlan(plan)
                            }
                        }
                        subscriptionInfoCard
                    }
                    .padding(16)
                    .padding(.bottom, 24)
                }
            }
        }
        .fcPrimaryNavigation(title: "Купить абонемент")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    promoField = ""
                    showPromoSheet = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "tag.fill")
                        Text("Промокод")
                            .font(FCTypography.labelLarge())
                    }
                    .foregroundStyle(Theme.onPrimary)
                }
            }
        }
        .task { await load() }
        .sheet(isPresented: $showPromoSheet) {
            NavigationStack {
                Form {
                    TextField("Промокод", text: $promoField)
                        .textInputAutocapitalization(.characters)
                    if isApplyingPromo {
                        ProgressView()
                    }
                }
                .navigationTitle("Введите промокод")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Отмена") { showPromoSheet = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Применить") { applyPromoFromSheet() }
                            .disabled(promoField.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isApplyingPromo)
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .sheet(item: $purchaseSheetPlan) { wrapped in
            purchaseConfirmSheet(plan: wrapped.plan)
        }
        .alert("Сообщение", isPresented: Binding(get: { snackMessage != nil }, set: { if !$0 { snackMessage = nil } })) {
            Button("OK", role: .cancel) { snackMessage = nil }
        } message: { Text(snackMessage ?? "") }
        .sheet(item: $safariSheet) { item in
            SafariView(url: item.url)
        }
    }

    private func promoAppliedCard(code: String, discount: Int) -> some View {
        HStack {
            HStack(spacing: 12) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Theme.success)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Промокод применён")
                        .font(FCTypography.titleSmall())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onBackground)
                    Text("\(code) — скидка \(discount)%")
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.success)
                }
            }
            Spacer()
            Button {
                appliedPromo = nil
                discountPct = 0
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.success.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
    }

    private func subscriptionPlanCard(_ plan: SubscriptionPlan, onBuy: @escaping () -> Void) -> some View {
        let popular = plan.isPopular
        return VStack(spacing: 0) {
            if popular {
                Text("ПОПУЛЯРНЫЙ ВЫБОР")
                    .font(FCTypography.labelMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(
                        LinearGradient(
                            colors: [Theme.primary, Theme.accentOrange],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
            }
            VStack(alignment: .leading, spacing: 16) {
                Text(plan.safeName)
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
                Text(plan.safeDescription)
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                ForEach(Array(plan.safeFeatures.enumerated()), id: \.offset) { _, feature in
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(Theme.success)
                        Text(feature)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onBackground)
                    }
                }
                HStack(spacing: 8) {
                    if plan.safeDurationDays > 0 {
                        subscriptionChip(icon: "calendar", text: "\(plan.safeDurationDays) дней")
                    }
                    if let v = plan.visitsCount {
                        subscriptionChip(icon: "ticket.fill", text: "\(v) посещений")
                    }
                }
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 4) {
                        if discountPct > 0 {
                            Text("\(Int(plan.price)) ₽")
                                .font(FCTypography.bodyLarge())
                                .strikethrough()
                                .foregroundStyle(Theme.onSurfaceVariant)
                            Text("\(Int(plan.price * Double(100 - discountPct) / 100)) ₽")
                                .font(FCTypography.headlineSmall())
                                .fontWeight(.bold)
                                .foregroundStyle(Theme.success)
                        } else {
                            Text("\(Int(plan.price)) ₽")
                                .font(FCTypography.headlineSmall())
                                .fontWeight(.bold)
                                .foregroundStyle(Theme.onBackground)
                        }
                    }
                    Spacer()
                    Button(action: onBuy) {
                        Text("Купить")
                            .font(FCTypography.labelLarge())
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.onPrimary)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .background(Theme.primary)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous)
                .stroke(popular ? Theme.primary : Color.clear, lineWidth: popular ? 2 : 0)
        )
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private func subscriptionChip(icon: String, text: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption)
            Text(text)
                .font(FCTypography.labelMedium())
        }
        .foregroundStyle(Theme.onSurface)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Theme.surfaceVariant.opacity(0.8))
        .clipShape(Capsule())
    }

    private var subscriptionInfoCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "info.circle.fill")
                    .foregroundStyle(Theme.primary)
                Text("Информация")
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onBackground)
            }
            Text(
                "• Оплата онлайн или в клубе\n" +
                "• Если клуб включил проверку — подтверждение через Сбер ID перед оплатой\n" +
                "• Заморозка до 14 дней (по правилам тарифа)\n" +
                "• Возврат в течение 14 дней"
            )
            .font(FCTypography.bodyMedium())
            .foregroundStyle(Theme.onSurfaceVariant)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surfaceVariant.opacity(0.45))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
    }

    private func load() async {
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        do {
            let list = try await app.api.getSubscriptionPlans()
            plans = list.filter { !$0.safeId.isEmpty && !$0.safeName.isEmpty }
        } catch {
            loadError = (error as? FitnessAPIError)?.localizedDescription ?? error.localizedDescription
            plans = []
        }
    }

    private func applyPromoFromSheet() {
        isApplyingPromo = true
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 500_000_000)
            let map = ["WELCOME10": 10, "FITNESS20": 20, "NEWYEAR25": 25, "VIP30": 30]
            let key = promoField.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
            if let d = map[key] {
                appliedPromo = key
                discountPct = d
                showPromoSheet = false
            } else {
                snackMessage = "Промокод недействителен"
            }
            isApplyingPromo = false
        }
    }

    @ViewBuilder
    private func purchaseConfirmSheet(plan: SubscriptionPlan) -> some View {
        let final = discountPct > 0 ? Int(plan.price * Double(100 - discountPct) / 100) : Int(plan.price)
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Подтверждение покупки")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                Text(plan.safeName)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                if plan.safeDurationDays > 0 {
                    Text("Срок: \(plan.safeDurationDays) дней")
                        .font(FCTypography.bodyMedium())
                }
                if let v = plan.visitsCount {
                    Text("Посещений: \(v)")
                        .font(FCTypography.bodyMedium())
                }
                if let purchaseError {
                    Text(purchaseError)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.error)
                }
                Text("К оплате: \(final) ₽")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .padding(.top, 8)
                Spacer()
                Button {
                    Task { await confirmPurchase(plan) }
                } label: {
                    HStack(spacing: 10) {
                        if isPurchasing {
                            ProgressView()
                                .tint(Theme.onPrimary)
                        }
                        Text(isPurchasing ? "Оформление…" : "Оплатить")
                            .font(FCTypography.titleMedium())
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Theme.primary)
                    .foregroundStyle(Theme.onPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(isPurchasing)
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(Theme.background)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") {
                        purchaseSheetPlan = nil
                        purchaseError = nil
                    }
                    .disabled(isPurchasing)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func confirmPurchase(_ plan: SubscriptionPlan) async {
        isPurchasing = true
        purchaseError = nil
        defer { isPurchasing = false }
        let outcome = await app.api.purchaseSubscriptionParsed(planId: plan.safeId, promoCode: appliedPromo)
        switch outcome {
        case .success:
            purchaseSheetPlan = nil
            snackMessage = "Абонемент оформлен"
            dismiss()
        case .verificationRequired(let url, let msg):
            purchaseSheetPlan = nil
            snackMessage = msg
            safariSheet = SafariIdent(url: url)
        case .error(let msg):
            purchaseError = msg
        }
    }
}

struct SafariView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}

// MARK: - Shop

enum ShopCategory: String, CaseIterable {
    case services, subscriptions, goods, deposits

    var titleRu: String {
        switch self {
        case .services: return "Услуги"
        case .subscriptions: return "Абонементы"
        case .goods: return "Товары"
        case .deposits: return "Депозиты"
        }
    }
}

struct ShopItem: Identifiable, Hashable {
    let id: String
    let name: String
    let description: String
    let price: Double
    let oldPrice: Double?
    let isPromo: Bool
    let promoText: String?
    let category: ShopCategory
}

struct ShopView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var items: [ShopItem] = []
    @State private var isLoading = true
    @State private var toast: String?
    @State private var selectedCategory: ShopCategory = .services

    private var filtered: [ShopItem] {
        items.filter { $0.category == selectedCategory }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ShopCategory.allCases, id: \.self) { cat in
                        Button {
                            selectedCategory = cat
                        } label: {
                            Text(cat.titleRu)
                                .font(FCTypography.labelLarge())
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .foregroundStyle(selectedCategory == cat ? Theme.onPrimary : Theme.onSurface)
                                .background(selectedCategory == cat ? Theme.primary : Theme.surfaceVariant)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .background(Theme.surface)

            ZStack {
                Theme.background
                if isLoading {
                    ProgressView()
                        .tint(Theme.primary)
                } else if filtered.isEmpty {
                    shopEmptyState
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(filtered) { item in
                                shopItemCard(item)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 12)
                        .padding(.bottom, 28)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Магазин")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: AppRoute.purchaseHistory) {
                    Image(systemName: "list.clipboard.fill")
                        .foregroundStyle(Theme.onPrimary)
                }
            }
        }
        .task { await load() }
        .alert("Готово", isPresented: Binding(get: { toast != nil }, set: { if !$0 { toast = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(toast ?? "") }
    }

    private var shopEmptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "shippingbox.fill")
                .font(.system(size: 56))
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.45))
            Text("Товары скоро появятся")
                .font(FCTypography.bodyLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private func shopItemCard(_ item: ShopItem) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.name)
                        .font(FCTypography.titleMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onBackground)
                    if item.isPromo, let promo = item.promoText, !promo.isEmpty {
                        Text(promo)
                            .font(FCTypography.bodySmall())
                            .fontWeight(.medium)
                            .foregroundStyle(Theme.success)
                    }
                }
                Spacer(minLength: 8)
                if item.isPromo {
                    Text("АКЦИЯ")
                        .font(FCTypography.labelSmall())
                        .foregroundStyle(Theme.onPrimary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Theme.accentOrange)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
            }

            Spacer().frame(height: 8)

            Text(item.description)
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)

            Spacer().frame(height: 12)

            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    if let old = item.oldPrice {
                        Text("ЦЕНА: \(Int(old)) руб.")
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .strikethrough(true, color: Theme.onSurfaceVariant)
                        Text("СКИДКА: \(max(0, Int(old - item.price))) руб.")
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.error)
                    }
                    Text(item.price == 0 ? "БЕСПЛАТНО" : "ИТОГО: \(Int(item.price)) руб.")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(item.price == 0 ? Theme.success : Theme.onSurface)
                }
                Spacer(minLength: 8)
                shopBuyControl(for: item)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    @ViewBuilder
    private func shopBuyControl(for item: ShopItem) -> some View {
        if item.category == .subscriptions || item.id.hasPrefix("sub-") {
            NavigationLink(value: AppRoute.subscriptionPlans) {
                Text("КУПИТЬ")
                    .font(FCTypography.labelLarge())
                    .foregroundStyle(Theme.primary)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous)
                            .stroke(Theme.outlineVariant, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        } else {
            Button {
                Task { await buy(item) }
            } label: {
                Text(item.price == 0 ? "ПОЛУЧИТЬ" : "КУПИТЬ")
                    .font(FCTypography.labelLarge())
                    .foregroundStyle(Theme.primary)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous)
                            .stroke(Theme.outlineVariant, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        var all: [ShopItem] = []
        if let products = try? await app.api.getProducts() {
            all += products.map {
                ShopItem(
                    id: $0.id,
                    name: $0.name,
                    description: $0.description ?? "",
                    price: $0.price,
                    oldPrice: nil,
                    isPromo: false,
                    promoText: nil,
                    category: .services
                )
            }
        }
        all += extraSubscriptionRows()
        all += mockIfEmpty(base: all)
        items = all
    }

    private func extraSubscriptionRows() -> [ShopItem] {
        [
            ShopItem(id: "sub-1", name: "Безлимит на месяц", description: "Оформление на экране тарифов.", price: 4500, oldPrice: nil, isPromo: false, promoText: nil, category: .subscriptions),
            ShopItem(id: "sub-2", name: "Безлимит на 3 месяца", description: "Оформление на экране тарифов.", price: 12000, oldPrice: 13500, isPromo: true, promoText: "Скидка при оплате онлайн", category: .subscriptions),
            ShopItem(id: "dep-1", name: "Пополнение депозита", description: "На ресепшене клуба.", price: 1000, oldPrice: nil, isPromo: false, promoText: nil, category: .deposits),
        ]
    }

    private func mockIfEmpty(base: [ShopItem]) -> [ShopItem] {
        guard base.isEmpty else { return [] }
        return [
            ShopItem(id: "srv-1", name: "Пробная тренировка", description: "Запись в расписании.", price: 0, oldPrice: nil, isPromo: false, promoText: nil, category: .services),
            ShopItem(id: "srv-2", name: "Йога", description: "Групповое занятие.", price: 350, oldPrice: 450, isPromo: true, promoText: "Акция для новых клиентов", category: .services),
            ShopItem(id: "good-1", name: "Полотенце", description: "В клубе.", price: 800, oldPrice: nil, isPromo: false, promoText: nil, category: .goods),
        ] + extraSubscriptionRows()
    }

    private func buy(_ item: ShopItem) async {
        if item.category == .deposits || item.id.hasPrefix("dep-") {
            toast = "Депозит пополняется на ресепшене клуба или через администратора."
            return
        }
        if item.price == 0 {
            toast = "Бесплатная услуга: запись в разделе «Расписание»."
            return
        }
        if item.category == .subscriptions || item.id.hasPrefix("sub-") {
            return
        }
        guard item.id.hasPrefix("product-") || item.category == .services || item.category == .goods else {
            toast = "Обратитесь в клуб для оформления."
            return
        }
        do {
            _ = try await app.api.purchaseProduct(id: item.id, body: PurchaseProductRequest(quantity: 1, paymentMethod: "card"))
            toast = "Оформлено: \(item.name)"
        } catch {
            toast = "Не удалось оформить онлайн. Уточните в клубе."
        }
    }
}

// MARK: - Clubs (`ClubsScreen.kt`, `ClubInfoScreen.kt`)

struct ClubsListView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var clubs: [ClubItem] = []
    @State private var isLoading = true

    var body: some View {
        ZStack {
            Theme.background
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if clubs.isEmpty {
                clubsEmptyBody
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(clubs) { c in
                            clubListRow(c)
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 28)
                }
            }
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Сеть клубов")
        .task {
            isLoading = true
            defer { isLoading = false }
            clubs = (try? await app.api.getClubs()) ?? []
        }
    }

    private var clubsEmptyBody: some View {
        VStack(spacing: 16) {
            Image(systemName: "mappin.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("Клубы пока не добавлены")
                .font(FCTypography.bodyLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(32)
    }

    private func clubListRow(_ c: ClubItem) -> some View {
        ZStack(alignment: .trailing) {
            NavigationLink(value: AppRoute.clubDetail(c.id)) {
                HStack(spacing: 16) {
                    Image(systemName: "figure.strengthtraining.traditional")
                        .font(.system(size: 36))
                        .foregroundStyle(Theme.primary)
                        .frame(width: 48, alignment: .center)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(c.name)
                            .font(FCTypography.titleMedium())
                            .fontWeight(.bold)
                            .foregroundStyle(Theme.onBackground)
                        Text(c.address)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    Spacer(minLength: 52)
                }
                .padding(16)
                .padding(.trailing, (c.phone ?? "").isEmpty ? 0 : 12)
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
            }
            .buttonStyle(.plain)

            if let p = c.phone, !p.isEmpty {
                Button {
                    dialPhoneRaw(p)
                } label: {
                    Image(systemName: "phone.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(Theme.primary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .padding(.trailing, 12)
            }
        }
    }
}

struct ClubInfoView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    let clubId: String?
    @State private var info: ClubInfo?
    private var chromeTitle: String { info?.name ?? "FitnessClub" }

    var body: some View {
        Group {
            if let info {
                List {
                    Section {
                        Text(info.name)
                            .font(FCTypography.titleLarge())
                            .fontWeight(.bold)
                            .foregroundStyle(Theme.onBackground)
                        Text(info.address)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onSurfaceVariant)
                        if let promo = info.promoTitle, !promo.isEmpty {
                            Text(promo)
                                .font(FCTypography.bodyMedium())
                                .foregroundStyle(Theme.primary)
                        }
                        if let sub = info.promoSubtitle, !sub.isEmpty {
                            Text(sub)
                                .font(FCTypography.bodySmall())
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                    }
                    Section("Контакты") {
                        Text(info.phone)
                            .font(FCTypography.bodyMedium())
                        Text(info.email)
                            .font(FCTypography.bodyMedium())
                        Text(info.workingHours)
                            .font(FCTypography.bodyMedium())
                    }
                    if !info.amenities.isEmpty {
                        Section("Удобства") {
                            ForEach(info.amenities, id: \.self) { a in
                                Text(a)
                                    .font(FCTypography.bodyMedium())
                            }
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: chromeTitle)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if !(info?.phone ?? "").isEmpty {
                    Button {
                        dialPhoneRaw(info?.phone ?? "")
                    } label: {
                        Image(systemName: "phone.fill")
                    }
                }
            }
        }
        .task {
            if let clubId {
                info = try? await app.api.getClubDetails(id: clubId)
            } else {
                info = try? await app.api.getClubInfo()
            }
        }
    }
}

// MARK: - Trainers (`TrainersScreen.kt`, `TrainerDetailsScreen.kt`)

struct TrainersListView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var trainers: [Trainer] = []
    @State private var isLoading = true
    @State private var loadError: String?

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .tint(Theme.primary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let loadError {
                trainersErrorPane(loadError)
            } else if trainers.isEmpty {
                Text("Список тренеров пуст")
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(Array(trainers.enumerated()), id: \.offset) { i, t in
                            NavigationLink(value: AppRoute.trainerDetail(t.id ?? "trainer-\(i)")) {
                                trainerTeamCardRow(t)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 20)
                }
            }
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Наша команда")
        .task { await loadTrainers() }
    }

    private func trainersErrorPane(_ err: String) -> some View {
        VStack(spacing: 16) {
            Text(err)
                .font(FCTypography.bodyLarge())
                .foregroundStyle(Theme.error)
                .multilineTextAlignment(.center)
            Button("Повторить") {
                Task { await loadTrainers() }
            }
            .font(FCTypography.labelLarge())
            .foregroundStyle(Theme.onPrimary)
            .padding(.horizontal, 28)
            .padding(.vertical, 12)
            .background(Theme.primary)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }

    private func trainerTeamInitials(_ name: String) -> String {
        let parts = name.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        let letters = parts.compactMap { $0.first.map { String($0).uppercased() } }
        if letters.count >= 2 { return letters.prefix(2).joined() }
        if let f = letters.first { return f }
        return String(name.prefix(2)).uppercased()
    }

    /// Карточка как `TrainerCard` на Android — клик целиком + кнопка «Записаться» тем же переходом.
    private func trainerTeamCardRow(_ t: Trainer) -> some View {
        HStack(alignment: .center, spacing: 16) {
            ZStack {
                Circle()
                    .fill(Theme.primary.opacity(0.2))
                    .frame(width: 80, height: 80)
                Text(trainerTeamInitials(t.name))
                    .font(FCTypography.headlineSmall())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.primary)
                    .minimumScaleFactor(0.75)
                    .lineLimit(1)
            }
            VStack(alignment: .leading, spacing: 8) {
                Text(t.name)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
                    .fixedSize(horizontal: false, vertical: true)

                Text((t.specialization ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? "Специализация уточняется в клубе"
                    : (t.specialization ?? ""))
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle((t.specialization ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? Theme.onSurfaceVariant
                        : Theme.primary)
                    .lineLimit(2)

                if let r = t.rating, r > 0 {
                    HStack(spacing: 6) {
                        Image(systemName: "star.fill")
                            .foregroundStyle(Theme.accentOrange)
                            .font(.system(size: 16))
                        Text(String(format: "%.1f", r))
                            .font(FCTypography.bodyMedium())
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.onBackground)
                        Text("(отзывы)")
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                }

                Text("Записаться")
                    .font(FCTypography.titleSmall())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Theme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                    .padding(.top, 4)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 3, x: 0, y: 1)
    }

    private func loadTrainers() async {
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        do {
            trainers = try await app.api.getTrainers()
        } catch {
            trainers = []
            loadError = error.localizedDescription
        }
    }
}

struct TrainerDetailView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    let trainerId: String
    @State private var trainer: Trainer?
    @State private var isLoading = true
    @State private var loadError: String?

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .tint(Theme.primary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let loadError {
                VStack(spacing: 16) {
                    Text(loadError)
                        .foregroundStyle(Theme.error)
                        .multilineTextAlignment(.center)
                    Button("Повторить") { Task { await loadTrainer() } }
                        .font(FCTypography.labelLarge())
                        .foregroundStyle(Theme.onPrimary)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 12)
                        .background(Theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(24)
            } else if let trainer {
                ScrollView {
                    VStack(spacing: 24) {
                        ZStack {
                            Circle()
                                .fill(Theme.primary.opacity(0.2))
                                .frame(width: 120, height: 120)
                            Text(trainerDetailInitials(trainer.name))
                                .font(FCTypography.headlineMedium())
                                .fontWeight(.bold)
                                .foregroundStyle(Theme.primary)
                        }
                        Text(trainer.name)
                            .font(FCTypography.headlineSmall())
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)

                        if let spec = trainer.specialization, !spec.isEmpty {
                            Text(spec)
                                .font(FCTypography.titleMedium())
                                .foregroundStyle(Theme.primary)
                                .multilineTextAlignment(.center)
                        }

                        if let rating = trainer.rating, rating > 0 {
                            HStack(spacing: 8) {
                                Image(systemName: "star.fill")
                                    .foregroundStyle(Theme.accentOrange)
                                Text(String(format: "%.1f", rating))
                                    .font(FCTypography.titleMedium())
                                    .fontWeight(.semibold)
                            }
                        }

                        trainerHintCard

                        NavigationLink {
                            PersonalTrainingView()
                        } label: {
                            Text("Записаться на персональную")
                                .font(FCTypography.titleMedium())
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.onPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(Theme.primary)
                                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                    .frame(maxWidth: .infinity)
                }
            } else {
                Text("Тренер не найден")
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Тренер")
        .task { await loadTrainer() }
    }

    private var trainerHintCard: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "person.fill")
                .font(.system(size: 22))
                .foregroundStyle(Theme.primary)
            Text("Запишитесь на персональную тренировку и выберите удобное время в календаре.")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onBackground)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surfaceVariant.opacity(0.65))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
    }

    private func trainerDetailInitials(_ name: String) -> String {
        let parts = name.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        let letters = parts.compactMap { $0.first.map { String($0).uppercased() } }
        if letters.count >= 2 { return letters.prefix(2).joined() }
        if let f = letters.first { return f }
        return String(name.prefix(2)).uppercased()
    }

    private func loadTrainer() async {
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        do {
            trainer = try await app.api.getTrainer(id: trainerId)
        } catch {
            trainer = nil
            loadError = error.localizedDescription
        }
    }
}

// MARK: - Personal training (`PersonalTrainingScreen.kt`)

struct PersonalTrainingView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var weekStartMonday: Date = fcStartOfWeekMonday(containing: Date())
    @State private var selectedDate: Date = Date()
    @State private var allSlots: [PersonalSlot] = []
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var snack: String?
    @State private var selectedTypeFilter: String?
    @State private var selectedTrainerFilter: String?
    @State private var slotPendingBook: PersonalSlot?

    struct PersonalSlot: Identifiable, Hashable {
        let id: String
        let time: String
        let trainer: String
        let type: String
        let room: String
        let available: Bool
    }

    private var fcCal: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.locale = Locale(identifier: "ru_RU")
        c.firstWeekday = 2
        return c
    }

    private var daysInWeek: [Date] {
        (0..<7).compactMap { fcCal.date(byAdding: .day, value: $0, to: weekStartMonday) }
    }

    private var displayedSlots: [PersonalSlot] {
        allSlots.filter { s in
            (selectedTypeFilter == nil || s.type == selectedTypeFilter) &&
                (selectedTrainerFilter == nil || s.trainer == selectedTrainerFilter)
        }
    }

    private var monthTitleLabel: String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "ru_RU")
        df.dateFormat = "LLLL yyyy"
        let raw = df.string(from: selectedDate)
        return raw.prefix(1).uppercased() + raw.dropFirst()
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button {
                    shiftWeek(by: -1)
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Theme.primary)
                        .padding(12)
                }
                .buttonStyle(.plain)

                Text(monthTitleLabel)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onBackground)

                Spacer(minLength: 0)

                Button {
                    shiftWeek(by: 1)
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Theme.primary)
                        .padding(12)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 8)
            .padding(.top, 8)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(daysInWeek, id: \.timeIntervalSince1970) { d in
                        personalDayChip(d)
                    }
                }
                .padding(.horizontal, 8)
            }
            .padding(.top, 8)

            HStack(spacing: 8) {
                personalMenuChevron(title: selectedTypeFilter ?? "Все тренировки", hasValue: selectedTypeFilter != nil) {
                    Button("Все тренировки") { selectedTypeFilter = nil }
                    ForEach(typeOptions(), id: \.self) { t in
                        Button(t) { selectedTypeFilter = t }
                    }
                }
                personalMenuChevron(title: selectedTrainerFilter ?? "Тренер (все)", hasValue: selectedTrainerFilter != nil) {
                    Button("Все тренеры") { selectedTrainerFilter = nil }
                    ForEach(trainerOptions(), id: \.self) { t in
                        Button(t) { selectedTrainerFilter = t }
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)

            ZStack {
                Theme.background
                if loadError != nil {
                    personalErrorBody
                } else if isLoading {
                    ProgressView()
                        .tint(Theme.primary)
                } else if displayedSlots.isEmpty {
                    personalEmptyBody
                } else {
                    ScrollView {
                        LazyVStack(spacing: 6) {
                            ForEach(displayedSlots) { s in
                                personalSlotRow(s)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .padding(.bottom, 24)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Индивидуальная тренировка")
        .onAppear {
            weekStartMonday = fcStartOfWeekMonday(containing: selectedDate)
            if let match = daysInWeek.first(where: { fcCal.isDate($0, inSameDayAs: selectedDate) }) {
                selectedDate = match
            }
        }
        .task(id: isoDayKey(selectedDate)) { await load() }
        .alert("Записаться на тренировку?", isPresented: Binding(
            get: { slotPendingBook != nil },
            set: { if !$0 { slotPendingBook = nil } }
        )) {
            Button("Отмена", role: .cancel) { slotPendingBook = nil }
            Button("Записаться") {
                if let s = slotPendingBook {
                    Task { await bookConfirmed(s) }
                }
                slotPendingBook = nil
            }
        } message: {
            if let s = slotPendingBook {
                Text("\(s.type)\nВремя: \(s.time)\nТренер: \(s.trainer)\nЗал: \(s.room)")
            }
        }
        .alert("Готово", isPresented: Binding(get: { snack != nil }, set: { if !$0 { snack = nil } })) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(snack ?? "")
        }
    }

    private func typeOptions() -> [String] {
        Array(Set(allSlots.map(\.type))).sorted()
    }

    private func trainerOptions() -> [String] {
        Array(Set(allSlots.map(\.trainer))).sorted()
    }

    private func isoDayKey(_ d: Date) -> String {
        let c = fcCal.dateComponents([.year, .month, .day], from: d)
        guard let y = c.year, let m = c.month, let day = c.day else { return UUID().uuidString }
        return String(format: "%04d-%02d-%02d", y, m, day)
    }

    private func shiftWeek(by delta: Int) {
        guard let nw = fcCal.date(byAdding: .weekOfYear, value: delta, to: weekStartMonday),
              let ns = fcCal.date(byAdding: .weekOfYear, value: delta, to: selectedDate) else { return }
        weekStartMonday = nw
        selectedDate = ns
        if daysInWeek.contains(where: { fcCal.isDate($0, inSameDayAs: selectedDate) }) == false {
            selectedDate = weekStartMonday
        }
    }

    private func personalDayChip(_ day: Date) -> some View {
        let sel = fcCal.isDate(day, inSameDayAs: selectedDate)
        let weekend = fcCal.isDateInWeekend(day)
        let dayNum = fcCal.component(.day, from: day)
        let df = DateFormatter()
        df.locale = Locale(identifier: "ru_RU")
        df.dateFormat = "EE"
        var short = df.string(from: day)
        short = short.replacingOccurrences(of: ".", with: "")
        short = short.prefix(1).uppercased() + short.dropFirst()
        let bg = sel ? Theme.primary : Theme.surface
        let muted = sel ? Theme.onPrimary.opacity(0.8) : Theme.onSurface.opacity(weekend ? 1 : 0.65)
        let strong = sel ? Theme.onPrimary : (weekend ? Theme.accentOrange : Theme.onSurface)

        return Button {
            selectedDate = day
        } label: {
            VStack(spacing: 4) {
                Text(short.uppercased())
                    .font(FCTypography.labelSmall())
                    .foregroundStyle(muted)
                Text("\(dayNum)")
                    .font(FCTypography.titleMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(strong)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func personalMenuChevron<C: View>(title: String, hasValue: Bool, @ViewBuilder items: () -> C) -> some View {
        Menu {
            items()
        } label: {
            Label(title, systemImage: hasValue ? "line.3.horizontal.decrease.circle.fill" : "chevron.down")
                .font(FCTypography.labelMedium())
                .foregroundStyle(hasValue ? Theme.primary : Theme.onBackground)
                .frame(maxWidth: 180, alignment: .leading)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius28, style: .continuous))
    }

    private var personalErrorBody: some View {
        VStack(spacing: 16) {
            Text(loadError ?? "")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.error)
                .multilineTextAlignment(.center)
            Button("Повторить") { Task { await load() } }
                .font(FCTypography.labelLarge())
                .foregroundStyle(Theme.onPrimary)
                .padding(.horizontal, 28)
                .padding(.vertical, 12)
                .background(Theme.primary)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var personalEmptyBody: some View {
        VStack(spacing: 16) {
            Image(systemName: "calendar.badge.exclamationmark")
                .font(.system(size: 56))
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.45))
            Text("Нет доступных слотов на эту дату")
                .font(FCTypography.bodyLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Text("Выберите другой день или снимите фильтры. Слоты приходят из CRM: на эту дату нет свободных персональных занятий.")
                .font(FCTypography.bodySmall())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 8)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func personalSlotRow(_ slot: PersonalSlot) -> some View {
        let parts = slot.time.split(separator: "-", maxSplits: 1).map(String.init)
        let t0 = parts.first ?? slot.time
        let t1 = parts.count > 1 ? parts[1] : ""

        return Button {
            guard slot.available else { return }
            slotPendingBook = slot
        } label: {
            HStack(alignment: .center, spacing: 12) {
                VStack(spacing: 2) {
                    Text(t0)
                        .font(FCTypography.titleMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(slot.available ? Theme.primary : Theme.onSurfaceVariant)
                    if !t1.isEmpty {
                        Text(t1)
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                }
                .frame(width: 62)

                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(slot.available ? Theme.primary : Theme.onSurfaceVariant)
                    .frame(width: 3, height: 50)

                VStack(alignment: .leading, spacing: 2) {
                    Text(slot.type)
                        .font(FCTypography.titleSmall())
                        .fontWeight(.semibold)
                        .foregroundStyle(slot.available ? Theme.primary : Theme.onSurfaceVariant)
                    Text(slot.trainer)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.onBackground)
                    Text(slot.room)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if !slot.available {
                    Text("Занято")
                        .font(FCTypography.labelSmall())
                        .foregroundStyle(Theme.error)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity)
            .background(slot.available ? Theme.surface : Theme.surfaceVariant.opacity(0.55))
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
        }
        .buttonStyle(.plain)
        .allowsHitTesting(slot.available)
    }

    private func load() async {
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        let c = fcCal.dateComponents([.year, .month, .day], from: selectedDate)
        guard let y = c.year, let m = c.month, let d = c.day else { return }
        let ds = String(format: "%04d-%02d-%02d", y, m, d)
        do {
            let list = try await app.api.getTrainings(date: ds, type: "personal")
            allSlots = list.filter { $0.type == .personal }.map { t in
                PersonalSlot(
                    id: t.id,
                    time: "\(hm(t.startTime))-\(hm(t.endTime))",
                    trainer: t.trainer.name.isEmpty ? "Без тренера" : t.trainer.name,
                    type: t.name,
                    room: t.room,
                    available: !t.isBooked && t.spotsLeft > 0
                )
            }
        } catch {
            allSlots = []
            loadError = error.localizedDescription
        }
    }

    private func hm(_ iso: String) -> String {
        guard let r = iso.range(of: "T") else { return "??:??" }
        let s = iso.index(after: r.lowerBound)
        let e = iso.index(s, offsetBy: 5, limitedBy: iso.endIndex) ?? iso.endIndex
        return String(iso[s..<e])
    }

    private func bookConfirmed(_ s: PersonalSlot) async {
        do {
            _ = try await app.api.bookTraining(id: s.id)
            snack = "Вы успешно записаны на \(s.time.split(separator: "-").first.map(String.init) ?? s.time)"
            await load()
        } catch {
            snack = error.localizedDescription
        }
    }
}

// MARK: - Lockers (`LockerScreen.kt`)

struct LockersView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var lockers: [Locker] = []
    @State private var booking: LockerBooking?
    @State private var bannerError: String?
    @State private var alertMessage: String?
    @State private var showLockerQr = false
    @State private var qrSheetBooking: LockerBooking?
    @State private var isLoading = false

    private let gridColumns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 4)

    var body: some View {
        ZStack {
            Theme.background

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let bannerErrorText = bannerError {
                        Text(bannerErrorText)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.error)
                            .padding(14)
                            .frame(maxWidth: .infinity)
                            .background(Theme.error.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                            .onTapGesture { bannerError = nil }
                            .padding(.horizontal, 16)
                    }

                    if let booking {
                        lockerMyBookingCard(booking)
                    } else if isLoading {
                        ProgressView()
                            .tint(Theme.primary)
                            .frame(maxWidth: .infinity)
                            .padding(60)
                    } else {
                        Text("Выберите свободный шкафчик")
                            .font(FCTypography.titleMedium())
                            .fontWeight(.medium)
                            .foregroundStyle(Theme.onBackground)
                            .padding(.horizontal, 16)
                            .padding(.top, 4)

                        LazyVGrid(columns: gridColumns, spacing: 12) {
                            ForEach(lockers) { l in
                                lockerCell(l)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)
                    }
                }
                .padding(.top, 16)
            }
        }
        .fcPrimaryNavigation(title: "Шкафчики")
        .sheet(isPresented: $showLockerQr, onDismiss: { qrSheetBooking = nil }) {
            if let booking = qrSheetBooking {
                NavigationStack {
                    VStack(spacing: 16) {
                        Text("QR-код шкафчика")
                            .font(FCTypography.titleLarge())
                            .fontWeight(.semibold)

                        if let img = QRCodeGenerator.image(from: booking.qrCodeData, dimension: 256) {
                            Image(uiImage: img)
                                .interpolation(.none)
                                .resizable()
                                .scaledToFit()
                                .padding(14)
                                .frame(width: 220, height: 220)
                                .background(Theme.surface)
                                .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                        }

                        Text("Поднесите к сканеру на дверце")
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .multilineTextAlignment(.center)

                        Spacer(minLength: 8)
                        Button("Закрыть") { showLockerQr = false }
                            .font(FCTypography.titleMedium())
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity)
                    .background(Theme.background)
                    .navigationBarTitleDisplayMode(.inline)
                }
                .presentationDetents([.medium, .large])
            }
        }
        .task { await refresh() }
        .alert("Ошибка", isPresented: Binding(get: { alertMessage != nil }, set: { if !$0 { alertMessage = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(alertMessage ?? "") }
    }

    private func lockerMyBookingCard(_ booking: LockerBooking) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Ваш шкафчик")
                        .font(FCTypography.labelMedium())
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text("№ \(booking.locker.number)")
                        .font(FCTypography.headlineMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onBackground)
                }
                Spacer()
                Image(systemName: "lock.open.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(Theme.primary)
            }
            Text("До \(fcLockerEndsAtFormatted(booking.endsAt))")
                .font(FCTypography.bodySmall())
                .foregroundStyle(Theme.onSurfaceVariant)

            HStack(spacing: 8) {
                Button {
                    qrSheetBooking = booking
                    showLockerQr = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "qrcode")
                        Text("Показать QR")
                            .font(FCTypography.titleSmall())
                            .fontWeight(.semibold)
                    }
                    .foregroundStyle(Theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Theme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                }
                .buttonStyle(.plain)

                Button("Освободить") {
                    Task {
                        do {
                            try await app.api.releaseLocker()
                            await refresh()
                        } catch {
                            alertMessage = error.localizedDescription
                        }
                    }
                }
                .font(FCTypography.titleSmall())
                .foregroundStyle(Theme.primary)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous)
                        .stroke(Theme.outlineVariant, lineWidth: 1)
                )
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
        .padding(.horizontal, 16)
    }

    private func lockerCell(_ l: Locker) -> some View {
        let available = l.status == "available"
        return Button {
            guard available else { return }
            Task {
                do {
                    booking = try await app.api.bookLocker(id: l.id)
                } catch {
                    alertMessage = error.localizedDescription
                }
            }
        } label: {
            VStack(spacing: 6) {
                Image(systemName: available ? "lock.open.fill" : "lock.fill")
                    .font(.title3)
                    .foregroundStyle(available ? Theme.primary : Theme.onSurfaceVariant)
                Text(l.number)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
            }
            .frame(maxWidth: .infinity)
            .aspectRatio(1, contentMode: .fit)
            .padding(8)
            .background(
                Group {
                    if available {
                        Theme.primary.opacity(0.18)
                    } else {
                        Theme.surfaceVariant.opacity(0.72)
                    }
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
        }
        .buttonStyle(.plain)
        .opacity(available ? 1 : 0.6)
        .allowsHitTesting(available && booking == nil)
    }

    private func refresh() async {
        isLoading = lockers.isEmpty && booking == nil
        defer { isLoading = false }
        lockers = (try? await app.api.getLockers()) ?? []
        booking = try? await app.api.getMyLockerBooking()
    }
}

// MARK: - Guest pass (`GuestPassScreen.kt`)

struct GuestPassView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var passes: [GuestPass] = []
    @State private var isLoading = true
    @State private var showCreate = false
    @State private var guestDraft = ""
    @State private var showQrPass: GuestPass?
    @State private var bannerError: String?

    var body: some View {
        ZStack {
            Theme.background

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    infoInviteCard

                    if let bannerErrorText = bannerError {
                        Text(bannerErrorText)
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.error)
                            .padding(.horizontal, 16)
                    }

                    Text("Мои пропуска")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onBackground)
                        .padding(.horizontal, 16)

                    if isLoading {
                        ProgressView()
                            .tint(Theme.primary)
                            .frame(maxWidth: .infinity)
                            .padding(40)
                    } else if passes.isEmpty {
                        guestEmptyCard
                    } else {
                        LazyVStack(spacing: 12) {
                            ForEach(passes) { p in
                                guestPassRow(p)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)
                    }
                }
                .padding(.top, 16)
            }
        }
        .fcPrimaryNavigation(title: "Гостевой пропуск")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    guestDraft = ""
                    showCreate = true
                } label: {
                    Image(systemName: "plus")
                        .foregroundStyle(Theme.onPrimary)
                }
            }
        }
        .sheet(isPresented: $showCreate) {
            NavigationStack {
                Form {
                    TextField("Имя гостя (необязательно)", text: $guestDraft)
                }
                .navigationTitle("Создать пропуск")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Отмена") { showCreate = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Создать") {
                            Task {
                                do {
                                    _ = try await app.api.createGuestPass(
                                        CreateGuestPassRequest(guestName: guestDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                            ? nil
                                            : guestDraft)
                                    )
                                    showCreate = false
                                    await load()
                                } catch {
                                    bannerError = error.localizedDescription
                                }
                            }
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .sheet(item: $showQrPass) { pass in
            NavigationStack {
                VStack(spacing: 20) {
                    Text("Гостевой пропуск")
                        .font(FCTypography.titleLarge())
                        .fontWeight(.semibold)
                    Text(pass.guestName ?? "Гость")
                        .font(FCTypography.titleMedium())

                    if let img = QRCodeGenerator.image(from: pass.qrCodeData, dimension: 280) {
                        Image(uiImage: img)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 240, height: 240)
                            .padding(12)
                            .background(Theme.surface)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                    }

                    Text("Покажите QR на входе")
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)

                    Button("Закрыть") { showQrPass = nil }
                        .font(FCTypography.titleMedium())
                    Spacer()
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Theme.background)
            }
        }
        .task { await load() }
    }

    private var infoInviteCard: some View {
        HStack(alignment: .center, spacing: 16) {
            Image(systemName: "person.badge.plus.fill")
                .font(.system(size: 38))
                .foregroundStyle(Theme.primary)
            VStack(alignment: .leading, spacing: 6) {
                Text("Приведите друга")
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                Text("Создайте пропуск и покажите гостю QR-код для входа. Один пропуск = один визит.")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.primary.opacity(0.18))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
        .padding(.horizontal, 16)
    }

    private var guestEmptyCard: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.crop.circle.badge.plus")
                .font(.system(size: 56))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("Нет пропусков")
                .font(FCTypography.titleMedium())
                .fontWeight(.semibold)
            Text("Создайте пропуск для гостя")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
            Button {
                guestDraft = ""
                showCreate = true
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "plus.circle.fill")
                    Text("Создать пропуск")
                        .fontWeight(.semibold)
                }
                .foregroundStyle(Theme.onPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Theme.primary)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(36)
        .frame(maxWidth: .infinity)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
        .padding(.horizontal, 16)
    }

    private func guestPassRow(_ p: GuestPass) -> some View {
        let active = p.status.lowercased() == "active"
        return HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text(p.guestName ?? "Гость")
                    .font(FCTypography.titleSmall())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onBackground)
                Text(passStatusSubtitle(p.status))
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            Spacer(minLength: 8)
            if active {
                Button {
                    showQrPass = p
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "qrcode")
                        Text("Показать QR")
                            .font(FCTypography.labelLarge())
                            .fontWeight(.semibold)
                    }
                    .foregroundStyle(Theme.onPrimary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Theme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(active ? Theme.surface : Theme.surfaceVariant.opacity(0.55))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
    }

    private func passStatusSubtitle(_ status: String) -> String {
        switch status.lowercased() {
        case "active", "активен": return "Активен"
        case "used", "completed": return "Использован"
        default: return passStatusRu(status)
        }
    }

    private func passStatusRu(_ s: String) -> String {
        switch s.lowercased() {
        case "active", "активен": return "Активен"
        case "expired": return "Истёк"
        case "cancelled": return "Отменён"
        default: return s
        }
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        passes = (try? await app.api.getGuestPasses()) ?? []
    }
}

struct DocumentsView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var docs: [ApiDocument] = []
    @State private var importer = false
    @State private var message: String?
    @State private var bannerError: String?
    @State private var isLoading = true

    var body: some View {
        ZStack {
            Theme.background
            VStack(spacing: 12) {
                if let bannerErrorText = bannerError {
                    HStack(spacing: 12) {
                        Text(bannerErrorText)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.error)
                            .frame(maxWidth: .infinity)
                        Button("OK") {
                            bannerError = nil
                        }
                        .font(FCTypography.labelLarge())
                        .foregroundStyle(Theme.primary)
                    }
                    .padding(14)
                    .background(Theme.error.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }

                Group {
                    if isLoading {
                        ProgressView()
                            .tint(Theme.primary)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if docs.isEmpty {
                        documentsEmptyState
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 10) {
                                ForEach(docs) { d in
                                    documentDownloadCard(d)
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 28)
                            .padding(.top, 6)
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .fcPrimaryNavigation(title: "Документы")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    importer = true
                } label: {
                    Image(systemName: "arrow.up.doc.fill")
                        .foregroundStyle(Theme.onPrimary)
                }
            }
        }
        .fileImporter(
            isPresented: $importer,
            allowedContentTypes: [.pdf, .jpeg, .png, .plainText, .item],
            allowsMultipleSelection: false
        ) { result in
            Task {
                guard case .success(let urls) = result, let url = urls.first else { return }
                let got = url.startAccessingSecurityScopedResource()
                defer { if got { url.stopAccessingSecurityScopedResource() } }
                do {
                    let data = try Data(contentsOf: url)
                    let name = url.lastPathComponent
                    let mime = mimeFor(url: url)
                    _ = try await app.api.uploadDocument(
                        fileData: data,
                        fileName: name,
                        mime: mime,
                        displayName: name,
                        category: "upload"
                    )
                    await load()
                } catch {
                    await MainActor.run { bannerError = error.localizedDescription }
                }
            }
        }
        .task { await load() }
        .alert("", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(message ?? "") }
    }

    private var documentsEmptyState: some View {
        VStack(spacing: 18) {
            Image(systemName: "doc.text.fill")
                .font(.system(size: 64))
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.45))
            Text("Нет документов")
                .font(FCTypography.titleMedium())
                .fontWeight(.semibold)
            Text("Загрузите договор, справку или другой файл — он появится в списке.")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12)
            Button {
                importer = true
            } label: {
                Text("Загрузить")
                    .font(FCTypography.titleSmall())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Theme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 32)
            .padding(.top, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }

    private func documentDownloadCard(_ d: ApiDocument) -> some View {
        Button {
            Task {
                await downloadDocument(d)
            }
        } label: {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: "doc.richtext.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(Theme.primary)

                VStack(alignment: .leading, spacing: 4) {
                    Text(d.name)
                        .font(FCTypography.titleSmall())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onBackground)
                        .multilineTextAlignment(.leading)
                    Text(formatDocDate(d.createdAt))
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                Spacer(minLength: 8)
                Text("Скачать")
                    .font(FCTypography.labelLarge())
                    .foregroundStyle(Theme.primary)
                    .padding(.top, 2)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 1)
        }
        .buttonStyle(.plain)
    }

    private func downloadDocument(_ d: ApiDocument) async {
        do {
            let data = try await app.api.downloadDocument(id: d.id)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(d.name)
            try data.write(to: url)
            await MainActor.run {
                message = "Сохранено во временные файлы: \(url.lastPathComponent)"
            }
        } catch {
            await MainActor.run { bannerError = error.localizedDescription }
        }
    }

    private func formatDocDate(_ iso: String) -> String {
        fcFormatRussianDate(fromIso: iso)
    }

    private func mimeFor(url: URL) -> String {
        let ext = url.pathExtension.lowercased()
        switch ext {
        case "pdf": return "application/pdf"
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        default: return "application/octet-stream"
        }
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        docs = (try? await app.api.getDocuments()) ?? []
    }
}

// MARK: - Purchases

struct PurchaseHistoryView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var items: [PurchaseItem] = []
    @State private var isLoading = true

    var body: some View {
        ZStack {
            Theme.background
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if items.isEmpty {
                purchaseEmptyBody
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(items) { p in
                            purchaseCard(p)
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 28)
                }
            }
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "История покупок")
        .task { await load() }
    }

    private var purchaseEmptyBody: some View {
        VStack(spacing: 16) {
            Image(systemName: "doc.text.fill")
                .font(.system(size: 56))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("Пока нет покупок")
                .font(FCTypography.titleMedium())
                .fontWeight(.medium)
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("Чеки появятся здесь после покупок")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(32)
    }

    private func purchaseCard(_ p: PurchaseItem) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                Text(p.productName)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.medium)
                    .foregroundStyle(Theme.onBackground)
                    .multilineTextAlignment(.leading)
                Spacer()
                Text("\(Int(p.total)) ₽")
                    .font(FCTypography.titleMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.primary)
            }
            HStack {
                Text("\(p.quantity) шт. × \(Int(p.price)) ₽")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
                Spacer()
                Text(fcFormatRussianDate(fromIso: p.createdAt))
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        items = (try? await app.api.getPurchases()) ?? []
    }
}

// MARK: - Edit profile (`EditProfileScreen.kt`)

struct EditProfileView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var name = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var message: String?

    var body: some View {
        Form {
            Section {
                TextField("Имя", text: $name)
                TextField("Телефон", text: $phone)
                    .keyboardType(.phonePad)
                TextField("Email", text: $email)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Редактировать профиль")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Сохранить") {
                    Task { await save() }
                }
                .font(FCTypography.labelLarge())
                .fontWeight(.semibold)
            }
        }
        .task {
            if let u = app.currentUser {
                name = u.name
                phone = u.phone
                email = u.email
            }
        }
        .alert("", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(message ?? "") }
    }

    private func save() async {
        guard var u = app.currentUser else { return }
        u = User(
            id: u.id,
            email: email,
            phone: phone,
            name: name,
            avatarUrl: u.avatarUrl,
            bonusPoints: u.bonusPoints,
            passportVerificationStatus: u.passportVerificationStatus,
            dateOfBirth: u.dateOfBirth,
            createdAt: u.createdAt,
            isVerified: u.isVerified,
            sberId: u.sberId
        )
        do {
            let updated = try await app.api.updateProfile(u)
            app.updateCachedUser(updated)
            message = "Сохранено"
        } catch {
            message = error.localizedDescription
        }
    }
}

// MARK: - Referral (`ReferralScreen.kt`)

struct ReferralView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var showCopied = false

    private var referralCode: String {
        guard let id = app.currentUser?.id, id.count >= 6 else { return "REF------" }
        return "REF\(id.suffix(6).uppercased())"
    }

    private var referralLink: String {
        "https://fitnessclub.app/invite/\(referralCode)"
    }

    private var shareMessage: String {
        "Присоединяйся к FitnessClub! Используй мой промокод \(referralCode) при регистрации и получи скидку! \(referralLink)"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Theme.primary, Theme.accentOrange],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 120, height: 120)
                    Image(systemName: "person.3.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(Theme.onPrimary)
                }
                .padding(.top, 8)

                Text("Приглашай друзей и получай бонусы!")
                    .font(FCTypography.headlineSmall())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
                    .multilineTextAlignment(.center)

                Text("За каждого приглашённого друга вы оба получите бонусные баллы")
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .multilineTextAlignment(.center)

                VStack(spacing: 20) {
                    Text("Ваш реферальный код")
                        .font(FCTypography.titleMedium())
                        .foregroundStyle(Theme.onPrimary)
                    Text(referralCode)
                        .font(FCTypography.headlineLarge())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onPrimary.opacity(0.95))

                    HStack(spacing: 12) {
                        Button {
                            UIPasteboard.general.string = referralCode
                            showCopied = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                showCopied = false
                            }
                        } label: {
                            HStack {
                                Image(systemName: "doc.on.doc")
                                Text("Копировать")
                            }
                            .font(FCTypography.labelLarge())
                            .foregroundStyle(Theme.onPrimary)
                            .frame(height: 48)
                            .padding(.horizontal, 16)
                            .overlay(
                                RoundedRectangle(cornerRadius: Theme.radius12)
                                    .stroke(Theme.onPrimary, lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)

                        ShareLink(item: shareMessage) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.title3)
                                .foregroundStyle(Theme.onPrimary)
                                .frame(width: 48, height: 48)
                                .background(Theme.onPrimary.opacity(0.25))
                                .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                        }
                    }
                }
                .padding(24)
                .frame(maxWidth: .infinity)
                .background(Theme.accentOrange)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))

                if showCopied {
                    Text("Код скопирован")
                        .font(FCTypography.labelLarge())
                        .foregroundStyle(Theme.success)
                }

                Text("Ваши награды")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 12) {
                    referralRewardCard(icon: "person.fill", title: "Вы получите", value: "500", subtitle: "бонусов")
                    referralRewardCard(icon: "person.badge.plus", title: "Друг получит", value: "300", subtitle: "бонусов")
                }

                VStack(alignment: .leading, spacing: 16) {
                    Text("Статистика")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onBackground)
                    referralStatRow(icon: "person.3", label: "Приглашено друзей", value: "5")
                    Divider()
                    referralStatRow(icon: "checkmark.circle", label: "Зарегистрировалось", value: "3")
                    Divider()
                    referralStatRow(icon: "star.fill", label: "Заработано бонусов", value: "1500")
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)

                VStack(alignment: .leading, spacing: 16) {
                    Text("Как это работает")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onBackground)
                    referralHowStep(number: "1", text: "Поделитесь своим кодом с друзьями")
                    referralHowStep(number: "2", text: "Друг регистрируется с вашим кодом")
                    referralHowStep(number: "3", text: "Друг покупает абонемент")
                    referralHowStep(number: "4", text: "Вы оба получаете бонусы!")
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.surfaceVariant.opacity(0.45))
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
            }
            .padding(16)
            .padding(.bottom, 24)
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Приведи друга")
    }

    private func referralRewardCard(icon: String, title: String, value: String, subtitle: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(Theme.primary)
            Text(title)
                .font(FCTypography.bodySmall())
                .foregroundStyle(Theme.onSurfaceVariant)
            Text(value)
                .font(FCTypography.headlineMedium())
                .fontWeight(.bold)
                .foregroundStyle(Theme.accentOrange)
            Text(subtitle)
                .font(FCTypography.bodySmall())
                .foregroundStyle(Theme.onBackground)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
        .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 1)
    }

    private func referralStatRow(icon: String, label: String, value: String) -> some View {
        HStack {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .foregroundStyle(Theme.onSurfaceVariant)
                Text(label)
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onBackground)
            }
            Spacer()
            Text(value)
                .font(FCTypography.titleMedium())
                .fontWeight(.semibold)
                .foregroundStyle(Theme.onBackground)
        }
    }

    private func referralHowStep(number: String, text: String) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Text(number)
                .font(FCTypography.labelLarge())
                .fontWeight(.bold)
                .foregroundStyle(Theme.onPrimary)
                .frame(width: 28, height: 28)
                .background(Theme.primary)
                .clipShape(Circle())
            Text(text)
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurface)
        }
    }
}

// MARK: - Notifications (`NotificationsScreen.kt`)

fileprivate func fcParseNotificationCreatedAt(_ raw: String) -> Date? {
    let s = raw.replacingOccurrences(of: " ", with: "T")
    let f1 = ISO8601DateFormatter()
    f1.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let d = f1.date(from: s) { return d }
    let f2 = ISO8601DateFormatter()
    f2.formatOptions = [.withInternetDateTime]
    if let d = f2.date(from: s) { return d }
    let df = DateFormatter()
    df.locale = Locale(identifier: "en_US_POSIX")
    df.timeZone = TimeZone(secondsFromGMT: 0)
    df.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
    if let d = df.date(from: s) { return d }
    df.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    return df.date(from: s)
}

fileprivate func fcNotificationRelativeTime(from createdAt: String) -> String {
    guard let created = fcParseNotificationCreatedAt(createdAt) else { return "" }
    let now = Date()
    let mins = max(0, Int(now.timeIntervalSince(created) / 60))
    if mins < 60 { return "\(mins) мин" }
    let hours = mins / 60
    if hours < 24 { return "\(hours) ч" }
    let cal = Calendar.current
    let days = cal.dateComponents([.day], from: cal.startOfDay(for: created), to: cal.startOfDay(for: now)).day ?? 0
    if days == 1 { return "Вчера" }
    if days < 7 { return "\(days) дней" }
    return "Неделю"
}

fileprivate func fcNotificationVisual(_ apiType: String) -> (icon: String, color: Color) {
    switch apiType.lowercased() {
    case "training_reminder": ("figure.strengthtraining.traditional", Theme.accentOrange)
    case "booking_confirmed": ("checkmark.circle.fill", Theme.success)
    case "booking_cancelled": ("xmark.circle.fill", Theme.error)
    case "spot_freed": ("calendar.badge.plus", Theme.success)
    case "schedule_change": ("clock.fill", Theme.warning)
    case "promo": ("tag.fill", Theme.primary)
    case "subscription": ("creditcard.fill", Theme.accentBlue)
    case "bonus": ("star.fill", Theme.accentOrange)
    default: ("info.circle.fill", Theme.onSurfaceVariant)
    }
}

struct NotificationsView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var items: [ApiNotification] = []
    @State private var isLoading = true

    private var hasUnread: Bool { items.contains { !$0.isRead } }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            if isLoading {
                ProgressView()
                    .tint(Theme.primary)
            } else if items.isEmpty {
                notificationsEmptyState
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(items) { n in
                            notificationCard(n)
                        }
                    }
                    .padding(16)
                }
            }
        }
        .fcPrimaryNavigation(title: "Уведомления")
        .toolbar {
            if hasUnread {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Прочитать все") {
                        Task {
                            try? await app.api.markAllNotificationsRead()
                            await load()
                        }
                    }
                    .font(FCTypography.labelLarge())
                    .foregroundStyle(Theme.onPrimary)
                }
            }
        }
        .task { await load() }
    }

    private var notificationsEmptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "bell.slash.fill")
                .font(.system(size: 72))
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.45))
            Text("Нет уведомлений")
                .font(FCTypography.titleLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("Здесь будут появляться уведомления о тренировках, акциях и новостях клуба")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.75))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }

    private func notificationCard(_ n: ApiNotification) -> some View {
        let visual = fcNotificationVisual(n.type)
        let bg = n.isRead ? Theme.surface : Theme.primary.opacity(0.14)

        return Button {
            Task {
                if !n.isRead {
                    try? await app.api.markNotificationRead(id: n.id)
                }
                await load()
            }
        } label: {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    Circle()
                        .fill(visual.color.opacity(0.16))
                        .frame(width: 44, height: 44)
                    Image(systemName: visual.icon)
                        .font(.system(size: 20))
                        .foregroundStyle(visual.color)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(n.title)
                            .font(FCTypography.titleSmall())
                            .fontWeight(n.isRead ? .regular : .semibold)
                            .foregroundStyle(Theme.onBackground)
                            .lineLimit(1)
                        Spacer(minLength: 4)
                        Text(fcNotificationRelativeTime(from: n.createdAt))
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    Text(n.message)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .lineLimit(2)
                }

                if !n.isRead {
                    Circle()
                        .fill(Theme.primary)
                        .frame(width: 8, height: 8)
                        .padding(.top, 6)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
            .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        items = (try? await app.api.getNotifications()) ?? []
    }
}

// MARK: - Settings / Help / About (`SettingsScreen.kt`, `HelpScreen.kt`, `AboutScreen.kt`)

struct SettingsView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.openURL) private var openURL
    @State private var showFeedback = false
    @State private var rating = 5
    @State private var comment = ""
    @State private var toast: String?
    @State private var pushEnabled = true
    @State private var emailEnabled = true
    @State private var trainingReminders = true
    @State private var scheduleChanges = true
    @State private var promoNotifications = false
    @State private var biometricEnrollPresented = false
    @State private var biometricEnrollPassword = ""
    @State private var biometricEnrollBusy = false

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                settingsSectionTitle("Уведомления")
                settingsCard {
                    settingsToggle(icon: "bell.fill", title: "Push-уведомления", subtitle: "Получать push-уведомления", on: $pushEnabled)
                    Divider().padding(.leading, 52)
                    settingsToggle(icon: "envelope.fill", title: "Email-уведомления", subtitle: "Получать уведомления на почту", on: $emailEnabled)
                    Divider().padding(.leading, 52)
                    settingsToggle(icon: "figure.strengthtraining.traditional", title: "Напоминания о тренировках", subtitle: "За 1 час до начала", on: $trainingReminders)
                    settingsToggle(icon: "calendar", title: "Изменения в расписании", subtitle: "Уведомлять об изменениях", on: $scheduleChanges)
                    settingsToggle(icon: "tag.fill", title: "Акции и предложения", subtitle: "Специальные предложения клуба", on: $promoNotifications)
                }

                settingsSectionTitle("Безопасность")
                settingsCard {
                    settingsRow(icon: "lock.fill", title: "Изменить пароль", subtitle: nil) {
                        openURL(AppConfiguration.forgotPasswordURL)
                    }
                    Divider().padding(.leading, 52)
                    settingsToggle(icon: "touchid", title: "Биометрия", subtitle: "Вход по отпечатку пальца", on: Binding(
                        get: { BiometricCredentialStore.hasSavedLogin },
                        set: { on in
                            if !on {
                                BiometricCredentialStore.clear()
                                return
                            }
                            if BiometryLoginUX.canUseBiometrics {
                                biometricEnrollPassword = ""
                                biometricEnrollPresented = true
                            } else {
                                toast = "На устройстве недоступна биометрия"
                            }
                        }
                    ))
                    Divider().padding(.leading, 52)
                    settingsRow(icon: "shield.checkered", title: "Двухфакторная аутентификация", subtitle: nil) {
                        toast = "2FA появится в обновлении приложения"
                    }
                }

                settingsSectionTitle("Приложение")
                settingsCard {
                    settingsRow(icon: "globe", title: "Язык", subtitle: "Русский") {
                        toast = "Другие языки будут добавлены позже"
                    }
                    Divider().padding(.leading, 52)
                    settingsRow(icon: "paintpalette.fill", title: "Тема", subtitle: "Светлая") {
                        toast = "Выбор темы — в следующем обновлении"
                    }
                    Divider().padding(.leading, 52)
                    settingsRow(icon: "trash.fill", title: "Очистить кэш", subtitle: nil) {
                        URLCache.shared.removeAllCachedResponses()
                        toast = "Кэш очищен"
                    }
                }

                settingsSectionTitle("Поддержка")
                settingsCard {
                    NavigationLink {
                        HelpView()
                    } label: {
                        settingsRowLabel(icon: "questionmark.circle.fill", title: "Помощь", subtitle: nil, showDisclosure: false)
                    }
                    Divider().padding(.leading, 52)
                    settingsRow(icon: "text.bubble.fill", title: "Обратная связь", subtitle: "Оцените работу клуба") {
                        showFeedback = true
                    }
                    Divider().padding(.leading, 52)
                    settingsRow(icon: "star.fill", title: "Оценить приложение", subtitle: nil) {
                        openURL(AppConfiguration.appStoreURL)
                    }
                    Divider().padding(.leading, 52)
                    NavigationLink {
                        AboutView()
                    } label: {
                        settingsRowLabel(icon: "info.circle.fill", title: "О приложении", subtitle: "Версия \(AppConfiguration.appVersion)", showDisclosure: false)
                    }
                }

                settingsSectionTitle("Правовая информация")
                settingsCard {
                    settingsRow(icon: "doc.text.fill", title: "Пользовательское соглашение", subtitle: nil) {
                        openURL(AppConfiguration.termsURL)
                    }
                    Divider().padding(.leading, 52)
                    settingsRow(icon: "hand.raised.fill", title: "Политика конфиденциальности", subtitle: nil) {
                        openURL(AppConfiguration.privacyURL)
                    }
                }
            }
            .padding(.bottom, 24)
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Настройки")
        .sheet(isPresented: $showFeedback) {
            NavigationStack {
                Form {
                    Picker("Оценка", selection: $rating) {
                        ForEach(1...5, id: \.self) { Text("\($0)").tag($0) }
                    }
                    TextField("Комментарий", text: $comment, axis: .vertical)
                        .lineLimit(3...6)
                    Button("Отправить") {
                        Task {
                            do {
                                _ = try await app.api.submitFeedback(FeedbackRequest(rating: rating, comment: comment, type: "general", referenceId: nil))
                                showFeedback = false
                                toast = "Спасибо!"
                            } catch {
                                toast = error.localizedDescription
                            }
                        }
                    }
                }
                .navigationTitle("Обратная связь")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Закрыть") { showFeedback = false } } }
            }
        }
        .sheet(isPresented: $biometricEnrollPresented) {
            NavigationStack {
                Form {
                    Section {
                        Text(
                            """
                            Введите пароль учётной записи один раз: учётные данные сохраняются в связке ключей \
                            и доступны только с Face ID или Touch ID при нажатии «Войти по отпечатку пальца».
                            """
                        )
                        .font(FCTypography.bodySmall())

                        SecureField("Пароль", text: $biometricEnrollPassword)

                        Button("Сохранить и включить биометрию") {
                            Task { await confirmBiometricEnrollment() }
                        }
                        .disabled(biometricEnrollBusy || biometricEnrollPassword.isEmpty)

                        if biometricEnrollBusy {
                            ProgressView().frame(maxWidth: .infinity)
                        }
                    }
                }
                .navigationTitle("Биометрия")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Закрыть") {
                            biometricEnrollPresented = false
                            biometricEnrollPassword = ""
                        }
                    }
                }
            }
        }
        .alert("", isPresented: Binding(get: { toast != nil }, set: { if !$0 { toast = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(toast ?? "") }
    }


    @MainActor
    private func confirmBiometricEnrollment() async {
        guard let mail = app.currentUser?.email, !mail.isEmpty else {
            toast = "Войдите в аккаунт ещё раз, затем включите биометрию"
            return
        }
        biometricEnrollBusy = true
        defer {
            biometricEnrollBusy = false
        }

        guard !biometricEnrollPassword.isEmpty else { return }

        do {
            let r = try await app.api.login(email: mail, password: biometricEnrollPassword)
            app.applyAuth(r)
            try BiometricCredentialStore.save(email: mail, password: biometricEnrollPassword)
            biometricEnrollPresented = false
            biometricEnrollPassword = ""
            toast = "Вход по отпечатку включён"
        } catch let e as FitnessAPIError {
            toast = e.localizedDescription
        } catch BiometricCredentialStoreError.notAvailable {
            toast = "Биометрия недоступна на устройстве"
        } catch BiometricCredentialStoreError.keychain(let status) where status == errSecUserCanceled {
            toast = nil
        } catch BiometricCredentialStoreError.keychain {
            toast = "Не удалось сохранить данные для биометрии"
        } catch {
            toast = error.localizedDescription
        }
    }

    private func settingsSectionTitle(_ t: String) -> some View {
        Text(t)
            .font(FCTypography.titleSmall())
            .fontWeight(.semibold)
            .foregroundStyle(Theme.primary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 4)
    }

    private func settingsCard(@ViewBuilder content: () -> some View) -> some View {
        VStack(spacing: 0, content: content)
            .background(Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
            .padding(.horizontal, 16)
            .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private func settingsToggle(icon: String, title: String, subtitle: String, on: Binding<Bool>) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Theme.onSurfaceVariant)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onSurface)
                Text(subtitle)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            Spacer()
            Toggle("", isOn: on)
                .labelsHidden()
                .tint(Theme.primary)
        }
        .padding(16)
        .contentShape(Rectangle())
        .onTapGesture {
            on.wrappedValue.toggle()
        }
    }

    private func settingsRow(icon: String, title: String, subtitle: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(FCTypography.bodyLarge())
                        .foregroundStyle(Theme.onSurface)
                    if let subtitle {
                        Text(subtitle)
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func settingsRowLabel(icon: String, title: String, subtitle: String?, showDisclosure: Bool = true) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Theme.onSurfaceVariant)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onSurface)
                if let subtitle {
                    Text(subtitle)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
            }
            Spacer()
            if showDisclosure {
                Image(systemName: "chevron.right")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

struct HelpView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.openURL) private var openURL

    @State private var contactEmail = ""
    @State private var subject = ""
    @State private var bodyText = ""
    @State private var categoryApi = "other"

    @State private var errorMessage: String?
    @State private var successMessage: String?
    @State private var isSubmitting = false

    private var hasProfile: Bool { app.currentUser != nil }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Часто задаваемые вопросы и инструкции — на странице поддержки.")
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onBackground)

                Button("Открыть страницу помощи") {
                    openURL(AppConfiguration.helpURL)
                }
                .font(FCTypography.titleSmall())
                .fontWeight(.semibold)
                .foregroundStyle(Theme.onPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Theme.primary)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))

                Text("Обращение в клуб")
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onBackground)
                    .padding(.top, 10)

                if hasProfile == false {
                    helpTicketField(title: "Email для ответа", text: $contactEmail, useEmailKeyboard: true)
                } else {
                    helpTicketField(title: "Email для ответа (необязательно)", text: $contactEmail, subtitle: "По умолчанию — из профиля", useEmailKeyboard: true)
                }

                Menu {
                    ForEach(fcHelpTicketCategories, id: \.api) { pair in
                        Button(pair.label) { categoryApi = pair.api }
                    }
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Тематика")
                                .font(FCTypography.labelMedium())
                                .foregroundStyle(Theme.onSurfaceVariant)
                            Text(fcHelpCategoryLabel(for: categoryApi))
                                .font(FCTypography.bodyLarge())
                                .foregroundStyle(Theme.onBackground)
                        }
                        Spacer()
                        Image(systemName: "chevron.down.circle.fill")
                            .foregroundStyle(Theme.primary)
                    }
                    .padding(14)
                    .background(Theme.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous).stroke(Theme.outlineVariant, lineWidth: 1))
                }

                helpTicketField(title: "Тема", text: $subject)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Сообщение")
                        .font(FCTypography.labelMedium())
                        .foregroundStyle(Theme.onSurfaceVariant)
                    TextEditor(text: $bodyText)
                        .frame(minHeight: 140)
                        .padding(8)
                        .background(Theme.surface)
                        .scrollContentBackground(.hidden)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous).stroke(Theme.outlineVariant, lineWidth: 1))
                        .font(FCTypography.bodyLarge())
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.error)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let successMessage {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(successMessage)
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onBackground)
                        Button("Понятно") {
                            self.successMessage = nil
                        }
                        .font(FCTypography.titleSmall())
                        .foregroundStyle(Theme.primary)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.primary.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                }

                Button {
                    submitTicket()
                } label: {
                    Text(isSubmitting ? "Отправка…" : "Отправить обращение")
                        .font(FCTypography.titleSmall())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(isSubmitting ? Theme.primary.opacity(0.55) : Theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(isSubmitting)

                Text("Ответ поддержки может занять несколько рабочих дней.")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .padding(.top, 4)
            }
            .padding(24)
            .padding(.bottom, 36)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Помощь")
        .onAppear {
            contactEmail = (app.currentUser?.email ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }

    private func helpTicketField(title: String, text: Binding<String>, subtitle: String? = nil, useEmailKeyboard: Bool = false) -> some View {
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
                .padding(14)
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous).stroke(Theme.outlineVariant, lineWidth: 1))
                .font(FCTypography.bodyLarge())
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(useEmailKeyboard ? .emailAddress : .default)
        }
    }

    private func submitTicket() {
        errorMessage = nil
        successMessage = nil
        let subj = subject.trimmingCharacters(in: .whitespacesAndNewlines)
        let msg = bodyText.trimmingCharacters(in: .whitespacesAndNewlines)
        let emailTrim = contactEmail.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !subj.isEmpty else {
            errorMessage = "Укажите тему обращения"
            return
        }
        guard msg.count >= 5 else {
            errorMessage = "Опишите проблему не менее чем в 5 символов"
            return
        }
        if !hasProfile {
            guard fcLooksLikeEmail(emailTrim) else {
                errorMessage = "Укажите email для ответа поддержки"
                return
            }
        } else if !emailTrim.isEmpty && !fcLooksLikeEmail(emailTrim) {
            errorMessage = "Некорректный email"
            return
        }

        isSubmitting = true
        Task {
            do {
                _ = try await app.api.createSupportTicket(
                    SupportTicketRequest(
                        subject: subj,
                        message: msg,
                        category: categoryApi,
                        contactEmail: emailTrim.isEmpty ? nil : emailTrim
                    )
                )
                await MainActor.run {
                    isSubmitting = false
                    subject = ""
                    bodyText = ""
                    successMessage = "Обращение отправлено. Ответ придёт на указанный email."
                }
            } catch {
                await MainActor.run {
                    isSubmitting = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

struct AboutView: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("Фитнес Клуб")
                    .font(FCTypography.headlineMedium())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onBackground)
                Spacer().frame(height: 8)
                Text("Версия \(AppConfiguration.appVersion)")
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onSurfaceVariant)
                Spacer().frame(height: 32)

                Text("Мобильное приложение для участников фитнес-клуба. Расписание, абонементы, бронирование и многое другое.")
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                Spacer().frame(height: 24)

                Button {
                    openURL(AppConfiguration.appStoreURL)
                } label: {
                    Text("Оценить в App Store")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                }
                .buttonStyle(.plain)
                Spacer().frame(height: 8)
                Text("(На Android — кнопка «Оценить в Google Play»; на iOS открываем App Store.)")
                    .font(FCTypography.labelSmall())
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.75))
                    .multilineTextAlignment(.center)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "О приложении")
    }
}
