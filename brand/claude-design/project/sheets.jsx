// Category picker + Attribute editor sheets

// ── Category tree (Ukrainian marketplace-style) ────────────────
const CATEGORY_TREE = [
  { name: 'Мода і стиль', icon: '👗', children: [
    { name: 'Жіночий одяг', children: [
      { name: 'Куртки та пуховики', children: [
        { name: 'Зимові' }, { name: 'Демісезонні' }, { name: 'Пухові' }, { name: 'Парки' },
      ]},
      { name: 'Сукні' }, { name: 'Взуття' }, { name: 'Сумки' }, { name: 'Аксесуари' },
    ]},
    { name: 'Чоловічий одяг', children: [
      { name: 'Куртки' }, { name: 'Сорочки' }, { name: 'Штани' }, { name: 'Взуття' },
    ]},
    { name: 'Дитячий одяг', children: [{ name: 'Для дівчаток' }, { name: 'Для хлопчиків' }] },
  ]},
  { name: 'Електроніка', icon: '📱', children: [
    { name: 'Телефони та смартфони' }, { name: 'Ноутбуки' }, { name: 'Аудіо' }, { name: 'Телевізори' },
  ]},
  { name: 'Дім і сад', icon: '🏡', children: [
    { name: 'Меблі' }, { name: 'Посуд' }, { name: 'Декор' },
  ]},
  { name: 'Транспорт', icon: '🚗', children: [
    { name: 'Легкові авто' }, { name: 'Мото' }, { name: 'Велосипеди' },
  ]},
  { name: 'Хобі, спорт', icon: '⚽', children: [
    { name: 'Спорттовари' }, { name: 'Туризм' }, { name: 'Музичні інструменти' },
  ]},
  { name: 'Для дітей', icon: '🧸', children: [
    { name: 'Іграшки' }, { name: 'Коляски' }, { name: 'Книги' },
  ]},
];

// Walk tree from root, following the path array
function findNode(path) {
  let arr = CATEGORY_TREE, node = null;
  for (const seg of path) {
    node = arr?.find(n => n.name === seg);
    if (!node) return null;
    arr = node.children;
  }
  return node;
}
function getChildrenAtDepth(path, depth) {
  let arr = CATEGORY_TREE;
  for (let i = 0; i < depth; i++) {
    const node = arr?.find(n => n.name === path[i]);
    if (!node?.children) return null;
    arr = node.children;
  }
  return arr;
}

// ── Sheet scaffold ─────────────────────────────────────────────
function BottomSheet({ title, subtitle, onClose, children, footer, tall }) {
  const { c } = useTheme();
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 120 }}>
      <div onClick={onClose} style={{
        position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.45)',
        animation: 'fadeIn 220ms ease both',
      }}/>
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        height: tall ? '92%' : 'auto', maxHeight: '92%',
        background: c.surface, borderRadius: '26px 26px 0 0',
        animation: 'slideUp 280ms cubic-bezier(0.22, 1, 0.36, 1) both',
        display: 'flex', flexDirection: 'column',
      }}>
        <div style={{ width: 36, height: 5, borderRadius: 999, background: `${c.onSurface}22`, margin: '10px auto 12px' }}/>
        <div style={{
          padding: '0 20px 12px', display: 'flex', alignItems: 'flex-start', gap: 10,
        }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontFamily: TOKENS.type.display, fontSize: 20, fontWeight: 700, color: c.onSurface, letterSpacing: -0.2 }}>{title}</div>
            {subtitle && <div style={{ fontSize: 13, color: `${c.onSurface}99`, marginTop: 2 }}>{subtitle}</div>}
          </div>
          <button onClick={onClose} style={{
            width: 32, height: 32, borderRadius: 10, border: 'none', background: c.surfaceLow,
            color: c.onSurface, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
          }}>{Icon.close(16, c.onSurface)}</button>
        </div>
        <div style={{ flex: 1, overflow: 'auto', padding: '0 20px 8px' }}>
          {children}
        </div>
        {footer && (
          <div style={{ padding: '12px 20px 32px', borderTop: `1px solid ${c.outlineVariant}33` }}>
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Category picker ────────────────────────────────────────────
function CategoryPicker({ value, onClose, onChange }) {
  const { c } = useTheme();
  const [path, setPath] = React.useState(value ?? []);
  const [query, setQuery] = React.useState('');

  // Current drill-down options (next column to pick from)
  const depth = path.length;
  const options = getChildrenAtDepth(path, depth) ?? null;
  const currentNode = depth > 0 ? findNode(path) : null;

  // Flat search
  const searchResults = React.useMemo(() => {
    if (!query.trim()) return null;
    const q = query.trim().toLowerCase();
    const out = [];
    const walk = (arr, trail) => {
      for (const n of arr) {
        const nt = [...trail, n.name];
        if (n.name.toLowerCase().includes(q)) out.push(nt);
        if (n.children) walk(n.children, nt);
      }
    };
    walk(CATEGORY_TREE, []);
    return out.slice(0, 30);
  }, [query]);

  // When at a leaf path, show siblings (parent's children) with current highlighted
  const displayDepth = options ? depth : depth - 1;
  const displayOptions = options ?? getChildrenAtDepth(path, depth - 1) ?? CATEGORY_TREE;

  const pick = (name) => {
    const basePath = options ? path : path.slice(0, -1);
    const next = [...basePath, name];
    const node = findNode(next);
    if (node?.children) setPath(next);
    else { onChange(next); onClose(); }
  };

  const pickFromSearch = (trail) => {
    onChange(trail); onClose();
  };

  const canFinishHere = currentNode && !currentNode.children;

  return (
    <BottomSheet
      title="Категорія"
      subtitle="Виберіть найточнішу категорію"
      onClose={onClose}
      tall
      footer={
        <div style={{ display: 'flex', gap: 10 }}>
          <AppButton variant="secondary" onClick={() => setPath([])} style={{ height: 48, flex: '0 0 auto', padding: '0 18px' }}>
            Скинути
          </AppButton>
          <AppButton
            variant="primary" full
            disabled={path.length === 0}
            onClick={() => { onChange(path); onClose(); }}
            style={{ height: 48 }}
          >
            Обрати{path.length ? ` · ${path[path.length-1]}` : ''}
          </AppButton>
        </div>
      }
    >
      {/* Search */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '10px 12px', borderRadius: 12,
        background: c.surfaceLow, marginBottom: 14,
      }}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
          <circle cx="11" cy="11" r="7" stroke={`${c.onSurface}77`} strokeWidth="2"/>
          <path d="M17 17l4 4" stroke={`${c.onSurface}77`} strokeWidth="2" strokeLinecap="round"/>
        </svg>
        <input
          value={query} onChange={e => setQuery(e.target.value)}
          placeholder="Пошук категорії"
          style={{
            flex: 1, background: 'transparent', border: 'none', outline: 'none',
            fontFamily: TOKENS.type.body, fontSize: 15, color: c.onSurface,
          }}
        />
        {query && (
          <button onClick={() => setQuery('')} style={{
            background: 'none', border: 'none', cursor: 'pointer', padding: 4, display: 'flex',
          }}>{Icon.close(14, `${c.onSurface}77`)}</button>
        )}
      </div>

      {/* Breadcrumbs */}
      {!query && path.length > 0 && (
        <div style={{
          display: 'flex', alignItems: 'center', gap: 6,
          marginBottom: 12, overflowX: 'auto', whiteSpace: 'nowrap',
          paddingBottom: 4,
        }}>
          <button onClick={() => setPath([])} style={{
            border: 'none', background: 'none', color: c.primary,
            cursor: 'pointer', fontFamily: TOKENS.type.body, fontSize: 13, fontWeight: 600, padding: 0, flexShrink: 0,
          }}>Усі</button>
          {path.map((p, i) => (
            <React.Fragment key={i}>
              <span style={{ color: `${c.onSurface}55`, fontSize: 12, flexShrink: 0 }}>›</span>
              <button onClick={() => setPath(path.slice(0, i))} style={{
                border: 'none', background: 'none',
                color: i === path.length - 1 ? c.onSurface : c.primary,
                fontWeight: i === path.length - 1 ? 700 : 600,
                cursor: 'pointer', fontFamily: TOKENS.type.body, fontSize: 13, padding: 0, flexShrink: 0,
              }}>{p}</button>
            </React.Fragment>
          ))}
        </div>
      )}

      {/* Results */}
      {searchResults ? (
        searchResults.length === 0 ? (
          <div style={{ padding: '40px 20px', textAlign: 'center', color: `${c.onSurface}99`, fontSize: 14 }}>
            Нічого не знайдено
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {searchResults.map((trail, i) => {
              const name = trail[trail.length - 1];
              const parents = trail.slice(0, -1).join(' › ');
              return (
                <button key={i} onClick={() => pickFromSearch(trail)} style={{
                  display: 'flex', alignItems: 'center', gap: 10,
                  padding: '12px 10px', borderRadius: 10, border: 'none',
                  background: 'transparent', textAlign: 'left', cursor: 'pointer',
                  fontFamily: TOKENS.type.body,
                }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 15, fontWeight: 500, color: c.onSurface }}>{name}</div>
                    <div style={{ fontSize: 12, color: `${c.onSurface}88`, marginTop: 2 }}>{parents}</div>
                  </div>
                  {Icon.chevron(14, `${c.onSurface}55`)}
                </button>
              );
            })}
          </div>
        )
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {displayOptions.map((n, i) => {
            const isActive = path[displayDepth] === n.name;
            const isLeaf = !n.children;
            return (
              <button key={n.name} onClick={() => pick(n.name)} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '14px 10px', borderRadius: 10, border: 'none',
                borderBottom: i < displayOptions.length - 1 ? `1px solid ${c.outlineVariant}22` : 'none',
                background: isActive ? `${c.primary}10` : 'transparent', textAlign: 'left', cursor: 'pointer',
                fontFamily: TOKENS.type.body,
              }}>
                {n.icon && (
                  <div style={{
                    width: 36, height: 36, borderRadius: 10,
                    background: c.surfaceLow, display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 20,
                  }}>{n.icon}</div>
                )}
                <div style={{ flex: 1, fontSize: 15, fontWeight: isActive ? 700 : 500, color: isActive ? c.primary : c.onSurface }}>{n.name}</div>
                {isActive && !isLeaf && Icon.check(16, c.primary)}
                {isLeaf ? (
                  isActive
                    ? <span style={{ fontSize: 11, fontWeight: 700, color: c.primary, letterSpacing: 0.3, display: 'inline-flex', alignItems: 'center', gap: 4 }}>{Icon.check(14, c.primary)} ОБРАНО</span>
                    : <span style={{ fontSize: 11, fontWeight: 700, color: c.success, letterSpacing: 0.3 }}>ОБРАТИ</span>
                ) : (!isActive && Icon.chevron(14, `${c.onSurface}55`))}
              </button>
            );
          })}
        </div>
      )}
    </BottomSheet>
  );
}

// ── Attribute editor ───────────────────────────────────────────
function AttrEditSheet({ attr, onClose, onSave }) {
  const { c } = useTheme();
  const [val, setVal] = React.useState(attr.value);
  const [multi, setMulti] = React.useState(() => {
    if (attr.type !== 'multiselect') return [];
    return (attr.value || '').split(',').map(s => s.trim()).filter(Boolean);
  });
  const [query, setQuery] = React.useState('');

  const filtered = React.useMemo(() => {
    if (!attr.options) return [];
    if (!query.trim()) return attr.options;
    const q = query.trim().toLowerCase();
    return attr.options.filter(o => o.toLowerCase().includes(q));
  }, [attr.options, query]);

  const toggleMulti = (opt) => {
    setMulti(m => m.includes(opt) ? m.filter(x => x !== opt) : [...m, opt]);
  };

  const finish = () => {
    if (attr.type === 'multiselect') {
      onSave(multi.join(', '));
    } else if (attr.type === 'number') {
      const cleaned = String(val ?? '').replace(/[^\d.,-]/g, '').trim();
      onSave(cleaned ? `${cleaned}${attr.unit ? ' ' + attr.unit : ''}` : '');
    } else {
      onSave(val ?? '');
    }
    onClose();
  };

  const empty = attr.type === 'multiselect' ? multi.length === 0 : !String(val ?? '').trim();
  const saveDisabled = attr.required && empty;

  return (
    <BottomSheet
      title={attr.name}
      subtitle={attr.required ? 'Обов\u2019язкове поле' : 'Необов\u2019язкове поле'}
      onClose={onClose}
      tall={attr.type === 'select' || attr.type === 'multiselect'}
      footer={
        <div style={{ display: 'flex', gap: 10 }}>
          {!attr.required && (
            <AppButton variant="secondary" onClick={() => { onSave(''); onClose(); }} style={{ height: 48, flex: '0 0 auto', padding: '0 18px' }}>
              Очистити
            </AppButton>
          )}
          <AppButton variant="primary" full disabled={saveDisabled} onClick={finish} style={{ height: 48 }}>
            Зберегти{attr.type === 'multiselect' && multi.length ? ` · ${multi.length}` : ''}
          </AppButton>
        </div>
      }
    >
      {/* Required banner */}
      {attr.required && empty && (
        <div style={{
          padding: '10px 12px', borderRadius: 10,
          background: `${c.error}14`, color: c.error,
          display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12,
          fontSize: 13, fontWeight: 500,
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="9" stroke={c.error} strokeWidth="1.8"/>
            <path d="M12 7v6M12 16v.5" stroke={c.error} strokeWidth="2" strokeLinecap="round"/>
          </svg>
          Обов'язкове поле для публікації
        </div>
      )}

      {/* SELECT */}
      {attr.type === 'select' && (
        <>
          {attr.options.length > 6 && (
            <div style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '10px 12px', borderRadius: 12,
              background: c.surfaceLow, marginBottom: 10,
            }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                <circle cx="11" cy="11" r="7" stroke={`${c.onSurface}77`} strokeWidth="2"/>
                <path d="M17 17l4 4" stroke={`${c.onSurface}77`} strokeWidth="2" strokeLinecap="round"/>
              </svg>
              <input value={query} onChange={e => setQuery(e.target.value)}
                placeholder="Пошук" style={{
                flex: 1, background: 'transparent', border: 'none', outline: 'none',
                fontFamily: TOKENS.type.body, fontSize: 15, color: c.onSurface,
              }}/>
            </div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {filtered.map(opt => {
              const sel = val === opt;
              return (
                <button key={opt} onClick={() => setVal(opt)} style={{
                  display: 'flex', alignItems: 'center', gap: 12,
                  padding: '14px 14px', borderRadius: 12, border: 'none',
                  background: sel ? `${c.primary}12` : 'transparent',
                  textAlign: 'left', cursor: 'pointer',
                  fontFamily: TOKENS.type.body,
                }}>
                  <div style={{ flex: 1, fontSize: 15, fontWeight: sel ? 600 : 500, color: sel ? c.primary : c.onSurface }}>{opt}</div>
                  <div style={{
                    width: 22, height: 22, borderRadius: '50%',
                    border: `2px solid ${sel ? c.primary : `${c.onSurface}33`}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    {sel && <div style={{ width: 10, height: 10, borderRadius: '50%', background: c.primary }}/>}
                  </div>
                </button>
              );
            })}
          </div>
        </>
      )}

      {/* MULTISELECT */}
      {attr.type === 'multiselect' && (
        <>
          {multi.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 12 }}>
              {multi.map(m => (
                <div key={m} style={{
                  display: 'inline-flex', alignItems: 'center', gap: 6,
                  padding: '6px 6px 6px 12px', borderRadius: 999,
                  background: `${c.primary}18`, color: c.primary,
                  fontFamily: TOKENS.type.body, fontSize: 13, fontWeight: 600,
                }}>
                  {m}
                  <button onClick={() => toggleMulti(m)} style={{
                    width: 20, height: 20, borderRadius: '50%', border: 'none',
                    background: `${c.primary}22`, color: c.primary, cursor: 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>{Icon.close(12, c.primary)}</button>
                </div>
              ))}
            </div>
          )}
          <div style={{ fontSize: 11, fontWeight: 700, color: `${c.onSurface}77`, letterSpacing: 0.5, textTransform: 'uppercase', padding: '8px 4px 6px' }}>
            Оберіть один або кілька
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {filtered.map(opt => {
              const sel = multi.includes(opt);
              return (
                <button key={opt} onClick={() => toggleMulti(opt)} style={{
                  display: 'flex', alignItems: 'center', gap: 12,
                  padding: '12px 14px', borderRadius: 12, border: 'none',
                  background: sel ? `${c.primary}12` : 'transparent',
                  textAlign: 'left', cursor: 'pointer',
                  fontFamily: TOKENS.type.body,
                }}>
                  <div style={{
                    width: 22, height: 22, borderRadius: 6,
                    border: `2px solid ${sel ? c.primary : `${c.onSurface}33`}`,
                    background: sel ? c.primary : 'transparent',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    {sel && Icon.check(14, c.onPrimary)}
                  </div>
                  <div style={{ flex: 1, fontSize: 15, fontWeight: sel ? 600 : 500, color: c.onSurface }}>{opt}</div>
                </button>
              );
            })}
          </div>
        </>
      )}

      {/* INPUT (free text) */}
      {attr.type === 'input' && (
        <div>
          <AppInput value={val || ''} onChange={setVal} placeholder={attr.placeholder || ''}/>
          {attr.hint && (
            <div style={{ fontSize: 12, color: `${c.onSurface}88`, marginTop: 8 }}>{attr.hint}</div>
          )}
        </div>
      )}

      {/* NUMBER */}
      {attr.type === 'number' && (
        <div>
          <div style={{
            background: c.surfaceLow, borderRadius: 14,
            padding: '18px 16px', display: 'flex', alignItems: 'baseline', gap: 6,
          }}>
            <input
              type="text" inputMode="decimal"
              value={String(val ?? '').replace(new RegExp('\\\\s*' + (attr.unit || '') + '\\\\s*$'), '')}
              onChange={e => setVal(e.target.value.replace(/[^\d.,]/g, ''))}
              placeholder={attr.placeholder || '0'}
              autoFocus
              style={{
                flex: 1, background: 'transparent', border: 'none', outline: 'none',
                fontFamily: TOKENS.type.display, fontSize: 32, fontWeight: 700,
                color: c.onSurface, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums',
                padding: 0, margin: 0, width: '100%',
              }}
            />
            {attr.unit && (
              <span style={{ fontFamily: TOKENS.type.display, fontSize: 22, fontWeight: 600, color: `${c.onSurface}88` }}>{attr.unit}</span>
            )}
          </div>
          {attr.hint && (
            <div style={{ fontSize: 12, color: `${c.onSurface}88`, marginTop: 8 }}>{attr.hint}</div>
          )}
        </div>
      )}
    </BottomSheet>
  );
}

Object.assign(window, { CategoryPicker, AttrEditSheet, BottomSheet });
