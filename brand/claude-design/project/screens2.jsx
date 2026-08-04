// Screens: Home/Generate (photo picker), Processing (5-stage AI)

function Home({ photos, setPhotos, hint, setHint, onGenerate, onLogout }) {
  const { c } = useTheme();
  const fileRef = React.useRef(null);

  const kinds = ['jacket', 'phone', 'chair', 'shoe', 'bag', 'lamp'];

  const addPhoto = () => {
    if (photos.length >= 8) return;
    const kind = kinds[photos.length % kinds.length];
    setPhotos([...photos, { kind, hue: 20 + photos.length * 8 }]);
  };
  const removePhoto = (i) => setPhotos(photos.filter((_, j) => j !== i));

  return (
    <div style={{
      height: '100%', display: 'flex', flexDirection: 'column',
      background: c.background, color: c.onBackground,
      fontFamily: TOKENS.type.body,
    }}>
      {/* Header */}
      <div style={{ padding: '56px 20px 16px', display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{
          width: 40, height: 40, borderRadius: 12,
          background: `linear-gradient(135deg, ${c.primaryBright}, ${c.primary})`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: `0 6px 16px -4px ${c.primary}77`,
        }}>{Icon.camera(22, c.onPrimary)}</div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 12, color: `${c.onBackground}99`, fontWeight: 600, letterSpacing: 0.4 }}>Вітаємо,</div>
          <div style={{ fontFamily: TOKENS.type.display, fontSize: 18, fontWeight: 700, color: c.onBackground, lineHeight: 1.1 }}>Олександр</div>
        </div>
        <button onClick={onLogout} style={{
          width: 40, height: 40, borderRadius: 12,
          background: c.surfaceLow, color: c.onSurface,
          border: 'none', cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>{Icon.user(20, c.onSurface)}</button>
      </div>

      {/* Main */}
      <div style={{ flex: 1, overflow: 'auto', padding: '8px 20px 140px' }}>
        <h1 style={{
          fontFamily: TOKENS.type.display, fontSize: 28, fontWeight: 700,
          lineHeight: 1.15, letterSpacing: -0.4, margin: '8px 0 6px',
          color: c.onBackground, textWrap: 'balance',
        }}>Нове оголошення</h1>
        <p style={{ fontSize: 14, color: `${c.onBackground}99`, margin: '0 0 20px' }}>
          Додайте 1–8 фото. AI підготує все інше.
        </p>

        {/* Photo grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: 10, marginBottom: 20,
        }}>
          {photos.map((p, i) => (
            <div key={i} style={{
              position: 'relative', aspectRatio: '1 / 1',
              animation: 'popIn 280ms cubic-bezier(0.34, 1.56, 0.64, 1) both',
            }}>
              <ProductShot kind={p.kind} hue={p.hue} radius={14}/>
              {i === 0 && (
                <div style={{
                  position: 'absolute', left: 8, top: 8,
                  padding: '3px 8px', borderRadius: 6,
                  background: c.primary, color: c.onPrimary,
                  fontSize: 10, fontWeight: 700, letterSpacing: 0.5,
                }}>ГОЛОВНЕ</div>
              )}
              <button onClick={() => removePhoto(i)} style={{
                position: 'absolute', right: 6, top: 6,
                width: 24, height: 24, borderRadius: '50%',
                background: 'rgba(0,0,0,0.55)', border: 'none',
                color: '#fff', cursor: 'pointer',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                backdropFilter: 'blur(8px)',
              }}>{Icon.close(14, '#fff')}</button>
            </div>
          ))}

          {photos.length < 8 && (
            <button onClick={addPhoto} style={{
              aspectRatio: '1 / 1',
              background: c.surfaceLow,
              border: `2px dashed ${c.outline}66`,
              borderRadius: 14, cursor: 'pointer',
              display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center',
              gap: 6, color: c.primary,
              fontFamily: TOKENS.type.body,
            }}>
              {Icon.plus(24, c.primary)}
              <span style={{ fontSize: 12, fontWeight: 600 }}>Додати</span>
            </button>
          )}
        </div>

        {/* Source picker */}
        <div style={{ display: 'flex', gap: 10, marginBottom: 24 }}>
          <button onClick={addPhoto} style={{
            flex: 1, height: 52, borderRadius: 14,
            background: c.surfaceLowest, border: `1px solid ${c.outlineVariant}44`,
            cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            color: c.onSurface, fontFamily: TOKENS.type.body,
            fontSize: 14, fontWeight: 600,
          }}>{Icon.camera(20, c.primary)}Камера</button>
          <button onClick={addPhoto} style={{
            flex: 1, height: 52, borderRadius: 14,
            background: c.surfaceLowest, border: `1px solid ${c.outlineVariant}44`,
            cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            color: c.onSurface, fontFamily: TOKENS.type.body,
            fontSize: 14, fontWeight: 600,
          }}>{Icon.gallery(20, c.primary)}Галерея</button>
        </div>

        {/* Optional hint */}
        <SectionHeader title="Підказка для AI (необов'язково)" />
        <AppInput
          value={hint} onChange={setHint} multiline rows={2}
          placeholder="Напр. Nike Air Max 90, розмір 42, носив 2 місяці"
          maxLength={120}
        />

      </div>

      {/* Sticky CTA */}
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        padding: '16px 20px 40px',
        background: `linear-gradient(to top, ${c.background} 70%, ${c.background}00)`,
      }}>
        <AppButton variant="magic" full disabled={photos.length === 0}
          onClick={onGenerate}
          leading={Icon.sparkle(20, c.onPrimary)}
        >
          {photos.length === 0 ? 'Додайте фото' : `Створити оголошення (${photos.length})`}
        </AppButton>
      </div>
    </div>
  );
}

// ─── Processing (5-stage animation) ───────────────────────────
function Processing({ onDone, photos, speedMs = 1200 }) {
  const { c } = useTheme();
  const stages = [
    { label: 'Завантажуємо фото', sub: 'Зашифровано з кінця в кінець', icon: Icon.gallery },
    { label: 'Аналізуємо предмет', sub: 'Визначаємо марку, стан, деталі', icon: Icon.eye },
    { label: 'Підбираємо категорію', sub: 'Серед 3 200+ категорій Торгу', icon: Icon.tag },
    { label: 'Заповнюємо атрибути', sub: 'Розмір, колір, матеріал, стан', icon: Icon.edit },
    { label: 'Оцінюємо ціну', sub: 'За схожими оголошеннями', icon: Icon.bolt },
  ];

  const [step, setStep] = React.useState(0);
  const [progress, setProgress] = React.useState(0);

  React.useEffect(() => {
    const total = stages.length * speedMs;
    const tick = 40;
    let elapsed = 0;
    const id = setInterval(() => {
      elapsed += tick;
      const p = Math.min(1, elapsed / total);
      setProgress(p);
      setStep(Math.min(stages.length - 1, Math.floor((elapsed / total) * stages.length)));
      if (p >= 1) {
        clearInterval(id);
        setTimeout(onDone, 350);
      }
    }, tick);
    return () => clearInterval(id);
  }, [speedMs]);

  return (
    <div style={{
      height: '100%', display: 'flex', flexDirection: 'column',
      background: c.background, color: c.onBackground,
      fontFamily: TOKENS.type.body, overflow: 'hidden',
    }}>
      {/* Aurora backdrop */}
      <div style={{
        position: 'absolute', inset: 0, pointerEvents: 'none', overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute', top: '10%', left: '-30%', width: '160%', height: '70%',
          background: `radial-gradient(ellipse, ${c.primary}44, transparent 60%)`,
          filter: 'blur(40px)',
          animation: 'drift 6s ease-in-out infinite alternate',
        }}/>
        <div style={{
          position: 'absolute', top: '30%', right: '-30%', width: '160%', height: '60%',
          background: `radial-gradient(ellipse, ${c.warningVariant}33, transparent 60%)`,
          filter: 'blur(50px)',
          animation: 'drift 7s ease-in-out infinite alternate-reverse',
        }}/>
      </div>

      {/* Top status */}
      <div style={{ padding: '56px 20px 0', position: 'relative', zIndex: 1 }}>
        <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: 1.2, color: c.primary, textTransform: 'uppercase' }}>
          AI · створення оголошення
        </div>
      </div>

      {/* Pulse */}
      <div style={{
        flex: 1, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
        padding: '32px 28px', position: 'relative', zIndex: 1,
      }}>
        <div style={{ position: 'relative', width: 200, height: 200,
          display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {/* Rotating ring */}
          <svg width="200" height="200" style={{ position: 'absolute', transform: 'rotate(-90deg)' }}>
            <circle cx="100" cy="100" r="90" stroke={`${c.primary}22`} strokeWidth="4" fill="none"/>
            <circle cx="100" cy="100" r="90" stroke={c.primary} strokeWidth="4" fill="none"
              strokeDasharray={`${Math.PI * 2 * 90}`}
              strokeDashoffset={`${Math.PI * 2 * 90 * (1 - progress)}`}
              strokeLinecap="round"
              style={{ transition: 'stroke-dashoffset 200ms ease' }}
            />
          </svg>
          <PulsingCircles size={150}>
            {Icon.sparkle(48, c.onPrimary)}
          </PulsingCircles>
        </div>

        <div key={step} style={{
          marginTop: 32, textAlign: 'center',
          animation: 'fadeInUp 320ms ease both',
        }}>
          <div style={{
            fontFamily: TOKENS.type.display, fontSize: 24, fontWeight: 700,
            letterSpacing: -0.3, color: c.onBackground,
          }}>{stages[step].label}…</div>
          <div style={{ fontSize: 14, color: `${c.onBackground}99`, marginTop: 6 }}>
            {stages[step].sub}
          </div>
        </div>

        <div style={{ fontFamily: TOKENS.type.display, fontSize: 13, color: `${c.onBackground}77`, marginTop: 12, fontVariantNumeric: 'tabular-nums' }}>
          {Math.round(progress * 100)}%
        </div>
      </div>

      {/* Stage list */}
      <div style={{ padding: '0 20px 40px', position: 'relative', zIndex: 1 }}>
        <AppCard style={{ padding: 6 }}>
          {stages.map((s, i) => {
            const done = i < step;
            const active = i === step;
            return (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '10px 12px',
              }}>
                <div style={{
                  width: 28, height: 28, borderRadius: 8, flexShrink: 0,
                  background: done ? c.success : active ? `${c.primary}22` : c.surfaceLow,
                  color: done ? '#fff' : active ? c.primary : `${c.onSurface}77`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  transition: 'all 220ms ease',
                }}>
                  {done ? Icon.check(18, '#fff') : s.icon(16, active ? c.primary : `${c.onSurface}77`)}
                </div>
                <div style={{
                  flex: 1, fontSize: 14, fontWeight: active ? 600 : 500,
                  color: done ? `${c.onSurface}99` : active ? c.onSurface : `${c.onSurface}99`,
                  textDecoration: done ? 'line-through' : 'none',
                  transition: 'all 220ms ease',
                }}>{s.label}</div>
                {active && (
                  <div style={{ display: 'flex', gap: 3 }}>
                    {[0,1,2].map(d => (
                      <div key={d} style={{
                        width: 5, height: 5, borderRadius: '50%', background: c.primary,
                        animation: `bounce 900ms ease-in-out ${d*120}ms infinite`,
                      }}/>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </AppCard>
      </div>
    </div>
  );
}

Object.assign(window, { Home, Processing });
