# Store screenshots

Finished App Store and Google Play screenshots, one folder per target and language. They are
generated on demand and not tracked in git (only the feature graphics are). How to make, check and
upload them: the `sellsnap-screenshots` skill (`.claude/skills/sellsnap-screenshots/`).

## Layout

Paths relative to `store/assets/`:

```
app-store/iphone-6.9/<lang>/NN-<id>.jpg        1320x2868   App Store Connect APP_IPHONE_67
app-store/ipad-13/<lang>/NN-<id>.jpg           2752x2064   App Store Connect APP_IPAD_PRO_3GEN_129
app-store/iphone-duo-inner/<lang>/NN-<id>.jpg  2007x2853   iPhone Duo inner display (uploaded by hand)
app-store/iphone-duo-outer/<lang>/NN-<id>.jpg  1398x2034   iPhone Duo outer display (uploaded by hand)
play-store/phone/<lang>/NN-<id>.jpg            1440x2560   Google Play phone screenshots
play-store/tablet/<lang>/NN-<id>.jpg           2560x1440   Google Play 7" and 10" tablet screenshots
play-store/feature-graphic/                    1024x500    Google Play feature graphic per language
```

`NN` is the upload order. `<lang>` is a language code (`en uk pl ro bg pt kk`); App Store gets
en, uk, pl, ro, pt; Google Play gets all seven.

## Where each piece comes from

| Path | Role |
| --- | --- |
| `store/captures/<device>/<lang>/<scene>_<theme>.png` | headless renders of real screens with demo data (`StoreScenesTest` in `composeApp/src/jvmTest`) |
| `store/tools/scenes.json` | which scene, theme and caption fills each slot, and why the caption is true |
| `store/copy/copy.json` | captions per language |
| `store/tools/generate-store-screenshots.mjs` | frames, status bar, caption layout, target sizes |
| `store/tools/upload-screenshots.rb` | uploads to App Store Connect and Google Play |
