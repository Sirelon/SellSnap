(function () {
  const LANG_KEY = "sellsnap-lang";
  const THEME_KEY = "sellsnap-theme";
  const SUPPORTED_LANGS = ["uk", "en"];

  const I18N = {
    uk: {
      page_title: "SellSnap — Сфотографуй. ШІ напише. Публікуй на OLX.",
      page_description: "Фото — на вхід, оголошення — на вихід. ШІ напише назву, опис і ціну, а ви публікуєте на OLX в один тап.",
      lang_uk: "UA",
      lang_en: "EN",
      theme_system_label: "Системна",
      theme_light_label: "Світла",
      theme_dark_label: "Темна",

      hero_h1: "Сфотографуй. ШІ напише. Публікуй на OLX.",
      hero_sub: "Фото — на вхід, оголошення — на вихід. ШІ напише за вас.",

      store_apple_eyebrow: "Завантажуйте в",
      store_apple_name: "App Store",

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
      feature_manage_title: "Усі оголошення — в одному місці",
      feature_manage_desc: "Опубліковані оголошення з'являються в апці одразу після публікації.",
      feature_price_title: "Чесна ціна від ШІ",
      feature_price_desc: "ШІ підкаже діапазон, у якому ваша річ дійсно продається — без здогадок.",
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
      lang_uk: "UA",
      lang_en: "EN",
      theme_system_label: "System",
      theme_light_label: "Light",
      theme_dark_label: "Dark",

      hero_h1: "Snap it. AI writes it. OLX gets it.",
      hero_sub: "Photo in, listing out — AI does the typing.",

      store_apple_eyebrow: "Download on the",
      store_apple_name: "App Store",

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
      feature_manage_title: "Every ad lives in one tidy spot",
      feature_manage_desc: "Published listings show up inside the app the moment they go live.",
      feature_price_title: "A fair price from AI",
      feature_price_desc: "AI suggests the range your item actually sells for — no guessing.",
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
  };

  function detectInitialLang() {
    const fromQuery = new URLSearchParams(window.location.search).get("lang");
    if (fromQuery && SUPPORTED_LANGS.includes(fromQuery)) return fromQuery;
    const stored = localStorage.getItem(LANG_KEY);
    if (stored && SUPPORTED_LANGS.includes(stored)) return stored;
    const nav = (navigator.language || "en").toLowerCase();
    return nav.startsWith("en") ? "en" : "uk";
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
    document.querySelectorAll("[data-set-lang]").forEach((btn) => {
      btn.setAttribute("aria-pressed", btn.dataset.setLang === lang ? "true" : "false");
    });
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
    document.querySelectorAll("[data-set-lang]").forEach((btn) => {
      btn.addEventListener("click", () => applyLang(btn.dataset.setLang));
    });
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
