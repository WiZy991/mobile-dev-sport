import Foundation

enum UserFacingError {
    static func message(_ error: Error) -> String {
        let raw = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        let lower = raw.lowercased()

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

        if lower.contains("401") || lower.contains("unauthorized") {
            return "Сессия истекла. Выйдите и войдите заново."
        }
        if lower.contains("403") || lower.contains("forbidden") {
            return "У вас нет прав для этого действия."
        }
        if lower.contains("404") {
            return "Сервис временно недоступен. Попробуйте позже."
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
            return "Ошибка CRM: \(raw)"
        }
        if raw.isEmpty {
            return "Не удалось выполнить запрос. Повторите попытку."
        }
        return "Не удалось загрузить данные. Повторите попытку."
    }
}
