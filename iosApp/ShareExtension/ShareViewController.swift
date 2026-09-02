//
//  ShareViewController.swift
//  ShareExtension
//
//  Created by Oleksandr Romanishyn on 02.09.2026.
//

import UIKit
import UniformTypeIdentifiers
import UserNotifications
import os.log

private let appGroupId = "group.com.sirelon.sellsnap"
private let sharedImagesDirName = "SharedImages"
private let pendingPathsDefaultsKey = "pendingSharedImagePaths"
private let sharedImagesNotificationIdentifier = "com.sirelon.sellsnap.sharedImagesNotification"
private let log = OSLog(subsystem: "com.sirelon.sellsnap.ShareExtension", category: "share")

class ShareViewController: UIViewController {

    private var didStartHandling = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
    }

    // extensionContext.open(_:) silently fails if called before the extension's scene is
    // actually foregrounded - viewDidLoad fires too early for that, so the URL handoff (and
    // therefore the whole share) was a silent no-op. viewDidAppear guarantees the scene is active.
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !didStartHandling else { return }
        didStartHandling = true
        handleSharedItems()
    }

    private func handleSharedItems() {
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .compactMap { $0.attachments }
            .flatMap { $0 }
            .filter { $0.hasItemConformingToTypeIdentifier(UTType.image.identifier) }

        os_log("handleSharedItems: %d image provider(s)", log: log, type: .default, providers.count)

        guard !providers.isEmpty else {
            complete()
            return
        }

        let group = DispatchGroup()
        var savedPaths: [String] = []
        let lock = NSLock()

        for provider in providers {
            group.enter()
            provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, error in
                defer { group.leave() }
                if let error {
                    os_log("loadFileRepresentation failed: %{public}@", log: log, type: .error, error.localizedDescription)
                    return
                }
                guard let url, let savedPath = self.saveToAppGroup(sourceUrl: url) else { return }
                lock.lock()
                savedPaths.append(savedPath)
                lock.unlock()
            }
        }

        group.notify(queue: .main) { [weak self] in
            os_log("finish: %d path(s) saved", log: log, type: .default, savedPaths.count)
            self?.finish(with: savedPaths)
        }
    }

    private func saveToAppGroup(sourceUrl: URL) -> String? {
        guard let containerUrl = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else {
            os_log("containerURL(forSecurityApplicationGroupIdentifier:) returned nil - App Group entitlement missing or invalid", log: log, type: .error)
            return nil
        }
        let imagesDir = containerUrl.appendingPathComponent(sharedImagesDirName)
        do {
            try FileManager.default.createDirectory(at: imagesDir, withIntermediateDirectories: true)
            let destination = imagesDir.appendingPathComponent(UUID().uuidString + "." + sourceUrl.pathExtension)
            try FileManager.default.copyItem(at: sourceUrl, to: destination)
            return destination.path
        } catch {
            os_log("saveToAppGroup failed: %{public}@", log: log, type: .error, error.localizedDescription)
            return nil
        }
    }

    // Share extensions are not allowed to open their containing app via extensionContext.open(_:) -
    // that's exclusive to Today/widget extensions (confirmed: Apple's App Extension Programming
    // Guide, developer forums). A local notification is the only Apple-sanctioned way to get the
    // user back into the app, since tapping it counts as the explicit user interaction extensions
    // are otherwise blocked from simulating.
    private func finish(with paths: [String]) {
        guard !paths.isEmpty else {
            complete()
            return
        }
        UserDefaults(suiteName: appGroupId)?.set(paths, forKey: pendingPathsDefaultsKey)
        scheduleNotification()
        complete()
    }

    private func scheduleNotification() {
        let content = UNMutableNotificationContent()
        content.title = "Photo ready"
        content.body = "Tap to continue your listing in SellSnap"
        content.sound = .default
        let request = UNNotificationRequest(identifier: sharedImagesNotificationIdentifier, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request) { error in
            if let error {
                os_log("scheduleNotification failed: %{public}@", log: log, type: .error, error.localizedDescription)
            }
        }
    }

    private func complete() {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
}
