# Store screenshot reference

Checked 2026-11-28 on the official pages below. Re-open them before changing a size or adding a slot.

## Targets the compositor writes

| Target | Size | Console slot | Source capture |
| --- | --- | --- | --- |
| `app-store/iphone-6.9` | 1320x2868 | `APP_IPHONE_67` (iPhone 6.9" / "Dynamic Island large") | `iphone` 1320x2868 @3x |
| `app-store/ipad-13` | 2752x2064 landscape | `APP_IPAD_PRO_3GEN_129` (iPad 13") | `ipad` 2752x2064 @2x |
| `app-store/iphone-duo-inner` | 2007x2853 | iPhone Duo, inner display; no API type, upload by hand | `iphone-duo-inner` @3x (Inferred density) |
| `app-store/iphone-duo-outer` | 1398x2034 | iPhone Duo, outer display; no API type, upload by hand | `iphone-duo-outer` @3x (Inferred density) |
| `play-store/phone` | 1440x2560 | `phoneScreenshots` | `android-phone` 1080x2400 @2.625 |
| `play-store/tablet` | 2560x1440 | `sevenInchScreenshots` and `tenInchScreenshots` | `android-tablet` 2560x1600 @2x |

Store languages: App Store en-US, uk, pl, ro, pt-PT (no bg, no kk). Play uk, pl-PL, ro, bg, pt-PT, kk.

## Apple

Source: App Store Connect Help, Screenshot specifications,
https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/

- 1 to 10 screenshots per device size, `.jpeg`, `.jpg` or `.png`, no alpha.
- Required: at least one for iPhone "Dynamic Island (medium display)" (1179x2556 or 1206x2622), and
  13" iPad when the app runs on iPad. App Store Connect scales the highest provided size down for
  consistent UIs; the listing has carried only `APP_IPHONE_67` sets so far.
- iPhone Duo: outer 1398x2034, inner 2007x2853 (portrait). Screenshots become required in April 2027
  for apps built with the iOS 27.1 SDK or later; submitting them adds a "made for iPhone Duo" badge.
- The ASC API enum `ScreenshotDisplayType` has no 6.9", 6.3", 13" or Duo value
  (https://developer.apple.com/documentation/appstoreconnectapi/screenshotdisplaytype): 6.9" images
  go into `APP_IPHONE_67`, 13" into `APP_IPAD_PRO_3GEN_129`.

App Review Guidelines 2.3, https://developer.apple.com/app-store/review/guidelines/:
- 2.3.3: screenshots show the app in use, not only title art, login or splash; text and image
  overlays are allowed.
- 2.3.7: no prices or terms in screenshots that do not belong to the metadata type.
- 2.3.8: content fit for 4+.
- 2.3.9: fictional account information, never a real person's data; rights to every image.
- 2.3.10: no names, icons or imagery of other mobile platforms (no Android frame on App Store images).

## Google Play

Source: Play Console Help, Add preview assets,
https://support.google.com/googleplay/android-developer/answer/9866151

- Phone: 2 to 8, PNG or JPEG, up to 8 MB, each side 320 to 3,840 px, 9:16 or 16:9; the long side at
  most 2.3x the short side.
- Tablets (7" and 10"): at least 4 for large-screen features, 1,080 to 7,680 px, 16:9 or 9:16.
- Foldable: 3 to 8, each side 960 to 3,840 px, include one 1:1 inner-screen shot. Not uploaded yet:
  `supply` has no foldable image type.
- Feature graphic 1024x500, no alpha. Screenshots JPEG or 24-bit PNG, no alpha.
- Captions at most about 20% of the image. No "best", "#1", awards, rankings, discounts, or calls to
  action like "Download now". Full battery, Wi-Fi and signal; no notifications or carrier name.

Metadata policy, https://support.google.com/googleplay/android-developer/answer/9898842: screenshots
reflect the app's actual experience; no store-performance, price or Play-program claims.

## Console state (2026-11-28)

Waiting for upload:
- App Store, every set (iPhone 6.9", iPad 13", all five languages): 4.2 was WAITING_FOR_REVIEW, so
  they go into the next editable version (`sellsnap-release` step 7). en-US had no screenshots before.
- App Store iPhone Duo sets: by hand in App Store Connect (no API type).
- Play tablet sets: held until the release with the wide-window layouts (PR #355) is live, so the
  images match the installed app. Upload with `--slots=sevenInchScreenshots,tenInchScreenshots`.

Done: Play phone sets (8 per listing) uploaded 2026-11-28 for uk, pl-PL, ro, bg, pt-PT, kk.
Play has no en-US listing. The service account can open and commit listing edits.
