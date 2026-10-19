import Foundation
import Combine

enum BLINKAuthState {
    case starting
    case signedOut
    case signedIn(BLINKAuthUser)
    case unavailable
}

/// Serializes account actions in the UI, persists only server-issued sessions in Keychain,
/// and validates a stored token with Supabase before granting authenticated UI access.
@MainActor
final class BLINKAuthStore: ObservableObject {
    @Published private(set) var state: BLINKAuthState = .starting
    @Published private(set) var isBusy = false
    @Published var message: String?

    private let api: BLINKAuthAPI?
    private let vault: any BLINKSessionVault
    private var session: BLINKAuthSession?
    private var didRestore = false

    init(
        configuration: BLINKBackendConfiguration? = .fromBundle(),
        vault: any BLINKSessionVault = BLINKKeychainSessionVault(),
        urlSession: URLSession = .shared
    ) {
        self.vault = vault
        self.api = configuration.map { BLINKAuthAPI(configuration: $0, urlSession: urlSession) }
    }

    func restore() async {
        if didRestore { return }
        didRestore = true
        guard let api else {
            state = .unavailable
            return
        }
        guard var saved = vault.load() else {
            state = .signedOut
            return
        }
        do {
            if saved.shouldRefresh {
                saved = try await api.refresh(refreshToken: saved.refreshToken)
                try vault.save(saved)
            }
            let user = try await api.currentUser(accessToken: saved.accessToken)
            session = saved
            state = .signedIn(user)
        } catch {
            // Never trust a stored token without revalidation, including when offline.
            // Expired/revoked sessions are discarded; transient network failures retain
            // Keychain data for a future retry without exposing authenticated content.
            if let authError = error as? BLINKAuthError, authError.invalidSession {
                vault.clear()
                session = nil
                message = "Your session expired. Please sign in again."
            } else {
                message = "Unable to check your session. Check your connection and try again."
            }
            state = .signedOut
        }
    }

    func signIn(email: String, password: String) async {
        guard !isBusy, let api else { return }
        isBusy = true
        message = nil
        defer { isBusy = false }
        do {
            let (newSession, user) = try await api.signIn(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
            try vault.save(newSession)
            session = newSession
            state = .signedIn(user)
        } catch {
            message = error.localizedDescription
        }
    }

    func signUp(email: String, password: String) async {
        guard !isBusy, let api else { return }
        isBusy = true
        message = nil
        defer { isBusy = false }
        do {
            let (newSession, user) = try await api.signUp(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
            if let newSession {
                try vault.save(newSession)
                session = newSession
                let confirmedUser: BLINKAuthUser
                if let user { confirmedUser = user }
                else { confirmedUser = try await api.currentUser(accessToken: newSession.accessToken) }
                state = .signedIn(confirmedUser)
            } else {
                // With confirmation enabled, no session exists yet.
                message = "If the account can be created, check your email for a confirmation link, then sign in."
            }
        } catch {
            message = error.localizedDescription
        }
    }

    func sendPasswordReset(email: String) async {
        guard !isBusy, let api else { return }
        isBusy = true
        message = nil
        defer { isBusy = false }
        do {
            try await api.requestPasswordReset(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            message = "If this email has an account, check your inbox. Follow the web recovery link."
        } catch {
            message = error.localizedDescription
        }
    }

    /// Tokens may rotate; update Keychain before returning to private pages.
    func revalidate() async {
        guard !isBusy, let api, var current = session else { return }
        isBusy = true
        defer { isBusy = false }
        do {
            if current.shouldRefresh {
                current = try await api.refresh(refreshToken: current.refreshToken)
                try vault.save(current)
            }
            let user = try await api.currentUser(accessToken: current.accessToken)
            session = current
            state = .signedIn(user)
        } catch {
            if let authError = error as? BLINKAuthError, authError.invalidSession {
                clearLocalSession()
                message = "Your session expired. Please sign in again."
            }
            // Transient network errors do not discard already validated local state;
            // no new private backend operation is authorized without revalidation.
        }
    }

    func signOut() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        let token = session?.accessToken
        clearLocalSession()
        if let api, let token {
            do { try await api.signOut(accessToken: token) }
            catch {
                message = "Signed out on this device. Server sign-out could not be confirmed."
            }
        }
    }

    private func clearLocalSession() {
        session = nil
        vault.clear()
        state = .signedOut
    }
}
