# Seller Image Prompt Pack

Scope: Seller / SellSnap only. This document does not request generation in Codex. It is a prompt pack for an external image model, plus implementation notes for adding the returned assets later.

Creative direction: make SellSnap feel like a premium selling assistant with a subtle sense of humor. The images should be warm, polished, clever, and slightly theatrical, but still believable inside a real mobile app.

Hard rule: generated assets must contain zero text in any language. No Polish, Ukrainian, English, Latin/Cyrillic characters, numbers, captions, labels, signs, watermarks, brand marks, pseudo-text, fake UI words, or readable/gibberish lettering. Any UI card, phone screen, listing card, tag, or document shape must use blank blocks, abstract lines, icons, or empty rounded rectangles only.

## Audit Notes

- `composeApp/src/commonMain/composeResources/drawable/img_onboarding_1.webp` is a 400x266 raster image and is currently unused.
- Runtime seller screens mostly use vector icons and code-built compositions. Actual listing photos come from user uploads and flow through preview, publish confirmation, and success screens.
- The prototype has explicit placeholder product imagery in `brand/claude-design/project/placeholders.jsx`. Kotlin previews also use remote Unsplash sneaker URLs in `PreviewAdScreen.kt`.
- Do not bake UI text into generated images. Seller copy is localized through `strings.xml`, and Ukrainian/English text should remain editable in resources. External models may invent Polish-looking text if not constrained, so keep repeating the zero-text rule.
- Avoid OLX logos or third-party brand logos in generated imagery. Use generic marketplace cards, phone UI blocks, camera motifs, checks, tags, and product photos.

## Screenshot References

If you provide screenshots of the current app to an image model, use this add-on at the top of every prompt:

```text
Input images: use the provided app screenshots only as visual references for color palette, spacing, card shapes, mood, and where the asset will sit in the UI.
Do not copy, preserve, translate, or invent any text from the screenshots.
The generated image must contain zero text: no words, letters, numbers, symbols, captions, app labels, fake UI text, Polish text, Ukrainian text, English text, pseudo-text, logos, or watermarks.
Replace every visible text area with blank abstract bars, icon-only shapes, or empty rounded rectangles.
```

## Global Style

Use a warm, premium mobile-app visual style that matches SellSnap:

- Warm orange accents: `#D0600A`, `#F08030`
- Amber accent: `#FBBF24`
- Success green: `#1B8E5A`
- Warm cream surfaces: `#FFF8F2`, `#FFF0E0`
- Deep warm brown for dark details: `#3A1F00`

Make the visual language feel like "marketplace magic": everyday resale objects getting a red-carpet treatment, tidy floating UI cards, satisfying before/after transformations, premium soft shadows, little celebratory details, and playful visual jokes that do not need words.

Keep assets readable at mobile sizes, with a clear central subject and clean negative space. Avoid a cartoon look: prefer realistic photography, editorial product photography, or photorealistic 3D with natural materials and believable lighting. Funny should mean witty composition and charming object staging, not memes, captions, mascot characters, exaggerated cartoon proportions, or chaotic clutter.

## Primary Runtime Assets

### 1. Onboarding Step 1: Snap A Photo

Suggested filename: `img_seller_onboarding_snap.webp`

Target: square, 2048x2048 source, displayed around 160-176 dp.

```text
Use case: photorealistic-natural
Asset type: mobile onboarding illustration for an AI marketplace listing app
Primary request: show the first step, taking photos of an item to sell, but make the item feel like it is getting a glamorous mini photo shoot
Scene/backdrop: clean warm cream studio background with subtle orange accents, like a tiny product-photo set
Subject: a hand holding a smartphone camera over clean resale sneakers or a small lamp; the item sits under a soft spotlight with tiny floating focus brackets, sparkle marks, and two polished photo thumbnails popping out of the phone
Style/medium: premium editorial product photography with subtle photoreal 3D UI overlays, realistic materials, not cartoon
Composition/framing: centered square composition, large readable subject, generous padding, no cropped edges, smartphone angled like a hero camera
Lighting/mood: soft daylight, playful confidence, "this old thing is about to become a star"
Color palette: warm cream surfaces, orange accents, amber sparkle accent, deep brown details
Constraints: zero text in any language; no letters, no numbers, no pseudo-text, no logos, no brand names, no OLX logo, no watermark, no busy background
Avoid: cartoon illustration, stock-photo look, cluttered marketplace stall, real app screenshots with text, meme-style humor
```

### 2. Onboarding Step 2: AI Writes The Ad

Suggested filename: `img_seller_onboarding_ai.webp`

Target: square, 2048x2048 source, displayed around 160-176 dp.

```text
Use case: photorealistic-natural
Asset type: mobile onboarding illustration for the AI generation step
Primary request: show AI turning messy seller effort into a neat premium listing, like a satisfying magic trick
Scene/backdrop: warm cream background with subtle orange and amber glow, tiny floating spark trails
Subject: product photo cards enter one side looking casual, then transform into a perfectly organized listing card stack with price tag shapes, category chips, check marks, and a polished sparkle burst
Style/medium: premium editorial product photo composite with photorealistic floating UI cards, realistic shadows, not cartoon
Composition/framing: centered square composition, clear left-to-right transformation, readable at small mobile size
Lighting/mood: clever, efficient, polished, quietly funny, like the app just did the boring work with a wink
Color palette: SellSnap orange, amber, cream, deep warm brown details, tiny success green accents
Constraints: zero text in any language; all UI cards must be blank abstract bars or empty shapes; no fake UI words, no logos, no brand names, no watermark
Avoid: cartoon illustration, robot characters, sci-fi blue interface, dense tiny controls, corporate AI cliches, Polish-looking pseudo text
```

### 3. Onboarding Step 3: Publish Successfully

Suggested filename: `img_seller_onboarding_publish.webp`

Target: square, 2048x2048 source, displayed around 160-176 dp.

```text
Use case: photorealistic-natural
Asset type: mobile onboarding illustration for publishing a listing
Primary request: show a successful marketplace listing publication with a polished little victory moment
Scene/backdrop: clean warm cream background with tasteful confetti, soft orange glow
Subject: a smartphone with an abstract listing card receiving a big green success check stamp; a small publish arrow launches upward while product photo cards line up neatly behind it
Style/medium: premium editorial photo composite with photorealistic UI objects and natural shadows, not cartoon
Composition/framing: centered square composition, success check clearly visible, generous padding, confetti kept around the edges
Lighting/mood: accomplished, quick, celebratory, elegant, slightly cheeky
Color palette: success green, SellSnap orange, amber confetti, warm cream
Constraints: zero text in any language; no app names, no listing words, no labels, no numbers, no OLX logo, no brand names, no watermark
Avoid: cartoon illustration, party-heavy scene, fireworks, sale/sold text inside the image, visual noise, Polish-looking pseudo text
```

### 4. Seller Landing / Auth Welcome

Suggested filename: `img_seller_landing_welcome.webp`

Target: portrait or square, 2048x2048 source. Use if replacing the current logo-only welcome with richer art.

```text
Use case: photorealistic-natural
Asset type: welcome/auth hero image for a seller listing app
Primary request: show a trustworthy seller preparing an item online, but make the scene feel charming and aspirational
Scene/backdrop: modern apartment or clean desk, warm daylight, Ukraine/Eastern Europe feel without landmarks
Subject: hands arranging a small sellable item beside a smartphone camera view, a clean shipping box, and a few elegant floating photo/listing cards; the item should look like it is getting a tiny premium presentation
Style/medium: premium editorial lifestyle photo with tasteful magical-realism overlays
Composition/framing: simple centered subject, enough negative space for app UI around it when cropped
Lighting/mood: warm, practical, trustworthy, fancy but not luxury-snobby
Color palette: warm neutrals with subtle orange accents, cream surfaces, small green success cue
Constraints: zero text in any language; phone screen and floating cards must be blank abstract blocks only; no logos, no brand names, no watermark
Avoid: obvious stock model posing, luxury shopping scene, cluttered room, fake marketplace branding, Polish-looking pseudo text
```

### 5. Generate Ad Header

Suggested filename: `img_seller_generate_header.webp`

Target: landscape, 2048x1152 source. Use as a banner candidate for `GenerateAdScreen`.

```text
Use case: photorealistic-natural
Asset type: wide hero/banner image for the new listing screen
Primary request: show the app idea, turning ordinary item photos into a fancy marketplace listing in seconds
Scene/backdrop: clean tabletop flat lay with warm cream surface, like a compact creative studio
Subject: a smartphone photographing two or three second-hand items for sale, such as sneakers, a jacket, and a small lamp; the items are arranged like a playful premium catalog shoot, with translucent listing cards and price-tag shapes floating above them
Style/medium: premium editorial product/lifestyle photo with subtle 3D UI overlays
Composition/framing: wide horizontal frame, central phone and items, safe empty margins for cropping
Lighting/mood: bright, efficient, practical, charming, a bit showy in a tasteful way
Color palette: warm neutrals, SellSnap orange accent, natural shadows, amber highlights
Constraints: zero text in any language; translucent listing cards and price tags must be blank shapes only; no logos, no brand names, no watermark
Avoid: cartoon illustration, messy garage-sale scene, dark moody lighting, AI robot imagery, corporate SaaS hero cliches, Polish-looking pseudo text
```

### 6. Empty Photo Placeholder

Suggested filename: `img_seller_empty_photo.webp`

Target: square, 1024x1024 source. Use if replacing camera-icon fallbacks in empty carousel or missing thumbnail states.

```text
Use case: product-mockup
Asset type: empty photo/gallery placeholder for a seller listing app
Primary request: show a neutral add-photo state without using text, but make it feel inviting instead of empty
Scene/backdrop: warm cream background with a soft stage-like glow
Subject: a soft camera lens and empty rounded photo frame, with one small orange plus badge, subtle product silhouettes waiting in the background, and a tiny sparkle suggesting "add the first shot"
Style/medium: photorealistic 3D product-style object, realistic materials and soft studio lighting, not cartoon
Composition/framing: centered square icon-like composition, high contrast at small size
Lighting/mood: calm, inviting, clear, playful without being childish
Color palette: warm cream, SellSnap orange, deep brown details, small amber accent
Constraints: zero text in any language; no labels, no letters, no numbers, no logos, no watermark, simple silhouette
Avoid: cartoon illustration, real camera brand marks, complex scene, low-contrast pale icon, sad empty-state feeling
```

### 7. AI Processing Visual

Suggested filename: `img_seller_processing.webp`

Target: square, 2048x2048 source. Use only if replacing the current pulsing sparkle composition with an image.

```text
Use case: photorealistic-natural
Asset type: processing-state hero illustration for AI ad generation
Primary request: show AI analysis in progress for product photos as a stylish little assembly line of useful magic
Scene/backdrop: warm cream background with soft orange radial glow and subtle motion-like trails
Subject: product photo cards orbiting into a clean listing card, with abstract price tag, category chip, sparkle shapes, and a tiny green check waiting at the end like the finish line
Style/medium: premium editorial product photo composite with photorealistic floating cards, no character, no robot, not cartoon
Composition/framing: centered circular motion, clear at small mobile size, premium kinetic feel
Lighting/mood: smart, calm, fast, delightful, like the boring form fields are folding themselves
Color palette: SellSnap orange, amber, cream, tiny green completion accent
Constraints: zero text in any language; cards must be blank abstract UI shapes only; no logos, no brand names, no watermark
Avoid: cartoon illustration, blue sci-fi HUD, dense code screens, robot face, busy neon effects, Polish-looking pseudo text
```

### 8. Publish Success Hero

Suggested filename: `img_seller_publish_success.webp`

Target: square, 2048x2048 source. Use if replacing or augmenting the success check/confetti hero.

```text
Use case: photorealistic-natural
Asset type: publish-success hero illustration for a seller app
Primary request: show a listing successfully published as a polished victory screen image
Scene/backdrop: warm cream background with subtle confetti pieces and a soft success glow
Subject: a smartphone with an abstract listing card, green success check badge, tiny product thumbnail card, upload/share arrow, and neatly arranged celebratory shapes around the edges
Style/medium: premium editorial product photo composite with photorealistic UI cards, realistic shadows, not cartoon
Composition/framing: centered square, strong success badge, minimal surrounding elements, enough empty space for UI
Lighting/mood: celebratory, clean, trustworthy, a bit triumphant
Color palette: success green, SellSnap orange, amber, cream
Constraints: zero text in any language; listing card must be blank abstract blocks only; no marketplace logos, no brand names, no watermark
Avoid: cartoon illustration, fireworks, sale text, confetti covering the subject, over-the-top party visuals, Polish-looking pseudo text
```

## Optional Demo Product Set

Use these only for previews, demo data, or replacing prototype placeholder product shots. Runtime production listings should keep using user-uploaded photos.

Common constraints for every demo product image:

```text
Use case: product-mockup
Asset type: square product photo for app preview/demo
Style/medium: realistic marketplace product photography with a tasteful premium twist, clean but not luxury
Composition/framing: item centered, fills 70-80 percent of frame, complete object visible, square crop
Lighting/mood: soft natural daylight, honest resale condition, slightly playful catalog charm
Color palette: warm neutral background, subtle orange accent object allowed
Constraints: zero text in any language; no brand logos, no watermarks, no people, no hands, no readable tags or labels
Avoid: showroom luxury styling, heavy shadows, clutter, fake labels, cartoon rendering
```

Individual primary requests:

1. `demo_product_sneakers.webp`: a pair of clean used white and gray sneakers with light outsole wear, photographed from a three-quarter angle, sitting like they are ready for their comeback.
2. `demo_product_smartphone.webp`: a modern black smartphone shown front and back with a simple charging cable, clean neutral surface, no logos and a completely blank dark screen, arranged like a tiny tech showcase.
3. `demo_product_jacket.webp`: a neutral beige casual jacket on a simple hanger, lightly used but clean, full garment visible, styled like a friendly boutique listing.
4. `demo_product_chair.webp`: a compact wooden chair with a fabric seat, clean second-hand condition, photographed at a slight angle, with warm natural shadows.
5. `demo_product_bag.webp`: a small brown shoulder bag or backpack, clean used condition, straps visible, no logo, arranged with a little premium travel-shop energy.
6. `demo_product_lamp.webp`: a small warm table lamp with a simple shade, unplugged cord visible, neutral background, glowing softly like the most confident item in the room.

## Copy And Text Stub Notes

These are not image prompts, but they affect the same seller polish pass:

- `GenerateAdScreen.kt` hardcodes `Add 1-$MAX_PHOTOS photos. AI will handle the rest.` while resources also contain `new_listing_subtitle` with 1-8 photos. Current `MAX_PHOTOS` is 5.
- `GenerateAdScreen.kt` account header still shows generic `Welcome`; SIR-40 tracks real account info/logout.
- Seller camera permission and iOS settings copy now use seller/SellSnap wording.
- `SellerAuthViewModel.kt` sends terms/privacy clicks to malformed `https:google.com` placeholders.
- The design prototype uses "Torg/torh" placeholder marketplace naming, while the implementation is OLX. Keep generated images brand-neutral and keep final marketplace wording in resources.

## Integration Notes For Later

When final files are available:

- Add runtime assets under `composeApp/src/commonMain/composeResources/drawable/`.
- Prefer `.webp` for opaque art. Use `.png` only if transparency is required.
- Wire onboarding assets in `OnboardingScreen.kt` by replacing `OnboardingStepIconContent` internals or by extending `OnboardingItem` with drawable resources.
- Replace preview-only remote URLs in `PreviewAdScreen.kt` with local demo resources only if those previews need stable local assets.
- Keep all localized text in `composeResources/values/strings.xml` and `values-uk/strings.xml`; do not rely on generated images for text.
