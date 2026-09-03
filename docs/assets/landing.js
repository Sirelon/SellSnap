(function () {
  const LANG_KEY = "sellsnap-lang";
  const THEME_KEY = "sellsnap-theme";
  const SUPPORTED_LANGS = ["uk", "en", "bg", "kk", "pl", "pt", "ro"];

  const I18N = {
    uk: {
      page_title: "SellSnap — Сфотографуй. ШІ напише. Публікуй на OLX.",
      page_description: "Фото — на вхід, оголошення — на вихід. ШІ напише назву, опис і ціну, а ви публікуєте на OLX в один тап.",
      theme_system_label: "Системна",
      theme_light_label: "Світла",
      theme_dark_label: "Темна",

      hero_h1: "Сфотографуй. ШІ напише. Публікуй на OLX.",
      hero_sub: "Фото — на вхід, оголошення — на вихід. ШІ напише за вас.",

      store_apple_eyebrow: "Завантажуйте в",
      store_apple_name: "App Store",
      store_google_eyebrow: "Завантажуйте в",
      store_google_name: "Google Play",

      mock_step_1_title: "Сфотографуйте",
      mock_step_2_title: "Готуємо ваше оголошення…",
      mock_step_2_sub: "ШІ робить свою справу…",
      mock_step_3_title: "Опубліковано! 🎉",
      mock_step_3_sub: "Ваше оголошення на OLX.",

      how_it_works: "Як це працює",
      onboarding_step1_title: "Сфотографуйте 📸",
      onboarding_step1_subtitle: "Наведіть, клацніть — готово. Усе, що хочете продати, ми оформимо.",
      onboarding_step2_title: "ШІ напише замість вас ✍️",
      onboarding_step2_subtitle: "Назва, опис і чесна ціна — встигнуть з'явитися, поки кава охолоне.",
      onboarding_step3_title: "Публікація в один тап 🚀",
      onboarding_step3_subtitle: "Один тап — і ви на OLX. Покупці, знайомтеся з вашими речами.",

      why_sellsnap: "Чому SellSnap",
      feature_publish_title: "Публікуйте прямо на OLX — без копіювання",
      feature_publish_desc: "Підключіть акаунт OLX — і ваше оголошення летить туди в один тап.",
      feature_manage_title: "Повний контроль після публікації",
      feature_manage_desc: "Редагуйте, продовжуйте термін, знімайте з публікації чи видаляйте — а ще перегляди й перегляди телефону просто в апці.",
      feature_price_title: "Чесна ціна від ШІ",
      feature_price_desc: "ШІ підкаже діапазон, у якому ваша річ дійсно продається — без здогадок.",
      feature_accounts_title: "Керуйте кількома акаунтами OLX",
      feature_accounts_desc: "Підключіть до 3 акаунтів і перемикайте активний в один тап — зручно для особистого й бізнес-акаунта.",
      feature_share_title: "Починайте прямо з галереї",
      feature_share_desc: "Поділіться фото просто з Галереї — SellSnap підхопить звідти.",
      feature_guest_title: "Спробуйте без акаунта",
      feature_guest_desc: "Створіть оголошення й скопіюйте текст — публікація на OLX вручну теж працює.",

      tips_for_better_photos: "Поради для фото 📷",
      tip_lighting: "Гарне світло = кращий результат",
      tip_angles: "Знімайте з кількох кутів",
      tip_defects: "Не ховайте подряпини — покупці люблять чесність",

      pro_tip_label: "Лайфхак",
      pro_tip_body: "Підключіть OLX — і наступне оголошення вийде у 5 разів швидше.",

      final_cta_h2: "Готові продати швидше?",
      final_cta_sub: "Готово швидше, ніж охолоне кава.",

      footer_tagline: "Фото — на вхід, оголошення — на вихід.",
      footer_privacy: "Політика конфіденційності",
      footer_terms: "Умови обслуговування",
      footer_support: "Підтримка",
      footer_copyright: "© 2026 SellSnap. Усі права захищені.",
    },
    en: {
      page_title: "SellSnap — Snap it. AI writes it. OLX gets it.",
      page_description: "Photo in, listing out. AI writes the title, description and price — you publish to OLX in one tap.",
      theme_system_label: "System",
      theme_light_label: "Light",
      theme_dark_label: "Dark",

      hero_h1: "Snap it. AI writes it. OLX gets it.",
      hero_sub: "Photo in, listing out — AI does the typing.",

      store_apple_eyebrow: "Download on the",
      store_apple_name: "App Store",
      store_google_eyebrow: "Get it on",
      store_google_name: "Google Play",

      mock_step_1_title: "Snap it",
      mock_step_2_title: "Cooking up your ad…",
      mock_step_2_sub: "AI is doing its thing…",
      mock_step_3_title: "Published! 🎉",
      mock_step_3_sub: "Your ad is live on OLX.",

      how_it_works: "How it works",
      onboarding_step1_title: "Snap it 📸",
      onboarding_step1_subtitle: "Point, shoot, done. Anything you want to sell — we'll take it from there.",
      onboarding_step2_title: "AI does the writing ✍️",
      onboarding_step2_subtitle: "A title, a description, and a fair price — ready before your coffee cools.",
      onboarding_step3_title: "Publish in a tap 🚀",
      onboarding_step3_subtitle: "One tap and you're live on OLX. Buyers, meet your stuff.",

      why_sellsnap: "Why SellSnap",
      feature_publish_title: "Post straight to OLX — no copy-paste",
      feature_publish_desc: "Hook up your OLX account and your ad flies straight there in one tap.",
      feature_manage_title: "Full control after it's live",
      feature_manage_desc: "Edit it, extend it, take it down or delete it — plus views and phone reveals, right in the app.",
      feature_price_title: "A fair price from AI",
      feature_price_desc: "AI suggests the range your item actually sells for — no guessing.",
      feature_accounts_title: "Run more than one OLX account",
      feature_accounts_desc: "Connect up to 3 accounts and switch the active one in a tap — handy for a personal and a business account.",
      feature_share_title: "Start from your camera roll",
      feature_share_desc: "Share a photo straight from Photos — SellSnap picks up from there.",
      feature_guest_title: "Try it without an account",
      feature_guest_desc: "Build the ad and copy the text — pasting into OLX yourself works too.",

      tips_for_better_photos: "Photo tips 📷",
      tip_lighting: "Good light = better results",
      tip_angles: "Shoot from a few angles",
      tip_defects: "Don't hide the scratches — buyers love honesty",

      pro_tip_label: "Pro tip",
      pro_tip_body: "Connect OLX and your next listing flies out 5× faster.",

      final_cta_h2: "Ready to sell faster?",
      final_cta_sub: "Done before your coffee cools.",

      footer_tagline: "Photo in, listing out.",
      footer_privacy: "Privacy Policy",
      footer_terms: "Terms of Service",
      footer_support: "Support",
      footer_copyright: "© 2026 SellSnap. All rights reserved.",
    },
    bg: {
      page_title: "SellSnap — Снимай. AI пише. OLX го получава.",
      page_description: "Снимка влиза, обява излиза. AI пише заглавието, описанието и цената — ти публикуваш в OLX с един допир.",
      theme_system_label: "Системна",
      theme_light_label: "Светла",
      theme_dark_label: "Тъмна",
      hero_h1: "Снимай. AI пише. OLX го получава.",
      hero_sub: "Снимка влиза, обява излиза — AI пише вместо теб.",
      store_apple_eyebrow: "Изтегли от",
      store_apple_name: "App Store",
      store_google_eyebrow: "Изтегли от",
      store_google_name: "Google Play",
      mock_step_1_title: "Снимай",
      mock_step_2_title: "Готвим обявата ти…",
      mock_step_2_sub: "AI си върши работата…",
      mock_step_3_title: "Публикувано! 🎉",
      mock_step_3_sub: "Обявата ти е на живо в OLX.",
      how_it_works: "Как работи",
      onboarding_step1_title: "Снимай 📸",
      onboarding_step1_subtitle: "Насочи, снимай, готово. Каквото искаш да продадеш — ние поемаме оттук.",
      onboarding_step2_title: "AI пише вместо теб ✍️",
      onboarding_step2_subtitle: "Заглавие, описание и честна цена — готови преди кафето да изстине.",
      onboarding_step3_title: "Публикувай с един допир 🚀",
      onboarding_step3_subtitle: "Един допир и си на живо в OLX. Купувачи, посрещнете нещата му.",
      why_sellsnap: "Защо SellSnap",
      feature_publish_title: "Публикувай директно в OLX — без copy-paste",
      feature_publish_desc: "Свържи OLX акаунта си и обявата ти литва право там с един допир.",
      feature_manage_title: "Пълен контрол след публикуване",
      feature_manage_desc: "Редактирай, удължи, деактивирай или изтрий — плюс прегледи и прегледи на телефона, направо в приложението.",
      feature_price_title: "Честна цена от AI",
      feature_price_desc: "AI предлага диапазона, в който вещта ти наистина се продава — без гадаене.",
      feature_accounts_title: "Управлявай няколко OLX акаунта",
      feature_accounts_desc: "Свържи до 3 акаунта и превключвай активния с един допир — удобно за личен и бизнес акаунт.",
      feature_share_title: "Започни направо от галерията",
      feature_share_desc: "Сподели снимка направо от Снимки — SellSnap поема оттам.",
      feature_guest_title: "Пробвай без акаунт",
      feature_guest_desc: "Направи обявата и копирай текста — можеш и сам да го поставиш в OLX.",
      tips_for_better_photos: "Съвети за снимки 📷",
      tip_lighting: "Добра светлина = по-добри резултати",
      tip_angles: "Снимай от няколко ъгъла",
      tip_defects: "Не крий драскотините — купувачите ценят честността",
      pro_tip_label: "Полезен съвет",
      pro_tip_body: "Свържи OLX и следващата ти обява литва 5 пъти по-бързо.",
      final_cta_h2: "Готов ли си да продаваш по-бързо?",
      final_cta_sub: "Готово преди кафето да изстине.",
      footer_tagline: "Снимка влиза, обява излиза.",
      footer_privacy: "Политика за поверителност",
      footer_terms: "Условия за ползване",
      footer_support: "Поддръжка",
      footer_copyright: "© 2026 SellSnap. Всички права запазени.",
    },
    kk: {
      page_title: "SellSnap — Түсір. AI жазады. OLX алады.",
      page_description: "Суретті сал, хабарландыруды ал. AI тақырыпты, сипаттаманы және бағаны жазады — OLX-ке бір түртіп жариялайсың.",
      theme_system_label: "Жүйелік",
      theme_light_label: "Ашық",
      theme_dark_label: "Қараңғы",
      hero_h1: "Түсір. AI жазады. OLX алады.",
      hero_sub: "Суретті сал, хабарландыруды ал — терудің бәрін AI жасайды.",
      store_apple_eyebrow: "Жүктеп ал",
      store_apple_name: "App Store",
      store_google_eyebrow: "Жүктеп ал",
      store_google_name: "Google Play",
      mock_step_1_title: "Түсіріп ал",
      mock_step_2_title: "Хабарландыруың пісіп жатыр…",
      mock_step_2_sub: "AI өз ісін істеп жатыр…",
      mock_step_3_title: "Жарияланды! 🎉",
      mock_step_3_sub: "Хабарландыруың OLX-те тірі.",
      how_it_works: "Қалай жұмыс істейді",
      onboarding_step1_title: "Түсіріп ал 📸",
      onboarding_step1_subtitle: "Нұқта, суретке түсір, бітті. Не сатқың келсе де — қалғанын біз жасаймыз.",
      onboarding_step2_title: "Мәтінді AI жазады ✍️",
      onboarding_step2_subtitle: "Тақырып, сипаттама және әділ баға — кофең суымай дайын болады.",
      onboarding_step3_title: "Бір түртіп жариялау 🚀",
      onboarding_step3_subtitle: "Бір түртсең — OLX-те тірісің. Сатып алушылар затыңды тапты.",
      why_sellsnap: "Неге SellSnap",
      feature_publish_title: "Тікелей OLX-ке жарияла — көшіріп-жапсырудың қажеті жоқ",
      feature_publish_desc: "OLX аккаунтыңды қос, хабарландыруың бір түртумен тікелей сонда ұшып барады.",
      feature_manage_title: "Жарияланғаннан кейін толық бақылау",
      feature_manage_desc: "Өңде, ұзарт, тоқтата тұр немесе жой — тағы да қараулар мен телефонды ашулар қосымшада тікелей.",
      feature_price_title: "AI ұсынған әділ баға",
      feature_price_desc: "AI затыңның нақты сатылатын баға ауқымын ұсынады — болжаудың қажеті жоқ.",
      feature_accounts_title: "Бірнеше OLX аккаунтын басқар",
      feature_accounts_desc: "3-ке дейін аккаунт қос және белсендісін бір түртумен ауыстыр — жеке және бизнес аккаунтқа ыңғайлы.",
      feature_share_title: "Тікелей суреттеріңнен баста",
      feature_share_desc: "Фотоны Суреттерден бөліс — SellSnap қалғанын жасайды.",
      feature_guest_title: "Аккаунтсыз көріп көр",
      feature_guest_desc: "Хабарландыруды жаса да мәтінді көшір — OLX-ке өзің жапсырсаң да болады.",
      tips_for_better_photos: "Фото кеңестері 📷",
      tip_lighting: "Жақсы жарық = жақсы нәтиже",
      tip_angles: "Бірнеше бұрыштан түсір",
      tip_defects: "Тырнақшаларды жасырма — сатып алушылар адалдықты бағалайды",
      pro_tip_label: "Пайдалы кеңес",
      pro_tip_body: "OLX-ті қос — келесі хабарландыруың 5 есе жылдам ұшады.",
      final_cta_h2: "Жылдам сатуға дайынсың ба?",
      final_cta_sub: "Кофең суымай бітеді.",
      footer_tagline: "Суретті сал, хабарландыруды ал.",
      footer_privacy: "Құпиялылық саясаты",
      footer_terms: "Пайдалану шарттары",
      footer_support: "Қолдау",
      footer_copyright: "© 2026 SellSnap. Барлық құқықтар қорғалған.",
    },
    pl: {
      page_title: "SellSnap — Zrób zdjęcie. AI pisze. OLX publikuje.",
      page_description: "Zdjęcie na wejściu, ogłoszenie na wyjściu. AI pisze tytuł, opis i cenę — ty publikujesz na OLX jednym stuknięciem.",
      theme_system_label: "System",
      theme_light_label: "Jasny",
      theme_dark_label: "Ciemny",
      hero_h1: "Zrób zdjęcie. AI pisze. OLX publikuje.",
      hero_sub: "Zdjęcie na wejściu, ogłoszenie na wyjściu — AI pisze za ciebie.",
      store_apple_eyebrow: "Pobierz w",
      store_apple_name: "App Store",
      store_google_eyebrow: "Pobierz w",
      store_google_name: "Google Play",
      mock_step_1_title: "Zrób zdjęcie",
      mock_step_2_title: "Przygotowujemy twoje ogłoszenie…",
      mock_step_2_sub: "AI robi swoje…",
      mock_step_3_title: "Opublikowano! 🎉",
      mock_step_3_sub: "Twoje ogłoszenie jest już na OLX.",
      how_it_works: "Jak to działa",
      onboarding_step1_title: "Zrób zdjęcie 📸",
      onboarding_step1_subtitle: "Wyceluj, zrób zdjęcie, gotowe. Cokolwiek chcesz sprzedać — zajmiemy się resztą.",
      onboarding_step2_title: "AI pisze za ciebie ✍️",
      onboarding_step2_subtitle: "Tytuł, opis i uczciwa cena — gotowe, zanim kawa ostygnie.",
      onboarding_step3_title: "Opublikuj jednym kliknięciem 🚀",
      onboarding_step3_subtitle: "Jedno kliknięcie i jesteś na OLX. Kupujący, poznajcie swoje skarby.",
      why_sellsnap: "Dlaczego SellSnap",
      feature_publish_title: "Publikuj prosto na OLX — bez kopiowania",
      feature_publish_desc: "Połącz konto OLX, a twoje ogłoszenie poleci tam jednym stuknięciem.",
      feature_manage_title: "Pełna kontrola po publikacji",
      feature_manage_desc: "Edytuj, przedłuż, wstrzymaj albo usuń — do tego wyświetlenia i wyświetlenia telefonu, prosto w aplikacji.",
      feature_price_title: "Uczciwa cena od AI",
      feature_price_desc: "AI podpowiada widełki cenowe, w których twoja rzecz naprawdę się sprzeda — bez zgadywania.",
      feature_accounts_title: "Prowadź więcej niż jedno konto OLX",
      feature_accounts_desc: "Połącz do 3 kont i przełączaj aktywne jednym stuknięciem — przydatne dla konta prywatnego i firmowego.",
      feature_share_title: "Zacznij prosto ze zdjęć",
      feature_share_desc: "Udostępnij zdjęcie prosto ze Zdjęć — SellSnap zajmie się resztą.",
      feature_guest_title: "Wypróbuj bez konta",
      feature_guest_desc: "Stwórz ogłoszenie i skopiuj tekst — możesz też wkleić go na OLX samodzielnie.",
      tips_for_better_photos: "Wskazówki foto 📷",
      tip_lighting: "Dobre światło = lepsze wyniki",
      tip_angles: "Fotografuj z kilku kątów",
      tip_defects: "Nie ukrywaj zarysowań — kupujący cenią szczerość",
      pro_tip_label: "Wskazówka",
      pro_tip_body: "Połącz OLX, a twoje następne ogłoszenie poleci 5 razy szybciej.",
      final_cta_h2: "Czas sprzedawać szybciej?",
      final_cta_sub: "Gotowe, zanim kawa ostygnie.",
      footer_tagline: "Zdjęcie na wejściu, ogłoszenie na wyjściu.",
      footer_privacy: "Polityka prywatności",
      footer_terms: "Regulamin",
      footer_support: "Pomoc",
      footer_copyright: "© 2026 SellSnap. Wszelkie prawa zastrzeżone.",
    },
    pt: {
      page_title: "SellSnap — Fotografe. A IA escreve. O OLX recebe.",
      page_description: "Uma foto e pronto. A IA escreve o título, a descrição e o preço — publique no OLX num só toque.",
      theme_system_label: "Sistema",
      theme_light_label: "Claro",
      theme_dark_label: "Escuro",
      hero_h1: "Fotografe. A IA escreve. O OLX recebe.",
      hero_sub: "Uma foto e pronto — a IA trata da escrita.",
      store_apple_eyebrow: "Disponível na",
      store_apple_name: "App Store",
      store_google_eyebrow: "Disponível no",
      store_google_name: "Google Play",
      mock_step_1_title: "Fotografe",
      mock_step_2_title: "A cozinhar o seu anúncio…",
      mock_step_2_sub: "A IA está a tratar disso…",
      mock_step_3_title: "Publicado! 🎉",
      mock_step_3_sub: "O seu anúncio já está no OLX.",
      how_it_works: "Como funciona",
      onboarding_step1_title: "Fotografe 📸",
      onboarding_step1_subtitle: "Aponte, fotografe, pronto. Seja o que for que queira vender — nós tratamos do resto.",
      onboarding_step2_title: "A IA escreve por si ✍️",
      onboarding_step2_subtitle: "Um título, uma descrição e um preço justo — prontos antes de o café arrefecer.",
      onboarding_step3_title: "Publique num toque 🚀",
      onboarding_step3_subtitle: "Um toque e está no OLX. Compradores, conheçam os seus artigos.",
      why_sellsnap: "Porquê o SellSnap",
      feature_publish_title: "Publique diretamente no OLX — sem copiar e colar",
      feature_publish_desc: "Ligue a sua conta OLX e o seu anúncio segue diretamente para lá num só toque.",
      feature_manage_title: "Controlo total depois de publicar",
      feature_manage_desc: "Edite, prolongue, retire ou elimine — mais visualizações e visualizações do telefone, tudo na aplicação.",
      feature_price_title: "Um preço justo, sugerido pela IA",
      feature_price_desc: "A IA sugere a faixa de preço real para o seu artigo — sem adivinhar.",
      feature_accounts_title: "Faça a gestão de mais do que uma conta OLX",
      feature_accounts_desc: "Ligue até 3 contas e mude a conta ativa num toque — ideal para uma conta pessoal e uma profissional.",
      feature_share_title: "Comece a partir das suas fotos",
      feature_share_desc: "Partilhe uma foto diretamente das Fotos — o SellSnap trata do resto.",
      feature_guest_title: "Experimente sem criar conta",
      feature_guest_desc: "Crie o anúncio e copie o texto — também pode colá-lo no OLX manualmente.",
      tips_for_better_photos: "Dicas de fotografia 📷",
      tip_lighting: "Boa luz = melhores resultados",
      tip_angles: "Fotografe de vários ângulos",
      tip_defects: "Não esconda os arranhões — os compradores adoram honestidade",
      pro_tip_label: "Dica pro",
      pro_tip_body: "Ligue o OLX e o seu próximo anúncio sai 5× mais rápido.",
      final_cta_h2: "Pronto para vender mais rápido?",
      final_cta_sub: "Feito antes de o café arrefecer.",
      footer_tagline: "Uma foto e pronto.",
      footer_privacy: "Política de Privacidade",
      footer_terms: "Termos de Serviço",
      footer_support: "Suporte",
      footer_copyright: "© 2026 SellSnap. Todos os direitos reservados.",
    },
    ro: {
      page_title: "SellSnap — Fotografiază. AI scrie. OLX publică.",
      page_description: "Faci o poză, primești un anunț. AI scrie titlul, descrierea și prețul — tu publici pe OLX dintr-o atingere.",
      theme_system_label: "Sistem",
      theme_light_label: "Luminoasă",
      theme_dark_label: "Întunecată",
      hero_h1: "Fotografiază. AI scrie. OLX publică.",
      hero_sub: "Faci o poză, primești un anunț — AI scrie tot.",
      store_apple_eyebrow: "Descarcă din",
      store_apple_name: "App Store",
      store_google_eyebrow: "Descarcă din",
      store_google_name: "Google Play",
      mock_step_1_title: "Fotografiază",
      mock_step_2_title: "Îți pregătim anunțul…",
      mock_step_2_sub: "AI își face treaba…",
      mock_step_3_title: "Publicat! 🎉",
      mock_step_3_sub: "Anunțul tău e live pe OLX.",
      how_it_works: "Cum funcționează",
      onboarding_step1_title: "Fotografiază 📸",
      onboarding_step1_subtitle: "Îndreaptă, fotografiază, gata. Orice vrei să vinzi — ne ocupăm noi de restul.",
      onboarding_step2_title: "AI scrie pentru tine ✍️",
      onboarding_step2_subtitle: "Un titlu, o descriere și un preț corect — gata înainte să se răcească cafeaua.",
      onboarding_step3_title: "Publică dintr-o atingere 🚀",
      onboarding_step3_subtitle: "O atingere și ești live pe OLX. Cumpărătorii, cunoașteți lucrurile.",
      why_sellsnap: "De ce SellSnap",
      feature_publish_title: "Publică direct pe OLX — fără copy-paste",
      feature_publish_desc: "Conectează-ți contul OLX și anunțul zboară direct acolo dintr-o atingere.",
      feature_manage_title: "Control total după publicare",
      feature_manage_desc: "Editează, prelungește, retrage sau șterge — plus vizualizări și vizualizări telefon, direct din aplicație.",
      feature_price_title: "Preț corect, sugerat de AI",
      feature_price_desc: "AI îți sugerează intervalul de preț la care chiar se vinde produsul tău — fără presupuneri.",
      feature_accounts_title: "Administrează mai multe conturi OLX",
      feature_accounts_desc: "Conectează până la 3 conturi și schimbă-l pe cel activ dintr-o atingere — util pentru un cont personal și unul de firmă.",
      feature_share_title: "Începe direct din galerie",
      feature_share_desc: "Distribuie o poză direct din Poze — SellSnap se ocupă de rest.",
      feature_guest_title: "Încearcă fără cont",
      feature_guest_desc: "Creează anunțul și copiază textul — poți să-l lipești tu însuți pe OLX.",
      tips_for_better_photos: "Sfaturi foto 📷",
      tip_lighting: "Lumină bună = rezultate mai bune",
      tip_angles: "Fotografiază din mai multe unghiuri",
      tip_defects: "Nu ascunde zgârieturile — cumpărătorii apreciază sinceritatea",
      pro_tip_label: "Sfat pro",
      pro_tip_body: "Conectează OLX și următorul tău anunț zboară de 5 ori mai repede.",
      final_cta_h2: "Gata să vinzi mai repede?",
      final_cta_sub: "Gata înainte să se răcească cafeaua.",
      footer_tagline: "Faci o poză, primești un anunț.",
      footer_privacy: "Politica de confidențialitate",
      footer_terms: "Termeni și condiții",
      footer_support: "Suport",
      footer_copyright: "© 2026 SellSnap. Toate drepturile rezervate.",
    },
  };

  function detectInitialLang() {
    const fromQuery = new URLSearchParams(window.location.search).get("lang");
    if (fromQuery && SUPPORTED_LANGS.includes(fromQuery)) return fromQuery;
    const stored = localStorage.getItem(LANG_KEY);
    if (stored && SUPPORTED_LANGS.includes(stored)) return stored;
    const nav = (navigator.language || "en").toLowerCase().slice(0, 2);
    return SUPPORTED_LANGS.includes(nav) ? nav : "en";
  }

  function applyLang(lang) {
    if (!SUPPORTED_LANGS.includes(lang)) lang = "en";
    document.documentElement.lang = lang;
    document.documentElement.dataset.lang = lang;
    const dict = I18N[lang];
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      const key = el.dataset.i18n;
      if (dict[key] != null) el.textContent = dict[key];
    });
    document.querySelectorAll("[data-i18n-attr]").forEach((el) => {
      // Format: "attrName:key;attrName:key"
      el.dataset.i18nAttr.split(";").forEach((pair) => {
        const [attr, key] = pair.split(":").map((s) => s.trim());
        if (attr && key && dict[key] != null) el.setAttribute(attr, dict[key]);
      });
    });
    document.title = dict.page_title;
    const meta = document.querySelector('meta[name="description"]');
    if (meta) meta.setAttribute("content", dict.page_description);
    const langSelect = document.querySelector("[data-lang-select]");
    if (langSelect) langSelect.value = lang;
    localStorage.setItem(LANG_KEY, lang);
  }

  function applyTheme(theme) {
    const valid = ["system", "light", "dark"];
    if (!valid.includes(theme)) theme = "system";
    if (theme === "system") {
      delete document.documentElement.dataset.theme;
    } else {
      document.documentElement.dataset.theme = theme;
    }
    document.querySelectorAll("[data-set-theme]").forEach((btn) => {
      btn.setAttribute("aria-pressed", btn.dataset.setTheme === theme ? "true" : "false");
    });
    localStorage.setItem(THEME_KEY, theme);
  }

  function init() {
    applyLang(detectInitialLang());
    applyTheme(localStorage.getItem(THEME_KEY) || "system");
    const langSelect = document.querySelector("[data-lang-select]");
    if (langSelect) {
      langSelect.addEventListener("change", () => applyLang(langSelect.value));
    }
    document.querySelectorAll("[data-set-theme]").forEach((btn) => {
      btn.addEventListener("click", () => applyTheme(btn.dataset.setTheme));
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
