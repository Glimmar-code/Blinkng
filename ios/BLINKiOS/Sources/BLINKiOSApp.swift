import SwiftUI

/// The iOS client uses the same BLINK account and Supabase backend as the web
/// and Android builds; production feature data is intentionally not mocked.
@main
struct BLINKiOSApp: App {
    @UIApplicationDelegateAdaptor(BLINKiOSDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup {
            BLINKRootView()
        }
    }
}
