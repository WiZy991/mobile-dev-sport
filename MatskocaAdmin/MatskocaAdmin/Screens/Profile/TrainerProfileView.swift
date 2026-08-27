import SwiftUI
import PhotosUI

struct TrainerProfileView: View {
    @Bindable var controller: TrainerProfileController
    var onBack: (() -> Void)?
    @State private var photoItem: PhotosPickerItem?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if controller.requiredMode {
                    StaffInfoBanner(
                        text: "Чтобы начать работу, укажите телефон и специализацию — так клиенты смогут найти вас в приложении."
                    )
                } else {
                    StaffInfoBanner(text: controller.publicationBanner, color: StaffColors.onSurfaceVariant)
                    Text("Так вас увидят клиенты в разделе «Тренеры»")
                        .font(.caption)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                }
                if controller.isLoading {
                    StaffLoadingState()
                } else {
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        ZStack {
                            if let local = controller.localPhoto {
                                Image(uiImage: local)
                                    .resizable()
                                    .scaledToFill()
                            } else if let urlStr = controller.photoUrl, let url = URL(string: urlStr) {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let img): img.resizable().scaledToFill()
                                    default: placeholderPhoto
                                    }
                                }
                            } else {
                                placeholderPhoto
                            }
                        }
                        .frame(width: 96, height: 96)
                        .clipShape(Circle())
                    }
                    .onChange(of: photoItem) { _, item in
                        guard let item else { return }
                        Task {
                            if let data = try? await item.loadTransferable(type: Data.self) {
                                controller.applyPickedPhoto(data)
                            }
                        }
                    }
                    Text(controller.photoUrl == nil && controller.localPhoto == nil ? "Добавить фото" : "Нажмите на фото, чтобы изменить")
                        .font(.caption)
                        .foregroundStyle(StaffColors.onSurfaceVariant)

                    TextField("Имя", text: $controller.name)
                        .textFieldStyle(.roundedBorder)
                    Text("Специализация (до \(controller.specializationsMax))")
                        .font(.subheadline.weight(.semibold))
                    Text("Выбрано: \(controller.selectedSpecializations.count) из \(controller.specializationsMax)")
                        .font(.caption)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                    FlowLayout(spacing: 8) {
                        ForEach(controller.catalog, id: \.self) { item in
                            let selected = controller.selectedSpecializations.contains(item)
                            Button {
                                controller.toggleSpec(item)
                            } label: {
                                Text(item)
                                    .font(.caption)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(selected ? StaffColors.primary : StaffColors.primary.opacity(0.12))
                                    .foregroundStyle(selected ? StaffColors.onPrimary : StaffColors.primary)
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    TextField("О себе", text: $controller.descriptionText, axis: .vertical)
                        .lineLimit(3...6)
                        .textFieldStyle(.roundedBorder)
                    TextField("Телефон *", text: Binding(
                        get: { controller.phoneDisplay },
                        set: { controller.phoneDisplay = $0 }
                    ))
                    .keyboardType(.phonePad)
                    .textFieldStyle(.roundedBorder)
                    StaffPrimaryButton(
                        text: controller.isSaving
                            ? "Сохранение..."
                            : (controller.requiredMode ? "Сохранить и продолжить" : "Сохранить"),
                        action: controller.save
                    )
                    if let err = controller.errorMessage {
                        Text(err).font(.footnote).foregroundStyle(StaffColors.error)
                    }
                    if let status = controller.statusMessage {
                        StaffInfoBanner(text: status, color: StaffColors.success)
                    }
                }
            }
            .padding(16)
        }
        .background(StaffColors.background)
        .navigationTitle(controller.requiredMode ? "Заполните профиль" : "Профиль тренера")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let onBack, !controller.requiredMode {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Назад", action: onBack)
                }
            }
        }
        .staffToolbarStyle()
        .onAppear { controller.onAppear() }
    }

    private var placeholderPhoto: some View {
        ZStack {
            Circle().fill(StaffColors.primary.opacity(0.15))
            Image(systemName: "camera.fill")
                .foregroundStyle(StaffColors.primary)
        }
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(proposal: proposal, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(proposal: proposal, subviews: subviews)
        for (index, point) in result.positions.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + point.x, y: bounds.minY + point.y), proposal: .unspecified)
        }
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, positions: [CGPoint]) {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var positions: [CGPoint] = []
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
        }
        return (CGSize(width: maxWidth.isFinite ? maxWidth : x, height: y + rowHeight), positions)
    }
}
