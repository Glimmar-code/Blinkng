import Foundation

/// Store only client-safe, public Supabase URL and anonymous/publishable keys.
/// Service-role keys must never be embedded in an app bundle.
struct BLINKBackendConfiguration: Equatable {
    let url: URL
    let publicKey: String

    init?(values: [String: String]) {
        guard
            let rawURL = values["BLINK_SUPABASE_URL"]?.trimmingCharacters(in: .whitespacesAndNewlines),
            let url = URL(string: rawURL),
            let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
            components.scheme?.lowercased() == "https",
            components.host != nil,
            let key = values["BLINK_SUPABASE_ANON_KEY"]?.trimmingCharacters(in: .whitespacesAndNewlines),
            !key.isEmpty,
            !key.contains("$("),
            !key.lowercased().contains("sb_secret_"),
            !key.lowercased().contains("service_role")
        else {
            return nil
        }
        self.url = url
        self.publicKey = key
    }

    static func fromBundle(_ bundle: Bundle = .main) -> BLINKBackendConfiguration? {
        guard
            let url = bundle.object(forInfoDictionaryKey: "BLINK_SUPABASE_URL") as? String,
            let key = bundle.object(forInfoDictionaryKey: "BLINK_SUPABASE_ANON_KEY") as? String
        else {
            return nil
        }
        return BLINKBackendConfiguration(values: [
            "BLINK_SUPABASE_URL": url,
            "BLINK_SUPABASE_ANON_KEY": key
        ])
    }
}
