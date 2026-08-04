# SellSnap — App Store screenshot upload guide

Upload the files in the order shown. Filenames are already numbered `01…07` in flow order — keep that order in App Store Connect.

## 1. What to upload where

| Folder | App Store Connect slot | Pixel size | Localizations present |
| --- | --- | --- | --- |
| `iphone-6.9/` | iPhone 6.9-inch display | 1290 × 2796 (portrait) | `bg`, `pl`, `pt`, `ro` — 7 files each |
| `ipad-13/` | iPad 13-inch display | 2752 × 2064 (landscape) | `bg`, `pl`, `pt`, `ro`, `ua` — 6 files each |

Locale folder → App Store Connect localization:

- `bg` = Bulgarian
- `pl` = Polish
- `pt` = Portuguese (Portugal)
- `ro` = Romanian
- `ua` = Ukrainian

## 2. Known gaps

- **No Ukrainian iPhone set.** `iphone-6.9/` contains `bg`, `pl`, `pt`, `ro`. The underlying Ukrainian iPhone app screenshots were never captured (`store/captures/iphone/ua/` holds only `auth.png`), so there is nothing to render from. Romanian was captured on 2026-08-04 and is now complete.
- **iPad has 6 shots, iPhone has 7.** The iPhone-only extra is `04-ai-steps.jpg`; the iPad set has no equivalent scene.
- **`ipad-13/ua/` is a bonus regeneration.** The Ukrainian listing is already shipped, so re-uploading it is optional.
- **Portuguese `02-add-photos.jpg` has a clipped header.** The underlying pt capture sits at a different scroll offset than pl/bg, so the "Adicione 1 a 8 fotos" heading is cut off and the status bar overlaps the photo thumbnails. `pt/01-welcome.jpg` likewise lost its hero image. Both are still on-message and legible — upload them, or re-capture the Portuguese iPhone screenshots first if you want them pixel-clean.

## 3. Polish

### iPhone 6.9-inch — `iphone-6.9/pl/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Sprzedawaj / szybciej z AI | Zdjęcie na wejściu, ogłoszenie na wyjściu | Zdjęcie · AI | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Nowe ogłoszenie / w minutę | Dodaj zdjęcie, resztę podpowie AI | Aparat · Galeria | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | AI pisze tekst / gdy Ty czekasz | Tytuł, opis i cena bez wysiłku | Tytuł · Opis | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-ai-steps.jpg` | Twórz ogłoszenia / szybciej | AI pomoże z tekstem, ceną i szczegółami | Zdjęcie · AI | AI analysing — steps ticked (`analysing_progress`, light) |
| 5 | `05-review.jpg` | Sprawdź wszystko / przed publikacją | Edytuj tekst, cenę i szczegóły | Cena · Szczegóły | Result — title + description (`result_top`, light) |
| 6 | `06-details.jpg` | Opublikuj / jednym dotknięciem | Gotowe ogłoszenie bez zbędnej pracy | Opis · Publikacja | Result — details + publish button (`result_bottom`, dark) |
| 7 | `07-confirm.jpg` | Publikuj / bez obaw | Ostateczna weryfikacja przed OLX | Weryfikacja · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

### iPad 13-inch — `ipad-13/pl/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Sprzedawaj / szybciej z AI | Zdjęcie na wejściu, ogłoszenie na wyjściu | Zdjęcie · AI | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Nowe ogłoszenie / w minutę | Dodaj zdjęcie, resztę podpowie AI | Aparat · Galeria | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | AI pisze tekst / gdy Ty czekasz | Tytuł, opis i cena bez wysiłku | Tytuł · Opis | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-review.jpg` | Sprawdź wszystko / przed publikacją | Edytuj tekst, cenę i szczegóły | Cena · Szczegóły | Result — title + description (`result_top`, dark) |
| 5 | `05-details.jpg` | Opublikuj / jednym dotknięciem | Gotowe ogłoszenie bez zbędnej pracy | Opis · Publikacja | Result — details + publish button (`result_bottom`, light) |
| 6 | `06-confirm.jpg` | Publikuj / bez obaw | Ostateczna weryfikacja przed OLX | Weryfikacja · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

## 4. Romanian

### iPhone 6.9-inch — `iphone-6.9/ro/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Vinde mai repede / cu AI | Foto la intrare, anunț la ieșire | Foto · AI | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Anunț nou / într-un minut | Adaugă o poză, AI se ocupă de rest | Cameră · Galerie | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | AI scrie textul / cât aștepți | Titlu, descriere și preț — fără efort | Titlu · Descriere | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-ai-steps.jpg` | Creează anunțuri / mai repede | AI te ajută cu textul, prețul și detaliile | Foto · AI | AI analysing — steps ticked (`analysing_progress`, light) |
| 5 | `05-review.jpg` | Verifică totul / înainte de publicare | Editează textul, prețul și detaliile | Preț · Detalii | Result — title + description (`result_top`, light) |
| 6 | `06-details.jpg` | Publică / dintr-o atingere | Anunț gata fără muncă în plus | Descriere · Publicare | Result — details + publish button (`result_bottom`, dark) |
| 7 | `07-confirm.jpg` | Publică / fără ezitare | Verificare finală înainte de OLX | Verificare · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

### iPad 13-inch — `ipad-13/ro/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Vinde mai repede / cu AI | Foto la intrare, anunț la ieșire | Foto · AI | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Anunț nou / într-un minut | Adaugă o poză, AI se ocupă de rest | Cameră · Galerie | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | AI scrie textul / cât aștepți | Titlu, descriere și preț — fără efort | Titlu · Descriere | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-review.jpg` | Verifică totul / înainte de publicare | Editează textul, prețul și detaliile | Preț · Detalii | Result — title + description (`result_top`, dark) |
| 5 | `05-details.jpg` | Publică / dintr-o atingere | Anunț gata fără muncă în plus | Descriere · Publicare | Result — details + publish button (`result_bottom`, light) |
| 6 | `06-confirm.jpg` | Publică / fără ezitare | Verificare finală înainte de OLX | Verificare · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

## 5. Bulgarian

### iPhone 6.9-inch — `iphone-6.9/bg/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Продавайте / по-бързо с AI | Снимка на входа, обява на изхода | Снимка · AI | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Нова обява / за минута | Добавете снимка, AI прави останалото | Камера · Галерия | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | AI пише текста / докато чакате | Заглавие, описание и цена без усилие | Заглавие · Описание | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-ai-steps.jpg` | Създавайте обяви / по-бързо | AI помага с текст, цена и детайли | Снимка · AI | AI analysing — steps ticked (`analysing_progress`, light) |
| 5 | `05-review.jpg` | Проверете всичко / преди публикуване | Редактирайте текст, цена и детайли | Цена · Детайли | Result — title + description (`result_top`, light) |
| 6 | `06-details.jpg` | Публикувайте / с едно докосване | Готова обява без излишна работа | Описание · Публикация | Result — details + publish button (`result_bottom`, dark) |
| 7 | `07-confirm.jpg` | Публикувайте / без съмнения | Финална проверка преди OLX | Проверка · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

### iPad 13-inch — `ipad-13/bg/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Продавайте / по-бързо с AI | Снимка на входа, обява на изхода | Снимка · AI | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Нова обява / за минута | Добавете снимка, AI прави останалото | Камера · Галерия | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | AI пише текста / докато чакате | Заглавие, описание и цена без усилие | Заглавие · Описание | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-review.jpg` | Проверете всичко / преди публикуване | Редактирайте текст, цена и детайли | Цена · Детайли | Result — title + description (`result_top`, dark) |
| 5 | `05-details.jpg` | Публикувайте / с едно докосване | Готова обява без излишна работа | Описание · Публикация | Result — details + publish button (`result_bottom`, light) |
| 6 | `06-confirm.jpg` | Публикувайте / без съмнения | Финална проверка преди OLX | Проверка · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

## 6. Portuguese (Portugal)

### iPhone 6.9-inch — `iphone-6.9/pt/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Venda mais rápido / com IA | Foto na entrada, anúncio na saída | Foto · IA | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Novo anúncio / num minuto | Adicione uma foto, a IA faz o resto | Câmara · Galeria | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | A IA escreve / enquanto espera | Título, descrição e preço sem esforço | Título · Descrição | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-ai-steps.jpg` | Crie anúncios / mais rápido | A IA ajuda com texto, preço e detalhes | Foto · IA | AI analysing — steps ticked (`analysing_progress`, light) |
| 5 | `05-review.jpg` | Reveja tudo / antes de publicar | Edite texto, preço e detalhes | Preço · Detalhes | Result — title + description (`result_top`, light) |
| 6 | `06-details.jpg` | Publique / com um toque | Anúncio pronto sem trabalho extra | Descrição · Publicar | Result — details + publish button (`result_bottom`, dark) |
| 7 | `07-confirm.jpg` | Publique / com confiança | Verificação final antes do OLX | Verificação · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

### iPad 13-inch — `ipad-13/pt/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Venda mais rápido / com IA | Foto na entrada, anúncio na saída | Foto · IA | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Novo anúncio / num minuto | Adicione uma foto, a IA faz o resto | Câmara · Galeria | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | A IA escreve / enquanto espera | Título, descrição e preço sem esforço | Título · Descrição | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-review.jpg` | Reveja tudo / antes de publicar | Edite texto, preço e detalhes | Preço · Detalhes | Result — title + description (`result_top`, dark) |
| 5 | `05-details.jpg` | Publique / com um toque | Anúncio pronto sem trabalho extra | Descrição · Publicar | Result — details + publish button (`result_bottom`, light) |
| 6 | `06-confirm.jpg` | Publique / com confiança | Verificação final antes do OLX | Verificação · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

## 7. Ukrainian

Already-shipped listing — re-uploading is optional. iPad only; there is no regenerated Ukrainian iPhone set in this folder.

### iPad 13-inch — `ipad-13/ua/`

| # | File | Headline (on image) | Sub-line (on image) | Chips | App screen shown |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-welcome.jpg` | Продавайте / швидше з AI | Фото на вході, оголошення на виході | Фото · AI | Welcome / sign-in (`auth`) |
| 2 | `02-add-photos.jpg` | Нове оголошення / за хвилину | Додайте фото, решту підкаже AI | Камера · Галерея | New listing — add photos (`generate_ad_top`, light) |
| 3 | `03-ai-writes.jpg` | AI пише текст / поки ви чекаєте | Назва, опис і ціна без рутини | Назва · Опис | AI analysing — starting (`analysing_start`, dark) |
| 4 | `04-review.jpg` | Перевірте все / перед запуском | Редагуйте текст, ціну й деталі | Ціна · Деталі | Result — title + description (`result_top`, dark) |
| 5 | `05-details.jpg` | Опублікуйте / в один тап | Готове оголошення без зайвої роботи | Опис · Публікація | Result — details + publish button (`result_bottom`, light) |
| 6 | `06-confirm.jpg` | Публікуйте / без сумнівів | Фінальна перевірка перед OLX | Перевірка · OLX | Pre-publish confirmation sheet (`result_publish_dialog`, dark) |

## Note

- The headline, sub-line and chip text listed above is **already baked into the image pixels**. There is nothing to paste into App Store Connect for the screenshots themselves — the tables exist only so you can confirm you are uploading the right file to the right localization.
- Headline lines are shown joined with ` / `; on the image they are stacked on separate lines. Chips are shown joined with ` · `; on the image they are two separate pills.
- The repo contains **no app name, subtitle, description or keywords metadata** for the App Store listing, so that listing text is not covered by this document.
