import SwiftUI
import UIKit
import UserNotifications
import Combine

/// APNs integration foundation. Do not send tokens to Android FCM tables:
/// there is no iOS APNs backend registration endpoint or Apple signing yet.
@MainActor
final class BLINKPushPermissionStore: ObservableObject {
    static let shared = BLINKPushPermissionStore()
    @Published private(set) var state = BLINKAPNsRegistrationState()

    /// User-initiated opt-in. Avoid prompting at initial launch.
    func requestPermission() async {
        do {
            let granted = try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])
            state.status = granted ? .authorized : .denied
            state.message = granted
                ? "Alerts allowed on this device. Remote delivery needs Apple Developer and APNs backend setup."
                : "Notifications are off. You can enable them in iPhone Settings."
            // Do not request an APNs token until a signed build has the proper
            // entitlement and a secure token-registration service exists.
        } catch {
            state.status = .failed
            state.message = "Unable to request notifications. Try again in Settings."
        }
    }

    func tokenWasIssued(_ value: Data) {
        guard !value.isEmpty else { return }
        // Do not log or persist raw tokens. Server upload is deliberately absent.
        state.status = .tokenCaptured
        state.message = "APNs device token received. Delivery is not configured yet."
    }

    func didFailRegistration() {
        state.status = .failed
        state.message = "APNs registration failed. Check signing and Apple Developer configuration."
    }
}

final class BLINKiOSDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil)
        -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Task { @MainActor in
            BLINKVoIPPushManager.shared.startAtLaunchIfEnabled()
        }
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in
            BLINKPushPermissionStore.shared.tokenWasIssued(deviceToken)
        }
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Task { @MainActor in BLINKPushPermissionStore.shared.didFailRegistration() }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler:
                                    @escaping (UNNotificationPresentationOptions) -> Void) {
        // The app shows alerts for explicit user-enabled notifications.
        completionHandler([.banner, .sound])
    }
}

struct BLINKNotificationSettingsView: View {
    @StateObject private var push = BLINKPushPermissionStore.shared

    var body: some View {
        Form {
            Section("Notification alerts") {
                Text(push.state.message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Button("Allow notification alerts") {
                    Task { await push.requestPermission() }
                }
            }
            Section("Delivery status") {
                Label("Background push notifications not connected",
                      systemImage: "bell.slash")
                    .font(.subheadline)
                Text("Your iPhone cannot receive remote BLINK alerts until an eligible Apple Developer account, signed APNs capability and secured server delivery are configured and verified.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Notifications")
    }
}
