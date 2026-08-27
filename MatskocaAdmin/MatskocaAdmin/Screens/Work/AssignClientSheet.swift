import SwiftUI

struct AssignClientSheet: View {
    @Bindable var controller: WorkController
    @State private var showEditSession = false

    var body: some View {
        NavigationStack {
            Group {
                if let dialog = controller.state.assignDialog {
                    content(dialog)
                } else {
                    Text("Нет данных")
                        .padding()
                }
            }
            .navigationTitle(controller.state.assignDialog.map { "Запись: \($0.sessionTitle)" } ?? "Запись")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { controller.dismissAssignDialog() }
                }
            }
            .sheet(isPresented: $showEditSession) {
                if let session = controller.assignSessionForEdit,
                   let trainingId = session.trainingId {
                    CreateSessionSheet(
                        controller: controller,
                        editing: CreateSessionEditContext(
                            trainingId: trainingId,
                            name: session.title,
                            type: session.type,
                            date: session.date,
                            startTime: session.startTime,
                            durationMinutes: session.durationMinutes,
                            room: session.room == "—" ? "" : session.room
                        )
                    )
                }
            }
        }
    }

    @ViewBuilder
    private func content(_ dialog: AssignClientDialogUi) -> some View {
        List {
            Section {
                Button("Изменить время или зал") {
                    showEditSession = true
                }
            }
            if !dialog.booked.isEmpty {
                Section("Уже записаны") {
                    ForEach(dialog.booked) { row in
                        HStack {
                            Button {
                                if let id = row.clientId {
                                    controller.onOpenClient?(id)
                                    controller.dismissAssignDialog()
                                }
                            } label: {
                                Text(row.title)
                                    .underline(row.clientId != nil)
                                    .foregroundStyle(StaffColors.onSurface)
                            }
                            .buttonStyle(.plain)
                            Spacer()
                            if !row.meta.isEmpty {
                                Button("Снять") {
                                    controller.cancelAssignBooking(row.meta)
                                }
                                .foregroundStyle(StaffColors.error)
                            }
                        }
                    }
                }
            }
            Section("Поиск клиента") {
                TextField("Поиск", text: Binding(
                    get: { controller.state.assignDialog?.query ?? "" },
                    set: { controller.onAssignQueryChange($0) }
                ))
                .textInputAutocapitalization(.never)
                StaffPrimaryButton(
                    text: dialog.loading ? "Ищем..." : "Найти",
                    action: controller.searchAssignClients,
                    enabled: !dialog.loading
                )
                if let err = dialog.errorMessage {
                    Text(err).font(.footnote).foregroundStyle(StaffColors.error)
                }
            }
            Section {
                ForEach(dialog.clients) { client in
                    Button {
                        if let id = client.clientId {
                            controller.bookAssignClient(id)
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(client.title).foregroundStyle(StaffColors.onSurface)
                            if !client.subtitle.isEmpty {
                                Text(client.subtitle)
                                    .font(.caption)
                                    .foregroundStyle(StaffColors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
