import SwiftUI

/// Дневник тренировок — порт `TrainingDiaryScreen.kt`.
struct TrainingDiaryView: View {
    @State private var entries: [TrainingDiaryEntry] = []
    @State private var showAddSheet = false
    @State private var draftTitle = ""
    @State private var draftDuration = ""
    @State private var draftNotes = ""
    @State private var addError: String?

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Theme.background.ignoresSafeArea()

            if entries.isEmpty {
                VStack(spacing: 8) {
                    Text("Пока нет записей")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onBackground)
                        .padding(.top, 36)
                    Text("Нажмите + и добавьте первую тренировку.")
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(24)
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(entries) { entry in
                            diaryCard(entry)
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 88)
                }
            }

            Button {
                openAddSheet()
            } label: {
                Image(systemName: "plus")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Theme.onPrimary)
                    .frame(width: 56, height: 56)
                    .background(Theme.primary)
                    .clipShape(Circle())
                    .shadow(color: Color.black.opacity(0.18), radius: 6, x: 0, y: 3)
            }
            .buttonStyle(.plain)
            .padding(24)
        }
        .fcPrimaryNavigation(title: "Дневник тренировок")
        .onAppear { reload() }
        .sheet(isPresented: $showAddSheet) {
            NavigationStack {
                Form {
                    Section {
                        TextField("Название", text: $draftTitle)
                        TextField("Длительность (мин)", text: $draftDuration)
                            .keyboardType(.numberPad)
                        TextField("Заметка", text: $draftNotes, axis: .vertical)
                            .lineLimit(2...4)
                    }
                    if let addError {
                        Section {
                            Text(addError)
                                .foregroundStyle(Theme.error)
                        }
                    }
                }
                .navigationTitle("Новая запись")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Отмена") { showAddSheet = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Сохранить") { saveEntry() }
                    }
                }
            }
            .presentationDetents([.medium, .large])
        }
    }

    private func diaryCard(_ entry: TrainingDiaryEntry) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(entry.title)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.onBackground)
                Spacer()
                Button {
                    TrainingDiaryRepository.deleteEntry(id: entry.id)
                    reload()
                } label: {
                    Image(systemName: "trash")
                        .foregroundStyle(Theme.error)
                }
                .buttonStyle(.plain)
            }
            Text(formatDate(entry.dateMillis))
                .font(FCTypography.bodySmall())
                .foregroundStyle(Theme.onSurfaceVariant)
            if let mins = entry.durationMinutes {
                Text("Длительность: \(mins) мин")
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onBackground)
            }
            if !entry.notes.isEmpty {
                Text(entry.notes)
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surfaceVariant.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius14, style: .continuous))
    }

    private func openAddSheet() {
        draftTitle = ""
        draftDuration = ""
        draftNotes = ""
        addError = nil
        showAddSheet = true
    }

    private func saveEntry() {
        let title = draftTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else {
            addError = "Введите название тренировки"
            return
        }
        let duration = Int(draftDuration.trimmingCharacters(in: .whitespacesAndNewlines))
        TrainingDiaryRepository.addEntry(
            NewTrainingDiaryEntry(
                title: title,
                durationMinutes: duration,
                notes: draftNotes.trimmingCharacters(in: .whitespacesAndNewlines)
            )
        )
        showAddSheet = false
        reload()
    }

    private func reload() {
        entries = TrainingDiaryRepository.observeEntries()
    }

    private func formatDate(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "d MMMM yyyy, HH:mm"
        return f.string(from: date)
    }
}
