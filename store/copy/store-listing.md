# SellSnap — Store listing text (App Store + Google Play)

Copy-paste text for every listing field, in all 8 app languages. Written for SellSnap 2.2 (build 9).

The per-language **What's New** blocks below match what's pushed to the stores automatically from `.claude/tmp/release-metadata/` (gitignored, regenerated fresh each release): Android reads `android/<locale>/changelogs/<version_code>.txt`, iOS reads `ios/<locale>/release_notes.txt` (App Store) and the same file feeds TestFlight's localized build info. The `sellsnap-release` skill regenerates both those files and this section on every release.

## Field limits cheat-sheet

| Field | App Store | Google Play |
| --- | --- | --- |
| App name / Title | 30 chars | 30 chars |
| Subtitle | 30 chars | — |
| Promotional text | 170 chars | — |
| Short description | — | 80 chars |
| Keywords | 100 chars (comma-separated, no spaces) | — (Play indexes the description) |
| Description | 4000 chars | 4000 chars |
| What's New / Release notes | 4000 chars | 500 chars |

The **Description** and **What's New** blocks below are shared — paste the same text into both stores.

Notes before you paste:

- **No emoji anywhere** — App Store Connect rejects emoji in metadata fields ("This field contains one or more invalid characters"). All blocks below are emoji-free. If a subtitle is rejected for the same reason, the `→` arrow is the culprit — replace it with a comma or "=".
- **App Store Connect has no Bulgarian or Kazakh localization.** Use the bg/kk sections for Google Play only; on the App Store those users see your primary (English) listing.
- **"OLX" in the name/subtitle is a trademark risk.** The name below avoids it; the subtitle and keywords use it, which is common practice but could be challenged in review. If Apple/Google object, drop it from the subtitle first.
- **Kazakh is machine-of-mine quality** — I'd have a native speaker skim it before shipping. The KZ market is also currently disabled in-app (no OLX.kz credentials), so kk is for the in-app language audience, not an active market.

---

## 1. English (primary / fallback)

**App name / Play title** (26): `SellSnap: AI Listing Maker`
**Subtitle** (25): `Photo in, OLX listing out`
**Promotional text** (134): Snap a photo and SellSnap writes the title, description, and price — then publishes straight to OLX. Selling has never been this fast.
**Play short description** (70): Snap a photo — AI writes your OLX listing and publishes it in one tap.
**Keywords** (87): `olx,sell,listing,ad,secondhand,resale,ai,camera,price,marketplace,declutter,classifieds`

**Description:**

```
Got stuff to sell? Snap it. We'll list it.

SellSnap turns a photo into a ready-to-publish OLX listing. Point your camera at anything — the AI writes the title and description and suggests a fair price. Review, tweak what you like, and publish to OLX in one tap.

HOW IT WORKS
1. Snap it — add 1–8 photos of the thing you're selling.
2. AI does the writing — title, description, and a fair price, ready before your coffee cools.
3. Publish in a tap — your listing goes straight to OLX. Buyers, meet your stuff.

WHY SELLSNAP
• No blank-page struggle — the AI writes the whole listing
• Smart price suggestions with a realistic range
• Post straight to OLX — no copy-paste
• Automatic category suggestions and detail fields
• All your OLX ads in one tidy spot
• Add an optional hint (brand, size, condition) for even sharper results
• Light and dark themes

WORKS WHERE YOU SELL
SellSnap supports OLX in Ukraine, Poland, Romania, Bulgaria, and Portugal. Pick your country and listings come out in the right language and currency.

TRY IT WITHOUT AN ACCOUNT
No OLX account? Use guest mode: build the ad, then copy the title, description, and price and paste them into OLX yourself. Connect OLX later to publish in one tap.

YOUR DATA, YOUR CALL
Analytics are off by default and never include your photos or listing content. You can delete your local data anytime — straight from Profile.

From snap to live on OLX in about a minute. Download SellSnap and turn your clutter into cash.

SellSnap is an independent app and is not affiliated with or endorsed by OLX. Publishing requires an OLX account.
```

**What's New (v2.2):**

```
SellSnap 2.2 — stability improvements

• Improved crash reporting, so we catch and fix issues faster
• Security and dependency updates
```

---

## 2. Ukrainian (uk / uk-UA)

**App name / Play title** (23): `SellSnap: AI-оголошення`
**Subtitle** (28): `Фото → готове оголошення OLX`
**Promotional text** (~128): Зробіть фото — SellSnap напише назву, опис і ціну, а потім опублікує оголошення на OLX. Продавати ще ніколи не було так швидко.
**Play short description** (~72): Зробіть фото — AI напише оголошення для OLX і опублікує його в один тап.
**Keywords** (~77): `olx,продати,оголошення,ai,фото,ціна,продаж,барахолка,вживані,речі,маркетплейс`

**Description:**

```
Є що продати? Сфотографуйте — ми створимо оголошення.

SellSnap перетворює фото на готове оголошення для OLX. Наведіть камеру на будь-яку річ — AI напише назву й опис і запропонує справедливу ціну. Перегляньте, підправте за бажанням і опублікуйте на OLX одним тапом.

ЯК ЦЕ ПРАЦЮЄ
1. Сфотографуйте — додайте від 1 до 8 фото речі, яку продаєте.
2. AI пише текст — назва, опис і справедлива ціна будуть готові, поки холоне ваша кава.
3. Опублікуйте одним тапом — оголошення одразу з'являється на OLX.

ЧОМУ SELLSNAP
• Жодних мук із чистим аркушем — AI пише все оголошення за вас
• Розумні підказки ціни з реалістичним діапазоном
• Публікація прямо на OLX — без копіювання
• Автоматичний підбір категорії та полів з деталями
• Усі ваші оголошення OLX в одному місці
• Додайте підказку для AI (бренд, розмір, стан) — і результат буде ще точнішим
• Світла та темна теми

ПРАЦЮЄ ТАМ, ДЕ ВИ ПРОДАЄТЕ
SellSnap підтримує OLX в Україні, Польщі, Румунії, Болгарії та Португалії. Оберіть країну — і оголошення створюватимуться потрібною мовою та у місцевій валюті.

СПРОБУЙТЕ БЕЗ АКАУНТА
Немає акаунта OLX? Скористайтеся гостьовим режимом: створіть оголошення, скопіюйте назву, опис і ціну та вставте їх на OLX самостійно. Підключіть OLX пізніше, щоб публікувати одним тапом.

ВАШІ ДАНІ — ВАШЕ РІШЕННЯ
Аналітика вимкнена за замовчуванням і ніколи не включає ваші фото чи текст оголошень. Локальні дані можна видалити будь-коли — просто у профілі.

Від фото до опублікованого оголошення на OLX — близько хвилини. Завантажуйте SellSnap і перетворюйте зайві речі на гроші.

SellSnap — незалежний застосунок, не афілійований з OLX. Для публікації потрібен акаунт OLX.
```

**What's New (v2.2):**

```
SellSnap 2.2 — покращення стабільності

• Краще звітування про збої — виправляємо проблеми швидше
• Оновлення безпеки та залежностей
```

---

## 3. Polish (pl / pl-PL)

**App name / Play title** (25): `SellSnap: ogłoszenia z AI`
**Subtitle** (27): `Zdjęcie → gotowe ogłoszenie`
**Promotional text** (~138): Zrób zdjęcie — SellSnap napisze tytuł, opis i cenę, a potem opublikuje ogłoszenie na OLX. Sprzedawanie jeszcze nigdy nie było tak szybkie.
**Play short description** (79): Zrób zdjęcie — AI napisze ogłoszenie na OLX i opublikuje je jednym dotknięciem.
**Keywords** (~75): `olx,sprzedaj,ogłoszenie,ai,zdjęcie,cena,sprzedaż,używane,rzeczy,ogłoszenia`

**Description:**

```
Masz coś do sprzedania? Zrób zdjęcie — my przygotujemy ogłoszenie.

SellSnap zamienia zdjęcie w gotowe ogłoszenie na OLX. Wyceluj aparat w dowolną rzecz — AI napisze tytuł i opis oraz zaproponuje uczciwą cenę. Przejrzyj, popraw co chcesz i opublikuj na OLX jednym dotknięciem.

JAK TO DZIAŁA
1. Zrób zdjęcie — dodaj od 1 do 8 zdjęć rzeczy, którą sprzedajesz.
2. AI pisze tekst — tytuł, opis i uczciwa cena będą gotowe, zanim wystygnie Twoja kawa.
3. Opublikuj jednym dotknięciem — ogłoszenie trafia prosto na OLX.

DLACZEGO SELLSNAP
• Koniec walki z pustą kartką — AI pisze całe ogłoszenie za Ciebie
• Inteligentne podpowiedzi cen z realistycznym przedziałem
• Publikacja prosto na OLX — bez kopiowania
• Automatyczne podpowiedzi kategorii i pól ze szczegółami
• Wszystkie Twoje ogłoszenia OLX w jednym miejscu
• Dodaj wskazówkę dla AI (marka, rozmiar, stan) — wynik będzie jeszcze trafniejszy
• Jasny i ciemny motyw

DZIAŁA TAM, GDZIE SPRZEDAJESZ
SellSnap obsługuje OLX w Polsce, Ukrainie, Rumunii, Bułgarii i Portugalii. Wybierz kraj, a ogłoszenia powstaną we właściwym języku i lokalnej walucie.

WYPRÓBUJ BEZ KONTA
Nie masz konta OLX? Skorzystaj z trybu gościa: przygotuj ogłoszenie, skopiuj tytuł, opis i cenę i wklej je na OLX samodzielnie. Połącz konto OLX później, aby publikować jednym dotknięciem.

TWOJE DANE — TWOJA DECYZJA
Analityka jest domyślnie wyłączona i nigdy nie obejmuje Twoich zdjęć ani treści ogłoszeń. Dane lokalne możesz usunąć w każdej chwili — prosto z profilu.

Od zdjęcia do ogłoszenia na OLX w około minutę. Pobierz SellSnap i zamień nieużywane rzeczy w gotówkę.

SellSnap to niezależna aplikacja, niepowiązana z OLX. Do publikacji wymagane jest konto OLX.
```

**What's New (v2.2):**

```
SellSnap 2.2 — poprawki stabilności

• Lepsze raportowanie awarii, dzięki czemu szybciej naprawiamy problemy
• Aktualizacje bezpieczeństwa i zależności
```

---

## 4. Romanian (ro / ro-RO)

**App name / Play title** (24): `SellSnap: anunțuri cu AI`
**Subtitle** (29): `Poză → anunț gata de publicat`
**Promotional text** (~136): Fă o poză — SellSnap scrie titlul, descrierea și prețul, apoi publică anunțul direct pe OLX. Vânzarea n-a fost niciodată atât de rapidă.
**Play short description** (~71): Fă o poză — AI scrie anunțul pentru OLX și îl publică dintr-o atingere.
**Keywords** (~65): `olx,vinde,anunț,ai,poză,preț,vânzare,second hand,lucruri,anunțuri`

**Description:**

```
Ai ceva de vândut? Fă o poză — noi pregătim anunțul.

SellSnap transformă o poză într-un anunț gata de publicat pe OLX. Îndreaptă camera spre orice lucru — AI scrie titlul și descrierea și propune un preț corect. Verifică, ajustează ce vrei și publică pe OLX dintr-o atingere.

CUM FUNCȚIONEAZĂ
1. Fă o poză — adaugă 1–8 poze cu lucrul pe care îl vinzi.
2. AI scrie textul — titlul, descrierea și un preț corect sunt gata înainte să ți se răcească cafeaua.
3. Publică dintr-o atingere — anunțul ajunge direct pe OLX.

DE CE SELLSNAP
• Fără chinul paginii goale — AI scrie tot anunțul pentru tine
• Sugestii inteligente de preț, cu un interval realist
• Publicare direct pe OLX — fără copy-paste
• Sugestii automate de categorie și câmpuri cu detalii
• Toate anunțurile tale OLX într-un singur loc
• Adaugă un indiciu pentru AI (marcă, mărime, stare) — rezultatul va fi și mai precis
• Temă luminoasă și întunecată

FUNCȚIONEAZĂ ACOLO UNDE VINZI
SellSnap acceptă OLX în România, Ucraina, Polonia, Bulgaria și Portugalia. Alege țara, iar anunțurile sunt generate în limba potrivită și în moneda locală.

ÎNCEARCĂ FĂRĂ CONT
Nu ai cont OLX? Folosește modul de oaspete: creează anunțul, copiază titlul, descrierea și prețul și lipește-le singur pe OLX. Conectează OLX mai târziu ca să publici dintr-o atingere.

DATELE TALE, DECIZIA TA
Analiticele sunt dezactivate implicit și nu includ niciodată pozele sau textul anunțurilor tale. Poți șterge datele locale oricând — direct din profil.

De la poză la anunț publicat pe OLX în aproximativ un minut. Descarcă SellSnap și transformă lucrurile nefolosite în bani.

SellSnap este o aplicație independentă, neafiliată cu OLX. Publicarea necesită un cont OLX.
```

**What's New (v2.2):**

```
SellSnap 2.2 — îmbunătățiri de stabilitate

• Raportare a erorilor îmbunătățită, ca să rezolvăm problemele mai repede
• Actualizări de securitate și de dependențe
```

---

## 5. Bulgarian (bg-BG — **Google Play only**, App Store has no Bulgarian localization)

**Play title** (20): `SellSnap: обяви с AI`
**Play short description** (~73): Направете снимка — AI пише обявата за OLX и я публикува с едно докосване.
**(App Store fallback subtitle, if ever available)** (28): `Снимка → готова обява за OLX`
**(App Store fallback promo)** (~131): Направете снимка — SellSnap пише заглавието, описанието и цената, после публикува обявата в OLX. Продаването никога не е било толкова бързо.
**(App Store fallback keywords)** (~66): `olx,продай,обява,ai,снимка,цена,продажба,втора употреба,вещи,обяви`

**Description:**

```
Имате какво да продадете? Снимайте — ние ще създадем обявата.

SellSnap превръща снимката в готова обява за OLX. Насочете камерата към каквото и да е — AI пише заглавието и описанието и предлага справедлива цена. Прегледайте, редактирайте каквото искате и публикувайте в OLX с едно докосване.

КАК РАБОТИ
1. Снимайте — добавете от 1 до 8 снимки на вещта, която продавате.
2. AI пише текста — заглавие, описание и справедлива цена, готови преди кафето ви да изстине.
3. Публикувайте с едно докосване — обявата отива направо в OLX.

ЗАЩО SELLSNAP
• Без мъки пред празния лист — AI пише цялата обява вместо вас
• Умни ценови предложения с реалистичен диапазон
• Публикуване директно в OLX — без копиране
• Автоматични предложения за категория и полета с детайли
• Всичките ви обяви в OLX на едно място
• Добавете подсказка за AI (марка, размер, състояние) — резултатът ще е още по-точен
• Светла и тъмна тема

РАБОТИ ТАМ, КЪДЕТО ПРОДАВАТЕ
SellSnap поддържа OLX в България, Украйна, Полша, Румъния и Португалия. Изберете държава и обявите се създават на правилния език и в местната валута.

ОПИТАЙТЕ БЕЗ АКАУНТ
Нямате акаунт в OLX? Използвайте гост режим: създайте обявата, копирайте заглавието, описанието и цената и ги поставете в OLX сами. Свържете OLX по-късно, за да публикувате с едно докосване.

ВАШИТЕ ДАННИ — ВАШЕТО РЕШЕНИЕ
Аналитиката е изключена по подразбиране и никога не включва вашите снимки или текста на обявите. Можете да изтриете локалните данни по всяко време — направо от профила.

От снимка до публикувана обява в OLX за около минута. Изтеглете SellSnap и превърнете ненужните вещи в пари.

SellSnap е независимо приложение и не е свързано с OLX. За публикуване е необходим акаунт в OLX.
```

**What's New (v2.2):**

```
SellSnap 2.2 — подобрения в стабилността

• По-добро отчитане на сривове — отстраняваме проблемите по-бързо
• Актуализации на сигурността и зависимостите
```

---

## 6. Portuguese — Portugal (pt-PT)

**App name / Play title** (25): `SellSnap: anúncios com IA`
**Subtitle** (25): `Da foto ao anúncio no OLX`
**Promotional text** (~137): Tire uma foto — o SellSnap escreve o título, a descrição e o preço, e publica o anúncio diretamente no OLX. Vender nunca foi tão rápido.
**Play short description** (~72): Tire uma foto — a IA escreve o anúncio e publica-o no OLX com um toque.
**Keywords** (~71): `olx,vender,anúncio,ia,foto,preço,venda,usado,segunda mão,classificados`

**Description:**

```
Tem coisas para vender? Tire uma foto — nós tratamos do anúncio.

O SellSnap transforma uma foto num anúncio pronto a publicar no OLX. Aponte a câmara para qualquer coisa — a IA escreve o título e a descrição e sugere um preço justo. Reveja, ajuste o que quiser e publique no OLX com um toque.

COMO FUNCIONA
1. Tire uma foto — adicione 1 a 8 fotos do que está a vender.
2. A IA escreve o texto — título, descrição e um preço justo, prontos antes de o seu café arrefecer.
3. Publique com um toque — o anúncio vai direto para o OLX.

PORQUÊ O SELLSNAP
• Sem sofrer com a página em branco — a IA escreve o anúncio todo por si
• Sugestões de preço inteligentes, com um intervalo realista
• Publicação direta no OLX — sem copy-paste
• Sugestões automáticas de categoria e campos de detalhes
• Todos os seus anúncios do OLX num só lugar
• Adicione uma dica para a IA (marca, tamanho, estado) — o resultado fica ainda melhor
• Tema claro e escuro

FUNCIONA ONDE VENDE
O SellSnap suporta o OLX em Portugal, na Ucrânia, na Polónia, na Roménia e na Bulgária. Escolha o país e os anúncios são criados no idioma certo e na moeda local.

EXPERIMENTE SEM CONTA
Não tem conta OLX? Use o modo convidado: crie o anúncio, copie o título, a descrição e o preço e cole-os no OLX. Ligue a conta OLX mais tarde para publicar com um toque.

OS SEUS DADOS, A SUA DECISÃO
As estatísticas de utilização estão desativadas por predefinição e nunca incluem as suas fotos nem o texto dos anúncios. Pode apagar os dados locais a qualquer momento — diretamente no perfil.

Da foto ao anúncio publicado no OLX em cerca de um minuto. Descarregue o SellSnap e transforme o que não usa em dinheiro.

O SellSnap é uma aplicação independente, sem qualquer afiliação com o OLX. Para publicar é necessária uma conta OLX.
```

**What's New (v2.2):**

```
SellSnap 2.2 — melhorias de estabilidade

• Relatórios de falhas melhorados, para resolvermos problemas mais depressa
• Atualizações de segurança e de dependências
```

---

## 7. Russian (ru / ru-RU)

**App name / Play title** (25): `SellSnap: объявления с AI`
**Subtitle** (25): `Фото → готовое объявление`
**Promotional text** (~147): Сделайте фото — SellSnap напишет заголовок, описание и цену, а затем опубликует объявление на OLX. Продавать ещё никогда не было так просто и быстро.
**Play short description** (~74): Сделайте фото — AI напишет объявление для OLX и опубликует его в один тап.
**Keywords** (~69): `olx,продать,объявление,ai,фото,цена,продажа,барахолка,вещи,объявления`

**Description:**

```
Есть что продать? Сфотографируйте — мы создадим объявление.

SellSnap превращает фото в готовое объявление для OLX. Наведите камеру на любую вещь — AI напишет заголовок и описание и предложит справедливую цену. Просмотрите, поправьте что хотите и опубликуйте на OLX одним касанием.

КАК ЭТО РАБОТАЕТ
1. Сфотографируйте — добавьте от 1 до 8 фото вещи, которую продаёте.
2. AI пишет текст — заголовок, описание и справедливая цена будут готовы, пока остывает ваш кофе.
3. Опубликуйте одним касанием — объявление сразу попадает на OLX.

ПОЧЕМУ SELLSNAP
• Никаких мук с чистым листом — AI пишет всё объявление за вас
• Умные подсказки цены с реалистичным диапазоном
• Публикация прямо на OLX — без копирования
• Автоматический подбор категории и полей с деталями
• Все ваши объявления OLX в одном месте
• Добавьте подсказку для AI (бренд, размер, состояние) — результат будет ещё точнее
• Светлая и тёмная темы

РАБОТАЕТ ТАМ, ГДЕ ВЫ ПРОДАЁТЕ
SellSnap поддерживает OLX в Украине, Польше, Румынии, Болгарии и Португалии. Выберите страну — объявления будут создаваться на нужном языке и в местной валюте.

ПОПРОБУЙТЕ БЕЗ АККАУНТА
Нет аккаунта OLX? Используйте гостевой режим: создайте объявление, скопируйте заголовок, описание и цену и вставьте их на OLX самостоятельно. Подключите OLX позже, чтобы публиковать одним касанием.

ВАШИ ДАННЫЕ — ВАШЕ РЕШЕНИЕ
Аналитика выключена по умолчанию и никогда не включает ваши фото или текст объявлений. Локальные данные можно удалить в любой момент — прямо в профиле.

От фото до опубликованного объявления на OLX — около минуты. Скачайте SellSnap и превратите ненужные вещи в деньги.

SellSnap — независимое приложение, не аффилированное с OLX. Для публикации нужен аккаунт OLX.
```

**What's New (v2.2):**

```
SellSnap 2.2 — улучшения стабильности

• Улучшенные отчёты о сбоях — исправляем проблемы быстрее
• Обновления безопасности и зависимостей
```

---

## 8. Kazakh (kk-KZ — **Google Play only**, App Store has no Kazakh localization)

**Play title** (25): `SellSnap: AI хабарландыру`
**Play short description** (~70): Фото түсіріңіз — AI OLX хабарландыруын жазып, бір түртумен жариялайды.
**(Keywords, if ever needed)** (~58): `olx,сату,хабарландыру,ai,фото,баға,сатылым,заттар,жарнама`

**Description:**

```
Сатқыңыз келетін зат бар ма? Суретке түсіріңіз — хабарландыруды біз дайындаймыз.

SellSnap фотоны OLX-ке жариялауға дайын хабарландыруға айналдырады. Камераны кез келген затқа бағыттаңыз — AI атауы мен сипаттамасын жазып, әділ баға ұсынады. Қарап шығыңыз, қалағаныңызды өзгертіңіз және OLX-ке бір түртумен жариялаңыз.

ҚАЛАЙ ЖҰМЫС ІСТЕЙДІ
1. Суретке түсіріңіз — сатып жатқан затыңыздың 1–8 фотосын қосыңыз.
2. Мәтінді AI жазады — атауы, сипаттамасы және әділ баға кофеңіз суығанша дайын болады.
3. Бір түртумен жариялаңыз — хабарландыру бірден OLX-ке шығады.

НЕГЕ SELLSNAP
• Бос парақпен әуре болмайсыз — бүкіл хабарландыруды AI жазады
• Шынайы диапазонмен ақылды баға ұсыныстары
• Тікелей OLX-ке жариялау — көшірудің қажеті жоқ
• Санат пен толық мәліметтер өрістерін автоматты ұсыну
• Барлық OLX хабарландыруларыңыз бір жерде
• AI-ға қосымша дерек беріңіз (бренд, өлшем, күйі) — нәтиже одан да дәл болады
• Ашық және қараңғы тақырып

СІЗ САТАТЫН ЖЕРДЕ ЖҰМЫС ІСТЕЙДІ
SellSnap Украина, Польша, Румыния, Болгария және Португалиядағы OLX-ті қолдайды. Елді таңдаңыз — хабарландырулар тиісті тілде және жергілікті валютада жасалады.

АККАУНТСЫЗ БАЙҚАП КӨРІҢІЗ
OLX аккаунтыңыз жоқ па? Қонақ режимін пайдаланыңыз: хабарландыруды жасап, атауын, сипаттамасын және бағасын көшіріп, OLX-ке өзіңіз қойыңыз. Бір түртумен жариялау үшін OLX-ті кейін қосыңыз.

ДЕРЕКТЕРІҢІЗ — ӨЗ ШЕШІМІҢІЗ
Аналитика әдепкі бойынша өшірулі және ешқашан фотоларыңыз бен хабарландыру мәтінін қамтымайды. Жергілікті деректерді кез келген уақытта профильден өшіре аласыз.

Фотодан OLX-те жарияланған хабарландыруға дейін — шамамен бір минут. SellSnap-ты жүктеп алып, керек емес заттарды ақшаға айналдырыңыз.

SellSnap — OLX-пен байланысы жоқ тәуелсіз қосымша. Жариялау үшін OLX аккаунты қажет.
```

**What's New (v2.2):**

```
SellSnap 2.2 — тұрақтылықты жақсарту

• Ақаулар туралы есеп беру жақсарды — мәселелерді жылдамырақ шешеміз
• Қауіпсіздік пен тәуелділіктерге жаңартулар
```
