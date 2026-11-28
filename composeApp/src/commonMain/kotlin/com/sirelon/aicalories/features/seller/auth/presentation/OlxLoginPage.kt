package com.sirelon.sellsnap.features.seller.auth.presentation

/**
 * Which part of the OLX login the in-app web view shows, read from the page's host and path. The
 * Android and iOS web views use it to hold OLX's jump to `/oauth/authorize` after the sign-in, to
 * notice OLX dropping the seller on its home page, and to report where a seller closed the login
 * ([analyticsValue], `page` of `olx_login_web_view_ended`).
 *
 * Inferred: the hosts and paths come from tracing the login in the Android WebView on olx.ro
 * (2026-11-22) and on all six markets up to the sign-in page (2026-11-26); OLX documents none of them.
 */
enum class OlxLoginPage(val analyticsValue: String) {
    /** OLX's sign-in pages: `login.olx.<tld>`, `auth.olx.<tld>` and the account and callback pages around them. */
    SignIn("login"),

    /** `www.olx.<tld>/oauth/authorize`: OLX's permission page, or the step that sends the code to our redirect URI. */
    Authorize("consent"),

    /** OLX's home page: OLX turned down a sign-in that was too fresh and left the seller there. */
    OlxHome("olx_home"),
    ;

    companion object {
        /**
         * The home page, optionally under a language (`www.olx.ua/uk/`) and under `d`, the prefix
         * of OLX's newer site (`www.olx.kz/d/callback/`).
         */
        private val HOME_PATH = Regex("""([a-z]{2}/?)?(d/?)?""")

        fun of(host: String, path: String): OlxLoginPage {
            val trimmed = path.trim('/')
            val onSite = host.startsWith("www.olx.") || host.startsWith("m.olx.")
            return when {
                host.startsWith("www.olx.") && trimmed.endsWith("oauth/authorize") -> Authorize
                onSite && HOME_PATH.matches(trimmed) -> OlxHome
                else -> SignIn
            }
        }
    }
}

/**
 * The user agent of desktop Chrome, made from the Android WebView's own one so the Chrome
 * version stays true. iOS sets a desktop Safari one (`OlxExternalAuthLauncher.ios.kt`).
 *
 * Why: OLX sends a phone browser through `m.olx.<tld>`, whose sign-in ends on
 * `m.olx.<tld>/callback/` and then jumps to `www.olx.<tld>/oauth/authorize`. That `www` request
 * comes too soon after the sign-in and lands on the OLX home page (SIR-195, SIR-203). With a
 * desktop user agent, `www.olx.<tld>/account/` sends the seller to `login.olx.<tld>` or
 * `auth.olx.<tld>` directly, and the sign-in returns to `www.olx.<tld>/d/callback/`: the whole
 * login stays on `www`. Traced in the Android WebView on olx.kz, olx.ro and olx.pl, 2026-11-26.
 * The sign-in page itself is responsive and still renders for a phone.
 */
internal fun desktopUserAgent(mobileUserAgent: String): String =
    mobileUserAgent
        .replace(Regex("""\(Linux; Android[^)]*\)"""), "(X11; Linux x86_64)")
        .replace(Regex("""Version/[\d.]+ (?=Chrome/)"""), "")
        .replace(" Mobile Safari/", " Safari/")
