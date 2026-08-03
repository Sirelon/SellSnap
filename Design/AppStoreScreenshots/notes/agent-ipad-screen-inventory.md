# iPad source screen inventory (pl locale)

Produced by a subagent from direct visual inspection of all 15 PNGs in `/Users/sirelon/Projects/SellSnap/screenshots/ipad/pl/`.

---

## Blocking finding before anything else: the files are sideways

All 15 files are **2064x2752 (portrait canvas) with no EXIF orientation flag, but the UI inside them is rotated 90°**. The status bar runs down the right-hand edge. The real UI is **landscape 2752x2064** — almost certainly a raw simulator framebuffer grab of a landscape-oriented iPad, written without applying the rotation.

- To view/use correctly: rotate 270° (counter-clockwise) → 2752x2064.
- I worked from rotated copies in `/tmp/ipadrot/` and left the source files untouched.
- **As-is these cannot be uploaded to App Store Connect.** Anything built on top of them must rotate first.

## Second blocking finding: one file is a duplicate, one is mislabelled

- `analysing_progress_light.png` is **byte-identical** to `result_top_light.png` (md5 `4fa424dca4db5f0095686332180a346e`). There is no light-theme "analysing progress" capture.
- `analysing_start_light.png` does **not** show a start state — 3 steps are ticked, the 4th is caught mid-animation, and the 5th is spinning. It is functionally the light-theme *progress* screen.

Net: **14 unique images covering 5 unique app screens**, not 8.

---

## Per-file inventory

### 1. `auth.png`
- **theme** light (no dark variant exists)
- **visible_ui** Full-width hero photo band (sneakers + phone + folded jacket, warm daylight) with a small orange sparkle chip top-left. Headline `Hej, witaj w SellSnap 👋`; subhead `Wrzuć zdjęcie, dostań ogłoszenie — AI robi resztę.` Peach card `Po co łączyć konto OLX?` with three green ticks: `Publikuj prosto na OLX — bez kopiowania` / `Twoje ogłoszenia synchronizują się same` / `Wszystkie reklamy w jednym miejscu`. Full-width orange primary button `Kontynuuj z OLX`. Divider `lub`. Block `Wypróbuj bez konta` + `Możesz stworzyć ogłoszenie i skopiować tekst — wystarczy wkleić go samodzielnie na OLX.` Full-width peach secondary button `Wypróbuj bez konta`. **Single-column, stretched to full landscape width.** Two icons render as empty rounded-outline placeholders (tofu / missing glyph): the leading chip next to `Wypróbuj bez konta` and the inline icon in the secondary button.
- **user_intent** Deciding whether to connect their OLX account or try the app anonymously — the very first launch decision.
- **sell_message** "Connect OLX once — or try it with no account."
- **caption_must_not_say** "Your listing is live on OLX" / "AI wrote this listing" — nothing has been created or published on this screen.
- **crop_notes** Content fills the full height; hero band occupies the top ~23%. The benefits list uses only the left ~25% of its card, so ~75% of the card is empty peach. Both buttons stretch the full 2752px. Hero image is squeezed into a ~2752x480 band, so it is heavily cropped. Interesting content = top half.

### 2. `analysing_start_light.png` — MISLABELLED (actually a progress state)
- **theme** light
- **visible_ui** Cream-to-peach gradient. Centred pulsing orange orb with sparkle glyph + small green lightning badge. `Przygotowujemy Twoje ogłoszenie` / `Analizujemy Twoje zdjęcia i piszemy idealny tytuł, opis i cenę...`. Full-width white card, 5 rows: `Przesyłanie zdjęć — Gotowe ✓`, `Analizowanie zdjęć — Gotowe ✓`, `Tworzenie tytułu — Gotowe ✓`, `Pisanie opisu — Gotowe ✓` (tick circle rendered beige/half-state, mid-animation), `Szacowanie ceny — Pracujemy nad tym...` (spinner). Tip pill: `Wskazówka: zbliżenia szczegółów i śladów użytkowania pomagają napisać lepszy opis.` **Single-column, stretched.**
- **user_intent** Waiting while the AI finishes the last step (price estimation).
- **sell_message** "Watch the AI write your title, description and price."
- **caption_must_not_say** "Listing published" / "Done in 3 seconds" — it is still working, and no elapsed time is shown.
- **crop_notes** Content sits in a middle band. The white card is full-width but the rows use only the left ~18%, so ~82% of the card is blank white — the single sparsest element in the whole set. ~14% dead gradient above the orb and ~18% below the tip pill.

### 3. `analysing_start_dark.png` — the only genuine "start" state
- **theme** dark
- **visible_ui** Warm dark-brown radial gradient. Same orb + green lightning badge. Same headline/subhead. Near-black card, 5 rows: `Przesyłanie zdjęć — Pracujemy nad tym...` (spinner), then `Analizowanie zdjęć`, `Tworzenie tytułu`, `Pisanie opisu`, `Szacowanie ceny` as **empty outline circles with no status text**. Tip pill: `Wskazówka: dobre światło = lepsze wyniki.` **Single-column, stretched.**
- **user_intent** Has just tapped `Generuj z AI`; the first step (upload) has begun.
- **sell_message** "One tap starts the AI pipeline."
- **caption_must_not_say** "AI already wrote your title and description" — nothing has completed yet.
- **crop_notes** Same geometry as #2 and the same ~82%-empty card. Contrast is poor: dark-brown card on dark-brown gradient, and five empty circles read as "nothing is happening". Weakest storytelling frame in the set.

### 4. `analysing_progress_light.png` — UNUSABLE, duplicate
- **theme** light
- **visible_ui** Byte-identical to `result_top_light.png`. Shows the finished listing, not the analysing screen. See #10.
- **user_intent** n/a (wrong file).
- **sell_message** n/a — must not be used under this name.
- **caption_must_not_say** Anything about "analysing" — this is the result screen.
- **crop_notes** n/a. Recapture or delete.

### 5. `analysing_progress_dark.png`
- **theme** dark
- **visible_ui** Same layout as #3. Rows 1–4 (`Przesyłanie zdjęć`, `Analizowanie zdjęć`, `Tworzenie tytułu`, `Pisanie opisu`) all bright-green ticks + `Gotowe ✓`; row 5 `Szacowanie ceny — Pracujemy nad tym...` with an orange spinner. Tip pill: `Wskazówka: czyste tło sprawia, że przedmiot lepiej się wyróżnia.` **Single-column, stretched.**
- **user_intent** Watching the last of five AI steps finish.
- **sell_message** "Photos in, title / description / price out — automatically."
- **caption_must_not_say** "Published to OLX" / "Ready in 3 seconds" — publishing hasn't started and no duration is shown.
- **crop_notes** Same ~82% empty card. Best-reading of the three analysing frames: four bright-green ticks on dark give high contrast and a clear "AI is doing the work" read. ~18% dead space bottom, ~14% top — good caption room.

### 6. `generate_ad_top_light.png` — genuinely tablet-adaptive
- **theme** light
- **visible_ui** **Two-region wide layout:** persistent left **navigation rail** (`Nowe ogłoszenie` active in an orange chip, `Moje ogłoszenia`, `Profil`) beside the content pane. Orange promo banner with `SellSnap` logo lockup, `Masz co sprzedać? Sfotografuj. My się tym zajmiemy.` and `Zrób zdjęcie. AI zajmie się słowami.` H1 `Nowe ogłoszenie`, sub `Dodaj 1–8 zdjęć. My zajmiemy się resztą.` **Three-across photo grid** of the user's New Balance 998 shots (each with an ✕ remove button); third photo shows the size label `U998RE / 40.5 EU / Made in USA`. Empty 4th tile begins below. Sticky full-width gradient CTA `✦ Generuj z AI` (inset to start after the nav rail). The `Profil` rail icon renders as an outline placeholder (tofu).
- **user_intent** Has picked 3 photos of the shoes and is about to hand them to the AI.
- **sell_message** "Add up to 8 photos — that's your whole input." / "Real iPad layout with a sidebar."
- **caption_must_not_say** "AI has written your listing" / "Published on OLX" — no generated text exists yet.
- **crop_notes** Content is top-to-middle and genuinely fills the width. The 4th `Dodaj` tile is cut off by the sticky CTA. Modest dead space bottom-right of the grid and a decorative circle in the banner's empty right side. This is the only screen that visually proves a tablet layout.

### 7. `generate_ad_top_dark.png`
- **theme** dark
- **visible_ui** Identical structure and identical Polish copy to #6, on a near-black / dark-brown canvas. Same nav rail, same orange banner (banner keeps its light-orange fill so it pops harder), same 3-up grid, same `✦ Generuj z AI` gradient CTA.
- **user_intent** Same as #6.
- **sell_message** Same as #6.
- **caption_must_not_say** Same as #6.
- **crop_notes** Same geometry. The photo thumbnails float in a large black field, so the grid reads as less deliberate than in light; the tofu `Profil` icon is more conspicuous against black.

### 8. `generate_ad_bottom_light.png`
- **theme** light
- **visible_ui** Same nav rail. Scrolled down: only the bottom slivers of the three photos remain (cropped mid-shoe). A large empty `+ Dodaj` tile occupies the **left column only — columns 2 and 3 are completely blank**. Two side-by-side buttons `📷 Aparat` and `🖼 Galeria`. Peach card `Wskazówki foto 📷` with three green ticks: `Dobre światło = lepsze wyniki` / `Fotografuj z kilku kątów` / `Nie ukrywaj zarysowań — kupujący cenią szczerość`. Sticky `✦ Generuj z AI` CTA **overlapping** a free-text field whose placeholder `np. Nike Air Max 90, rozmiar 42, noszone 2 miesiące` is clipped behind it.
- **user_intent** Reviewing the photo tips and optionally adding a text hint before generating.
- **sell_message** "Built-in photo tips so the AI gets a better result."
- **caption_must_not_say** "Your ad is ready" / "Live on OLX" — this is still the input screen.
- **crop_notes** Very sparse: the `Dodaj` band leaves ~66% of its width empty, and the tips card uses only the left ~28%. Top strip is an unattractive mid-shoe crop. The CTA-over-textfield overlap reads as a layout bug. Weak store material.

### 9. `generate_ad_bottom_dark.png`
- **theme** dark
- **visible_ui** Same as #8 in dark. The empty `Dodaj` tile is a near-black rectangle; `Aparat` / `Galeria` are dark outlined buttons; the `Wskazówki foto` card is dark brown. Same CTA-overlapping-textfield clipping of `np. Nike Air Max 90, rozmiar 42, noszone 2 miesiące`.
- **user_intent** Same as #8.
- **sell_message** Same as #8.
- **caption_must_not_say** Same as #8.
- **crop_notes** Same sparseness plus the empty tile reads as a void rather than an affordance. Worst-looking frame of the 14.

### 10. `result_top_light.png` (= `analysing_progress_light.png`)
- **theme** light
- **visible_ui** Full-width photo carousel band (extreme close crop of the brown/black NB 998 laces and toe box) with 3 page dots. Green pill `✓ Gotowe w 23 sekund — przejrzyj przed publikacją.` Peach `Tytuł` card with `Kopiuj` and `✦ AI` chips at the far right, value `Buty New Balance 998 męskie 40,5`, counter `32/140`. Peach `Opis` card, same chips, three-line AI description (`Sprzedaję swoje buty New Balance 998 w rozmiarze 40,5. Buty są w dobrym stanie, noszone sporadycznie, nie mają większych śladów zużycia. Kolorystyka brązowo-czarna, klasyczny wygląd, wygodne i solidnie wykonane, Made in USA. Powód sprzedaży – po prostu leżą nieużywane w szafie. Zachęcam do kontaktu, chętnie odpowiem na pytania.`), counter `329/9000`. `Twoja cena` card begins with `zł320` **clipped mid-glyph** by the sticky CTA. Sticky full-width dark-green CTA `Opublikuj na OLX →`. No nav rail. **Single-column, stretched.** The back chevron collides with the status-bar clock.
- **user_intent** Reading the AI-written listing and about to publish it.
- **sell_message** "Title, description and price written for you in 23 seconds."
- **caption_must_not_say** "Already published on OLX" — the CTA has not been tapped; the pill explicitly says *review before publishing*.
- **crop_notes** Strong content, top-to-bottom. Photo band = top ~40%; the phone-shot image is blown up on a 2752px canvas so it looks over-zoomed. `Tytuł` value uses ~20% of its card width (big empty right). `Opis` genuinely uses the width but lines run ~150 characters. The clipped `zł320` is the main defect.

### 11. `result_top_dark.png`
- **theme** dark
- **visible_ui** Same layout. `✓ Gotowe w 26 sekund — przejrzyj przed publikacją.` `Tytuł`: `Buty New Balance 998 brązowe 40.5`, `33/140`. `Opis`: `Sprzedaję swoje buty New Balance 998 w rozmiarze 40.5. Są w bardzo dobrym stanie, zadbane, noszone okazjonalnie. Kolor to połączenie brązu i ciemniejszych wstawek, sznurowadła kremowe. Model wyprodukowany w USA, bardzo wygodny na co dzień. Oddaję, bo mam już za dużo par w szafie.`, `280/9000`. `Twoja cena` `zł300` with a `Kopiuj` chip — **fully visible, not clipped**. Sticky mint-green CTA `Opublikuj na OLX →`. Back chevron collides with the clock. Blue location-arrow in the status bar.
- **user_intent** Same as #10.
- **sell_message** "AI-written title, description and suggested price — ready to review."
- **caption_must_not_say** "Published" / "Your ad went live" — still pre-publish.
- **crop_notes** Same geometry as #10 but nothing is clipped, contrast is better, and the dark cards hide the flat peach fills. The single best-looking result frame.

### 12. `result_bottom_light.png`
- **theme** light
- **visible_ui** Scrolled to OLX field mapping. Clipped sliver `AI sugeruje: zł 280 – zł 350` at the top. `Kategoria` card: `Moda / Buty męskie / Obuwie sportowe / Pozostałe`. `Szczegóły` card with rows `Stan * Używane`, `Marka New Balance`, `Rozmiar * 40`, `Kolor Brązowy`, `Materiał Skóra ekologiczna`, empty input `Numer BDO (dla firm)`, `Dostawa Wybierz...` — each with a chevron pinned to the far right. `Lokalizacja` card: `Warszawa, Śródmieście`. Green pill `✓ Świetnie — wszystko gotowe ✨`. Sticky dark-green CTA `Opublikuj na OLX →`. **Single-column, stretched.**
- **user_intent** Checking the auto-filled OLX category and attributes before publishing.
- **sell_message** "OLX category and attributes filled in automatically."
- **caption_must_not_say** "Listing published" / "Buyers are messaging you" — publish is still pending, and no buyer/message UI exists here.
- **crop_notes** Most stretched screen in the set: every row is a short left-aligned value with a ~2400px empty gutter before the chevron. Solid apricot cards fill ~90% of the canvas, flat and low-contrast against the label text. Zero dead space, but zero visual interest.

### 13. `result_bottom_dark.png` — not the same crop as #12
- **theme** dark
- **visible_ui** A **different scroll position and different state** from #12. Clipped sliver `Gotowe w 26 sekund — przejrzyj przed publikacją.` at the top, then `Tytuł` (`Buty New Balance 998 brązowe 40.5`, `33/140`), `Opis` (280/9000), `Twoja cena` `zł300` with a **full-width orange price slider** `zł 250` — `zł 350` and `AI sugeruje: zł 250 – zł 350`, `Kategoria` `Moda / Buty damskie / Obuwie sportowe / Pozostałe`, `Lokalizacja` `Warszawa, Śródmieście`, green pill `✓ Świetnie — wszystko gotowe ✨`, sticky mint CTA `Opublikuj na OLX →`. **No `Szczegóły` attributes card at all.**
- **user_intent** Adjusting the AI-suggested price inside the recommended range before publishing.
- **sell_message** "AI suggests a price range — drag to set yours."
- **caption_must_not_say** "OLX attributes filled in automatically" (no `Szczegóły` card here) or "Published".
- **crop_notes** The price slider stretched across the full 2752px is the single most obviously phone-layout-blown-up element in the whole set. Note the category reads `Buty damskie` (women's) for a men's NB 998 — do not pair this frame with a caption claiming accurate categorisation. Not comparable to #12; they are different crops of different runs.

### 14. `result_publish_dialog_light.png`
- **theme** light
- **visible_ui** #12 behind a flat mid-grey scrim (peach desaturates to muddy taupe). A **phone-width bottom sheet floated in the middle of the canvas**, ~45% of the width, with a drag handle at the top: 3 photo thumbnails, `Gotowy do publikacji?`, `Twoje ogłoszenie pojawi się na OLX w ciągu 1–2 minut.`, `Buty New Balance 998 męskie 40,5`, `Moda / Buty męskie / Obuwie sportowe / Pozostałe`, `zł 320`, green primary `Tak, publikuj`, peach secondary `Poczekaj, chcę edytować`. The sheet is **clipped by the bottom edge of the screen** — no bottom padding below `Poczekaj, chcę edytować`.
- **user_intent** Final confirmation tap before the listing goes to OLX.
- **sell_message** "Confirm once — it's on OLX in 1–2 minutes."
- **caption_must_not_say** "Your listing is live" — the user hasn't confirmed yet; the dialog is asking.
- **crop_notes** Broken full-frame: a phone bottom sheet marooned mid-canvas with large voids left and right and clipped at the bottom. The grey scrim over peach looks washed out, like a rendering fault. Only usable if cropped tight to the sheet.

### 15. `result_publish_dialog_dark.png`
- **theme** dark
- **visible_ui** Same sheet over #13. Dark-brown card, `Gotowy do publikacji?`, `Twoje ogłoszenie pojawi się na OLX w ciągu 1–2 minut.`, `Buty New Balance 998 brązowe 40.5`, `Moda / Buty damskie / Obuwie sportowe / Pozostałe`, `zł 300`, mint `Tak, publikuj`, brown `Poczekaj, chcę edytować`. Also **clipped at the bottom edge**, also ~45% width floating mid-canvas.
- **user_intent** Same as #14.
- **sell_message** Same as #14.
- **caption_must_not_say** Same as #14.
- **crop_notes** The dark scrim reads far better than light's grey. Still clipped at the bottom and still surrounded by voids. Usable only as a tight crop of the sheet.

---

## Answers to the explicit questions

### Chronological order of the unique screens
There are **5 unique screens**, not 8; the extra files are top/bottom scroll crops and theme pairs.

1. `auth` — welcome / connect OLX or continue without an account
2. `generate_ad_top` → 3. `generate_ad_bottom` — same photo-picker screen, unscrolled then scrolled
4. `analysing_start` → 5. `analysing_progress` — same AI progress screen, early then late
6. `result_top` → 7. `result_bottom` — same generated-listing screen, unscrolled then scrolled
8. `result_publish_dialog` — confirmation sheet over the result screen

### Is `analysing_start` before or after `generate_ad_top`?
**After.** `generate_ad_top` is where the user adds photos and taps `✦ Generuj z AI`; the analysing screen is the result of that tap. Naming order in the folder is misleading.

### Actual difference between `analysing_start` and `analysing_progress`
It is the **same screen at two points in the same 5-step pipeline** (`Przesyłanie zdjęć` → `Analizowanie zdjęć` → `Tworzenie tytułu` → `Pisanie opisu` → `Szacowanie ceny`), plus a rotating tip line at the bottom.

- Intended: *start* = step 1 running, steps 2–5 pending. *Progress* = steps 1–4 done, step 5 running.
- Reality in these files: only `analysing_start_dark` is a true start state. `analysing_progress_dark` is a true progress state. `analysing_start_light` is actually a *progress* state (and its 4th tick is caught mid-animation, rendered beige). `analysing_progress_light` is not an analysing screen at all — it is a duplicate of `result_top_light`.

### Best hero candidates for the first iPad store screenshot
1. **`result_top_dark`** — the payoff frame. Item photo + `Gotowe w 26 sekund` + AI title + AI description + `zł300` + `Opublikuj na OLX` are all visible with nothing clipped. Highest contrast, hides the flat peach cards, and it is the only frame that shows the whole promise at once.
2. **`generate_ad_top_light`** — the only frame that proves this is a tablet app (nav rail + 3-across photo grid) and it shows the "before" half of the story with the `Generuj z AI` CTA. Best choice if slide 1 must read as "iPad-native".
3. **`analysing_progress_dark`** — clean, high contrast, four green ticks make "the AI does five jobs for you" instantly legible. Third because the card is ~82% empty.

### Broken, empty, mid-transition, sparse or unusable
- `analysing_progress_light.png` — **unusable**: byte-identical duplicate of `result_top_light.png`; not an analysing screen.
- `analysing_start_light.png` — **mislabelled** (a progress state) and **mid-transition** (row 4's tick is a beige half-state).
- `generate_ad_bottom_light` / `generate_ad_bottom_dark` — **sparse and buggy-looking**: two of three grid columns empty; sticky CTA overlaps and clips the hint text field. Dark is the worst-looking frame overall.
- `result_publish_dialog_light` / `_dark` — **clipped**: the bottom sheet runs off the bottom edge with no padding, and floats mid-canvas with large voids either side. Light's flat grey scrim additionally looks like a render fault.
- `result_top_light` — `zł320` is **clipped mid-glyph** by the sticky CTA.
- `result_bottom_light` — not broken but the **most stretched** frame; every row is ~90% empty gutter.
- `result_bottom_dark` — **not the same crop as its light counterpart** (different scroll position, no `Szczegóły` card, shows the price slider) and its category reads `Buty damskie` for a men's shoe.
- `auth`, `generate_ad_top_*` — an icon renders as an empty outline placeholder (tofu): `Wypróbuj bez konta` chip and inline icon on auth, `Profil` in the nav rail.
- `result_top_*`, `result_bottom_*`, `result_publish_dialog_*` — the back chevron collides with the status-bar clock.
- **All 15** — wrong file orientation (see the top of this doc).

### Light or dark, per screen
| Screen | Use | Why |
|---|---|---|
| `auth` | light (only option) | No dark variant was captured. Works fine. |
| `analysing_start` | dark, or skip | Dark is the only genuine start state, but five empty circles on low-contrast brown say "nothing is happening". Consider dropping this state entirely. |
| `analysing_progress` | dark (only option) | Light is a duplicate, so dark wins by default — and it genuinely looks good. |
| `generate_ad_top` | **light** | Photo grid and orange banner pop against cream; in dark the thumbnails float in a black void and the tofu `Profil` icon is more obvious. |
| `generate_ad_bottom` | light (both weak) | The empty `Dodaj` tile reads as an affordance in light and as a void in dark. Prefer not to ship either. |
| `result_top` | **dark** | Price not clipped, better contrast, avoids the flat peach cards. |
| `result_bottom` | depends on caption | Dark looks better and shows the price slider; light shows the `Szczegóły` OLX attribute auto-fill. Pick to match the claim. |
| `result_publish_dialog` | dark | Light's grey scrim over peach is muddy. Both need a tight crop. |

### Tablet-optimised or stretched phone? (blunt answer)
**Mostly stretched phone.** Two of the five unique screens are genuinely adaptive; three are not.

- **Genuinely tablet-optimised:** `generate_ad_top` and `generate_ad_bottom` — persistent left navigation rail, three-across photo grid, side-by-side `Aparat` / `Galeria` buttons, CTA inset to clear the rail. This is real wide-layout work.
- **Stretched phone:** `auth`, `analysing_*`, `result_top`, `result_bottom`, `result_publish_dialog`. Single centred column at full canvas width; buttons and rows span the entire 2752px; the progress-step card and every attribute row leave 80–90% of their width blank; the price slider stretches edge to edge; the publish sheet is a phone bottom sheet floated mid-canvas and clipped at the bottom. The nav rail disappears entirely on the analysing and result screens.

**Presentation recommendation:** these are landscape captures, so present them as **landscape 2752x2064** (rotate 270° first). Do not try to force them into a portrait iPad slot — the content is already landscape and would need re-capture, not re-cropping. Lead with `generate_ad_top` if the goal is "this is a real iPad app"; lead with `result_top_dark` if the goal is the product payoff. If a portrait iPad slot is mandatory, the whole set must be re-captured with the simulator in portrait.
