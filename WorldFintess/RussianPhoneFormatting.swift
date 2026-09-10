import SwiftUI
import UIKit

// MARK: - Parity с `PhoneRuMask.kt`

/// До 10 цифр после кода страны (без ведущей 7/8).
func normalizeRussianNationalDigits(_ input: String) -> String {
    var d = String(input.filter(\.isNumber))
    if d.first == "8" || d.first == "7" {
        d.removeFirst()
    }
    return String(d.prefix(10))
}

/// Вид номера для поля: `+7 (XXX) XXX-XX-XX` (в состоянии — только 10 национальных цифр).
func formatRussianPhoneMask(_ national10: String) -> String {
    let d = String(national10.filter(\.isNumber).prefix(10))
    if d.isEmpty { return "+7 (" }

    var sb = "+7 ("
    sb += String(d.prefix(3))
    if d.count < 3 { return sb }

    sb += ") "
    let rest = String(d.dropFirst(3))
    sb += String(rest.prefix(min(3, rest.count)))

    if d.count <= 6 { return sb }
    sb += "-"
    let mid = String(d.dropFirst(6))
    sb += String(mid.prefix(min(2, mid.count)))

    if d.count <= 8 { return sb }
    sb += "-"
    sb += String(d.dropFirst(8))
    return sb
}

func phoneForApi(_ national10: String) -> String {
    let n = normalizeRussianNationalDigits(national10)
    return n.count == 10 ? "+7\(n)" : ""
}

/**
 * Разбор текста из поля с маской `+7 (XXX) XXX-XX-XX`.
 * Backspace по скобке/пробелу снимает последнюю цифру (`PhoneRuMask.kt`).
 */
func nationalDigitsFromPhoneField(_ raw: String, previousNational: String) -> String {
    let prevFormatted = formatRussianPhoneMask(previousNational)
    let normalized = normalizeRussianNationalDigits(raw)
    if raw.count < prevFormatted.count,
       normalized.count >= previousNational.count,
       !previousNational.isEmpty
    {
        return String(previousNational.dropLast())
    }
    return normalized
}

// MARK: - UIKit field (SwiftUI TextField часто «съедает» маску)

/// Поле телефона с маской `+7 (XXX) XXX-XX-XX`, курсор всегда в конце (как `russianPhoneFieldValue`).
struct RussianPhoneTextField: UIViewRepresentable {
    @Binding var nationalDigits: String
    var textColor: UIColor
    var font: UIFont
    var placeholder: String = "Телефон"
    var onSubmit: (() -> Void)?

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> UITextField {
        let tf = UITextField()
        tf.keyboardType = .phonePad
        tf.textContentType = .telephoneNumber
        tf.autocorrectionType = .no
        tf.borderStyle = .none
        tf.delegate = context.coordinator
        tf.addTarget(context.coordinator, action: #selector(Coordinator.editingChanged(_:)), for: .editingChanged)
        tf.setContentHuggingPriority(.defaultLow, for: .horizontal)
        tf.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return tf
    }

    func updateUIView(_ uiView: UITextField, context: Context) {
        context.coordinator.parent = self
        uiView.textColor = textColor
        uiView.font = font
        uiView.attributedPlaceholder = NSAttributedString(
            string: placeholder,
            attributes: [.foregroundColor: textColor.withAlphaComponent(0.45)]
        )

        let formatted = formatRussianPhoneMask(nationalDigits)
        if uiView.text != formatted {
            uiView.text = formatted
            // Курсор в конец только когда сами обновили текст из binding.
            if uiView.isFirstResponder {
                let end = uiView.endOfDocument
                uiView.selectedTextRange = uiView.textRange(from: end, to: end)
            }
        }
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: RussianPhoneTextField

        init(_ parent: RussianPhoneTextField) {
            self.parent = parent
        }

        @objc func editingChanged(_ tf: UITextField) {
            apply(from: tf)
        }

        func textField(
            _ textField: UITextField,
            shouldChangeCharactersIn range: NSRange,
            replacementString string: String
        ) -> Bool {
            // Собираем итоговую строку сами — UITextField иначе может временно показать «сырые» цифры.
            let current = textField.text ?? ""
            guard let range = Range(range, in: current) else { return false }
            let next = current.replacingCharacters(in: range, with: string)
            let digits = nationalDigitsFromPhoneField(next, previousNational: parent.nationalDigits)
            parent.nationalDigits = digits
            let formatted = formatRussianPhoneMask(digits)
            textField.text = formatted
            let end = textField.endOfDocument
            textField.selectedTextRange = textField.textRange(from: end, to: end)
            return false
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            parent.onSubmit?()
            return true
        }

        private func apply(from tf: UITextField) {
            let digits = nationalDigitsFromPhoneField(tf.text ?? "", previousNational: parent.nationalDigits)
            if digits != parent.nationalDigits {
                parent.nationalDigits = digits
            }
            let formatted = formatRussianPhoneMask(digits)
            if tf.text != formatted {
                tf.text = formatted
                let end = tf.endOfDocument
                tf.selectedTextRange = tf.textRange(from: end, to: end)
            }
        }
    }
}
