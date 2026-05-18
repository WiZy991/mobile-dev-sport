import SwiftUI
import UIKit

/// Общие поля входа/регистрации: ввод и плейсхолдеры всегда **тёмные на белой карточке** (читаемо).
enum FCAuthFields {
    private static let inputColor = Theme.onBackground
    private static let promptColor = Theme.onSurfaceVariant.opacity(0.85)

    static func outlinedField(
        label: String,
        text: Binding<String>,
        contentType: UITextContentType?,
        keyboard: UIKeyboardType,
        icon: String,
        email: Bool = false
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Theme.onSurfaceVariant)
                .frame(width: 24)
            TextField(
                "",
                text: text,
                prompt: Text(label).foregroundStyle(promptColor)
            )
            .textContentType(contentType)
            .keyboardType(keyboard)
            .textInputAutocapitalization(email ? .never : .words)
            .autocorrectionDisabled(email)
            .foregroundStyle(inputColor)
            .tint(Theme.primary)
        }
        .font(FCTypography.bodyLarge())
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Theme.surface)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous)
                .stroke(Theme.outlineVariant, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous))
    }

    static func outlinedFieldPlaceholder(
        text: Binding<String>,
        placeholder: String,
        keyboard: UIKeyboardType,
        icon: String
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Theme.onSurfaceVariant)
                .frame(width: 24)
            TextField(
                "",
                text: text,
                prompt: Text(placeholder).foregroundStyle(promptColor)
            )
            .keyboardType(keyboard)
            .foregroundStyle(inputColor)
            .tint(Theme.primary)
        }
        .font(FCTypography.bodyLarge())
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Theme.surface)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous)
                .stroke(Theme.outlineVariant, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous))
    }

    static func secureField(
        label: String,
        text: Binding<String>,
        visible: Binding<Bool>
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "lock.fill")
                .foregroundStyle(Theme.onSurfaceVariant)
                .frame(width: 24)
            Group {
                if visible.wrappedValue {
                    TextField(
                        "",
                        text: text,
                        prompt: Text(label).foregroundStyle(promptColor)
                    )
                    .textContentType(.password)
                } else {
                    SecureField(
                        "",
                        text: text,
                        prompt: Text(label).foregroundStyle(promptColor)
                    )
                    .textContentType(.password)
                }
            }
            .textInputAutocapitalization(.never)
            .foregroundStyle(inputColor)
            .tint(Theme.primary)
            Button {
                visible.wrappedValue.toggle()
            } label: {
                Image(systemName: visible.wrappedValue ? "eye.slash.fill" : "eye.fill")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .font(FCTypography.bodyLarge())
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Theme.surface)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous)
                .stroke(Theme.outlineVariant, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous))
    }
}
