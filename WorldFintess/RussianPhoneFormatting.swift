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

/// Вид номера для поля: `+7 900 000 00 00` (в состоянии — только 10 национальных цифр).
/// Без скобок: на Android скобки = необязательный код города.
func formatRussianPhoneMask(_ national10: String) -> String {
    let d = String(national10.filter(\.isNumber).prefix(10))
    if d.isEmpty { return "+7 " }

    var sb = "+7 "
    sb += String(d.prefix(3))
    if d.count < 3 { return sb }

    sb += " "
    let rest = String(d.dropFirst(3))
    sb += String(rest.prefix(min(3, rest.count)))

    if d.count <= 6 { return sb }
    sb += " "
    let mid = String(d.dropFirst(6))
    sb += String(mid.prefix(min(2, mid.count)))

    if d.count <= 8 { return sb }
    sb += " "
    sb += String(d.dropFirst(8))
    return sb
}

func phoneForApi(_ national10: String) -> String {
    let n = normalizeRussianNationalDigits(national10)
    return n.count == 10 ? "+7\(n)" : ""
}

/**
 * Разбор текста из поля с маской `+7 900 000 00 00`.
 * Backspace по пробелу снимает последнюю цифру (`PhoneRuMask.kt`).
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

/// Поле телефона с маской `+7 900 000 00 00`, курсор всегда в конце.
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
        tf.font = font
        tf.textColor = textColor
        tf.attributedPlaceholder = NSAttributedString(
            string: placeholder,
            attributes: [.foregroundColor: textColor.withAlphaComponent(0.45)]
        )
        tf.returnKeyType = .done
        tf.text = formatRussianPhoneMask(nationalDigits)
        return tf
    }

    func updateUIView(_ uiView: UITextField, context: Context) {
        context.coordinator.parent = self
        uiView.font = font
        uiView.textColor = textColor
        let formatted = formatRussianPhoneMask(nationalDigits)
        if uiView.text != formatted {
            uiView.text = formatted
        }
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: RussianPhoneTextField

        init(_ parent: RussianPhoneTextField) {
            self.parent = parent
        }

        @objc func editingChanged(_ tf: UITextField) {
            let raw = tf.text ?? ""
            let prev = parent.nationalDigits
            let digits = nationalDigitsFromPhoneField(raw, previousNational: prev)
            parent.nationalDigits = digits
            let formatted = formatRussianPhoneMask(digits)
            if tf.text != formatted {
                tf.text = formatted
            }
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            parent.onSubmit?()
            return true
        }
    }
}
