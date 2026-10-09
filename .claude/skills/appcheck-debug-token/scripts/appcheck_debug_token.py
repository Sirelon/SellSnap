#!/usr/bin/env python3
"""Firebase App Check debug tokens for SellSnap (project sellsnap-6e85c).

A debug build on an emulator or simulator generates a random debug token on first launch and
stores it in app data. Until that token is registered in App Check, the OpenAI proxy
(functions/src/index.ts) answers 401 and listing generation fails. This script reads the token
from the device, registers it, verifies the registration (free exchange call, no OpenAI cost),
and lists or deletes tokens.

Auth: gcloud (`gcloud auth print-access-token`) for the admin API, the app's Firebase API key
(from google-services.json / GoogleService-Info.plist) for the exchange call. Python, not curl,
because zsh turns `$APP_ID:exchangeDebugToken` into a modifier and breaks the URL.
"""
import argparse
import json
import plistlib
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

PROJECT = "sellsnap-6e85c"
APPS = {
    "android": "1:186709313778:android:934749849d0b3b9dfc286b",
    "ios": "1:186709313778:ios:62e5f879f0dc1a83fc286b",
}
API = "https://firebaseappcheck.googleapis.com/v1"
ROOT = Path(__file__).resolve().parents[4]
UUID_RE = re.compile(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")


def sh(*cmd: str, timeout: int = 60) -> str:
    return subprocess.run(cmd, check=True, capture_output=True, text=True, timeout=timeout).stdout


def access_token() -> str:
    return sh("gcloud", "auth", "print-access-token").strip()


def api_key(app: str) -> str:
    if app == "android":
        data = json.loads((ROOT / "androidApp/google-services.json").read_text())
        return data["client"][0]["api_key"][0]["current_key"]
    with open(ROOT / "iosApp/iosApp/GoogleService-Info.plist", "rb") as f:
        return plistlib.load(f)["API_KEY"]


def call(method: str, url: str, body=None, key: str | None = None):
    headers = {"Content-Type": "application/json"}
    if key:
        headers["X-Goog-Api-Key"] = key
    else:
        headers["Authorization"] = f"Bearer {access_token()}"
        headers["x-goog-user-project"] = PROJECT
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {url} -> HTTP {e.code}: {e.read().decode(errors='replace')[:400]}")


def tokens_url(app: str) -> str:
    return f"{API}/projects/{PROJECT}/apps/{APPS[app]}/debugTokens"


def cmd_list(args):
    apps = ["android", "ios"] if args.app == "all" else [args.app]
    for app in apps:
        items = call("GET", tokens_url(app)).get("debugTokens", [])
        print(f"{app}: {len(items)} token(s)")
        for t in items:
            print(f"  {t['displayName']}  ->  {t['name']}")


def cmd_register(args):
    if not UUID_RE.fullmatch(args.token):
        sys.exit("token must be a UUID as printed by the debug provider")
    created = call("POST", tokens_url(args.app), {"displayName": args.name, "token": args.token})
    print(f"registered {args.app} token as '{created['displayName']}'")
    if not args.no_verify:
        verify(args.app, args.token)


def verify(app: str, token: str):
    url = f"{API}/projects/{PROJECT}/apps/{APPS[app]}:exchangeDebugToken"
    result = call("POST", url, {"debugToken": token}, key=api_key(app))
    print(f"verified: App Check issues tokens for it (ttl {result.get('ttl')})")


def cmd_verify(args):
    verify(args.app, args.token)


def cmd_delete(args):
    items = call("GET", tokens_url(args.app)).get("debugTokens", [])
    matches = [t for t in items if args.name in (t["displayName"], t["name"])]
    if not matches:
        sys.exit(f"no {args.app} token named '{args.name}'")
    for t in matches:
        call("DELETE", f"{API}/{t['name']}")
        print(f"deleted '{t['displayName']}'")


def cmd_android_token(args):
    # Logged with Log.d under the provider's full class name as the tag, when MainActivity
    # installs the provider factory. A relaunch logs it again.
    # -t bounds the dump: an unbounded `logcat -d` on a busy emulator can take minutes.
    log = sh("adb", "-s", args.serial, "logcat", "-d", "-t", "20000", timeout=45)
    lines = [l for l in log.splitlines() if "DebugAppCheckProvider" in l]
    found = UUID_RE.findall("\n".join(lines))
    if not found:
        sys.exit("no debug token in logcat; force-stop and relaunch a debug build of SellSnap "
                 "(adb shell am force-stop com.sirelon.sellsnap; adb shell am start -n "
                 "com.sirelon.sellsnap/.MainActivity), wait a few seconds, retry")
    print(found[-1])


def cmd_ios_token(args):
    log = sh("xcrun", "simctl", "spawn", args.udid, "log", "show", "--last", "30m",
             "--style", "compact", "--predicate", 'process == "SellSnap"')
    lines = [l for l in log.splitlines() if "App Check Debug Token" in l]
    found = UUID_RE.findall("\n".join(lines))
    if not found:
        sys.exit("no debug token in the simulator log; launch a Debug build of SellSnap first, "
                 "or read it from the Xcode console")
    print(found[-1])


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("list", help="list registered debug tokens")
    s.add_argument("--app", choices=["android", "ios", "all"], default="all")
    s.set_defaults(fn=cmd_list)

    s = sub.add_parser("register", help="register a token and verify it")
    s.add_argument("--app", choices=["android", "ios"], required=True)
    s.add_argument("--token", required=True)
    s.add_argument("--name", required=True, help='label shown in the console, e.g. "Pixel_10_Pro_Fold emulator (sirelon Mac)"')
    s.add_argument("--no-verify", action="store_true")
    s.set_defaults(fn=cmd_register)

    s = sub.add_parser("verify", help="check that App Check accepts a token (free)")
    s.add_argument("--app", choices=["android", "ios"], required=True)
    s.add_argument("--token", required=True)
    s.set_defaults(fn=cmd_verify)

    s = sub.add_parser("delete", help="delete a token by display name or resource name")
    s.add_argument("--app", choices=["android", "ios"], required=True)
    s.add_argument("--name", required=True)
    s.set_defaults(fn=cmd_delete)

    s = sub.add_parser("android-token", help="print the token the running emulator generated")
    s.add_argument("--serial", default="emulator-5554")
    s.set_defaults(fn=cmd_android_token)

    s = sub.add_parser("ios-token", help="print the token the running simulator generated")
    s.add_argument("--udid", default="booted")
    s.set_defaults(fn=cmd_ios_token)

    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
