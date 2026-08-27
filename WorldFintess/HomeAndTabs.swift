import SwiftUI
import UIKit

fileprivate extension Error {
    /// Pull-to-refresh / `.task` cancellation — не показывать пользователю.
    var isBenignCancellation: Bool {
        if self is CancellationError { return true }
        if let url = self as? URLError, url.code == .cancelled { return true }
        let ns = self as NSError
        if ns.domain == NSURLErrorDomain && ns.code == NSURLErrorCancelled { return true }
        let msg = localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return msg == "cancelled" || msg == "canceled"
    }
}

fileprivate extension Color {
    /// Парсинг hex как `parsePromoColor` в `HomeScreen.kt`.
    static func fcFromPromoHex(_ hex: String?, fallback: Color) -> Color {
        guard let hex, hex.hasPrefix("#") else { return fallback }
        let trimmed = hex.dropFirst()
        guard trimmed.count >= 6 else { return fallback }
        let rgbStr = String(trimmed.prefix(6))
        guard let v = UInt32(rgbStr, radix: 16) else { return fallback }
        return Color(hex: v)
    }
}

// MARK: - Home (`HomeScreen.kt`)

struct HomeTabView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var go: (AppRoute) -> Void
    var openQrSheet: () -> Void
    var switchToProfileTab: () -> Void
    /// Вкладка реально на экране (не скрыта opacity в MainShell).
    var isActive: Bool = true

    @State private var promotions: [ClubPromotion] = []
    @State private var promotionsReady = false
    @State private var promoIndex = 0
    @State private var unread = 0
    @State private var upcoming: [UpcomingRow] = []
    @State private var occ: GymOccupancy?
    @State private var isInsideGym = false
    @State private var brandName = AppConfiguration.appDisplayName
    @State private var clubHallName = ""

    private var preferredClubId: String? {
        let fromUser = app.currentUser?.clubId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return fromUser.isEmpty ? nil : fromUser
    }

    struct UpcomingRow: Identifiable {
        let id: String
        let name: String
        let time: String
        let trainer: String
        let room: String
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                fcHomeTopBar
                VStack(alignment: .leading, spacing: 0) {
                    if promotionsReady && !promotions.isEmpty {
                        promoBannerCarousel
                    }
                    occupancyCardAlways
                    quickMenuCard
                    if !upcoming.isEmpty {
                        sectionTitle("Ближайшие тренировки")
                        upcomingScroll
                    }
                }
                .padding(.bottom, 100)
            }
        }
        .background(Theme.background)
        .task(id: isActive) {
            guard isActive else { return }
            await load(forceRefresh: true)
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 20_000_000_000)
                guard !Task.isCancelled else { return }
                await loadOcc(forceRefresh: true)
            }
        }
        .task(id: app.currentUser?.clubId) {
            guard isActive else { return }
            await loadClubInfo(forceRefresh: true)
            await loadOcc(forceRefresh: true)
        }
        .task(id: promoIndex) {
            guard promotions.count > 1 else { return }
            try? await Task.sleep(nanoseconds: 4_500_000_000)
            promoIndex = (promoIndex + 1) % promotions.count
        }
        .refreshable { await load(forceRefresh: true) }
    }

    private var fcHomeTopBar: some View {
        HStack {
            Button {
                switchToProfileTab()
            } label: {
                Image(systemName: "person")
                    .font(.title3)
                    .foregroundStyle(Theme.onPrimary)
            }
            .frame(width: 36, alignment: .leading)

            Spacer(minLength: 8)

            BrandHeader(
                brandName: brandName,
                textColor: Theme.onPrimary,
                logoSize: 32
            )

            Spacer(minLength: 8)

            Button {
                go(.notifications)
            } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "bell")
                        .font(.title3)
                        .foregroundStyle(Theme.onPrimary)
                    if unread > 0 {
                        Text("\(min(unread, 99))")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(Theme.onPrimary)
                            .padding(4)
                            .background(Theme.error)
                            .clipShape(Capsule())
                            .offset(x: 10, y: -8)
                    }
                }
            }
            .frame(width: 36, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Theme.primary)
    }

    private var promoBannerCarousel: some View {
        let idx = min(max(promoIndex, 0), max(promotions.count - 1, 0))
        let currentPromotion = promotions[idx]
        return Button {
            promoNavigate(currentPromotion)
        } label: {
            ZStack(alignment: .leading) {
                LinearGradient(
                    colors: [
                        Color.fcFromPromoHex(currentPromotion.bgFrom, fallback: Theme.primary),
                        Color.fcFromPromoHex(currentPromotion.bgTo, fallback: Theme.accentBlue),
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )

                let urlStr = currentPromotion.imageUrl ?? ""
                if let u = URL(string: urlStr), !urlStr.isEmpty {
                    AsyncImage(url: u) { phase in
                        switch phase {
                        case .empty:
                            Color.clear
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFill()
                        case .failure:
                            Color.clear
                        @unknown default:
                            Color.clear
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                    .overlay(Color.black.opacity(0.25))
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text(currentPromotion.title)
                        .font(FCTypography.headlineMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onPrimary)
                    if let sub = currentPromotion.subtitle?.trimmingCharacters(in: .whitespacesAndNewlines), !sub.isEmpty {
                        Text(sub)
                            .font(FCTypography.bodyLarge())
                            .foregroundStyle(Theme.onPrimary.opacity(0.9))
                    }
                    Spacer().frame(height: 8)
                    Text(currentPromotion.resolvedButtonText)
                        .font(FCTypography.labelLarge())
                        .foregroundStyle(Theme.primary)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(Theme.onPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                    if promotions.count > 1 {
                        Spacer().frame(height: 12)
                        HStack(spacing: 6) {
                            ForEach(Array(promotions.enumerated()), id: \.element.id) { i, _ in
                                Circle()
                                    .fill(
                                        i == idx
                                            ? Theme.onPrimary
                                            : Theme.onPrimary.opacity(0.55)
                                    )
                                    .frame(width: i == idx ? 8 : 6, height: i == idx ? 8 : 6)
                            }
                        }
                    }
                }
                .padding(24)
                .allowsHitTesting(false)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 180)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius20, style: .continuous))
            .shadow(color: Color.black.opacity(0.12), radius: 4, x: 0, y: 2)
        }
        .buttonStyle(.plain)
        .padding(16)
    }

    private func promoNavigate(_ p: ClubPromotion) {
        switch p.resolvedActionType.lowercased() {
        case "subscriptions":
            go(.subscriptionPlans)
        case "none":
            break
        default:
            go(.shop)
        }
    }

    private var occupancyCardAlways: some View {
        let current = occ?.current
        let maxC = occ?.maxCapacity
        let pct = occ?.percentage ?? 0
        let statusStr = occ?.status ?? ""
        let displayName: String? = {
            let raw = clubHallName.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !raw.isEmpty else { return nil }
            return raw.count <= 36 ? raw : String(raw.prefix(35)).trimmingCharacters(in: .whitespacesAndNewlines) + "…"
        }()

        let bg: Color = {
            switch statusStr {
            case "low": return Theme.accentBlue.opacity(0.15)
            case "high": return Theme.accentOrange.opacity(0.2)
            default: return Theme.surfaceVariant.opacity(0.7)
            }
        }()

        return Button {
            if let clubId = preferredClubId {
                go(.clubDetail(clubId))
            } else {
                go(.clubInfo)
            }
        } label: {
            HStack(alignment: .center, spacing: 16) {
                FCOccupancyRing(percentage: pct, status: statusStr)
                    .frame(width: 64, height: 64)
                VStack(alignment: .leading, spacing: 4) {
                    if let displayName {
                        Text(displayName)
                            .font(FCTypography.titleSmall())
                            .fontWeight(.bold)
                            .foregroundStyle(Theme.onSurface)
                            .lineLimit(1)
                    }
                    Text("Заполненность зала")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onSurface)
                    if let current, let maxC {
                        Text("\(current) из \(maxC) человек")
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    } else {
                        Text("Загрузка...")
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    Task { await loadOcc() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .buttonStyle(.plain)
                Image(systemName: "chevron.right")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .padding(16)
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous))
            .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private var quickMenuCard: some View {
        VStack(spacing: 0) {
            quickMenuRow(
                icon: "qrcode",
                title: "Вход/выход в зал",
                subtitle: isInsideGym ? "Показать QR-код для выхода" : "Показать QR-код для прохода"
            ) {
                openQrSheet()
            }
            dividerInset
            quickMenuRow(icon: "cart.fill", title: "Приобрести", subtitle: "Карты, Абонементы, Услуги") {
                go(.shop)
            }
            dividerInset
            quickMenuRow(icon: "mappin.circle.fill", title: "Мы на карте", subtitle: "Покажем кратчайший путь") {
                go(.clubs)
            }
            dividerInset
            quickMenuRow(icon: "person.3.fill", title: "Наша команда", subtitle: "Опытные тренеры") {
                go(.trainers)
            }
            dividerInset
            quickMenuRow(icon: "book.fill", title: "Дневник тренировок", subtitle: "Личные записи и прогресс") {
                go(.trainingDiary)
            }
        }
        .padding(.vertical, 8)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius20, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private var dividerInset: some View {
        Divider()
            .padding(.horizontal, 16)
    }

    private func quickMenuRow(icon: String, title: String, subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                ZStack {
                    RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous)
                        .fill(Theme.primary.opacity(0.1))
                        .frame(width: 44, height: 44)
                    Image(systemName: icon)
                        .foregroundStyle(Theme.primary)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(FCTypography.titleSmall())
                        .fontWeight(.medium)
                        .foregroundStyle(Theme.onSurface)
                        .multilineTextAlignment(.leading)
                    Text(subtitle)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .padding(16)
        }
        .buttonStyle(.plain)
    }

    private func sectionTitle(_ t: String) -> some View {
        Text(t)
            .font(FCTypography.titleMedium())
            .fontWeight(.bold)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
    }

    private var upcomingScroll: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(upcoming) { row in
                    Button {
                        go(.trainingDetail(row.id))
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Label(row.time, systemImage: "clock")
                                    .font(FCTypography.labelSmall())
                            }
                            .labelStyle(.titleAndIcon)
                            Text(row.name)
                                .font(FCTypography.titleSmall())
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.onSurface)
                            Text(row.trainer)
                                .font(FCTypography.bodySmall())
                                .foregroundStyle(Theme.onSurfaceVariant)
                            Text(row.room)
                                .font(FCTypography.bodySmall())
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                        .padding(12)
                        .frame(width: 200, alignment: .leading)
                        .background(Theme.surface)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func load(forceRefresh: Bool = false) async {
        // Не блокируем UI общим isLoading — секции обновляются по мере ответов.
        async let n: Void = loadNotif(forceRefresh: forceRefresh)
        async let o: Void = loadOcc(forceRefresh: forceRefresh)
        async let a: Void = loadAccessStatus(forceRefresh: forceRefresh)
        async let p: Void = loadPromotions(forceRefresh: forceRefresh)
        async let b: Void = loadBookings(forceRefresh: forceRefresh)
        async let c: Void = loadClubInfo(forceRefresh: forceRefresh)
        _ = await (n, o, a, p, b, c)
    }

    private func loadClubInfo(forceRefresh: Bool = false) async {
        _ = forceRefresh
        if let clubId = preferredClubId,
           let details = try? await app.api.getClubDetails(id: clubId) {
            let hall = details.name.trimmingCharacters(in: .whitespacesAndNewlines)
            clubHallName = hall.isEmpty ? (app.currentUser?.clubName ?? AppConfiguration.appDisplayName) : hall
        } else if let name = app.currentUser?.clubName?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !name.isEmpty {
            clubHallName = name
        }
        guard let info = try? await app.api.getClubInfo() else { return }
        brandName = info.resolvedBrandName
        if clubHallName.isEmpty {
            let hall = info.name.trimmingCharacters(in: .whitespacesAndNewlines)
            clubHallName = hall.isEmpty ? brandName : hall
        }
    }

    private func loadNotif(forceRefresh: Bool = false) async {
        if let count = try? await app.api.getUnreadNotificationsCount(forceRefresh: forceRefresh) {
            unread = count
            return
        }
        guard let list = try? await app.api.getNotifications(forceRefresh: forceRefresh) else { return }
        unread = list.filter { !$0.isRead }.count
    }

    private func loadOcc(forceRefresh: Bool = false) async {
        occ = try? await app.api.getClubOccupancy(clubId: preferredClubId, forceRefresh: forceRefresh)
    }

    private func loadAccessStatus(forceRefresh: Bool = false) async {
        isInsideGym = (try? await app.api.getAccessStatus(forceRefresh: forceRefresh))?.isInside ?? false
    }

    private func loadPromotions(forceRefresh: Bool = false) async {
        defer { promotionsReady = true }
        do {
            let list = try await app.api.getClubPromotions(forceRefresh: forceRefresh)
            let sorted = list
                .sorted { ($0.sortOrder ?? 100) < ($1.sortOrder ?? 100) }
                .filter { !isLegacyDemoPromo(title: $0.title, subtitle: $0.subtitle) }
            promotions = sorted
            promoIndex = 0
        } catch {
            promotions = []
            promoIndex = 0
        }
    }

    private func isLegacyDemoPromo(title: String?, subtitle: String?) -> Bool {
        let t = (title ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let s = (subtitle ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return t.contains("СКИДКА 20%") || (t == "СКИДКА 20%!" && s.contains("12 и 6"))
    }

    private func loadBookings(forceRefresh: Bool = false) async {
        guard let bookings = try? await app.api.getMyBookings(upcoming: true, forceRefresh: forceRefresh) else { return }
        let rows = bookings
            .filter { $0.status.lowercased() != "cancelled" }
            .sorted { $0.training.startTime < $1.training.startTime }
            .prefix(8)
            .map { b -> UpcomingRow in
                let t = b.training
                let time = extractHM(t.startTime)
                return UpcomingRow(id: t.id, name: t.name, time: time, trainer: t.trainer.name, room: t.room)
            }
        upcoming = Array(rows)
    }

    private func extractHM(_ iso: String) -> String {
        guard let r = iso.range(of: "T") else { return "—" }
        let start = iso.index(after: r.lowerBound)
        let end = iso.index(start, offsetBy: 5, limitedBy: iso.endIndex) ?? iso.endIndex
        return String(iso[start..<end])
    }
}

// MARK: - Schedule (`ScheduleScreen.kt`)

struct ScheduleTabView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var onTraining: (AppRoute) -> Void
    @State private var trainings: [Training] = []
    @State private var selectedDate: Date = Calendar.current.startOfDay(for: Date())
    @State private var filterType: TrainingKind?
    @State private var isLoading = false
    @State private var networkError: String?

    private let dateRange = 0..<14

    var body: some View {
        VStack(spacing: 0) {
            scheduleTopBar
            dateSelector
            filterChips
            ZStack {
                Theme.background
                if isLoading {
                    ProgressView()
                } else if let networkError, trainings.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.circle")
                            .font(.system(size: 64))
                            .foregroundStyle(Theme.error)
                        Text(networkError)
                            .font(FCTypography.bodyLarge())
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                        Button {
                            Task { await load() }
                        } label: {
                            Text("Повторить")
                                .font(FCTypography.titleSmall())
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.onPrimary)
                                .padding(.horizontal, 28)
                                .padding(.vertical, 12)
                                .background(Theme.primary)
                                .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(32)
                } else if trainings.isEmpty {
                    scheduleEmptyState
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(trainings) { t in
                                Button {
                                    onTraining(.trainingDetail(t.id))
                                } label: {
                                    scheduleTrainingCard(t)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(16)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Theme.background)
        .task { await load() }
        .refreshable { await load() }
    }

    private var scheduleTopBar: some View {
        Text("Расписание")
            .font(FCTypography.titleLarge())
            .fontWeight(.bold)
            .foregroundStyle(Theme.onPrimary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Theme.primary)
    }

    private var dateSelector: some View {
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(dateRange), id: \.self) { offset in
                    let date = cal.date(byAdding: .day, value: offset, to: today) ?? today
                    let selected = cal.isDate(date, inSameDayAs: selectedDate)
                    let isToday = cal.isDateInToday(date)
                    Button {
                        selectedDate = cal.startOfDay(for: date)
                        Task { await load() }
                    } label: {
                        VStack(spacing: 4) {
                            Text(shortWeekday(date))
                                .font(FCTypography.labelSmall())
                                .foregroundStyle(selected ? Theme.onPrimary.opacity(0.9) : Theme.onSurfaceVariant)
                            Text("\(cal.component(.day, from: date))")
                                .font(FCTypography.titleMedium())
                                .fontWeight(.bold)
                                .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurfaceVariant)
                            if isToday {
                                Circle()
                                    .fill(selected ? Theme.onPrimary : Theme.primary)
                                    .frame(width: 6, height: 6)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(selected ? Theme.primary : Theme.surfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 12)
        }
        .background(Theme.surface)
    }

    private func shortWeekday(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "EEE"
        return f.string(from: date).replacingOccurrences(of: ".", with: "")
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                filterChip("Все", selected: filterType == nil) { filterType = nil; Task { await load() } }
                filterChip("Групповые", selected: filterType == .group) { filterType = .group; Task { await load() } }
                filterChip("Персональные", selected: filterType == .personal) { filterType = .personal; Task { await load() } }
                    .frame(minWidth: 110, alignment: .center)
                filterChip("Допуслуги", selected: filterType == .extra) { filterType = .extra; Task { await load() } }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    private func filterChip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(FCTypography.labelLarge())
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurface)
                .background(selected ? Theme.primary : Theme.surfaceVariant)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private func scheduleTrainingCard(_ t: Training) -> some View {
        let intensityColor = intensityUI(t.intensity)
        return HStack(alignment: .top, spacing: 16) {
            VStack {
                Text(extractHM(t.startTime))
                    .font(FCTypography.titleMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.primary)
                Text("\(t.durationMinutes) мин")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            RoundedRectangle(cornerRadius: 2)
                .fill(intensityColor)
                .frame(width: 3, height: 60)
            VStack(alignment: .leading, spacing: 4) {
                Text(typeRu(t.type))
                    .font(FCTypography.labelSmall())
                    .foregroundStyle(Theme.primary)
                Text(t.name)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onSurface)
                    .lineLimit(1)
                Label(t.trainer.name, systemImage: "person")
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .labelStyle(.titleAndIcon)
                Label(t.room, systemImage: "door.left.hand.closed")
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .labelStyle(.titleAndIcon)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 4) {
                if t.isBooked {
                    Text("Записан")
                        .font(FCTypography.labelSmall())
                        .foregroundStyle(Theme.success)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Theme.success.opacity(0.12))
                        .clipShape(Capsule())
                } else if t.isFull {
                    Text("Мест нет")
                        .font(FCTypography.labelSmall())
                        .foregroundStyle(Theme.error)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Theme.error.opacity(0.1))
                        .clipShape(Capsule())
                } else {
                    Text("Осталось \(t.spotsLeft)")
                        .font(FCTypography.labelMedium())
                        .foregroundStyle(t.spotsLeft <= 3 ? Theme.warning : Theme.onSurfaceVariant)
                }
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private var scheduleEmptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "calendar.badge.minus")
                .font(.system(size: 64))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("Нет тренировок на выбранную дату")
                .font(FCTypography.bodyLarge())
                .foregroundStyle(Theme.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(32)
    }

    private func load() async {
        isLoading = true
        networkError = nil
        defer { isLoading = false }
        let cal = Calendar(identifier: .gregorian)
        let c = cal.dateComponents([.year, .month, .day], from: selectedDate)
        let ds = String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
        do {
            let typeStr = filterType?.rawValue
            trainings = try await app.api.getTrainings(date: ds, type: typeStr)
        } catch {
            networkError = error.localizedDescription
        }
    }

    private func extractHM(_ iso: String) -> String {
        guard let r = iso.range(of: "T") else { return iso }
        let start = iso.index(after: r.lowerBound)
        let end = iso.index(start, offsetBy: 5, limitedBy: iso.endIndex) ?? iso.endIndex
        return String(iso[start..<end])
    }

    private func typeRu(_ k: TrainingKind) -> String {
        switch k {
        case .group: return "Групповая"
        case .personal: return "Персональная"
        case .extra: return "Допуслуга"
        }
    }

    private func intensityUI(_ i: TrainingIntensity?) -> Color {
        switch i {
        case .low: return Theme.accentGreen
        case .medium: return Theme.warning
        case .high: return Theme.error
        case .none: return Theme.outlineVariant
        }
    }
}

// MARK: - My bookings (`MyTrainingsScreen.kt`)

struct MyBookingsTabView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var onTraining: (AppRoute) -> Void
    @State private var bookings: [Booking] = []
    @State private var isLoading = false
    @State private var networkError: String?
    @State private var cancelId: String?

    var body: some View {
        VStack(spacing: 0) {
            Text("Мои записи")
                .font(FCTypography.titleLarge())
                .fontWeight(.bold)
                .foregroundStyle(Theme.onPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Theme.primary)

            ZStack {
                Theme.background
                if isLoading {
                    ProgressView()
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            if let networkError {
                                Text(networkError)
                                    .foregroundStyle(Theme.error)
                                    .padding(.horizontal, 16)
                            }
                            if upcoming.isEmpty && past.isEmpty {
                                emptyBookingsState
                            } else {
                                if !upcoming.isEmpty {
                                    Text("Предстоящие")
                                        .font(FCTypography.titleMedium())
                                        .fontWeight(.bold)
                                        .padding(.horizontal, 16)
                                    ForEach(upcoming) { b in
                                        bookingCard(b, allowCancel: true)
                                    }
                                }
                                if !past.isEmpty {
                                    Text("История")
                                        .font(FCTypography.titleMedium())
                                        .fontWeight(.bold)
                                        .padding(.horizontal, 16)
                                        .padding(.top, 8)
                                    ForEach(past) { b in
                                        bookingCard(b, allowCancel: false)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 16)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Theme.background)
        .task { await load() }
        .refreshable { await load(forceRefresh: true) }
        .alert("Отменить запись?", isPresented: Binding(
            get: { cancelId != nil },
            set: { if !$0 { cancelId = nil } }
        )) {
            Button("Назад", role: .cancel) { cancelId = nil }
            Button("Отменить", role: .destructive) {
                if let id = cancelId {
                    Task { await cancel(id) }
                }
                cancelId = nil
            }
        } message: {
            Text("Вы уверены, что хотите отменить запись на тренировку?")
        }
    }

    private var upcoming: [Booking] {
        bookings.filter { $0.isUpcomingList }.sorted { $0.training.startTime < $1.training.startTime }
    }

    private var past: [Booking] {
        bookings.filter { $0.isPastList }.sorted { $0.training.startTime > $1.training.startTime }
    }

    private var emptyBookingsState: some View {
        VStack(spacing: 16) {
            Image(systemName: "figure.strengthtraining.traditional")
                .font(.system(size: 56))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("У вас пока нет записей")
                .font(FCTypography.titleMedium())
                .fontWeight(.medium)
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Text("Запишитесь на тренировку в разделе Расписание")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(32)
    }

    private func bookingCard(_ b: Booking, allowCancel: Bool) -> some View {
        let chip = bookingStatusChip(b.status)

        return VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 8) {
                    Text(b.training.name)
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onSurface)
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    bookingStatusBadge(background: chip.0, textColor: chip.1, text: chip.2)
                }

                Spacer().frame(height: 8)

                bookingMetaRow(icon: "calendar", text: fcBookingCardDateTime(b.training.startTime))
                bookingMetaRow(icon: "person.fill", text: b.training.trainer.name)
                bookingMetaRow(icon: "sportscourt.fill", text: b.training.room)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                onTraining(.trainingDetail(b.training.id))
            }

            if allowCancel, b.status.lowercased() == "confirmed" {
                Button {
                    cancelId = b.id
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                        Text("Отменить запись")
                            .font(FCTypography.titleSmall())
                            .fontWeight(.medium)
                    }
                    .foregroundStyle(Theme.error)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Color.clear.contentShape(Rectangle()))
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous)
                            .stroke(Theme.error.opacity(0.65), lineWidth: 1)
                    )
                }
                .padding(.top, 12)
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 2, x: 0, y: 1)
        .padding(.horizontal, 16)
    }

    private func bookingMetaRow(icon: String, text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 13))
                .foregroundStyle(Theme.onSurfaceVariant)
                .frame(width: 18, alignment: .center)
            Text(text)
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.leading)
        }
        .padding(.top, 2)
    }

    private func bookingStatusBadge(background: Color, textColor: Color, text: String) -> some View {
        Text(text)
            .font(FCTypography.labelSmall())
            .foregroundStyle(textColor)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .fixedSize()
    }

    /// Как `StatusChip` / `BookingStatus` в `MyTrainingsScreen.kt`.
    private func bookingStatusChip(_ raw: String) -> (Color, Color, String) {
        switch raw.lowercased() {
        case "confirmed":
            return (Theme.success.opacity(0.12), Theme.success, "Подтверждено")
        case "waiting", "waiting_list":
            return (Theme.warning.opacity(0.12), Theme.warning, "В ожидании")
        case "completed":
            return (Theme.accentBlue.opacity(0.12), Theme.accentBlue, "Завершено")
        case "cancelled":
            return (Theme.error.opacity(0.12), Theme.error, "Отменено")
        default:
            return (Theme.surfaceVariant, Theme.onSurfaceVariant, raw)
        }
    }

    private func fcBookingCardDateTime(_ isoDateTime: String) -> String {
        guard isoDateTime.count >= 16 else { return isoDateTime }
        let date = String(isoDateTime.prefix(10))
        let timeStart = isoDateTime.index(isoDateTime.startIndex, offsetBy: 11)
        let timeEnd = isoDateTime.index(timeStart, offsetBy: 5, limitedBy: isoDateTime.endIndex) ?? isoDateTime.endIndex
        let time = String(isoDateTime[timeStart..<timeEnd])
        return "\(date) в \(time)"
    }

    private func load(forceRefresh: Bool = false) async {
        isLoading = true
        networkError = nil
        defer { isLoading = false }
        do {
            bookings = try await app.api.getMyBookings(forceRefresh: forceRefresh)
        } catch {
            if Task.isCancelled || error.isBenignCancellation { return }
            networkError = error.localizedDescription
        }
    }

    private func cancel(_ id: String) async {
        do {
            try await app.api.cancelBooking(id: id)
            await load(forceRefresh: true)
        } catch {
            if Task.isCancelled || error.isBenignCancellation { return }
            networkError = error.localizedDescription
        }
    }
}

// MARK: - Profile (`ProfileScreen.kt`)

struct ProfileTabView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var go: (AppRoute) -> Void
    @State private var subscriptions: [Subscription] = []
    @State private var stats: UserStats?
    @State private var isLoadingSubscriptions = false
    @State private var profileError: String?
    @State private var showLogoutConfirm = false
    @State private var sberBusy = false
    @State private var freezeTarget: Subscription?
    @State private var freezeDaysInput = ""
    @State private var showFreezeDialog = false
    @State private var cancelTarget: Subscription?
    @State private var showErrorAlert = false
    @State private var showBonusComingSoon = false
    @State private var showSubscriptionHistory = false

    private var preferredClubId: String? {
        let raw = app.currentUser?.clubId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return raw.isEmpty || raw == "0" ? nil : raw
    }

    private var clubFilteredSubscriptions: [Subscription] {
        guard let preferred = preferredClubId else { return subscriptions }
        return subscriptions.filter { sub in
            let sid = sub.clubId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            // Привязан к залу — только этот клуб.
            if !sid.isEmpty {
                return sid == preferred
            }
            // Без club_id — legacy «любой зал» (на турникете тоже пускает везде).
            // Покажем во всех preferred, пока CRM не проставит club_id с платежа.
            return true
        }
    }

    private var activeSubscriptions: [Subscription] {
        clubFilteredSubscriptions.filter { $0.status == .active || $0.status == .frozen || $0.status == .pending }
    }

    private var archivedSubscriptions: [Subscription] {
        clubFilteredSubscriptions.filter { !($0.status == .active || $0.status == .frozen || $0.status == .pending) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Text("Профиль")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onPrimary)
                Spacer()
                Button {
                    showLogoutConfirm = true
                } label: {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .foregroundStyle(Theme.onPrimary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Theme.primary)

            ZStack {
                Theme.background
                ScrollView {
                    VStack(spacing: 16) {
                        if let profileError {
                            Text(profileError)
                                .font(FCTypography.bodyMedium())
                                .foregroundStyle(Theme.error)
                        }
                        if let u = app.currentUser {
                            if let club = u.clubName, !club.isEmpty {
                                clubHeaderRow(club)
                            }
                            switchClubButton(currentLabel: u.clubName)
                            profileUserCard(u)
                            if !(u.isVerified || u.passportVerificationStatus == "verified") {
                                sberVerifyCard
                            }
                        }
                        if let stats {
                            gamificationCard(stats)
                        }
                        quickActionsRow
                        Text("Мои абонементы")
                            .font(FCTypography.titleMedium())
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        if isLoadingSubscriptions && subscriptions.isEmpty {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        subscriptionsSection
                        Text("Настройки")
                            .font(FCTypography.titleMedium())
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 8)
                        profileMenuCard
                    }
                    .padding(16)
                    .padding(.bottom, 24)
                }
                .refreshable { await refreshProfile() }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        // Всегда с сервера: счётчик посещений / visits_used не должен жить на 45с кэше.
        .task(id: app.subscriptionsRevision) { await load(forceRefresh: true) }
        .task(id: app.currentUser?.clubId) { await load(forceRefresh: true) }
        .onChange(of: profileError) { _, err in
            showErrorAlert = err != nil
        }
        .alert("Выйти из аккаунта?", isPresented: $showLogoutConfirm) {
            Button("Отмена", role: .cancel) {}
            Button("Выйти", role: .destructive) {
                Task { await app.logout() }
            }
        } message: {
            Text("Вы уверены, что хотите выйти?")
        }
        .alert("Заморозить абонемент", isPresented: $showFreezeDialog) {
            TextField("Дни", text: $freezeDaysInput)
                .keyboardType(.numberPad)
            Button("Заморозить") {
                guard let target = freezeTarget,
                      let days = Int(freezeDaysInput.filter(\.isNumber)),
                      days > 0,
                      days <= target.freezeDaysLeft else { return }
                Task { await freeze(target.id, days) }
            }
            Button("Отмена", role: .cancel) {
                freezeTarget = nil
            }
        } message: {
            if let target = freezeTarget {
                Text("Укажите количество дней заморозки (доступно: \(target.freezeDaysLeft)):")
            } else {
                Text("Укажите количество дней заморозки:")
            }
        }
        .alert("Отменить абонемент?", isPresented: Binding(
            get: { cancelTarget != nil },
            set: { if !$0 { cancelTarget = nil } }
        )) {
            Button("Отменить", role: .destructive) {
                guard let target = cancelTarget else { return }
                Task { await cancelSubscription(target.id) }
            }
            Button("Назад", role: .cancel) { cancelTarget = nil }
        } message: {
            Text("Доступ в клуб по этому абонементу будет закрыт. Отменить можно только активный или замороженный абонемент.")
        }
        .alert("Ошибка", isPresented: $showErrorAlert) {
            Button("OK") { profileError = nil }
        } message: {
            Text(profileError ?? "")
        }
        .alert("Бонусная программа", isPresented: $showBonusComingSoon) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Бонусная программа и приглашение друзей скоро появятся в приложении.")
        }
    }

    private func clubHeaderRow(_ club: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "dumbbell.fill")
                .font(.system(size: 16))
                .foregroundStyle(Theme.primary)
            Text(club)
                .font(FCTypography.titleMedium())
                .fontWeight(.bold)
                .foregroundStyle(Theme.primary)
                .lineLimit(1)
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, 4)
    }

    private func switchClubButton(currentLabel: String?) -> some View {
        Button {
            go(.selectPreferredClub)
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Theme.primary.opacity(0.15))
                        .frame(width: 44, height: 44)
                    Image(systemName: "arrow.left.arrow.right")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Theme.primary)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Выбрать другой клуб")
                        .font(FCTypography.titleSmall())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onBackground)
                    Text(
                        (currentLabel?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : "Сейчас: \($0)" }
                            ?? "Сменить зал для абонементов и покупки"
                    )
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .padding(14)
            .background(Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous)
                    .stroke(
                        LinearGradient(
                            colors: [Theme.primary, Theme.primary.opacity(0.5)],
                            startPoint: .leading,
                            endPoint: .trailing
                        ),
                        lineWidth: 1.5
                    )
            )
        }
        .buttonStyle(.plain)
    }

    private func profileUserCard(_ u: User) -> some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Theme.primary)
                    .frame(width: 64, height: 64)
                Text(String(u.name.prefix(1)).uppercased())
                    .font(FCTypography.headlineMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onPrimary)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(u.name)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
                    .lineLimit(2)
                Label(u.email, systemImage: "envelope.fill")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .lineLimit(1)
                Label(u.phone, systemImage: "phone.fill")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                go(.editProfile)
            } label: {
                Image(systemName: "pencil")
                    .foregroundStyle(Theme.primary)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius20, style: .continuous))
        .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)
    }

    private var sberVerifyCard: some View {
        Button {
            Task { await verifyWithSberFromProfile() }
        } label: {
            HStack(spacing: 12) {
                if sberBusy {
                    ProgressView()
                        .scaleEffect(0.85)
                } else {
                    Image(systemName: "checkmark.seal")
                        .foregroundStyle(Theme.primary)
                }
                Text(sberBusy ? "Подождите…" : "Подтвердить аккаунт через Сбер ID")
                    .font(FCTypography.bodyMedium())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.primary)
                Spacer()
            }
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(Theme.primary.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(sberBusy)
    }

    @ViewBuilder
    private var subscriptionsSection: some View {
        if activeSubscriptions.isEmpty && archivedSubscriptions.isEmpty && !isLoadingSubscriptions {
            emptySubsCard
        } else {
            if !activeSubscriptions.isEmpty {
                ForEach(activeSubscriptions, id: \.id) { s in
                    profileSubscriptionCard(s)
                }
            } else if !isLoadingSubscriptions {
                Text("Сейчас нет активных абонементов")
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Theme.surfaceVariant.opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
            }
            if !archivedSubscriptions.isEmpty {
                Button {
                    showSubscriptionHistory.toggle()
                } label: {
                    Text(showSubscriptionHistory
                        ? "Скрыть историю абонементов (\(archivedSubscriptions.count))"
                        : "Показать историю абонементов (\(archivedSubscriptions.count))")
                        .font(FCTypography.labelLarge())
                        .foregroundStyle(Theme.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
            if showSubscriptionHistory {
                ForEach(archivedSubscriptions, id: \.id) { s in
                    profileSubscriptionCard(s)
                }
            }
        }
    }

    private func gamificationCard(_ st: UserStats) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack {
                    Text("\(st.totalVisits)")
                        .font(FCTypography.headlineSmall())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onBackground)
                    Text("посещений")
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity)
                Spacer()
                VStack {
                    Text("\(st.streakDays)")
                        .font(FCTypography.headlineSmall())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.accentOrange)
                    Text("дней подряд")
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity)
            }
            if !st.achievements.isEmpty {
                Divider()
                Text("Достижения")
                    .font(FCTypography.titleSmall())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onBackground)
                ForEach(Array(st.achievements.prefix(4)), id: \.id) { ach in
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: "star.fill")
                            .foregroundStyle(Theme.accentOrange)
                        VStack(alignment: .leading) {
                            Text(ach.name)
                                .font(FCTypography.bodyMedium())
                                .fontWeight(.medium)
                                .foregroundStyle(Theme.onBackground)
                            Text(ach.description)
                                .font(FCTypography.bodySmall())
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(Theme.accentOrange.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius20, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private var quickActionsRow: some View {
        HStack {
            profileQuickIcon("qrcode", "QR-код") { go(.qrCode) }
            profileQuickIcon("cart.fill", "Купить") { go(.subscriptionPlans) }
            profileQuickIcon("person.3.fill", "Друзьям") { showBonusComingSoon = true }
            profileQuickIcon("bell.fill", "Уведомления") { go(.notifications) }
        }
        .frame(maxWidth: .infinity)
    }

    private func profileQuickIcon(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                ZStack {
                    Circle()
                        .fill(Theme.primary.opacity(0.2))
                        .frame(width: 56, height: 56)
                    Image(systemName: icon)
                        .font(.system(size: 24))
                        .foregroundStyle(Theme.primary)
                }
                Text(label)
                    .font(FCTypography.labelSmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }

    private var emptySubsCard: some View {
        VStack(spacing: 12) {
            Image(systemName: "creditcard.fill")
                .font(.system(size: 48))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("У вас нет активных абонементов")
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onSurfaceVariant)
            FCPrimaryButton(title: "Купить абонемент") {
                go(.subscriptionPlans)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Theme.surfaceVariant.opacity(0.55))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
    }

    private func profileSubscriptionCard(_ s: Subscription) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(s.name)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
                Spacer()
                subscriptionStatusChip(s.status)
            }
            if let club = s.clubName, !club.isEmpty {
                HStack(spacing: 6) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.primary)
                    Text(club)
                        .font(FCTypography.bodyMedium())
                        .fontWeight(.medium)
                        .foregroundStyle(Theme.primary)
                }
            }
            HStack {
                VStack(alignment: .leading) {
                    Text("Начало")
                        .font(FCTypography.labelSmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text(String(s.startDate.prefix(10)))
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.onBackground)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text("Окончание")
                        .font(FCTypography.labelSmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text(s.formattedEndDate)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.onBackground)
                }
            }
            if let left = s.visitsLeft, let total = s.visitsTotal {
                ProgressView(value: Double(s.visitsUsed), total: Double(max(total, 1)))
                    .tint(Theme.primary)
                Text("Осталось посещений: \(left) из \(total)")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            if s.freezeDaysTotal > 0 {
                Text("Дней заморозки: \(s.freezeDaysLeft) из \(s.freezeDaysTotal)")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            if s.status == .active, s.freezeDaysLeft > 0 {
                Button {
                    freezeTarget = s
                    let defaultDays = min(7, s.freezeDaysLeft)
                    freezeDaysInput = "\(max(defaultDays, 1))"
                    showFreezeDialog = true
                } label: {
                    HStack {
                        Image(systemName: "snowflake")
                        Text("Заморозить")
                    }
                    .font(FCTypography.labelLarge())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.primary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Theme.surfaceVariant.opacity(0.6))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                }
                .buttonStyle(.plain)
            }
            if s.isFrozen || s.status == .frozen {
                Button {
                    Task { await unfreeze(s.id) }
                } label: {
                    HStack {
                        Image(systemName: "play.fill")
                        Text("Разморозить")
                    }
                    .font(FCTypography.labelLarge())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Theme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                }
                .buttonStyle(.plain)
            }
            if (s.status == .active || s.status == .frozen) {
                let canCancelManually = s.visitsLeft.map { $0 > 0 } ?? true
                if canCancelManually {
                    Button {
                        cancelTarget = s
                    } label: {
                        Text("Отменить абонемент")
                            .font(FCTypography.labelLarge())
                            .foregroundStyle(Theme.error)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius20, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private func subscriptionStatusChip(_ st: SubscriptionState) -> some View {
        let (text, color): (String, Color) = {
            switch st {
            case .active: return ("Активен", Theme.success)
            case .frozen: return ("Заморожен", Theme.accentBlue)
            case .expired: return ("Истёк", Theme.error)
            case .pending: return ("Ожидание", Theme.warning)
            case .cancelled: return ("Отменён", Theme.error)
            }
        }()
        return Text(text)
            .font(FCTypography.labelSmall())
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }

    private var profileMenuCard: some View {
        VStack(spacing: 0) {
            menuRow("Уведомления", icon: "bell.fill") { go(.notifications) }
            Divider().padding(.leading, 52)
            menuRow("Документы", icon: "doc.fill") { go(.documents) }
            Divider().padding(.leading, 52)
            menuRow("История покупок", icon: "clock.fill") { go(.purchaseHistory) }
            Divider().padding(.leading, 52)
            menuRow("Настройки", icon: "gearshape.fill") { go(.settings) }
            Divider().padding(.leading, 52)
            menuRow("Помощь", icon: "questionmark.circle.fill") { go(.help) }
            Divider().padding(.leading, 52)
            menuRow("О приложении", icon: "info.circle.fill") { go(.about) }
        }
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius20, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private func menuRow(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .foregroundStyle(Theme.primary)
                    .frame(width: 24)
                Text(title)
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.onSurface)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .padding(16)
        }
        .buttonStyle(.plain)
    }

    private func verifyWithSberFromProfile() async {
        guard !sberBusy else { return }
        profileError = nil
        sberBusy = true
        defer { sberBusy = false }
        do {
            let r = try await SberIDAuthService.shared.loginCompleting(api: app.api, attachToLoggedInSession: true)
            app.applyAuth(r)
            if let p = try? await app.api.getProfile() {
                app.updateCachedUser(p)
            }
            await load()
        } catch SberIDAuthError.userCanceled {
            ()
        } catch let e as FitnessAPIError {
            profileError = e.localizedDescription
        } catch {
            if Task.isCancelled || error.isBenignCancellation { return }
            profileError = error.localizedDescription
        }
    }

    private func load(forceRefresh: Bool = false) async {
        profileError = nil
        async let subs: Void = loadSubs(forceRefresh: forceRefresh)
        async let st: Void = loadStats(forceRefresh: forceRefresh)
        _ = await (subs, st)
    }

    /// Pull-to-refresh: сбрасываем кэш и тянем абонементы + статистику посещений заново.
    private func refreshProfile() async {
        app.api.invalidateGetCache(pathPrefix: "subscriptions")
        app.api.invalidateGetCache(pathPrefix: "user/stats")
        await load(forceRefresh: true)
    }

    private func loadSubs(forceRefresh: Bool = false) async {
        isLoadingSubscriptions = true
        defer { isLoadingSubscriptions = false }
        do {
            let list = try await app.api.getMySubscriptions(forceRefresh: forceRefresh)
            guard !Task.isCancelled else { return }
            subscriptions = list
        } catch {
            // Pull-to-refresh отменяет предыдущий .task — это не ошибка для пользователя.
            if Task.isCancelled || error.isBenignCancellation { return }
            profileError = error.localizedDescription
        }
    }

    private func loadStats(forceRefresh: Bool = false) async {
        do {
            let st = try await app.api.getUserStats(forceRefresh: forceRefresh)
            guard !Task.isCancelled else { return }
            stats = st
        } catch {
            if Task.isCancelled || error.isBenignCancellation { return }
        }
    }

    private func freeze(_ id: String, _ days: Int) async {
        do {
            _ = try await app.api.freezeSubscription(id: id, days: days)
            freezeTarget = nil
            await loadSubs(forceRefresh: true)
        } catch {
            if Task.isCancelled || error.isBenignCancellation { return }
            profileError = error.localizedDescription
        }
    }

    private func unfreeze(_ id: String) async {
        do {
            _ = try await app.api.unfreezeSubscription(id: id)
            await loadSubs(forceRefresh: true)
        } catch {
            if Task.isCancelled || error.isBenignCancellation { return }
            profileError = error.localizedDescription
        }
    }

    private func cancelSubscription(_ id: String) async {
        do {
            _ = try await app.api.cancelSubscription(id: id)
            cancelTarget = nil
            await loadSubs(forceRefresh: true)
        } catch {
            cancelTarget = nil
            if Task.isCancelled || error.isBenignCancellation { return }
            profileError = error.localizedDescription
        }
    }
}

// MARK: - Training detail (`TrainingDetailsScreen.kt`)

struct TrainingDetailView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    let trainingId: String
    @State private var training: Training?
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var isBooking = false
    @State private var snackbarMessage: String?

    var body: some View {
        ZStack(alignment: .bottom) {
            Theme.background
                .ignoresSafeArea()

            Group {
                if isLoading, training == nil {
                    ProgressView()
                        .tint(Theme.primary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let err = loadError, training == nil {
                    trainingLoadErrorPane(message: err)
                } else if let t = training {
                    ScrollView {
                        trainingDetailScrollContent(t)
                            .padding(.bottom, 100)
                    }
                } else {
                    Text("Не удалось загрузить")
                        .font(FCTypography.bodyLarge())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }

            VStack(spacing: 12) {
                if let snack = snackbarMessage {
                    snackbarBanner(snack)
                }
                if let t = training {
                    trainingBookingBottomBar(t)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)
                        .padding(.top, 10)
                }
            }
        }
        .fcPrimaryNavigation(title: "Тренировка")
        .task { await load() }
    }

    private func trainingLoadErrorPane(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 56))
                .foregroundStyle(Theme.error)
            Text(message)
                .font(FCTypography.bodyLarge())
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.onBackground)
                .padding(.horizontal, 24)
            Button("Повторить") {
                Task { await load() }
            }
            .font(FCTypography.labelLarge())
            .foregroundStyle(Theme.onPrimary)
            .padding(.horizontal, 28)
            .padding(.vertical, 12)
            .background(Theme.primary)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func snackbarBanner(_ text: String) -> some View {
        Text(text)
            .font(FCTypography.bodyMedium())
            .foregroundStyle(Color.white)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(Theme.secondary)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
            .shadow(color: Color.black.opacity(0.18), radius: 6, x: 0, y: 2)
            .padding(.horizontal, 24)
    }

    private func trainingDetailScrollContent(_ training: Training) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                trainingTypeBadge(training.type)
                    .padding(.bottom, 12)

                Text(training.name)
                    .font(FCTypography.headlineMedium())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)

                if let d = training.description, !d.isEmpty {
                    Text(d)
                        .font(FCTypography.bodyLarge())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.top, 8)
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 24)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.primary.opacity(0.12))

            VStack(spacing: 20) {
                trainingDetailIconRow(
                    icon: "clock.fill",
                    title: "Время",
                    value: "\(fcFormatTrainingHm(training.startTime)) - \(fcFormatTrainingHm(training.endTime))",
                    subtitle: "\(training.durationMinutes) минут"
                )
                trainingDetailIconRow(
                    icon: "calendar",
                    title: "Дата",
                    value: fcFormatTrainingIsoDate(training.startTime)
                )
                trainingDetailIconRow(icon: "sportscourt.fill", title: "Зал", value: training.room)
                trainingDetailIconRow(
                    icon: "gauge.high",
                    title: "Интенсивность",
                    value: trainingIntensityTitle(training.intensity),
                    valueColor: trainingIntensityColor(training.intensity)
                )
                trainingDetailIconRow(
                    icon: "person.3.fill",
                    title: "Места",
                    value: "\(training.currentParticipants) / \(training.maxParticipants)",
                    subtitle: training.spotsLeft > 0 ? "Осталось \(training.spotsLeft) мест" : "Мест нет"
                )
                Divider()

                trainerCard(for: training)
            }
            .padding(24)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func trainingTypeBadge(_ kind: TrainingKind) -> some View {
        let isGroup = kind == .group
        let text = detailTrainingKindRu(kind)
        let tint = isGroup ? Theme.accentBlue : Theme.primary
        return Text(text)
            .font(FCTypography.labelMedium())
            .foregroundStyle(tint)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(tint.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func trainerCard(for training: Training) -> some View {
        let tr = training.trainer
        let initial = String(tr.name.prefix(1)).uppercased()
        let rating = tr.rating ?? 0
        return HStack(alignment: .center, spacing: 16) {
            ZStack {
                Circle()
                    .fill(Theme.primary)
                    .frame(width: 60, height: 60)
                Text(initial.isEmpty ? "?" : initial)
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onPrimary)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text("Тренер")
                    .font(FCTypography.labelMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                Text(tr.name)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onSurface)
                if let spec = tr.specialization, !spec.isEmpty {
                    Text(spec)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if rating > 0 {
                HStack(spacing: 4) {
                    Image(systemName: "star.fill")
                        .foregroundStyle(Theme.warning)
                    Text(String(format: "%.1f", rating))
                        .font(FCTypography.titleMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onBackground)
                }
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
    }

    private func trainingDetailIconRow(
        icon: String,
        title: String,
        value: String,
        subtitle: String? = nil,
        valueColor: Color = Theme.onSurface
    ) -> some View {
        HStack(alignment: .center, spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Theme.primary.opacity(0.22))
                    .frame(width: 44, height: 44)
                Image(systemName: icon)
                    .foregroundStyle(Theme.primary)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(FCTypography.labelMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                Text(value)
                    .font(FCTypography.bodyLarge())
                    .fontWeight(.medium)
                    .foregroundStyle(valueColor)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
            }
            Spacer(minLength: 0)
        }
    }

    /// Как нижний `Box` в `TrainingDetailsContent` на Android; отмена записи только на вкладке «Мои записи».
    private func trainingBookingBottomBar(_ training: Training) -> some View {
        Group {
            if training.isBooked {
                HStack(spacing: 10) {
                    Image(systemName: "checkmark")
                        .font(FCTypography.titleMedium())
                    Text("Вы записаны")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                }
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(Theme.success)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
            } else if training.isFull {
                Button {
                    guard !isBooking else { return }
                    Task { await waitList() }
                } label: {
                    Group {
                        if isBooking {
                            ProgressView()
                                .tint(Theme.primary)
                        } else {
                            HStack(spacing: 8) {
                                Image(systemName: "hourglass")
                                    .font(FCTypography.titleMedium())
                                Text("Записаться в лист ожидания")
                                    .font(FCTypography.titleMedium())
                                    .fontWeight(.semibold)
                                    .multilineTextAlignment(.center)
                            }
                            .foregroundStyle(Theme.primary)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.clear.contentShape(Rectangle()))
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous)
                            .stroke(Theme.outlineVariant, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .disabled(isBooking)
            } else {
                Button {
                    guard !isBooking else { return }
                    Task { await book() }
                } label: {
                    Group {
                        if isBooking {
                            ProgressView()
                                .tint(Theme.onPrimary)
                        } else {
                            HStack(spacing: 8) {
                                Image(systemName: "plus")
                                    .font(FCTypography.titleMedium())
                                Text("Записаться")
                                    .font(FCTypography.titleMedium())
                                    .fontWeight(.semibold)
                            }
                            .foregroundStyle(Theme.onPrimary)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Theme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                    .shadow(color: Color.black.opacity(0.2), radius: 4, x: 0, y: 2)
                }
                .buttonStyle(.plain)
                .disabled(isBooking)
            }
        }
    }

    private func detailTrainingKindRu(_ k: TrainingKind) -> String {
        switch k {
        case .group: return "Групповая"
        case .personal: return "Персональная"
        case .extra: return "Допуслуга"
        }
    }

    private func trainingIntensityTitle(_ i: TrainingIntensity?) -> String {
        guard let i else { return "—" }
        switch i {
        case .low: return "Низкая"
        case .medium: return "Средняя"
        case .high: return "Высокая"
        }
    }

    private func trainingIntensityColor(_ i: TrainingIntensity?) -> Color {
        guard let i else { return Theme.onSurface }
        switch i {
        case .low: return Theme.accentGreen
        case .medium: return Theme.warning
        case .high: return Theme.error
        }
    }

    private func fcFormatTrainingHm(_ value: String) -> String {
        if value.isEmpty { return "--:--" }
        if let tIdx = value.firstIndex(of: "T") {
            let start = value.index(after: tIdx)
            let end = value.index(start, offsetBy: 5, limitedBy: value.endIndex) ?? value.endIndex
            if end > start { return String(value[start..<end]) }
        }
        if value.count >= 5 { return String(value.prefix(5)) }
        return "--:--"
    }

    private func fcFormatTrainingIsoDate(_ value: String) -> String {
        if value.isEmpty { return "—" }
        if let tIdx = value.firstIndex(of: "T") {
            return String(value[..<tIdx])
        }
        if value.count >= 10 { return String(value.prefix(10)) }
        return value
    }

    private func enqueueSnackbar(_ text: String) {
        snackbarMessage = text
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.8) {
            snackbarMessage = nil
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        do {
            training = try await app.api.getTraining(id: trainingId)
            loadError = nil
        } catch {
            training = nil
            loadError = error.localizedDescription
        }
    }

    private func book() async {
        isBooking = true
        defer { isBooking = false }
        do {
            _ = try await app.api.bookTraining(id: trainingId)
            enqueueSnackbar("Вы успешно записаны на тренировку!")
            await load()
        } catch {
            enqueueSnackbar(error.localizedDescription)
        }
    }

    private func waitList() async {
        isBooking = true
        defer { isBooking = false }
        do {
            _ = try await app.api.joinWaitingList(trainingId: trainingId)
            enqueueSnackbar("Вы добавлены в лист ожидания")
            await load()
        } catch {
            enqueueSnackbar(error.localizedDescription)
        }
    }
}
