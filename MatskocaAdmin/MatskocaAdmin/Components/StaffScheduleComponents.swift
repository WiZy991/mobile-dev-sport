import SwiftUI

struct StaffScheduleTabContent: View {
    let schedule: ScheduleTabUi
    let onDaySelected: (String) -> Void
    let onTypeFilterSelected: (String?) -> Void

    var body: some View {
        if schedule.denied {
            StaffEmptyState(message: schedule.deniedMessage)
                .padding(16)
        } else {
            VStack(spacing: 0) {
                if !schedule.days.isEmpty {
                    StaffScheduleDateSelector(days: schedule.days, onDaySelected: onDaySelected)
                }
                StaffScheduleTypeFilters(
                    selectedFilter: schedule.selectedTypeFilter,
                    onFilterSelected: onTypeFilterSelected
                )

                if schedule.loading {
                    Spacer()
                    ProgressView().tint(StaffColors.primary)
                    Spacer()
                } else if schedule.sessions.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "calendar.badge.exclamationmark")
                            .font(.system(size: 64))
                            .foregroundStyle(StaffColors.onSurfaceVariant)
                        Text("Нет тренировок на выбранную дату")
                            .font(.body)
                            .foregroundStyle(StaffColors.onSurfaceVariant)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(32)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(schedule.sessions) { session in
                                StaffScheduleSessionCard(session: session)
                            }
                        }
                        .padding(16)
                    }
                }
            }
        }
    }
}

private struct StaffScheduleDateSelector: View {
    let days: [ScheduleDayUi]
    let onDaySelected: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(days) { day in
                    StaffScheduleDateItem(day: day) {
                        onDaySelected(day.date)
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 12)
        }
        .background(StaffColors.surface)
    }
}

private struct StaffScheduleDateItem: View {
    let day: ScheduleDayUi
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(spacing: 4) {
                Text(day.weekdayLabel)
                    .font(.caption2)
                    .opacity(0.8)
                Text(day.dayNumber)
                    .font(.title3)
                    .fontWeight(.bold)
                if day.isToday {
                    Circle()
                        .fill(day.selected ? StaffColors.onPrimary : StaffColors.primary)
                        .frame(width: 6, height: 6)
                }
            }
            .foregroundStyle(day.selected ? StaffColors.onPrimary : StaffColors.onSurfaceVariant)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(day.selected ? StaffColors.primary : Color(.systemGray5))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

private struct StaffScheduleTypeFilters: View {
    let selectedFilter: String?
    let onFilterSelected: (String?) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                filterChip("Все", selected: selectedFilter == nil) { onFilterSelected(nil) }
                filterChip("Групповые", selected: selectedFilter == "group") { onFilterSelected("group") }
                filterChip("Персональные", selected: selectedFilter == "personal") { onFilterSelected("personal") }
                filterChip("Допуслуги", selected: selectedFilter == "extra") { onFilterSelected("extra") }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    private func filterChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(selected ? StaffColors.primary : StaffColors.surface)
                .foregroundStyle(selected ? StaffColors.onPrimary : StaffColors.onBackground)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(Color.gray.opacity(0.3), lineWidth: selected ? 0 : 1))
        }
        .buttonStyle(.plain)
    }
}

struct StaffScheduleSessionCard: View {
    let session: ScheduleSessionUi

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(spacing: 2) {
                Text(session.startTime)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(StaffColors.primary)
                Text("\(session.durationMinutes) мин")
                    .font(.caption)
                    .foregroundStyle(StaffColors.onSurfaceVariant)
            }

            RoundedRectangle(cornerRadius: 2)
                .fill(typeAccentColor(session.type))
                .frame(width: 3, height: 60)

            VStack(alignment: .leading, spacing: 4) {
                Text(session.typeLabel)
                    .font(.caption2)
                    .foregroundStyle(StaffColors.primary)
                Text(session.title)
                    .font(.headline)
                    .lineLimit(2)
                HStack(spacing: 4) {
                    Image(systemName: "person")
                        .font(.caption)
                    Text(session.trainer)
                        .font(.subheadline)
                        .lineLimit(1)
                }
                .foregroundStyle(StaffColors.onSurfaceVariant)
                HStack(spacing: 4) {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.caption)
                    Text(session.room)
                        .font(.subheadline)
                }
                .foregroundStyle(StaffColors.onSurfaceVariant)
            }

            Spacer(minLength: 0)

            VStack(alignment: .trailing) {
                spotsLabelView
            }
        }
        .padding(16)
        .background(StaffColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.06), radius: 2, y: 1)
    }

    @ViewBuilder
    private var spotsLabelView: some View {
        if let label = sessionSpotsLabel(session) {
            if label.isFull {
                Text("Мест нет")
                    .font(.caption)
                    .fontWeight(.medium)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(StaffColors.error.opacity(0.1))
                    .foregroundStyle(StaffColors.error)
                    .clipShape(Capsule())
            } else if let spotsLeft = label.spotsLeft {
                Text("Осталось \(spotsLeft)")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundStyle(spotsLeft <= 3 ? StaffColors.warning : StaffColors.onSurfaceVariant)
            } else if label.clientCount > 0 {
                Text("Записано \(label.clientCount)")
                    .font(.caption)
                    .fontWeight(.medium)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(StaffColors.success.opacity(0.1))
                    .foregroundStyle(StaffColors.success)
                    .clipShape(Capsule())
            }
        }
    }

    private func typeAccentColor(_ type: String) -> Color {
        switch type {
        case "group": return StaffColors.accentBlue
        case "personal": return StaffColors.primary
        case "extra": return StaffColors.success
        default: return StaffColors.warning
        }
    }

    private struct SessionSpotsLabel {
        var spotsLeft: Int?
        var clientCount: Int = 0
        var isFull: Bool = false
    }

    private func sessionSpotsLabel(_ session: ScheduleSessionUi) -> SessionSpotsLabel? {
        if let booked = session.bookedCount, let maxParticipants = session.maxParticipants {
            let left = Swift.max(0, maxParticipants - booked)
            return SessionSpotsLabel(spotsLeft: left, isFull: booked >= maxParticipants)
        }
        if !session.clientNames.isEmpty {
            return SessionSpotsLabel(clientCount: session.clientNames.count)
        }
        return nil
    }
}
