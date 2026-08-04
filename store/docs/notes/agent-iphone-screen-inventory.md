# iPhone source screen inventory (pl locale)

Produced by a subagent from direct visual inspection of all 15 PNGs in `/Users/sirelon/Projects/SellSnap/screenshots/iphone/pl/` (1206x2622, Polish UI).

---

## 1. `auth.png`

- **theme:** light (no dark variant exists for this screen)
- **visible_ui:** status bar `19:54`. Large rounded hero illustration (sneakers, orange mushroom lamp, folded denim shirt, price tags, a hand holding an iPhone in camera mode, blank floating "card" placeholders); small peach app-icon badge with sparkle overlaid bottom-left of the image. Heading `Hej, witaj w SellSnap 👋`. Subheading `Wrzuć zdjęcie, dostań ogłoszenie — AI robi resztę.` Peach info card titled `Po co łączyć konto OLX?` with three green check rows: `Publikuj prosto na OLX — bez kopiowania`, `Twoje ogłoszenia synchronizują się same`, `Wszystkie reklamy w jednym miejscu`. Full-width dark-orange primary button `Kontynuuj z OLX`. No tab bar.
- **user_intent:** First launch — the user is about to connect their OLX account to start using the app.
- **sell_message:** "Connect your OLX account once and start."
- **caption_must_not_say:** "Your listing is live on OLX" / "AI already wrote your ad" — nothing has been photographed or generated on this screen.
- **crop_notes:** Content fills the whole frame; interesting content is top (illustration) + bottom (CTA). The `Kontynuuj z OLX` button sits ~30 px from the bottom edge, so a mock-up that crops the bottom will eat the CTA. No dead space.

---

## 2. `analysing_start_light.png`

- **theme:** light
- **visible_ui:** status bar `20:00`. Concentric peach halo with orange circle and outlined 4-point sparkle glyph; small green circular badge with lightning bolt at upper right of the halo. Heading `Przygotowujemy Twoje ogłoszenie`. Subheading `Analizujemy Twoje zdjęcia i piszemy idealny tytuł, opis i cenę…`. White checklist card, 5 rows: `Przesyłanie zdjęć` + `Pracujemy nad tym…` with a spinning arc, then `Analizowanie zdjęć`, `Tworzenie tytułu`, `Pisanie opisu`, `Szacowanie ceny` all with empty outline circles (not started). Peach tip strip at bottom: `Wskazówka: zbliżenia szczegółów i śladów użytkowania pomagają napisać lepszy opis.`
- **user_intent:** Photos have just been submitted; the user is watching upload begin.
- **sell_message:** "AI starts working the moment you hit generate."
- **caption_must_not_say:** "Ad written in seconds — done" / "Title, description and price ready" — every step except upload is still pending here.
- **crop_notes:** Vertically balanced: halo top third, headline middle, checklist lower-middle, tip strip at the very bottom. Moderate breathing room around the halo. Safe to crop the bottom tip strip; do not crop the checklist.

---

## 3. `analysing_start_dark.png`

- **theme:** dark
- **visible_ui:** status bar `20:02`. Same layout as #2 on a deep brown/near-black gradient; halo rings are brown-on-brown, centre circle orange with dark sparkle glyph, green lightning badge. Same heading/subheading in cream. Dark checklist card, `Przesyłanie zdjęć` + `Pracujemy nad tym…` spinning, remaining 4 steps as thin orange outline circles. Tip strip text differs from the light variant: `Wskazówka: dobre światło = lepsze wyniki AI.`
- **user_intent:** Same moment as #2 — upload starting — in dark mode.
- **sell_message:** "Works in dark mode too — AI kicks off instantly."
- **caption_must_not_say:** "Listing generated" / "Price estimated" — nothing is complete.
- **crop_notes:** Same as #2. Low internal contrast (brown halo on brown background) means the top third looks washy at small sizes; the checklist card barely separates from the background.

---

## 4. `analysing_progress_light.png`

- **theme:** light
- **visible_ui:** status bar `20:00`. Same halo, this time with a filled white sparkle glyph. Same heading + subheading. White checklist card with 4 solid green check circles: `Przesyłanie zdjęć` / `Gotowe ✓`, `Analizowanie zdjęć` / `Gotowe ✓`, `Tworzenie tytułu` / `Gotowe ✓`, `Pisanie opisu` / `Gotowe ✓`; 5th row `Szacowanie ceny` / `Pracujemy nad tym…` with a spinner. Tip strip: `Wskazówka: pierwsze zdjęcie to 80%% sukcesu sprzedaży — zrób je najlepiej 📸` — note the literal double percent sign `80%%` (copy/format bug, visible on screen).
- **user_intent:** Waiting out the last AI step (price estimation) after title and description are already written.
- **sell_message:** "Title and description written, price on the way."
- **caption_must_not_say:** "Published to OLX" / "Ready in 5 seconds" — publication has not happened and no duration is shown here.
- **crop_notes:** Best-filled of the analysing set. Four green ticks in the lower-middle are the visual payload. The `80%%` typo sits in the bottom strip — crop the bottom strip out if this screen is used.

---

## 5. `analysing_progress_dark.png`

- **theme:** dark
- **visible_ui:** status bar `20:02`. Same dark layout. Only ONE step complete: `Przesyłanie zdjęć` / `Gotowe ✓` (mint-green check circle); `Analizowanie zdjęć` / `Pracujemy nad tym…` spinning; `Tworzenie tytułu`, `Pisanie opisu`, `Szacowanie ceny` still empty. Tip strip: `Wskazówka: zbliżenia szczegółów i śladów użytkowania pomagają napisać lepszy opis.`
- **user_intent:** Waiting during photo analysis — an *earlier* moment than the light "progress" capture.
- **sell_message:** "AI is reading your photos."
- **caption_must_not_say:** "Title, description and price are ready" / "4 of 5 steps done" — only the upload finished.
- **crop_notes:** Same framing as #3. State mismatch with its light counterpart (1/5 vs 4/5 done) — the two cannot be presented as the same moment in a light/dark pair.

---

## 6. `generate_ad_top_light.png`

- **theme:** light
- **visible_ui:** status bar `17:31`. Orange gradient hero card: app-icon sparkle tile + wordmark `SellSnap`, bold `Masz co sprzedać? Sfotografuj. My się tym zajmiemy.`, sub-line `Zrób zdjęcie. AI zajmie się słowami.` Section heading `Nowe ogłoszenie`, sub-line `Dodaj 1–8 zdjęć. My zajmiemy się resztą.` Three identical square thumbnails of a New Balance sneaker tongue/label, each with a dark circular `×` remove button. Peach `+ Dodaj` add tile below. Sticky bottom gradient (orange→yellow) button `✨ Generuj z AI`, which **overlaps and half-hides a row of `Aparat` / `Galeria` buttons underneath**. Bottom tab bar: `Nowe ogłoszenie` (active, peach pill camera icon) · `Moje ogłoszenia` · `Profil`.
- **user_intent:** Three photos are picked; the user is one tap from asking AI to write the ad.
- **sell_message:** "Add up to 8 photos, tap Generate."
- **caption_must_not_say:** "Ad generated" / "Posted on OLX" — nothing has been generated yet.
- **crop_notes:** Content fills the frame top to bottom; no empty space. Interesting content is spread across all three thirds (hero card, photo grid, CTA). The CTA/`Aparat`-`Galeria` overlap sits ~80% down and is the one visible flaw — cropping the bottom ~12% removes both the overlap and the tab bar.

---

## 7. `generate_ad_top_dark.png`

- **theme:** dark
- **visible_ui:** status bar `17:32`. Same orange hero card (text renders near-black on orange in dark mode). `Nowe ogłoszenie` + `Dodaj 1–8 zdjęć. My zajmiemy się resztą.` **No photos added** — only the dark `+ Dodaj` tile, leaving a large empty area to its right. Full-width `Aparat` and `Galeria` buttons visible. Sticky gradient button reads `Najpierw dodaj zdjęcie` (i.e. the disabled/blocked state) and it **overlaps a `✓ Dobre światło = lepsze wyniki` tip line**. Same 3-item tab bar.
- **user_intent:** Empty state — the user has not picked any photo yet and the generate action is blocked.
- **sell_message:** "Shoot or pick photos to start a listing."
- **caption_must_not_say:** "AI writes your listing from your photos" (no photo present) / "Generate with AI" (the button literally says "add a photo first").
- **crop_notes:** Large dead space to the right of the `+ Dodaj` tile (roughly 55% of that band is empty). Interesting content is top (hero card) only. Weakest screen of the set for store use.

---

## 8. `generate_ad_bottom_light.png`

- **theme:** light
- **visible_ui:** status bar `17:31` drawn **over** the bottom edge of the three sneaker thumbnails (mid-scroll capture, content passes under the status bar). Below: `+ Dodaj` tile, white `Aparat` / `Galeria` buttons, peach card `Wskazówki foto 📸` with green checks `Dobre światło = lepsze wyniki`, `Fotografuj z kilku kątów`, `Nie ukrywaj zarysowań — kupujący cenią szczerość`. Peach card `Podpowiedz AI (opcjonalnie)` with a text field placeholder `np. Nike Air Max 90, rozmiar 42, noszone 2 miesiące`. Sticky gradient button `✨ Generuj z AI` overlapping the field's character counter. Tab bar at bottom.
- **user_intent:** Scrolled down to read the photo tips and optionally type an AI hint before generating.
- **sell_message:** "Add an optional hint and the AI tailors the ad."
- **caption_must_not_say:** "Your ad is ready" / "Published to OLX" — this is still the input screen.
- **crop_notes:** Mid-scroll: the top ~9% is a clipped photo strip sitting under the status bar, which looks broken. Interesting content is middle + bottom. Requires cropping the top strip to be presentable.

---

## 9. `generate_ad_bottom_dark.png`

- **theme:** dark
- **visible_ui:** status bar `17:32` drawn over a clipped `Dodaj 1–8 zdjęć. My zajmiemy się resztą.` line (mid-scroll). `+ Dodaj` tile with empty space to the right, `Aparat` / `Galeria`, `Wskazówki foto 📸` card with the same 3 tips, `Podpowiedz AI (opcjonalnie)` card with the same placeholder plus visible counter `0/120`. Sticky gradient button `Najpierw dodaj zdjęcie`. Tab bar.
- **user_intent:** Empty state, scrolled to the tips / AI-hint section, generation still blocked.
- **sell_message:** "Photo tips and an optional AI hint field."
- **caption_must_not_say:** "AI generated this from 3 photos" (no photos) / "Tap Generate" (button is in blocked state).
- **crop_notes:** Same clipped-header artefact at the top ~5%. Content evenly distributed middle/bottom, but the empty photo row leaves a visible hole. Needs top crop.

---

## 10. `result_top_light.png`

- **theme:** light
- **visible_ui:** status bar `20:00` with a back arrow at left. Edge-to-edge photo carousel (~35% of the frame) of the New Balance 998 tongue label — legible `U998RE`, `MFG: 2408`, size grid `07½ US / 07 UK / 40.5 EU / 25.5 CM`, `Made in USA`, QR code, plus a hand at the right; 3-dot page indicator with the first dot active. Green success banner with check icon: `Gotowe w 56 sekund — przejrzyj przed publikacją.` Peach `Tytuł` card with `Kopiuj` and `✨ AI` chips, value `Buty New Balance 998BRE 40.5 EU`, counter `31/140`. Peach `Opis` card with `Kopiuj` and `✨ AI` chips, first body line `Sprzedaję swoje buty New Balance model`. Sticky green pill button `Opublikuj na OLX →` partially covering the description body.
- **user_intent:** Reviewing the AI-written title and description before publishing.
- **sell_message:** "AI-written title and description in under a minute — review, then publish."
- **caption_must_not_say:** "Already live on OLX" — the publish button has not been pressed; also do not claim a price is shown (no price on this screen).
- **crop_notes:** Densest, most "product" screen of the set. Photo occupies the top third, proof banner + editable fields the middle, CTA at the bottom. No empty space. The sticky CTA clips the `Opis` text — cropping the bottom 10% removes the CTA and the awkward overlap but also removes the strongest button.

---

## 11. `result_top_dark.png`

- **theme:** dark
- **visible_ui:** status bar `20:02`, back arrow. Same photo carousel, 3-dot indicator. Green banner `Gotowe w 54 sekund — przejrzyj przed publikacją.` Brown `Tytuł` card with `Kopiuj` / `✨ AI`, value `Buty New Balance 998 U998BRE rozmiar 40,5` (wraps to two lines), counter `41/140`. Brown `Opis` card — **only the header row is visible; the body text is entirely hidden behind the sticky CTA**. Mint-green glowing pill `Opublikuj na OLX →`.
- **user_intent:** Same review moment, dark mode.
- **sell_message:** "AI-written listing, reviewable in dark mode."
- **caption_must_not_say:** "Published" / "Price set" — neither is on screen.
- **crop_notes:** Same distribution as #10; the black background makes the photo pop harder, but the empty `Opis` card reads as a rendering gap. Do not crop the bottom or the composition loses its only completed card.

---

## 12. `result_bottom_light.png`

- **theme:** light
- **visible_ui:** status bar `20:01`, back arrow. Peach `Szczegóły` card with tappable rows (chevrons): `Stan *` = `Używane`, `Marka` = `New Balance`, `Rozmiar *` = `40`, `Kolor` = `Brązowy`, `Materiał` = `Wybierz...`, a text input `Numer BDO (dla firm)`, `Dostawa` = `Wybierz...`. Peach `Lokalizacja` card with pin icon: `Warszawa, Śródmieście`. Green banner `Świetnie — wszystko gotowe ✨`, mostly covered by the sticky green `Opublikuj na OLX →` button.
- **user_intent:** Checking the auto-filled OLX category attributes and location before publishing.
- **sell_message:** "Condition, brand, size and location filled in for you."
- **caption_must_not_say:** "Every field completed automatically" (`Materiał` and `Dostawa` are still `Wybierz...`) / "Listing published".
- **crop_notes:** Content fills the frame; all interest is in the middle (the attribute list). No hero imagery — no photo at all on this screen. The green "wszystko gotowe" banner is 60% occluded by the CTA, so cropping the bottom 8% is cleaner than leaving the overlap.

---

## 13. `result_bottom_dark.png`

- **theme:** dark
- **visible_ui:** status bar `20:02`, back arrow. Same `Szczegóły` card: `Stan *` = `Używane`, `Marka` = `New Balance`, `Rozmiar *` = `40`, but `Kolor` = `Wybierz...` (unfilled, unlike the light capture), `Materiał` = `Wybierz...`, `Numer BDO (dla firm)` input, `Dostawa` = `Wybierz...`. `Lokalizacja` = `Warszawa, Śródmieście`. Green banner `Świetnie — wszystko gotowe ✨` (fully readable here), mint-green glowing `Opublikuj na OLX →`.
- **user_intent:** Same attribute-review moment, dark mode.
- **sell_message:** "OLX attributes pre-filled, ready to publish."
- **caption_must_not_say:** "All attributes detected" (three rows are empty) / "Ad is live".
- **crop_notes:** Same as #12. Slightly better bottom composition — the success banner is not occluded. Three visible `Wybierz...` placeholders make the card look less "done" than the light version.

---

## 14. `result_publish_dialog_light.png`

- **theme:** light
- **visible_ui:** status bar `20:01`, back arrow. Background dimmed to grey with the `Szczegóły` card (`Stan * Używane`, `Marka New Balance`, `Rozmiar * 40`) showing through. Foreground bottom sheet with drag handle and a row of three thumbnails: the first is the sneaker photo, **the other two render as blank peach squares** (images not loaded). Heading `Gotowy do publikacji?`. Line `Twoje ogłoszenie pojawi się na OLX w ciągu 1–2 minut.` Then `Buty New Balance 998BRE 40.5 EU`, category path `Moda / Buty męskie / Obuwie sportowe / Pozostałe`, price `zł 280` (currency symbol precedes the amount). Buttons: green `Tak, publikuj`, peach `Poczekaj, chcę edytować`.
- **user_intent:** Final confirmation — reviewing category and price, about to publish to OLX.
- **sell_message:** "Confirm category and price, then post straight to OLX."
- **caption_must_not_say:** "Your ad is live" (still a confirmation prompt) / "Posted instantly" (the copy says 1–2 minutes).
- **crop_notes:** All interest is in the bottom ~60% (the sheet); the top 40% is dimmed background and reads as filler. The two blank thumbnails are the most visible defect. Cropping the top is safe and improves it.

---

## 15. `result_publish_dialog_dark.png`

- **theme:** dark
- **visible_ui:** status bar `20:02`, back arrow. Dimmed dark background with `Szczegóły` rows behind. Bottom sheet with drag handle, **one** sneaker thumbnail (no blank placeholders). `Gotowy do publikacji?`, `Twoje ogłoszenie pojawi się na OLX w ciągu 1–2 minut.`, `Buty New Balance 998 U998BRE rozmiar 40,5`, `Moda / Buty męskie / Obuwie sportowe / Pozostałe`, price `zł 240`. Mint-green glowing `Tak, publikuj`, brown `Poczekaj, chcę edytować`.
- **user_intent:** Same final confirmation, dark mode.
- **sell_message:** "One tap to post the finished ad to OLX."
- **caption_must_not_say:** "Listing published" / "Sold" — nothing has been submitted.
- **crop_notes:** Same as #14 but visually clean (no broken thumbnails) and the glowing green CTA is the strongest single element in the whole set. Top 40% is dimmed background — croppable.

---

## Answers to the explicit questions

### Chronological order of the 8 unique screens

1. **auth** — `Kontynuuj z OLX`, no tab bar, welcome copy → pre-login entry point.
2. **generate_ad_top** — `Nowe ogłoszenie` tab active, `Dodaj 1–8 zdjęć`, photo picker; this is the compose/input screen.
3. **generate_ad_bottom** — the *same* screen scrolled down (identical status-bar time, identical tab bar, continuous content: photos → `+ Dodaj` → `Aparat`/`Galeria` → tips → AI hint → CTA).
4. **analysing_start** — `Przesyłanie zdjęć / Pracujemy nad tym…`, all other steps not started → immediately after tapping `Generuj z AI`.
5. **analysing_progress** — later steps ticked `Gotowe ✓` on the same checklist → later in the same wait.
6. **result_top** — `Gotowe w 56 sekund` (the analysis has finished and reports its duration), AI-filled `Tytuł` / `Opis`.
7. **result_bottom** — same screen scrolled down: `Szczegóły`, `Lokalizacja`, `Świetnie — wszystko gotowe ✨`.
8. **result_publish_dialog** — modal over the `result_bottom` content, `Gotowy do publikacji?` with `Tak, publikuj`.

The file names are the only source of confusion: "generate_ad" is the *input* screen, not the output.

### Is `analysing_start` before or after `generate_ad_top`?

**After.** `generate_ad_top` is where photos are attached and `Generuj z AI` is tapped; `analysing_start` is the first frame of the resulting progress screen (`Przesyłanie zdjęć` still in flight). The photos on `generate_ad_top_light` are the same New Balance sneaker that appears in `result_top_*`, confirming one continuous session.

**`analysing_start` vs `analysing_progress`:** the same screen at two moments — only the checklist state differs. `start` = step 1 spinning, steps 2–5 as empty circles. `progress` = one or more steps flipped to a green check with `Gotowe ✓`. Caveat: the two dark captures are out of sync with the light ones — `analysing_progress_light` shows 4 of 5 done (spinner on `Szacowanie ceny`), while `analysing_progress_dark` shows only 1 of 5 done (spinner on `Analizowanie zdjęć`), i.e. barely different from `analysing_start_dark`.

### Best hero candidates

1. **`result_top_light`** — the only single frame that shows the whole promise at once: real product photo, `Gotowe w 56 sekund` proof, AI-written `Tytuł` and the start of `Opis`, and the green `Opublikuj na OLX` CTA. Highest information density and it is truthful about "AI wrote this".
2. **`result_publish_dialog_dark`** — the payoff moment: product thumbnail, OLX category path, a concrete price (`zł 240`) and a glowing `Tak, publikuj`. Cleaner than its light twin (no blank thumbnails) and the price is the most persuasive single number in the set.
3. **`analysing_progress_light`** — four green ticks spelling out `Analizowanie zdjęć / Tworzenie tytułu / Pisanie opisu / Szacowanie ceny` are legible even at store-thumbnail size and explain *what* the AI does. Crop out the bottom tip strip (`80%%` typo).

`auth.png` has the prettiest artwork but the weakest claim (login only) — better as a last frame than a first one.

### Broken / unusable screenshots

- **`generate_ad_bottom_light`** and **`generate_ad_bottom_dark`** — mid-scroll captures; content runs under the status bar and the top strip is clipped mid-element. Usable only after a top crop.
- **`generate_ad_top_dark`** and **`generate_ad_bottom_dark`** — empty photo state with the blocked CTA `Najpierw dodaj zdjęcie`; not broken, but they cannot support any AI/generation claim. Avoid.
- **`result_publish_dialog_light`** — 2 of 3 thumbnails render as blank peach squares (unloaded images).
- **`result_top_dark`** — the `Opis` card body is completely hidden behind the sticky CTA, leaving an empty brown card.
- **`analysing_progress_dark`** — state mismatch (1/5 done); effectively a duplicate of `analysing_start_dark`, not a "progress" shot.
- **`generate_ad_top_light`** — sticky `Generuj z AI` button overlaps and half-hides the `Aparat` / `Galeria` row.
- **`result_bottom_light`** — the `Świetnie — wszystko gotowe ✨` banner is ~60% occluded by the CTA.
- Copy defects visible on screen: `80%%` (double percent) in `analysing_progress_light`; price formatted `zł 280` / `zł 240` with the symbol before the number, which is not the Polish convention (`280 zł`).
- Nothing is fully empty, black, or unrecoverable.

### Light vs dark, per screen

| Screen | Use | Why |
| --- | --- | --- |
| auth | light (only option) | No dark capture exists. |
| analysing_start | **light** | Warm cream gradient plus a crisp white checklist card; the dark halo is brown-on-brown and muddy. |
| analysing_progress | **light** | 4 green `Gotowe ✓` ticks read instantly; the dark capture shows only 1 tick and lower contrast. |
| generate_ad_top | **light** | Has actual photos and an enabled `Generuj z AI`; the dark one is an empty, blocked state. |
| generate_ad_bottom | **light** | Same reason; both variants need a top crop. |
| result_top | **light** | The first line of `Opis` is visible and the photo is well framed; the dark `Opis` card is empty behind the CTA. |
| result_bottom | **light** | `Kolor = Brązowy` makes the attribute card look more complete (dark shows `Wybierz...`). If the occluded success banner matters more, use dark. |
| result_publish_dialog | **dark** | No blank placeholder thumbnails and the glowing green `Tak, publikuj` is the strongest CTA in the set. Only use light if the missing thumbnails are re-captured. |
