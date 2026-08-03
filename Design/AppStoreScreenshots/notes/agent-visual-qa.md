# Visual QA of generated App Store screenshots

Produced by a subagent from direct visual inspection of the contact sheets and full-size renders (no code was read, nothing was fixed or regenerated). Pixel measurements were taken with ImageMagick; status-bar strings were OCR'd with tesseract.

## Scope and a caveat about moving files

- Viewed: all 8 contact sheets in `previews/` (there are 8, not 7 — `ipad-13-ua-contact-sheet.jpg` also exists).
- Opened at full size (10): `ipad-13/ro/{01-welcome,03-ai-writes,05-details,06-confirm}.jpg`, `ipad-13/pl/03-ai-writes.jpg`, `iphone-6.9/pt/{01-welcome,02-add-photos}.jpg` (twice — old and freshly regenerated), `iphone-6.9/bg/01-welcome.jpg`, `iphone-6.9/pl/07-confirm.jpg`.
- **The deliverable was being rewritten under me.** `node generate-app-store-screenshots.mjs --sheet` was running throughout (files re-stamped 22:11→22:13+, intermediate `.svg` files appearing and disappearing). The contact sheets in `previews/` belong to the 22:00–22:04 batch. `ipad-13/{bg,pt,ro,ua}` were still from that batch when I measured them, so every finding below matches the files on disk at review time. I re-opened the worst offender (`iphone-6.9/pt/02-add-photos.jpg`) after it was rewritten at 22:12:59 — **identical, the defect survived the regeneration**.
- Coverage gap worth stating up front: iPhone exists for `bg`, `pl`, `pt` only; iPad exists for `bg`, `pl`, `pt`, `ro`, `ua`. **`ro` and `ua` have no iPhone screenshots at all**, so those two storefronts would show fallback-language iPhone images — and iPhone is where nearly all the traffic is.

---

## a) TEXT OVERFLOW — clean

No headline, sub-line or pill label runs off the canvas, wraps badly, or collides with the star, the green tick badge, a decorative stroke or the device mock. Measured on all 21 iPhone images: headline and sub-line left margin is a consistent 80–86 px; the widest sub-line is `iphone-6.9/pl/01-welcome.jpg` at 882 px (ends x=967 of 1290), and the yellow star's left edge is at x≈1022 — a 55 px gap, the tightest in the set. Romanian, the longest-word locale, fits comfortably: `Verifică totul înainte de publicare` wraps to two clean lines and `Publică dintr-o atingere` stays on one.

Nit: that 55 px star clearance means ~3 more characters in any locale would collide. There is no visible guard.

## b) STRIKETHROUGH — clean, the bug is gone

No decorative stroke crosses headline or sub text anywhere. On iPhone the stroke sits below the chip row and arcs behind the green badge; on iPad it sits above the headline in the top 50 px.

| file | severity | what I see | why it matters |
|---|---|---|---|
| `ipad-13/*/05-details.jpg`, `ipad-13/*/06-confirm.jpg`, `ipad-13/pl/03-ai-writes.jpg` | nit | the top decorative arc is itself clipped by the canvas top edge — it enters and leaves the frame rather than terminating | reads as a positioning accident, not a flourish |

## c) DEVICE MOCK

| file | severity | what I see | why it matters |
|---|---|---|---|
| all 30 iPad images | should-fix | device frame bounding box is y 421→2011 on a 2064-high canvas: **421 px of air above, 53 px below**. Nothing is cut off, but the tablet visually slides off the bottom edge and its drop shadow is truncated | on a store page the mock looks mis-composed / like a cropping bug; it was the first thing that made me suspicious in every iPad contact sheet |
| `ipad-13/*/06-confirm.jpg` | should-fix | behind the centred confirm sheet, two disconnected fragments of the green "Publish on OLX" button poke out at the very bottom left and right | looks like torn compositing |
| all 21 iPhone images | clean | frame bbox e.g. 166→1125 x, 645→2537 y on 1290x2796 — fully inside, shadow intact, no squash (aspect preserved) | — |

## d) LEGIBILITY

| file | severity | what I see | why it matters |
|---|---|---|---|
| every `ipad-13/**` image | should-fix | the whole 12.9" UI is scaled into ~1900 px of a 2752 px canvas, so in-app body copy lands at 11–16 px. At contact-sheet scale (≈ App Store gallery thumbnail size) every word inside the tablet is unreadable grey mush | iPad shots communicate nothing until tapped; whatever the screen is "showing" is decorative |
| `ipad-13/*/05-details.jpg` | should-fix | worst case: eight attribute rows (`Marime / Stare / Culoare / Marca`) at ~11 px, plus a 3-line description paragraph | this is the least legible screen in the set and it occupies a whole slot |
| `ipad-13/pl/03-ai-writes.jpg` | should-fix | ~55–60% of the tablet screen is empty dark-brown void above and below the progress card | a phone-designed screen stretched onto a tablet; reads as an unfinished layout |
| `iphone-6.9/*/06-details.jpg`, `iphone-6.9/*/07-confirm.jpg` | nit | the app's dark theme is a brown that is very close in value to the orange background, so the device barely separates from the canvas; in `07-confirm` the scrimmed top rows (`Szczegóły / Stan / Marka`) sit at roughly 15% contrast | the two closing images look muddy next to the bright first five |
| `iphone-6.9/*/{01..05}` | clean | app copy is comfortably readable at full size and still readable at thumbnail scale | — |

## e) CAPTION vs SCREEN MISMATCH

**No image implies the listing is already published or live.** I checked all 39 caption/screen pairs: the furthest state shown is the pre-publish confirmation sheet ("Ready to publish?" with Yes / Wait-I-want-to-edit), and the in-app line "your listing will appear on OLX in 1–2 minutes" is future tense. That requirement is met.

Everything else:

| file | severity | what I see | why it matters |
|---|---|---|---|
| `ipad-13/bg/03-ai-writes.jpg`, `ipad-13/pt/03-ai-writes.jpg`, `ipad-13/ro/03-ai-writes.jpg`, `ipad-13/ua/03-ai-writes.jpg` | **blocker** | caption promises "AI writes the text **while you wait**" (`AI scrie textul cât aștepți`) but the device shows the **finished, editable listing** — title, full description, price, green "Publish on OLX" — plus the app's own banner "Ready in 25 seconds — read it before publishing". The wait is over; nothing is being written | the single claim the whole app rests on is illustrated with the wrong screen, and it is also a duplicate (see f). `ipad-13/pl/03` shows the correct progress screen, which proves the capture is available |
| `iphone-6.9/*/06-details.jpg` (all 3 locales) | should-fix | caption "Publish with one tap / listing ready, no extra work" over a **manual attribute form** — `Stan *`, `Rozmiar *`, `Marka`, `Material`, `Dostawa`, `Lokalizacja`, several empty, two marked required | it advertises zero effort on the one screen that shows effort. A reviewer reads this as a bait-and-switch |
| `ipad-13/*/05-details.jpg` (all 5 locales) | should-fix | same caption over the same kind of form (`Publică dintr-o atingere` / `Anunț gata fără muncă în plus`) | same as above |
| `iphone-6.9/*/04-ai-steps.jpg` | nit | caption ("Create listings faster / AI helps with text, price and details") and both chips (`Снимка` + `AI`) are recycled verbatim from slot 1, over the same screen as slot 3 | two of seven slots make the same promise twice |
| `ipad-13/ua/04-review.jpg` | nit | headline `Перевірте все перед запуском` — "before **launch**" | wrong register for a classified ad and adjacent to the "go live"/broadcast phrasing the team already decided to avoid in UA copy; `перед публікацією` is the honest word |

## f) DUPLICATES

Measured RMSE over the device region, all pairs inside each locale folder.

| file | severity | what I see | why it matters |
|---|---|---|---|
| `ipad-13/{bg,pt,ro,ua}/03-ai-writes.jpg` vs `04-review.jpg` | **blocker** | pixel-identical app content: RMSE **0.0013** (bg), **0.0006** (pt), **0.0008** (ro), **0.0004** (ua). The two images differ only in the caption block. `pl` is the exception at 0.28 | a capture bug leaking straight to the store: 2 of 6 iPad slots show the same screen for 4 of 5 locales. Anyone scrolling the gallery sees the same picture twice |
| `iphone-6.9/*/03-ai-writes.jpg` vs `04-ai-steps.jpg` | should-fix | not pixel duplicates (RMSE 0.81) but the **same screen** — "Preparing your listing" with the 5-step checklist — once in dark theme mid-progress and once in light theme nearly complete | burns a slot on a loading spinner shown twice. No other iPhone pair is close (minimum non-pair distance 0.19) |
| all other pairs | clean | no further duplicates | — |

## g) Other things that would embarrass us

| file | severity | what I see | why it matters |
|---|---|---|---|
| `iphone-6.9/pt/02-add-photos.jpg` | **blocker** | the app line "Adicione 1 a 8 fotos. Nós tratamos do resto." is rendered **outside the screen**, over the black bezel and the Dynamic Island, clipped by the top of the device frame; the status bar is shoved down beneath it and the battery glyph lands on top of the third photo thumbnail. Lower down, the "Gerar com IA" gradient button overlaps the "Dê uma dica à IA" card and a third, half-hidden element behind it. **Still present in the 22:12:59 re-render** | this is unmistakably broken compositing. It alone would make a reviewer distrust the whole set |
| `ipad-13/*/01-welcome.jpg` (all 5 locales) | **blocker** | mid-animation capture: "Try without account" appears **twice** — as a card heading and again as a button — and the button's top edge cuts through the card's own description line; the peach "Why connect OLX?" card is offset from the text it contains; the "Continue with OLX" button has a doubled/offset edge | this is screenshot **#1**, the one everybody sees. It looks like a layout crash |
| `iphone-6.9/pt/01-welcome.jpg` | **blocker** | same doubled "Experimentar sem conta" heading + button overlapping the description; a stray orphan dot floats at the top of the screen where content was clipped; the greeting and hero image present in `bg`/`pl` are missing, so the lead image is a legal footer with Terms and Privacy links | worst first impression of the three iPhone locales |
| `iphone-6.9/bg/01-welcome.jpg`, `iphone-6.9/pl/01-welcome.jpg` | should-fix | the primary CTA at the bottom is a **half-drawn, unlabelled orange sliver** — the button is caught mid-entrance with no text | the hero image ends on a broken button |
| `iphone-6.9/*/02-add-photos.jpg` (all 3) | should-fix | the "Generate with AI" button overlaps the Camera/Gallery row beneath it; only slivers of those labels show | same mid-animation root cause |
| `ipad-13/{bg,ro,ua}/0{2,3,4,5}.jpg` | should-fix | **system status bar is in Polish** — `Wt. 28 lip` — inside Bulgarian, Romanian and Ukrainian screenshots. `ipad-13/pt/02-add-photos.jpg` is also Polish (`Wt. 28 lip`) while pt 03–05 correctly read `quarta-feira, 29 de julho`. And **every** `01-welcome.jpg`, all five locales, is in English: `Mon 20 Jul` | the localized storefront shows another country's language in the chrome. Cheap to spot, cheap to fix, expensive in credibility |
| `ipad-13/bg/*`, `ipad-13/ua/*` | nit | the clock jumps around inside one set: bg reads 20:19 → 13:20 → 22:04 → 22:04 → 22:03; ua reads 20:19 → 15:31 → 19:21 | betrays that the "flow" is stitched from unrelated sessions |
| `ipad-13/*/0{3,4,5,6}.jpg` | should-fix | the app's back arrow `←` is drawn **on top of the status-bar clock** (`←22:01`) | an in-app iPad layout bug, now published at 2752 px |
| `ipad-13/ro/05-details.jpg`, `ipad-13/ro/06-confirm.jpg` | should-fix | category is `Moda si frumusete / Incaltaminte **dama** / Pantofi sport` — **women's** shoes — while the title and description sell brown men's New Balance 998s (pt correctly says `Calçado / Homem`). In `06-confirm` the detail row reads `Marime 41` while the title says `mărimea 40.5 EU` | contradictory demo data in the two closing images of the only device family Romania gets |
| `ipad-13/ro/{03,04}.jpg`, `iphone-6.9/*/05-review.jpg` | nit | the hero product photo is an extreme, tilted close-up of laces / a barcode label, filling 40% of the device | the "money shot" screen is illustrated with an unappealing photo; it also undercuts the in-app tip "good light = better results" |
| `ipad-13/*` set order | nit | app theme alternates light, light, dark, dark, light, dark within one locale | reads as accidental rather than as a dark-mode showcase |

---

## Verdicts

**Weakest set: `ipad-13/ro`.** It carries every systemic iPad defect (duplicate 03/04, mock 53 px off the bottom edge, unreadable in-app type, broken welcome screen, back-arrow over the clock) *plus* two of its own: a Polish system date in four of six images and demo data that files men's sneakers under women's shoes with a size that contradicts the title. It is also — with `ua` — an iPad-only locale, so this flawed set is the entire visual pitch for Romania. `ipad-13/ua` is a close second (same systemic issues, Polish date, plus the `запуском` copy slip); `ipad-13/bg` third (same, plus the jumping clock).

**If we could only fix 3 things:**
1. **Re-capture `03-ai-writes` on iPad for bg, pt, ro, ua** using the real progress screen that `pl` already has. Kills the blocker duplicate and the biggest caption lie in one move — 8 images.
2. **Let animations settle before capture** (add a settle delay / disable animations in the capture flow) and re-shoot `01-welcome` on all 5 iPad locales + pt iPhone, and `02-add-photos` on all 3 iPhone locales. This is the single root cause behind the doubled cards, the half-drawn CTAs and the pt text-over-the-notch bleed — the most obviously "broken" thing in the set.
3. **Fix the device chrome: set the simulator system language to the target locale and pin one clock time** for every capture. Removes `Wt. 28 lip` from bg/ro/ua/pt and `Mon 20 Jul` from all five welcome images.

Runners-up, both cheap and both worth doing in the same pass: move the iPad mock up ~200 px so it is optically centred, and re-caption the `*-details` slots (iPhone 06 / iPad 05) so "publish with one tap" is not sitting on a required-field form.

**Ship as-is? No.** Four of five iPad locales show the same screenshot twice under a caption that describes a different screen, and the very first image of every iPad locale plus two iPhone images contain visibly broken, overlapping UI.
