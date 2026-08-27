import SwiftUI

struct CreateSessionEditContext {
    let trainingId: String
    let name: String
    let type: String
    let date: String
    let startTime: String
    let durationMinutes: Int
    let room: String
}

struct CreateSessionSheet: View {
    @Bindable var controller: WorkController
    var editing: CreateSessionEditContext? = nil
    @Environment(\.dismiss) private var dismiss

    @State private var name = "Персональная тренировка"
    @State private var date = Date()
    @State private var startTime = Calendar.current.date(bySettingHour: 10, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var durationMinutes = 60
    @State private var room = ""
    @State private var errorMessage: String?
    @State private var isSaving = false

    private let durationChips = [30, 45, 60, 90]

    var body: some View {
        NavigationStack {
            Form {
                TextField("Название", text: $name)
                DatePicker("Дата", selection: $date, displayedComponents: .date)
                DatePicker("Начало", selection: $startTime, displayedComponents: .hourAndMinute)
                Text("Окончание в \(endTimeLabel)")
                    .font(.footnote)
                    .foregroundStyle(StaffColors.onSurfaceVariant)
                Section("Длительность") {
                    HStack {
                        ForEach(durationChips, id: \.self) { mins in
                            Button("\(mins) мин") {
                                durationMinutes = mins
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(durationMinutes == mins ? StaffColors.primary : StaffColors.primary.opacity(0.12))
                            .foregroundStyle(durationMinutes == mins ? StaffColors.onPrimary : StaffColors.primary)
                            .clipShape(Capsule())
                        }
                    }
                }
                TextField("Зал (необязательно)", text: $room)
                if editing == nil {
                    Text("После создания откроется окно, где можно прикрепить клиента.")
                        .font(.footnote)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                }
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(StaffColors.error)
                }
            }
            .navigationTitle(editing == nil ? "Новая запись" : "Изменить запись")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "..." : (editing == nil ? "Создать" : "Сохранить")) {
                        save()
                    }
                    .disabled(isSaving)
                }
            }
            .onAppear { applyEditingDefaults() }
        }
    }

    private var endTimeLabel: String {
        let cal = Calendar.current
        guard let end = cal.date(byAdding: .minute, value: durationMinutes, to: startTime) else { return "—" }
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        f.timeZone = cal.timeZone
        return f.string(from: end)
    }

    private func applyEditingDefaults() {
        if editing == nil {
            name = "Персональная тренировка"
            return
        }
        guard let editing else { return }
        name = editing.name
        room = editing.room
        durationMinutes = editing.durationMinutes
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        if let d = formatter.date(from: editing.date) {
            date = d
        }
        formatter.dateFormat = "HH:mm"
        if let t = formatter.date(from: editing.startTime) {
            let cal = Calendar.current
            let parts = cal.dateComponents([.hour, .minute], from: t)
            startTime = cal.date(bySettingHour: parts.hour ?? 10, minute: parts.minute ?? 0, second: 0, of: date) ?? startTime
        }
    }

    private func save() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedName = trimmed.isEmpty ? "Персональная тренировка" : trimmed
        isSaving = true
        errorMessage = nil
        Task { @MainActor in
            do {
                if let editing {
                    try await controller.updateTrainingSession(
                        trainingId: editing.trainingId,
                        name: resolvedName,
                        date: date,
                        startTime: startTime,
                        durationMinutes: durationMinutes,
                        room: room
                    )
                    controller.dismissAssignDialog()
                } else {
                    try await controller.createTrainingSession(
                        name: resolvedName,
                        type: "personal",
                        date: date,
                        startTime: startTime,
                        durationMinutes: durationMinutes,
                        room: room,
                        maxParticipants: 1
                    )
                }
                isSaving = false
                dismiss()
            } catch {
                isSaving = false
                errorMessage = UserFacingError.message(error)
            }
        }
    }
}
