import Foundation

struct TrainingDiaryEntry: Codable, Identifiable, Hashable {
    let id: String
    let dateMillis: Int64
    let title: String
    let durationMinutes: Int?
    let notes: String
}

struct NewTrainingDiaryEntry {
    let title: String
    let durationMinutes: Int?
    let notes: String
}

/// Локальный дневник тренировок (как `TrainingDiaryRepository.kt` на Android).
enum TrainingDiaryRepository {
    private static let storageKey = "training_diary_entries_v1"

    static func observeEntries() -> [TrainingDiaryEntry] {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let list = try? AppJSON.decoder().decode([TrainingDiaryEntry].self, from: data)
        else { return [] }
        return list.sorted { $0.dateMillis > $1.dateMillis }
    }

    static func addEntry(_ payload: NewTrainingDiaryEntry) {
        var all = observeEntries()
        let entry = TrainingDiaryEntry(
            id: UUID().uuidString,
            dateMillis: Int64(Date().timeIntervalSince1970 * 1000),
            title: payload.title,
            durationMinutes: payload.durationMinutes,
            notes: payload.notes
        )
        all.insert(entry, at: 0)
        persist(all)
    }

    static func deleteEntry(id: String) {
        var all = observeEntries()
        all.removeAll { $0.id == id }
        persist(all)
    }

    private static func persist(_ entries: [TrainingDiaryEntry]) {
        if let data = try? AppJSON.encoder().encode(entries) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }
}
