import AuthenticationServices
import CryptoKit
import Security
import UIKit

enum SberIDAuthError: Error, LocalizedError {
    case cannotStartSession
    case invalidCallbackURL
    case missingAuthorizationParameters
    case userCanceled

    var errorDescription: String? {
        switch self {
        case .cannotStartSession: return "Не удалось открыть окно авторизации"
        case .invalidCallbackURL: return "Некорректный ответ от Сбер ID"
        case .missingAuthorizationParameters: return "В ответе нет кода авторизации"
        case .userCanceled: return "Вход отменён"
        }
    }
}

/// PKCE + ASWebAuthenticationSession; сервер собирает URL авторизации (`/auth/sber/login`), приложение обменивает код на `POST /auth/sber/callback`.
@MainActor
final class SberIDAuthPresentationAnchorProvider: NSObject, ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap(\.windows).first { $0.isKeyWindow }
            ?? scenes.flatMap(\.windows).first
        return window ?? ASPresentationAnchor()
    }
}

@MainActor
final class SberIDAuthService: NSObject {
    static let shared = SberIDAuthService()

    private let anchorProvider = SberIDAuthPresentationAnchorProvider()
    private var authSession: ASWebAuthenticationSession?

    func loginCompleting(api: FitnessAPI, attachToLoggedInSession: Bool) async throws -> AuthResponse {
        let verifier = Self.randomPKCEVerifier()
        let challenge = Self.codeChallengeS256(verifier: verifier)
        let redirect = AppConfiguration.sberRedirectURI

        let authorizeURL = try await api.fetchSberAuthorizeURL(
            codeChallenge: challenge,
            redirectURI: redirect
        )

        let (code, state) = try await presentBrowserLogin(url: authorizeURL, callbackScheme: AppConfiguration.sberURLScheme)

        return try await api.exchangeSberAuthorizationCode(
            code: code,
            codeVerifier: verifier,
            redirectURI: redirect,
            state: state,
            authenticated: attachToLoggedInSession
        )
    }

    private func presentBrowserLogin(url: URL, callbackScheme: String) async throws -> (code: String, state: String) {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<(String, String), Error>) in
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: callbackScheme) { [weak self] callbackURL, error in
                self?.authSession = nil
                if let error {
                    let ns = error as NSError
                    if ns.domain == ASWebAuthenticationSessionError.errorDomain,
                       ns.code == ASWebAuthenticationSessionError.canceledLogin.rawValue
                    {
                        cont.resume(throwing: SberIDAuthError.userCanceled)
                        return
                    }
                    cont.resume(throwing: error)
                    return
                }
                guard let callbackURL else {
                    cont.resume(throwing: SberIDAuthError.invalidCallbackURL)
                    return
                }
                guard let items = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?.queryItems,
                      let code = items.first(where: { $0.name == "code" })?.value,
                      let state = items.first(where: { $0.name == "state" })?.value,
                      !code.isEmpty,
                      !state.isEmpty
                else {
                    cont.resume(throwing: SberIDAuthError.missingAuthorizationParameters)
                    return
                }
                cont.resume(returning: (code, state))
            }
            session.presentationContextProvider = anchorProvider
            session.prefersEphemeralWebBrowserSession = false
            self.authSession = session
            if !session.start() {
                self.authSession = nil
                cont.resume(throwing: SberIDAuthError.cannotStartSession)
            }
        }
    }

    private static func randomPKCEVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        precondition(status == errSecSuccess)
        return Data(bytes).base64URLEncodedString()
    }

    private static func codeChallengeS256(verifier: String) -> String {
        let hash = SHA256.hash(data: Data(verifier.utf8))
        return Data(hash).base64URLEncodedString()
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
