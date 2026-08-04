// Screens 1-3: Onboarding, Login. Screen 4-6 in screens2.jsx.

// ─── Onboarding (3-screen swipeable) ───────────────────────────
function Onboarding({ onDone }) {
  const { c } = useTheme();
  const [i, setI] = React.useState(0);

  const slides = [
    {
      accent: c.primary,
      icon: (
        <div style={{ position: 'relative', width: 160, height: 160 }}>
          <div style={{
            position: 'absolute', inset: 0, borderRadius: 32,
            background: `linear-gradient(135deg, ${c.primaryBright}, ${c.primary})`,
            boxShadow: `0 20px 40px -12px ${c.primary}77`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: c.onPrimary,
          }}>{Icon.camera(64, c.onPrimary)}</div>
          <div style={{
            position: 'absolute', right: -8, top: -8,
            width: 44, height: 44, borderRadius: '50%',
            background: c.warningVariant,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: `0 8px 16px -4px ${c.warning}66`,
          }}>{Icon.sparkle(22, '#3A1F00')}</div>
        </div>
      ),
      eyebrow: 'Крок 1',
      title: 'Сфотографуйте',
      body: 'Наведіть камеру на річ, яку хочете продати. Один знімок — або кілька ракурсів.',
    },
    {
      accent: c.warning,
      icon: (
        <div style={{
          width: 180, height: 160, position: 'relative',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <PulsingCircles size={160}>{Icon.sparkle(56, c.onPrimary)}</PulsingCircles>
        </div>
      ),
      eyebrow: 'Крок 2',
      title: 'AI пише за вас',
      body: 'Заголовок, опис, категорія, атрибути та ціна — усе заповнюється автоматично за 15 секунд.',
    },
    {
      accent: c.success,
      icon: (
        <div style={{
          width: 160, height: 160, borderRadius: 32,
          background: `linear-gradient(135deg, ${c.surfaceLowest}, ${c.surfaceLow})`,
          boxShadow: `0 20px 40px -12px ${c.onSurface}1C, inset 0 0 0 1px ${c.outlineVariant}55`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          position: 'relative',
        }}>
          <div style={{
            width: 96, height: 96, borderRadius: '50%',
            background: c.success,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>{Icon.check(48, '#fff')}</div>
          <div style={{
            position: 'absolute', bottom: 14, padding: '4px 10px',
            background: `${c.success}22`, color: c.success,
            borderRadius: 8, fontFamily: TOKENS.type.body,
            fontSize: 11, fontWeight: 700, letterSpacing: 0.5,
          }}>ОПУБЛІКОВАНО</div>
        </div>
      ),
      eyebrow: 'Крок 3',
      title: 'Готово за 90 секунд',
      body: 'Один тап — і оголошення вже на сайті. Без виснажливих форм, без плутанини з полями.',
    },
  ];

  const s = slides[i];

  return (
    <div style={{
      height: '100%', display: 'flex', flexDirection: 'column',
      background: c.background, color: c.onBackground,
      fontFamily: TOKENS.type.body,
    }}>
      {/* Skip */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '56px 20px 0' }}>
        <button onClick={onDone} style={{
          background: 'none', border: 'none', cursor: 'pointer',
          fontFamily: TOKENS.type.body, fontSize: 14, fontWeight: 500,
          color: `${c.onBackground}88`, padding: 8,
        }}>Пропустити</button>
      </div>

      {/* Hero */}
      <div style={{
        flex: 1, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
        padding: '24px 28px',
      }}>
        <div key={i} style={{ animation: 'fadeInUp 380ms ease both' }}>{s.icon}</div>

        <div style={{
          marginTop: 44, padding: '4px 12px', borderRadius: 999,
          background: `${s.accent}18`, color: s.accent,
          fontSize: 11, fontWeight: 700, letterSpacing: 1,
          textTransform: 'uppercase',
        }}>{s.eyebrow}</div>

        <h1 key={`t${i}`} style={{
          fontFamily: TOKENS.type.display,
          fontSize: 36, fontWeight: 700,
          lineHeight: 1.08, letterSpacing: -0.6,
          textAlign: 'center', margin: '14px 0 14px',
          color: c.onBackground,
          animation: 'fadeInUp 460ms ease both',
        }}>{s.title}</h1>

        <p key={`b${i}`} style={{
          fontFamily: TOKENS.type.body, fontSize: 16, lineHeight: 1.5,
          color: `${c.onBackground}AA`,
          textAlign: 'center', margin: 0, maxWidth: 300,
          textWrap: 'pretty',
          animation: 'fadeInUp 540ms ease both',
        }}>{s.body}</p>
      </div>

      {/* Dots */}
      <div style={{
        display: 'flex', justifyContent: 'center', gap: 8, padding: '0 0 24px',
      }}>
        {slides.map((_, j) => (
          <div key={j} onClick={() => setI(j)} style={{
            height: 6, borderRadius: 999,
            width: j === i ? 28 : 6,
            background: j === i ? c.primary : `${c.onBackground}22`,
            transition: 'all 280ms ease',
            cursor: 'pointer',
          }}/>
        ))}
      </div>

      {/* CTA */}
      <div style={{ padding: '0 20px 40px' }}>
        <AppButton
          variant="primary" full
          onClick={() => i === slides.length - 1 ? onDone() : setI(i + 1)}
          trailing={i === slides.length - 1 ? null : Icon.arrowRight(20, c.onPrimary)}
        >
          {i === slides.length - 1 ? 'Почати' : 'Далі'}
        </AppButton>
      </div>
    </div>
  );
}

// ─── Login ─────────────────────────────────────────────────────
function Login({ onLogin, onGuest }) {
  const { c } = useTheme();
  return (
    <div style={{
      height: '100%', display: 'flex', flexDirection: 'column',
      background: c.background, color: c.onBackground,
      fontFamily: TOKENS.type.body,
    }}>
      {/* Gradient canopy */}
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 360,
        background: `radial-gradient(60% 80% at 50% 0%, ${c.primary}33, transparent 70%)`,
        pointerEvents: 'none',
      }}/>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column',
        justifyContent: 'center', padding: '80px 28px 0', zIndex: 1 }}>

        {/* Logo */}
        <div style={{
          width: 80, height: 80, borderRadius: 22,
          background: `linear-gradient(135deg, ${c.primaryBright}, ${c.primary})`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: `0 20px 40px -12px ${c.primary}77`,
          marginBottom: 28,
        }}>
          <div style={{ position: 'relative', width: 44, height: 44 }}>
            {Icon.camera(44, c.onPrimary)}
            <div style={{
              position: 'absolute', right: -4, top: -4,
              width: 18, height: 18, borderRadius: '50%',
              background: c.warningVariant,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>{Icon.sparkle(11, '#3A1F00')}</div>
          </div>
        </div>

        <h1 style={{
          fontFamily: TOKENS.type.display, fontSize: 40, fontWeight: 700,
          letterSpacing: -0.8, lineHeight: 1, margin: '0 0 10px',
          color: c.onBackground,
        }}>SellSnap</h1>
        <div style={{ fontSize: 17, color: `${c.onBackground}AA`, marginBottom: 48, textWrap: 'pretty' }}>
          Продавайте речі за 90 секунд.<br/>Від фото до оголошення.
        </div>

        {/* Trust points */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 48 }}>
          {[
            { icon: Icon.bolt(18, c.primary), text: 'У 10 разів швидше, ніж вручну' },
            { icon: Icon.shield(18, c.success), text: 'Ваші дані нікуди не передаються' },
            { icon: Icon.sparkle(18, c.warning), text: 'AI пише українською мовою' },
          ].map((p, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{
                width: 32, height: 32, borderRadius: 10,
                background: c.surfaceLow,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                flexShrink: 0,
              }}>{p.icon}</div>
              <div style={{ fontSize: 15, color: c.onBackground }}>{p.text}</div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ padding: '0 20px 40px', display: 'flex', flexDirection: 'column', gap: 12, zIndex: 1 }}>
        <AppButton variant="primary" full onClick={onLogin}
          leading={
            <div style={{
              width: 22, height: 22, borderRadius: 6, background: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: c.primary, fontWeight: 800, fontSize: 13,
              fontFamily: TOKENS.type.display,
            }}>Т</div>
          }
        >Увійти через Торг</AppButton>
        <AppButton variant="secondary" full onClick={onGuest}>Продовжити як гість</AppButton>

        <div style={{
          textAlign: 'center', fontSize: 12, color: `${c.onBackground}77`,
          marginTop: 8, lineHeight: 1.5,
        }}>
          Продовжуючи, ви приймаєте <u>Умови</u> та <u>Політику конфіденційності</u>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { Onboarding, Login });
