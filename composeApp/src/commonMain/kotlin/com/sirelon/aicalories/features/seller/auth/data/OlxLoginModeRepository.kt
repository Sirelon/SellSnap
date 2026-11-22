package com.sirelon.sellsnap.features.seller.auth.data

import org.koin.core.module.Module

/**
 * Which screen runs the OLX login on Android and iOS (SIR-195).
 *
 * The system browser (Custom Tab, `ASWebAuthenticationSession`) is the preferred login: shared
 * OLX session, password managers, Google/Apple sign-in. Since November 2026 OLX rejects a phone
 * login that comes back too fast, so the default is the in-app web view, which can wait. The
 * owner switches back without a release by setting the Firestore document `config/olx_login`,
 * field `systemBrowser`, to `true` once the system-browser check in `.maestro/olx_login_canary.yaml`
 * passes again.
 */
interface OlxLoginModeRepository {
    /** True: the system browser. False (the default, also when offline): the in-app web view. */
    suspend fun useSystemBrowser(): Boolean
}

expect val olxLoginModeModule: Module
