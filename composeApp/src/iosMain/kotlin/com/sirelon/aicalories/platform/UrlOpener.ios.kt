package com.sirelon.sellsnap.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * `openURL:options:completionHandler:` rather than the one-argument `openURL:`, which has been
 * deprecated since iOS 10 and does nothing on current versions - a tap on "View on OLX" simply
 * had no effect. The options map is empty because there is nothing to configure; the point is
 * being on the supported API.
 */
actual fun openUrl(url: String) {
    if (url.isBlank()) return
    NSURL.URLWithString(url)?.let { nsUrl ->
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
    }
}
