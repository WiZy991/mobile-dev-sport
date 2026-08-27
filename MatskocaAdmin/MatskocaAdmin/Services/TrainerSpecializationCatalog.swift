import Foundation

/// Справочник специализаций — синхронно с CRM `StaffOnboardingService::specializationCatalog` и Android.
enum TrainerSpecializationCatalog {
    static let maxSelected = 5

    static let `default`: [String] = [
        "Персональный тренер",
        "Силовые тренировки",
        "Функциональный тренинг",
        "Кроссфит",
        "Йога",
        "Пилатес",
        "Стретчинг",
        "Кардио",
        "Бокс / единоборства",
        "Реабилитация",
        "Похудение",
        "Набор массы",
        "Подготовка к соревнованиям",
        "Детский фитнес",
        "Групповые программы",
    ]

    static func parseSelected(_ raw: String, catalog: [String] = Self.default) -> [String] {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let lowerCatalog = catalog.map { $0.lowercased() }
        var out: [String] = []
        for part in trimmed.split(whereSeparator: { ",;|".contains($0) }) {
            let label = part.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !label.isEmpty else { continue }
            if let idx = lowerCatalog.firstIndex(of: label.lowercased()) {
                let canonical = catalog[idx]
                if !out.contains(canonical) {
                    out.append(canonical)
                }
            }
            if out.count >= maxSelected { break }
        }
        return out
    }

    static func join(_ selected: [String]) -> String {
        selected.joined(separator: ", ")
    }
}
