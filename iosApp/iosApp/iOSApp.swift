import ComposeApp
import FirebaseAppCheck
import FirebaseCore
import FirebaseMessaging
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

// A push carries an optional `link` data key: "store" opens this app's App Store page, an
// https:// value opens as given, anything else (or nothing) just opens the app. No in-app routes.
private let appStoreUrl = "https://apps.apple.com/app/id6776001373"

private func pushLinkUrl(from userInfo: [AnyHashable: Any]) -> URL? {
    guard let link = userInfo["link"] as? String else { return nil }
    if link == "store" { return URL(string: appStoreUrl) }
    if link.hasPrefix("https://") { return URL(string: link) }
    return nil
}

// App Check proves to the OpenAI proxy (functions/src/index.ts) that a call comes from this app.
// App Attest only works on a real device, so Debug builds use the debug provider instead: it
// prints its token to the Xcode console on first launch, and that token must be registered under
// Firebase console > App Check > Apps > SellSnap iOS > Manage debug tokens, or the proxy answers 401.
private class AppAttestProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}

// Kotlin cannot see FirebaseAppCheck, so the shared code asks for tokens through this bridge
// (see OpenAIEndpointModule.ios.kt).
private class FirebaseAppCheckTokenFetcher: NSObject, AppCheckTokenFetcher {
    func fetch(onResult: @escaping (String?) -> Void) {
        AppCheck.appCheck().token(forcingRefresh: false) { token, _ in
            onResult(token?.token)
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        #if DEBUG
        // A fixed token from AppCheckDebugToken.local.xcconfig (via Info.plist) survives
        // simulator wipes and Maestro clearState; without one the SDK mints a random token.
        if let token = Bundle.main.object(forInfoDictionaryKey: "AppCheckDebugToken") as? String,
           !token.isEmpty {
            setenv("FIRAAppCheckDebugToken", token, 1)
        }
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
        #endif
        FirebaseApp.configure()
        AppCheckBridge.shared.fetcher = FirebaseAppCheckTokenFetcher()
        UNUserNotificationCenter.current().delegate = self
        // The notification permission prompt does not live here: an in-app sheet asks for it, and
        // so does the share extension when the share is the moment a notification is needed.
        // Registering for remote notifications shows no prompt; the APNs token arrives whether or
        // not alerts are allowed, which keeps FCM topic subscriptions working for every user.
        application.registerForRemoteNotifications()
        #if DEBUG
        // Debug and release share one Firebase app, so test pushes target this topic, never `all`.
        Messaging.messaging().subscribe(toTopic: "qa")
        #endif
        return true
    }

    // Firebase's docs require SwiftUI apps to hand the APNs token to FCM explicitly.
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("Remote notification registration failed: \(error.localizedDescription)")
    }

    // Without this, iOS drops a push that arrives while the app is open.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.notification.request.identifier == sharedImagesNotificationIdentifier {
            publishPendingSharedImages()
        } else if let url = pushLinkUrl(from: response.notification.request.content.userInfo) {
            DispatchQueue.main.async {
                UIApplication.shared.open(url)
            }
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
