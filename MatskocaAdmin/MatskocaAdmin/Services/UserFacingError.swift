import Foundation

enum UserFacingError {
    static func message(_ error: Error) -> String {
        let raw = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        let lower = raw.lowercased()
        let detail = httpDetail(raw)

        if let urlError = error as? URLError {
            switch urlError.code {
            case .timedOut:
                return "Сервер долго отвечает. Повторите попытку."
            case .cannotFindHost, .cannotConnectToHost, .notConnectedToInternet, .networkConnectionLost:
                return "Нет связи с CRM. Запустите backend и проверьте адрес API."
            default:
                break
            }
        }

        // Ошибки входа/регистрации (тоже HTTP 401) — не путать с истекшей сессией.
        if isAuthCredentialsFailure(lower: lower, detail: detail) {
            return "Неверный email или пароль"
        }
        if lower.contains("registration_rejected") || detail.localizedCaseInsensitiveContains("отклон") {
            return "Регистрация отклонена администратором"
        }
        if lower.contains("invalid_refresh") || lower.contains("missing_refresh") {
            return "Сессия истекла. Выйдите и войдите заново."
        }

        if lower.contains("401") || lower.contains("unauthorized") {
            let clean = stripApiCode(detail)
            if isVagueCredentialsText(clean) {
                return "Неверный email или пароль"
            }
            if hasCyrillic(clean) && !looksLikeSessionExpiry(clean) {
                return clean
            }
            return "Сессия истекла. Выйдите и войдите заново."
        }
        if lower.contains("403") || lower.contains("forbidden") {
            return "У вас нет прав для этого действия."
        }
        if lower.contains("no route found") || lower.range(of: #"\b404\b"#, options: .regularExpression) != nil {
            return "На CRM ещё нет этого API. Задеплойте свежий crm-backend-symfony (migrate + перезапуск контейнера)."
        }
        if lower.contains("500") || lower.contains("internal server error") {
            return "Ошибка сервера CRM. Уже разбираемся, попробуйте позже."
        }
        if lower.contains("could not find driver") {
            return "Сервер CRM настроен некорректно. Обратитесь к администратору."
        }
        if lower.contains("<!doctype html") || lower.contains("<html") || lower.contains("html response") {
            return "Сервер вернул техническую ошибку. Проверьте, что backend запущен."
        }
        if lower.contains("json parse") || lower.contains("empty response") {
            return "Не удалось прочитать ответ CRM. Запустите backend:\ncd crm-backend-symfony\nphp -S 0.0.0.0:8000 -t public public/index.php"
        }
        if raw.hasPrefix("HTTP ") {
            let clean = stripApiCode(detail)
            return hasCyrillic(clean) ? clean : "Ошибка CRM: \(raw)"
        }
        if raw.isEmpty {
            return "Не удалось выполнить запрос. Повторите попытку."
        }
        if hasCyrillic(raw) {
            return raw
        }
        return "Не удалось загрузить данные. Повторите попытку."
    }

    private static func httpDetail(_ raw: String) -> String {
        if raw.hasPrefix("HTTP ") {
            if let range = raw.range(of: ": ") {
                return String(raw[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func stripApiCode(_ detail: String) -> String {
        detail.replacingOccurrences(
            of: #"\s*\[[a-z0-9_]+]\s*$"#,
            with: "",
            options: [.regularExpression, .caseInsensitive]
        ).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func hasCyrillic(_ text: String) -> Bool {
        text.unicodeScalars.contains { scalar in
            (0x0400...0x04FF).contains(scalar.value)
        }
    }

    private static func looksLikeSessionExpiry(_ detail: String) -> Bool {
        let d = detail.lowercased()
        return d.contains("сессия") || d.contains("refresh") || d.contains("unauthorized")
    }

    private static func isAuthCredentialsFailure(lower: String, detail: String) -> Bool {
        if lower.contains("invalid_credentials") || lower.contains("missing_credentials") {
            return true
        }
        return isVagueCredentialsText(stripApiCode(detail))
    }

    private static func isVagueCredentialsText(_ detail: String) -> Bool {
        let d = detail.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        if d == "неверные данные" || d.hasPrefix("неверные данные") { return true }
        return d.contains("неверн") &&
            (d.contains("парол") || d.contains("email") || d.contains("e-mail") ||
                d.contains("данн") || d.contains("логин"))
    }
}
