import SwiftUI

/// Выбор «моего» зала — паритет Android `SelectPreferredClubScreen`.
/// Меняет `users.club_id`: главная, карта, occupancy, `entry_qr_format`, фильтр абонементов и покупка.
struct SelectPreferredClubView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.dismiss) private var dismiss

    @State private var clubs: [ClubItem] = []
    @State private var selectedId: String?
    @State private var isLoading = true
    @State private var isSaving = false
    @State private var error: String?

    private let subtitle =
        "Абонемент действует только в выбранном зале — в другие по нему не пройти."

    var body: some View {
        Group {
            if isLoading && clubs.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error, clubs.isEmpty {
                Text(error)
                    .font(FCTypography.bodyLarge())
                    .foregroundStyle(Theme.error)
                    .multilineTextAlignment(.center)
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 14) {
                        hintBanner
                        ForEach(clubs) { club in
                            clubCard(club)
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 24)
                }
            }
        }
        .background(Theme.background)
        .fcPrimaryNavigation(title: "Выбрать клуб")
        .task { await load() }
        .disabled(isSaving)
        .overlay {
            if isSaving {
                ProgressView()
                    .padding(20)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private var hintBanner: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "mappin.circle.fill")
                .foregroundStyle(Theme.primary)
            Text(subtitle)
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.onBackground)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .background(Theme.primary.opacity(0.1))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous)
                .stroke(Theme.primary.opacity(0.22), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
    }

    private func clubCard(_ club: ClubItem) -> some View {
        let selected = club.id == selectedId
        return Button {
            Task { await select(club.id) }
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                ZStack(alignment: .bottomLeading) {
                    clubImage(club)
                        .frame(height: 132)
                        .frame(maxWidth: .infinity)
                        .clipped()
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.55)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    Text(club.name)
                        .font(FCTypography.titleMedium())
                        .fontWeight(.bold)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .padding(16)
                    if selected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.title2)
                            .foregroundStyle(.white)
                            .padding(12)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    }
                }
                .frame(height: 132)
                .clipped()

                HStack(spacing: 8) {
                    Image(systemName: "mappin.and.ellipse")
                        .foregroundStyle(Theme.primary)
                    Text(club.address.isEmpty ? "Адрес уточняется" : club.address)
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .lineLimit(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if selected {
                        Text("Выбран")
                            .font(FCTypography.labelLarge())
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.primary)
                    }
                }
                .padding(14)
            }
            .background(Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.radius20, style: .continuous)
                    .stroke(selected ? Theme.primary : Color.clear, lineWidth: 2.5)
            )
            .shadow(color: .black.opacity(selected ? 0.12 : 0.06), radius: selected ? 6 : 2, y: 2)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func clubImage(_ club: ClubItem) -> some View {
        if let urlStr = club.imageUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
           !urlStr.isEmpty,
           let url = URL(string: urlStr)
        {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let img):
                    img.resizable().scaledToFill()
                default:
                    placeholderImage(for: club.id)
                }
            }
        } else {
            placeholderImage(for: club.id)
        }
    }

    @ViewBuilder
    private func placeholderImage(for clubId: String) -> some View {
        if let asset = RegistrationVenues.fallbackAssetName(forClubId: clubId),
           UIImage(named: asset) != nil
        {
            Image(asset).resizable().scaledToFill()
        } else {
            Theme.primary.opacity(0.25)
        }
    }

    private func load() async {
        isLoading = true
        error = nil
        defer { isLoading = false }
        selectedId = app.currentUser?.clubId
        do {
            let list = try await app.api.getClubs()
            clubs = Self.orderedClubs(list)
            if clubs.isEmpty {
                clubs = RegistrationVenues.orderedCards.map(RegistrationVenues.clubItem(for:))
            }
            if clubs.isEmpty {
                error = "Нет доступных клубов"
            }
        } catch {
            clubs = RegistrationVenues.orderedCards.map(RegistrationVenues.clubItem(for:))
            if clubs.isEmpty {
                self.error = error.localizedDescription
            }
        }
    }

    private func select(_ clubId: String) async {
        let id = clubId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty, Int(id).map({ $0 > 0 }) == true else { return }
        if id == selectedId {
            dismiss()
            return
        }
        isSaving = true
        error = nil
        defer { isSaving = false }
        do {
            let user = try await app.api.assignClub(clubId: id)
            app.updateCachedUser(user)
            app.subscriptionsRevision = UUID()
            selectedId = user.clubId ?? id
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }

    /// Порядок как на регистрации (известные id), остальные в конце.
    private static func orderedClubs(_ fromApi: [ClubItem]) -> [ClubItem] {
        let filtered = fromApi.filter { !$0.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        let preferredOrder = ["2", "11", "1"]
        let byId = Dictionary(uniqueKeysWithValues: filtered.map { ($0.id, $0) })
        var out: [ClubItem] = []
        var seen = Set<String>()
        for id in preferredOrder {
            if let c = byId[id] {
                out.append(c)
                seen.insert(id)
            }
        }
        for c in filtered where !seen.contains(c.id) {
            out.append(c)
        }
        return out
    }
}
