import SwiftUI

/// Phase 1: an independently buildable native iOS application.
/// Account, feed and message services will be connected to the existing BLINK
/// Supabase backend in the following phases; no demo user data is fabricated.
@main
struct BLINKiOSApp: App {
    var body: some Scene {
        WindowGroup {
            BLINKRootView()
        }
    }
}
