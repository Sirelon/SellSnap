import ComposeApp
import FirebaseAppCheck
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
