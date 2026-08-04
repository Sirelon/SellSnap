// Preview & Edit + Publish + Success

function Preview({ data, setData, onBack, onPublish }) {
  const { c } = useTheme();
  const [carouselIdx, setCarouselIdx] = React.useState(0);
  const [confirm, setConfirm] = React.useState(false);
  const [editAttr, setEditAttr] = React.useState(null); // attr object being edited
  const [pickCat, setPickCat] = React.useState(false);
  const [showErrors, setShowErrors] = React.useState(false);

  const photos = data.photos;

  // Validation
  const missingRequired = data.attributes.filter(a => a.required && !String(a.value || '').trim());
  const titleTooShort = (data.title || '').trim().length < 10;
  const descTooShort = (data.description || '').trim().length < 30;
  const errors = [
    ...(titleTooShort ? [{ key: 'title', label: 'Заголовок закороткий (мін. 10 символів)' }] : []),
    ...(descTooShort ? [{ key: 'desc', label: 'Опис закороткий (мін. 30 символів)' }] : []),
    ...missingRequired.map(a => ({ key: `attr:${a.name}`, label: `${a.name} — обов’язково` })),
  ];
  const isValid = errors.length === 0;

  const tryPublish = () => {
    if (!isValid) {
      setShowErrors(true);
      // Auto-open first missing required attribute
      if (missingRequired[0]) setEditAttr(missingRequired[0]);
      return;
    }
    setConfirm(true);
  };

  return (
    <div style={{
      height: '100%', display: 'flex', flexDirection: 'column',
      background: c.background, color: c.onBackground,
      fontFamily: TOKENS.type.body,
    }}>
      {/* Header */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '56px 16px 12px',
        background: c.background,
        position: 'relative', zIndex: 2,
      }}>
        <button onClick={onBack} style={{
          width: 40, height: 40, borderRadius: 12, border: 'none',
          background: c.surfaceLow, color: c.onSurface, cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>{Icon.arrowLeft(20, c.onSurface)}</button>
        <div style={{ flex: 1, textAlign: 'center' }}>
          <div style={{ fontSize: 12, color: `${c.onBackground}99`, fontWeight: 600, letterSpacing: 0.4 }}>Перегляд</div>
          <div style={{ fontFamily: TOKENS.type.display, fontSize: 17, fontWeight: 700 }}>Ваше оголошення</div>
        </div>
        <div style={{ width: 40, height: 40 }}/>
      </div>

      <div style={{ flex: 1, overflow: 'auto', padding: '0 0 140px' }}>
        {/* AI banner */}
        <div style={{ padding: '0 20px 12px' }}>
          <div style={{
            padding: '10px 14px', borderRadius: 12,
            background: `linear-gradient(135deg, ${c.primary}18, ${c.warningVariant}22)`,
            border: `1px solid ${c.primary}33`,
            display: 'flex', alignItems: 'center', gap: 10,
          }}>
            {Icon.sparkle(18, c.primary)}
            <div style={{ flex: 1, fontSize: 13, color: c.onBackground, lineHeight: 1.35 }}>
              <b>Готово за 14 с.</b> Перевірте та змініть будь-що перед публікацією.
            </div>
          </div>
        </div>

        {/* Carousel */}
        <div style={{ padding: '0 20px 16px' }}>
          <div style={{
            position: 'relative', aspectRatio: '4 / 3', borderRadius: 20, overflow: 'hidden',
            boxShadow: `0 8px 24px -8px ${c.onBackground}1F`,
          }}>
            {photos.map((p, i) => (
              <div key={i} style={{
                position: 'absolute', inset: 0,
                opacity: i === carouselIdx ? 1 : 0,
                transition: 'opacity 240ms ease',
              }}>
                <ProductShot kind={p.kind} hue={p.hue} radius={20} showLabel/>
              </div>
            ))}

            {/* Arrows */}
            {photos.length > 1 && (
              <>
                <button onClick={() => setCarouselIdx((carouselIdx - 1 + photos.length) % photos.length)} style={{
                  position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)',
                  width: 36, height: 36, borderRadius: '50%', border: 'none',
                  background: 'rgba(255,255,255,0.85)', backdropFilter: 'blur(6px)',
                  cursor: 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>{Icon.arrowLeft(18, '#3A1F00')}</button>
                <button onClick={() => setCarouselIdx((carouselIdx + 1) % photos.length)} style={{
                  position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)',
                  width: 36, height: 36, borderRadius: '50%', border: 'none',
                  background: 'rgba(255,255,255,0.85)', backdropFilter: 'blur(6px)',
                  cursor: 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>{Icon.arrowRight(18, '#3A1F00')}</button>
              </>
            )}

            {/* Counter */}
            <div style={{
              position: 'absolute', right: 12, top: 12,
              padding: '4px 10px', borderRadius: 999,
              background: 'rgba(0,0,0,0.55)', color: '#fff',
              fontSize: 12, fontWeight: 600, backdropFilter: 'blur(6px)',
            }}>{carouselIdx + 1} / {photos.length}</div>

            {/* Dots */}
            {photos.length > 1 && (
              <div style={{
                position: 'absolute', bottom: 12, left: 0, right: 0,
                display: 'flex', justifyContent: 'center', gap: 5,
              }}>
                {photos.map((_, i) => (
                  <div key={i} style={{
                    width: i === carouselIdx ? 18 : 5, height: 5, borderRadius: 999,
                    background: i === carouselIdx ? '#fff' : 'rgba(255,255,255,0.55)',
                    transition: 'all 220ms ease',
                  }}/>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Validation banner — shows when user tried to publish */}
        {showErrors && !isValid && (
          <div style={{ padding: '0 20px 12px' }}>
            <div style={{
              padding: '12px 14px', borderRadius: 14,
              background: `${c.error}12`, border: `1px solid ${c.error}44`,
              display: 'flex', alignItems: 'flex-start', gap: 10,
            }}>
              <IconBadge color={c.error} size={32} radius={10}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="9" stroke={c.error} strokeWidth="1.8"/>
                  <path d="M12 7v6M12 16v.5" stroke={c.error} strokeWidth="2" strokeLinecap="round"/>
                </svg>
              </IconBadge>
              <div style={{ flex: 1, fontSize: 13, color: c.onBackground, lineHeight: 1.4 }}>
                <b>Виправте {errors.length} {errors.length === 1 ? 'поле' : errors.length < 5 ? 'поля' : 'полів'} перед публікацією:</b>
                <ul style={{ margin: '6px 0 0', paddingLeft: 18, color: `${c.onBackground}BB` }}>
                  {errors.slice(0, 3).map(e => <li key={e.key} style={{ marginTop: 2 }}>{e.label}</li>)}
                  {errors.length > 3 && <li style={{ marginTop: 2 }}>…та ще {errors.length - 3}</li>}
                </ul>
              </div>
            </div>
          </div>
        )}

        {/* Title */}
        <div style={{ padding: '0 20px' }}>
          <SectionHeader title="Заголовок" trailing={<div style={{display:'flex',gap:6,alignItems:'center'}}>{showErrors && titleTooShort && <ErrorPill/>}<CopyBtn value={data.title}/><AiBadge/></div>}/>
          <AppInput value={data.title} onChange={v => setData({ ...data, title: v })} maxLength={70}
            style={showErrors && titleTooShort ? { borderBottom: `2px solid ${c.error}` } : undefined}/>
          {showErrors && titleTooShort && (
            <div style={{ fontSize: 12, color: c.error, marginTop: 6, paddingLeft: 4 }}>
              Додайте ще {Math.max(10 - (data.title || '').trim().length, 1)} символів
            </div>
          )}
        </div>

        {/* Price with slider */}
        <div style={{ padding: '20px 20px 0' }}>
          <SectionHeader title="Ціна" trailing={<div style={{display:'flex',gap:6,alignItems:'center'}}><CopyBtn value={`${data.price} ₴`}/><AiBadge text="AI оцінка"/></div>}/>
          <PriceSlider data={data} setData={setData}/>
        </div>

        {/* Description */}
        <div style={{ padding: '20px 20px 0' }}>
          <SectionHeader title="Опис" trailing={<div style={{display:'flex',gap:6,alignItems:'center'}}>{showErrors && descTooShort && <ErrorPill/>}<CopyBtn value={data.description}/><AiBadge/></div>}/>
          <AppInput
            value={data.description} onChange={v => setData({ ...data, description: v })}
            multiline rows={5} maxLength={2500}
            style={showErrors && descTooShort ? { borderBottom: `2px solid ${c.error}` } : undefined}
          />
          {showErrors && descTooShort && (
            <div style={{ fontSize: 12, color: c.error, marginTop: 6, paddingLeft: 4 }}>
              Опишіть детальніше (мін. 30 символів)
            </div>
          )}
        </div>

        {/* Category */}
        <div style={{ padding: '20px 20px 0' }}>
          <SectionHeader title="Категорія" trailing={<AiBadge/>}/>
          <AppCard style={{ padding: '12px 14px', cursor: 'pointer' }} onClick={() => setPickCat(true)}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: `${c.onSurface}AA`, marginBottom: 6, flexWrap: 'wrap' }}>
              {data.category.map((p, i) => (
                <React.Fragment key={i}>
                  <span>{p}</span>
                  {i < data.category.length - 1 && <span style={{ color: `${c.onSurface}55` }}>›</span>}
                </React.Fragment>
              ))}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ fontFamily: TOKENS.type.display, fontSize: 17, fontWeight: 600, color: c.onSurface }}>
                {data.category[data.category.length - 1]}
              </div>
              <button style={{
                background: 'none', border: 'none', color: c.primary,
                fontFamily: TOKENS.type.body, fontSize: 13, fontWeight: 600, cursor: 'pointer',
              }}>Змінити</button>
            </div>
          </AppCard>
        </div>

        {/* Location */}
        <div style={{ padding: '20px 20px 0' }}>
          <SectionHeader title="Місцезнаходження"/>
          <AppCard style={{ padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 12 }}>
            <IconBadge color={c.success} size={38} radius={10}>{Icon.pin(18, c.success)}</IconBadge>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 15, fontWeight: 600, color: c.onSurface }}>{data.location}</div>
              <div style={{ fontSize: 12, color: `${c.onSurface}99`, marginTop: 2, display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 6, height: 6, borderRadius: '50%', background: c.success, display: 'inline-block' }}/>
                Визначено автоматично
              </div>
            </div>
            <button style={{
              background: 'none', border: 'none', color: c.primary,
              fontFamily: TOKENS.type.body, fontSize: 13, fontWeight: 600, cursor: 'pointer',
            }}>Змінити</button>
          </AppCard>
        </div>

        {/* Attributes */}
        <div style={{ padding: '20px 20px 0' }}>
          <SectionHeader title="Атрибути" trailing={<AiBadge/>}/>
          <AppCard style={{ padding: 0, overflow: 'hidden' }}>
            {data.attributes.map((a, i, arr) => (
              <AttrRow key={a.name} attr={a} showError={showErrors} onClick={() => setEditAttr(a)} last={i === arr.length - 1}/>
            ))}
          </AppCard>
        </div>

        {/* Validation */}
        <div style={{ padding: '20px 20px 0' }}>
          {isValid ? (
            <div style={{
              padding: 12, borderRadius: 12,
              background: `${c.success}14`,
              display: 'flex', alignItems: 'center', gap: 10,
            }}>
              <IconBadge color={c.success} size={32} radius={10}>{Icon.shield(16, c.success)}</IconBadge>
              <div style={{ flex: 1, fontSize: 13, color: c.onBackground }}>
                <b>Перевірено.</b> Усі обов'язкові поля заповнені.
              </div>
            </div>
          ) : (
            <div style={{
              padding: 12, borderRadius: 12,
              background: `${c.warning}14`,
              display: 'flex', alignItems: 'center', gap: 10,
            }}>
              <IconBadge color={c.warning} size={32} radius={10}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                  <path d="M12 3L2 20h20L12 3z" stroke={c.warning} strokeWidth="1.8" strokeLinejoin="round"/>
                  <path d="M12 10v5M12 17.5v.5" stroke={c.warning} strokeWidth="2" strokeLinecap="round"/>
                </svg>
              </IconBadge>
              <div style={{ flex: 1, fontSize: 13, color: c.onBackground }}>
                <b>Залишилось {errors.length} {errors.length === 1 ? 'поле' : errors.length < 5 ? 'поля' : 'полів'}.</b> Заповніть, щоб опублікувати.
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Sticky publish */}
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        padding: '16px 20px 40px',
        background: `linear-gradient(to top, ${c.background} 70%, ${c.background}00)`,
      }}>
        <AppButton variant={isValid ? 'success' : 'primary'} full onClick={tryPublish}>
          {isValid ? 'Опублікувати на Торгу' : `Опублікувати · ${errors.length} ${errors.length === 1 ? 'помилка' : errors.length < 5 ? 'помилки' : 'помилок'}`}
        </AppButton>
      </div>

      {confirm && (
        <ConfirmSheet data={data} onClose={() => setConfirm(false)} onPublish={onPublish}/>
      )}
      {pickCat && (
        <CategoryPicker
          value={data.category}
          onClose={() => setPickCat(false)}
          onChange={(next) => setData({ ...data, category: next })}
        />
      )}
      {editAttr && (
        <AttrEditSheet
          attr={editAttr}
          onClose={() => setEditAttr(null)}
          onSave={(v) => {
            const attrs = [...data.attributes];
            const idx = data.attributes.findIndex(x => x.name === editAttr.name);
            attrs[idx] = { ...editAttr, value: v };
            setData({ ...data, attributes: attrs });
          }}
        />
      )}
    </div>
  );
}

function ErrorPill() {
  const { c } = useTheme();
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '3px 8px', borderRadius: 999,
      background: `${c.error}18`, color: c.error,
      fontSize: 10, fontWeight: 700, letterSpacing: 0.5,
    }}>
      <svg width="10" height="10" viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="9" stroke={c.error} strokeWidth="2.5"/>
        <path d="M12 7v6M12 16v.5" stroke={c.error} strokeWidth="2.5" strokeLinecap="round"/>
      </svg>
      ПОМИЛКА
    </div>
  );
}

function CopyBtn({ value }) {
  const { c } = useTheme();
  const [done, setDone] = React.useState(false);
  const copy = (e) => {
    e.stopPropagation();
    try { navigator.clipboard?.writeText(String(value ?? '')); } catch {}
    setDone(true);
    setTimeout(() => setDone(false), 1400);
  };
  return (
    <button onClick={copy} style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '3px 8px', borderRadius: 999,
      background: done ? `${c.success}22` : c.surfaceLow,
      color: done ? c.success : c.primary,
      border: 'none', cursor: 'pointer',
      fontFamily: TOKENS.type.body, fontSize: 10, fontWeight: 700, letterSpacing: 0.5,
      transition: 'all 160ms ease',
    }}>
      {done ? (
        <><svg width="10" height="10" viewBox="0 0 24 24" fill="none"><path d="M5 12.5l4.5 4.5L19 7.5" stroke={c.success} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/></svg>СКОПІЙОВАНО</>
      ) : (
        <><svg width="10" height="10" viewBox="0 0 24 24" fill="none"><rect x="8" y="8" width="12" height="12" rx="2" stroke="currentColor" strokeWidth="2"/><path d="M4 16V4h12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>КОПІЮВАТИ</>
      )}
    </button>
  );
}

function AiBadge({ text = 'AI' }) {
  const { c } = useTheme();
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '3px 8px', borderRadius: 999,
      background: `${c.primary}18`, color: c.primary,
      fontSize: 10, fontWeight: 700, letterSpacing: 0.5,
    }}>
      {Icon.sparkle(10, c.primary)}
      {text}
    </div>
  );
}

function AttrRow({ attr, onClick, last, showError }) {
  const { c } = useTheme();
  const empty = !String(attr.value || '').trim();
  const hasError = showError && attr.required && empty;
  return (
    <button onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: 12,
      width: '100%', textAlign: 'left',
      padding: '14px 14px',
      background: hasError ? `${c.error}08` : 'transparent',
      border: 'none', cursor: 'pointer',
      borderBottom: last ? 'none' : `1px solid ${c.outlineVariant}33`,
      fontFamily: TOKENS.type.body,
    }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: hasError ? c.error : `${c.onSurface}99`, fontWeight: 600, letterSpacing: 0.3, textTransform: 'uppercase' }}>
          <span>{attr.name}</span>
          {attr.required ? (
            <span style={{ color: c.error, fontSize: 13, lineHeight: 1 }}>*</span>
          ) : (
            <span style={{
              padding: '1px 6px', borderRadius: 4,
              background: c.surfaceLow, color: `${c.onSurface}88`,
              fontSize: 9, fontWeight: 700, letterSpacing: 0.4,
            }}>ОПЦ.</span>
          )}
          {/* type hint */}
          {attr.type === 'multiselect' && (
            <span style={{ color: `${c.onSurface}66`, fontSize: 9, fontWeight: 600, letterSpacing: 0.3 }}>· КІЛЬКА</span>
          )}
        </div>
        <div style={{
          fontSize: 15, marginTop: 2, fontWeight: 500,
          color: hasError ? c.error : empty ? `${c.onSurface}66` : c.onSurface,
          fontStyle: empty ? 'italic' : 'normal',
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>{empty ? (attr.required ? 'Потрібно заповнити' : 'Не вказано') : attr.value}</div>
      </div>
      {Icon.chevron(14, hasError ? c.error : `${c.onSurface}55`)}
    </button>
  );
}

function PriceSlider({ data, setData }) {
  const { c } = useTheme();
  const min = data.priceMin, max = data.priceMax;
  const val = data.price;
  const pct = ((val - min) / (max - min)) * 100;

  const ref = React.useRef(null);
  const [drag, setDrag] = React.useState(false);

  const onMove = (clientX) => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    const p = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    const newVal = Math.round((min + p * (max - min)) / 10) * 10;
    setData({ ...data, price: newVal });
  };

  React.useEffect(() => {
    if (!drag) return;
    const mm = (e) => onMove(e.clientX);
    const tm = (e) => onMove(e.touches[0].clientX);
    const up = () => setDrag(false);
    window.addEventListener('mousemove', mm);
    window.addEventListener('touchmove', tm);
    window.addEventListener('mouseup', up);
    window.addEventListener('touchend', up);
    return () => {
      window.removeEventListener('mousemove', mm);
      window.removeEventListener('touchmove', tm);
      window.removeEventListener('mouseup', up);
      window.removeEventListener('touchend', up);
    };
  }, [drag, min, max]);

  return (
    <AppCard style={{ padding: '16px 16px 14px' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 14 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
            <input
              type="text"
              inputMode="numeric"
              value={val.toLocaleString('uk-UA')}
              onChange={(e) => {
                const digits = e.target.value.replace(/\D/g, '');
                const n = digits === '' ? 0 : parseInt(digits, 10);
                setData({ ...data, price: Math.min(max, Math.max(min, n)) });
              }}
              style={{
                fontFamily: TOKENS.type.display, fontSize: 32, fontWeight: 700,
                color: c.onSurface, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums',
                background: 'transparent', border: 'none', outline: 'none',
                width: `${Math.max(3, val.toLocaleString('uk-UA').length)}ch`,
                padding: 0, margin: 0,
              }}
            />
            <span style={{ fontFamily: TOKENS.type.display, fontSize: 32, fontWeight: 700, color: c.onSurface, letterSpacing: -0.5 }}>₴</span>
          </div>
          <div style={{ fontSize: 12, color: `${c.onSurface}99`, marginTop: 2 }}>
            Рекомендовано: <b style={{ color: c.primary }}>{data.priceSuggested.toLocaleString('uk-UA')} ₴</b>
          </div>
        </div>
        <div style={{
          padding: '4px 10px', borderRadius: 8,
          background: val < data.priceSuggested * 0.9 ? `${c.warning}22`
            : val > data.priceSuggested * 1.15 ? `${c.error}18`
            : `${c.success}22`,
          color: val < data.priceSuggested * 0.9 ? c.warning
            : val > data.priceSuggested * 1.15 ? c.error
            : c.success,
          fontSize: 11, fontWeight: 700, letterSpacing: 0.3, textTransform: 'uppercase',
        }}>
          {val < data.priceSuggested * 0.9 ? 'Занизько'
            : val > data.priceSuggested * 1.15 ? 'Зависоко'
            : 'У ринку'}
        </div>
      </div>

      {/* Slider */}
      <div ref={ref}
        onMouseDown={(e) => { setDrag(true); onMove(e.clientX); }}
        onTouchStart={(e) => { setDrag(true); onMove(e.touches[0].clientX); }}
        style={{
          position: 'relative', height: 28, cursor: 'pointer', touchAction: 'none',
          display: 'flex', alignItems: 'center',
        }}>
        <div style={{
          position: 'absolute', left: 0, right: 0, height: 6, borderRadius: 3,
          background: c.surfaceLow,
        }}/>
        <div style={{
          position: 'absolute', left: 0, width: `${pct}%`, height: 6, borderRadius: 3,
          background: `linear-gradient(90deg, ${c.primaryBright}, ${c.primary})`,
        }}/>
        {/* Suggested tick */}
        <div style={{
          position: 'absolute', left: `${((data.priceSuggested - min) / (max - min)) * 100}%`,
          transform: 'translateX(-50%)',
          width: 2, height: 12, background: c.warning, borderRadius: 1,
        }}/>
        {/* Handle */}
        <div style={{
          position: 'absolute', left: `${pct}%`, transform: 'translateX(-50%)',
          width: 24, height: 24, borderRadius: '50%',
          background: '#fff',
          boxShadow: `0 2px 8px ${c.primary}66, 0 0 0 3px ${c.primary}`,
          transition: drag ? 'none' : 'box-shadow 160ms',
        }}/>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, fontSize: 11, color: `${c.onSurface}77`, fontVariantNumeric: 'tabular-nums' }}>
        <span>{min.toLocaleString('uk-UA')} ₴</span>
        <span>{max.toLocaleString('uk-UA')} ₴</span>
      </div>
    </AppCard>
  );
}

function ConfirmSheet({ data, onClose, onPublish }) {
  const { c } = useTheme();
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 100 }}>
      <div onClick={onClose} style={{
        position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.4)',
        animation: 'fadeIn 220ms ease both',
      }}/>
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        background: c.surface, borderRadius: '26px 26px 0 0',
        padding: '12px 20px 40px',
        animation: 'slideUp 280ms cubic-bezier(0.22, 1, 0.36, 1) both',
      }}>
        <div style={{ width: 36, height: 5, borderRadius: 999, background: `${c.onSurface}22`, margin: '0 auto 20px' }}/>
        <h2 style={{
          fontFamily: TOKENS.type.display, fontSize: 22, fontWeight: 700,
          color: c.onSurface, margin: '0 0 6px', letterSpacing: -0.2,
        }}>Опублікувати зараз?</h2>
        <p style={{ fontSize: 14, color: `${c.onSurface}99`, margin: '0 0 20px', lineHeight: 1.45 }}>
          Оголошення з'явиться на Торгу протягом 1–2 хвилин.
        </p>

        <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
          <div style={{ width: 80, height: 80, flexShrink: 0 }}>
            <ProductShot kind={data.photos[0].kind} hue={data.photos[0].hue} radius={12}/>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontFamily: TOKENS.type.display, fontSize: 15, fontWeight: 600, color: c.onSurface, lineHeight: 1.3, textWrap: 'pretty' }}>{data.title}</div>
            <div style={{ fontSize: 12, color: `${c.onSurface}99`, marginTop: 4 }}>{data.category[data.category.length - 1]}</div>
            <div style={{ fontFamily: TOKENS.type.display, fontSize: 20, fontWeight: 700, color: c.primary, marginTop: 6 }}>
              {data.price.toLocaleString('uk-UA')} ₴
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <AppButton variant="success" full onClick={onPublish}>
            Так, опублікувати
          </AppButton>
          <AppButton variant="secondary" full onClick={onClose}>
            Повернутись до редагування
          </AppButton>
        </div>
      </div>
    </div>
  );
}

// ─── Publishing (short pulse) ──────────────────────────────────
function Publishing({ onDone }) {
  const { c } = useTheme();
  React.useEffect(() => {
    const id = setTimeout(onDone, 1600);
    return () => clearTimeout(id);
  }, []);
  return (
    <div style={{
      height: '100%', display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      background: c.background, color: c.onBackground,
      fontFamily: TOKENS.type.body, gap: 24,
    }}>
      <PulsingCircles size={140}>{Icon.share(44, c.onPrimary)}</PulsingCircles>
      <div style={{ fontFamily: TOKENS.type.display, fontSize: 22, fontWeight: 700 }}>Публікуємо…</div>
    </div>
  );
}

// ─── Success ───────────────────────────────────────────────────
function Success({ data, onNew, onView }) {
  const { c } = useTheme();
  const [show, setShow] = React.useState(false);
  React.useEffect(() => { setShow(true); }, []);

  return (
    <div style={{
      height: '100%', display: 'flex', flexDirection: 'column',
      background: c.background, color: c.onBackground,
      fontFamily: TOKENS.type.body, position: 'relative', overflow: 'hidden',
    }}>
      {/* confetti */}
      {show && [...Array(24)].map((_, i) => {
        const hue = [20, 38, 140, 200][i % 4];
        return (
          <div key={i} style={{
            position: 'absolute', top: -10,
            left: `${(i * 37) % 100}%`,
            width: 8, height: 8, borderRadius: 2,
            background: `hsl(${hue}, 70%, 55%)`,
            animation: `confetti 2.5s ${i * 60}ms ease-in forwards`,
            transform: `rotate(${i * 30}deg)`,
            zIndex: 1,
          }}/>
        );
      })}

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', padding: '32px 28px', zIndex: 2 }}>
        <div style={{
          width: 120, height: 120, borderRadius: '50%',
          background: c.success,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: `0 20px 40px -8px ${c.success}77`,
          animation: 'popIn 500ms cubic-bezier(0.34, 1.56, 0.64, 1) both',
        }}>{Icon.check(60, '#fff')}</div>

        <h1 style={{
          fontFamily: TOKENS.type.display, fontSize: 32, fontWeight: 700,
          letterSpacing: -0.5, margin: '28px 0 8px', textAlign: 'center',
          animation: 'fadeInUp 400ms 120ms ease both',
        }}>Опубліковано! 🎉</h1>
        <p style={{ fontSize: 16, color: `${c.onBackground}AA`, textAlign: 'center', margin: 0, maxWidth: 280, textWrap: 'pretty',
          animation: 'fadeInUp 400ms 180ms ease both',
        }}>
          Ваше оголошення тепер активне. Перші перегляди з'являться за 10–15 хвилин.
        </p>

        <AppCard style={{
          width: '100%', marginTop: 28, padding: 12,
          animation: 'fadeInUp 400ms 240ms ease both',
        }}>
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <div style={{ width: 64, height: 64, flexShrink: 0 }}>
              <ProductShot kind={data.photos[0].kind} hue={data.photos[0].hue} radius={10}/>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: c.onSurface, lineHeight: 1.25, textWrap: 'pretty' }}>{data.title}</div>
              <div style={{ fontFamily: TOKENS.type.display, fontSize: 17, fontWeight: 700, color: c.primary, marginTop: 4 }}>
                {data.price.toLocaleString('uk-UA')} ₴
              </div>
            </div>
          </div>
          <div style={{
            marginTop: 10, padding: '8px 12px', borderRadius: 10,
            background: c.surfaceLow,
            display: 'flex', alignItems: 'center', gap: 8,
            fontFamily: 'ui-monospace, "SF Mono", monospace', fontSize: 12,
            color: c.primary,
          }}>
            {Icon.link(14, c.primary)}
            <span style={{ flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              torh.ua/o/9JPK-2834
            </span>
            <button style={{
              background: c.primary, color: c.onPrimary, border: 'none',
              borderRadius: 6, padding: '4px 10px', cursor: 'pointer',
              fontFamily: TOKENS.type.body, fontSize: 11, fontWeight: 700,
            }}>Копіювати</button>
          </div>
        </AppCard>

        {/* Stats preview */}
        <div style={{
          display: 'flex', gap: 10, width: '100%', marginTop: 16,
          animation: 'fadeInUp 400ms 300ms ease both',
        }}>
          {[
            { k: 'Активне', v: 'Статус', sub: 'На модерації · до 2 хв' },
            { k: '90с', v: 'Загальний час', sub: 'Від фото до публікації' },
          ].map((s, i) => (
            <AppCard key={i} style={{ flex: 1, padding: '10px 12px' }}>
              <div style={{ fontFamily: TOKENS.type.display, fontSize: 20, fontWeight: 700, color: c.onSurface }}>{s.k}</div>
              <div style={{ fontSize: 12, fontWeight: 600, color: c.onSurface, marginTop: 2 }}>{s.v}</div>
              <div style={{ fontSize: 11, color: `${c.onSurface}99`, marginTop: 1 }}>{s.sub}</div>
            </AppCard>
          ))}
        </div>
      </div>

      <div style={{ padding: '0 20px 40px', display: 'flex', flexDirection: 'column', gap: 10, zIndex: 2 }}>
        <AppButton variant="primary" full onClick={onView}
          leading={Icon.eye(18, '#fff')}>
          Переглянути на Торгу
        </AppButton>
        <AppButton variant="secondary" full onClick={onNew}
          leading={Icon.plus(18)}>
          Створити ще одне
        </AppButton>
      </div>
    </div>
  );
}

Object.assign(window, { Preview, Publishing, Success });
