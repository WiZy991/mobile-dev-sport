import SwiftUI
import PhotosUI
import UIKit

@Observable
@MainActor
final class TrainerProfileController {
    var name = ""
    var selectedSpecializations: [String] = []
    var catalog: [String] = []
    var specializationsMax = TrainerSpecializationCatalog.maxSelected
    var descriptionText = ""
    var phoneNationalDigits = ""
    var photoUrl: String?
    var localPhoto: UIImage?
    var publicationStatus = ""
    var publicationStatusLabel = ""
    var profileComplete = true
    var needsModeration = false
    var requiredMode = false
    var isLoading = true
    var isSaving = false
    var errorMessage: String?
    var statusMessage: String?

    private let env: AppEnvironment
    var onFinished: (() -> Void)?

    private static let maxPhotoSide: CGFloat = 1600

    init(env: AppEnvironment, requiredMode: Bool = false) {
        self.env = env
        self.requiredMode = requiredMode
    }

    var phoneDisplay: String {
        get { RussianPhoneMask.formatMask(phoneNationalDigits) }
        set { phoneNationalDigits = RussianPhoneMask.normalizeNationalDigits(newValue) }
    }

    func onAppear() {
        reload()
    }

    func toggleSpec(_ value: String) {
        if let idx = selectedSpecializations.firstIndex(of: value) {
            selectedSpecializations.remove(at: idx)
        } else if selectedSpecializations.count < specializationsMax {
            selectedSpecializations.append(value)
        }
    }

    func applyPickedPhoto(_ data: Data) {
        localPhoto = UIImage(data: data)
        Task { @MainActor in
            do {
                let prepared = try Self.preparePhotoForUpload(data)
                let profile = try await env.withRefresh { token in
                    try await env.apiClient.uploadTrainerPhoto(token: token, imageData: prepared)
                }
                apply(profile)
                statusMessage = moderationAwarePhotoMessage(for: profile)
            } catch {
                errorMessage = UserFacingError.message(error)
            }
        }
    }

    func reload() {
        isLoading = true
        errorMessage = nil
        Task { @MainActor in
            do {
                let profile = try await env.withRefresh { token in
                    try await env.apiClient.loadTrainerProfile(token: token)
                }
                apply(profile)
                isLoading = false
            } catch {
                isLoading = false
                errorMessage = UserFacingError.message(error)
            }
        }
    }

    func save() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "Укажите имя"
            return
        }
        guard !selectedSpecializations.isEmpty else {
            errorMessage = "Выберите хотя бы одну специализацию"
            return
        }
        guard phoneNationalDigits.count == 10 else {
            errorMessage = "Введите полный номер телефона"
            return
        }
        isSaving = true
        errorMessage = nil
        Task { @MainActor in
            do {
                let profile = try await env.withRefresh { token in
                    try await env.apiClient.updateTrainerProfile(
                        token: token,
                        name: trimmed,
                        specialization: selectedSpecializations.joined(separator: ", "),
                        description: descriptionText,
                        phone: RussianPhoneMask.phoneForApi(phoneNationalDigits)
                    )
                }
                apply(profile)
                isSaving = false
                statusMessage = moderationAwareSaveMessage(for: profile)
                if requiredMode {
                    if profile.profileComplete {
                        onFinished?()
                    } else {
                        errorMessage = "Чтобы начать работу, укажите телефон и специализацию"
                    }
                }
            } catch {
                isSaving = false
                errorMessage = UserFacingError.message(error)
            }
        }
    }

    private func apply(_ profile: TrainerPublicProfile) {
        name = profile.name
        selectedSpecializations = profile.specializations
        catalog = profile.specializationsCatalog.isEmpty
            ? TrainerSpecializationCatalog.default
            : profile.specializationsCatalog
        specializationsMax = max(1, profile.specializationsMax)
        descriptionText = profile.description
        phoneNationalDigits = RussianPhoneMask.normalizeNationalDigits(profile.phone)
        photoUrl = profile.photoUrl
        publicationStatus = profile.publicationStatus
        publicationStatusLabel = profile.publicationStatusLabel
        profileComplete = profile.profileComplete
        needsModeration = profile.needsModeration
    }

    private func moderationAwareSaveMessage(for profile: TrainerPublicProfile) -> String {
        if profile.publicationStatus == "moderation" || profile.needsModeration {
            return "Сохранено. Профиль на модерации — клиенты увидят его после проверки сотрудником."
        }
        return "Сохранено."
    }

    private func moderationAwarePhotoMessage(for profile: TrainerPublicProfile) -> String {
        if profile.publicationStatus == "moderation" || profile.needsModeration {
            return "Фото загружено. Профиль на модерации."
        }
        return "Фото обновлено"
    }

    /// Как Android preparePhotoForUpload: downscale + JPEG 85.
    private static func preparePhotoForUpload(_ data: Data) throws -> Data {
        guard let image = UIImage(data: data) else {
            throw StaffApiError.parseFailed("Не удалось обработать фото")
        }
        let maxSide = max(image.size.width, image.size.height)
        let scaled: UIImage
        if maxSide > maxPhotoSide {
            let scale = maxPhotoSide / maxSide
            let newSize = CGSize(
                width: max(1, image.size.width * scale),
                height: max(1, image.size.height * scale)
            )
            let renderer = UIGraphicsImageRenderer(size: newSize)
            scaled = renderer.image { _ in
                image.draw(in: CGRect(origin: .zero, size: newSize))
            }
        } else {
            scaled = image
        }
        guard let jpeg = scaled.jpegData(compressionQuality: 0.85) else {
            throw StaffApiError.parseFailed("Не удалось сжать фото")
        }
        return jpeg
    }

    var publicationBanner: String {
        switch publicationStatus {
        case "published":
            return "Статус: Опубликован. После сохранения изменений профиль снова уйдёт на модерацию."
        case "hidden":
            return "Статус: Скрыт в клиентском приложении. Обратитесь к сотруднику клуба."
        case "moderation":
            return "Статус: На модерации. Пока клиенты не видят профиль — сотрудник клуба проверит данные."
        default:
            return "Статус: \(publicationStatusLabel.isEmpty ? publicationStatus : publicationStatusLabel)"
        }
    }
}
