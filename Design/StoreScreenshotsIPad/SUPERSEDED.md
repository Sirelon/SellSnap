# Superseded

The generator that produced these iPad JPGs was removed on 2026-08-03. It hard-coded
Ukrainian copy inline, paired each screenshot to its caption by sorted filename index (so any
re-capture silently shifted every caption by one), and assumed **landscape** source
screenshots while the current Maestro captures arrive portrait-and-rotated. Its layout also
placed the tablet mock over its own sub-headline and tick badge — visible in screenshots 03
and 04 here.

Replaced by `Design/AppStoreScreenshots/` — multi-locale, captions bound to screens by name
via `scenes.json`, and a portrait/landscape-aware layout. See its `PROGRESS.md` for the full
rationale.

The JPGs here are the currently-shipped Ukrainian iPad App Store assets (2752x2064 landscape)
and are kept for reference only. `Design/AppStoreScreenshots/ipad-13/ua/` is a corrected
regeneration of the same set.
