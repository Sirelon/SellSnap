import ComposeApp
import FirebaseCore
import SwiftUI
import UserNotifications

private let shareAppGroupId = "group.com.sirelon.sellsnap"
private let sharedImagesDirName = "SharedImages"
private let pendingPathsDefaultsKey = "pendingSharedImagePaths"
private let sharedImagesNotificationIdentifier = "com.sirelon.sellsnap.sharedImagesNotification"

// ShareExtension (ShareViewController.swift) writes copies into the App Group container and
// stashes their paths in shared UserDefaults, then posts a local notification - share extensions
// aren't allowed to open their containing app directly, so tapping that notification is the only
// Apple-sanctioned way back in. Also checked on every foreground (see .onChange(of: scenePhase)
// below) as a fallback for when the user switches back manually instead of tapping the notification.
// Runs off the main thread: UserDefaults(suiteName:) can do synchronous cfprefsd IPC on first
// access, and this is called on every cold start too - doing it inline stalled the launch UI.
private func publishPendingSharedImages() {
    DispatchQueue.global(qos: .utility).async {
        let defaults = UserDefaults(suiteName: shareAppGroupId)
        guard let paths = defaults?.stringArray(forKey: pendingPathsDefaultsKey), !paths.isEmpty else {
            return
        }
        defaults?.removeObject(forKey: pendingPathsDefaultsKey)
        DispatchQueue.main.async {
            SharedImagesBridge_iosKt.publishSharedImagePaths(paths: paths)
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.notification.request.identifier == sharedImagesNotificationIdentifier {
            publishPendingSharedImages()
        }
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    OlxAuthCallbackBridge.shared.publishCallback(url: url.absoluteString)
                }
                .onChange(of: scenePhase) { _, newPhase in
                    if newPhase == .active {
                        publishPendingSharedImages()
                    }
                }
        }
    }
}
