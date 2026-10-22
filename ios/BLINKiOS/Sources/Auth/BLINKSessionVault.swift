import Foundation
import Security

protocol BLINKSessionVault {
    func load() -> BLINKAuthSession?
    func save(_ session: BLINKAuthSession) throws
    func clear()
}

enum BLINKSessionStorageError: LocalizedError {
    case saveFailed(OSStatus)
    var errorDescription: String? { "Could not securely save this login. Please try again." }
}

/// Tokens are scoped to this iOS app and never written to UserDefaults or log output.
/// ThisDeviceOnly prevents credentials from being copied by device backup.
struct BLINKKeychainSessionVault: BLINKSessionVault {
    private let service = "ng.com.blink.ios.supabase"
    private let account = "auth-session-v1"

    private var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }

    func load() -> BLINKAuthSession? {
        var options = query
        options[kSecReturnData as String] = true
        options[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(options as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(BLINKAuthSession.self, from: data)
    }

    func save(_ session: BLINKAuthSession) throws {
        let data = try JSONEncoder().encode(session)
        let update: [String: Any] = [kSecValueData as String: data]
        let result = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if result == errSecSuccess { return }
        if result == errSecItemNotFound {
            var options = query
            options[kSecValueData as String] = data
            options[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            let status = SecItemAdd(options as CFDictionary, nil)
            if status == errSecSuccess { return }
            throw BLINKSessionStorageError.saveFailed(status)
        }
        throw BLINKSessionStorageError.saveFailed(result)
    }

    func clear() {
        SecItemDelete(query as CFDictionary)
    }
}
