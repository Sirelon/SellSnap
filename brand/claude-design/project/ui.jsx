// Reusable UI primitives for SellSnap. Maps 1:1 to design system components.
// Uses window.TOKENS. Read theme from a React context (ThemeCtx).

const ThemeCtx = React.createContext({ c: window.TOKENS.light, dark: false });
const useTheme = () => React.useContext(ThemeCtx);

// ─── AppButton (primary w/ gradient) ───────────────────────────
function AppButton({ children, onClick, style, variant = 'primary', leading, trailing, disabled, full }) {
  const { c } = useTheme();
  const [press, setPress] = React.useState(false);
  const styles = {
    primary: {
      background: press
        ? `linear-gradient(135deg, ${c.primary} 0%, ${c.primary} 100%)`
        : `linear-gradient(135deg, ${c.primary} 0%, ${c.primaryBright} 100%)`,
      color: c.onPrimary,
      boxShadow: press ? 'none' : `0 8px 24px -8px ${c.primary}88, 0 2px 4px ${c.primary}33`,
    },
    secondary: {
      background: c.surfaceHigh,
      color: c.onSurface,
      boxShadow: press ? 'none' : `0 1px 2px ${c.onSurface}10`,
    },
    ghost: {
      background: 'transparent',
      color: c.primary,
    },
    magic: {
      background: `linear-gradient(135deg, ${c.primary} 0%, ${c.primaryBright} 50%, ${c.warningVariant} 100%)`,
      color: c.onPrimary,
      boxShadow: press ? 'none' : `0 10px 28px -6px ${c.primary}90, inset 0 1px 0 rgba(255,255,255,0.25)`,
    },
    success: {
      background: c.success,
      color: '#fff',
      boxShadow: press ? 'none' : `0 8px 20px -6px ${c.success}66`,
    }
  };
  const sty = styles[variant];
  return (
    <button
      onClick={disabled ? undefined : onClick}
      onMouseDown={() => setPress(true)}
      onMouseUp={() => setPress(false)}
      onMouseLeave={() => setPress(false)}
      onTouchStart={() => setPress(true)}
      onTouchEnd={() => setPress(false)}
      disabled={disabled}
      style={{
        ...sty,
        border: 'none',
        borderRadius: 18,
        height: 60,
        width: full ? '100%' : 'auto',
        padding: '0 28px',
        fontFamily: TOKENS.type.display,
        fontSize: 17,
        fontWeight: 700,
        letterSpacing: 0.1,
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 10,
        transform: press ? 'translateY(1px) scale(0.995)' : 'translateY(0)',
        transition: 'transform 120ms ease, box-shadow 180ms ease, background 180ms ease',
        ...style,
      }}
    >
      {leading}
      {children}
      {trailing}
    </button>
  );
}

// ─── AppCard ───────────────────────────────────────────────────
function AppCard({ children, style, onClick, elev = 2 }) {
  const { c } = useTheme();
  return (
    <div
      onClick={onClick}
      style={{
        background: c.surfaceLowest,
        color: c.onSurface,
        borderRadius: 16,
        boxShadow: `0 ${elev}px ${elev * 4}px ${c.onSurface}0A, 0 1px 2px ${c.onSurface}08`,
        cursor: onClick ? 'pointer' : 'default',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

// ─── AppChip ───────────────────────────────────────────────────
function AppChip({ children, tone = 'neutral', icon, style, onClick }) {
  const { c } = useTheme();
  const tones = {
    neutral: { bg: c.secondaryContainer, fg: c.onSecondaryContainer },
    primary: { bg: c.primary, fg: c.onPrimary },
    success: { bg: `${c.success}22`, fg: c.success },
    warning: { bg: `${c.warning}22`, fg: c.warning },
    soft: { bg: c.surfaceLow, fg: c.onSurface },
  };
  const t = tones[tone];
  return (
    <div onClick={onClick} style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '6px 12px', borderRadius: 999,
      background: t.bg, color: t.fg,
      fontFamily: TOKENS.type.body,
      fontSize: 12, fontWeight: 600, letterSpacing: 0.2,
      cursor: onClick ? 'pointer' : 'default',
      ...style,
    }}>
      {icon}
      {children}
    </div>
  );
}

// ─── AppInput (filled, single/multi-line) ──────────────────────
function AppInput({ label, value, onChange, placeholder, multiline, rows = 3, prefix, suffix, maxLength, style }) {
  const { c } = useTheme();
  const [focus, setFocus] = React.useState(false);
  const Tag = multiline ? 'textarea' : 'input';
  return (
    <div style={{
      background: c.surfaceLow,
      borderRadius: 12,
      padding: '10px 14px 12px',
      borderBottom: `2px solid ${focus ? c.primary : 'transparent'}`,
      transition: 'border-color 160ms ease',
      ...style,
    }}>
      {label && (
        <div style={{
          fontFamily: TOKENS.type.body, fontSize: 11,
          color: focus ? c.primary : `${c.onSurface}99`,
          fontWeight: 600, letterSpacing: 0.3, textTransform: 'uppercase',
          marginBottom: 4,
          transition: 'color 160ms ease',
        }}>{label}</div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {prefix && <span style={{ color: `${c.onSurface}88`, fontFamily: TOKENS.type.body, fontSize: 15 }}>{prefix}</span>}
        <Tag
          value={value}
          onChange={e => onChange?.(e.target.value)}
          placeholder={placeholder}
          rows={multiline ? rows : undefined}
          maxLength={maxLength}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          style={{
            flex: 1,
            background: 'transparent',
            border: 'none',
            outline: 'none',
            fontFamily: TOKENS.type.body,
            fontSize: 15,
            lineHeight: 1.5,
            color: c.onSurface,
            resize: 'none',
            width: '100%',
            padding: 0,
          }}
        />
        {suffix}
      </div>
      {maxLength && (
        <div style={{
          fontFamily: TOKENS.type.body, fontSize: 11,
          color: `${c.onSurface}77`,
          textAlign: 'right', marginTop: 4,
        }}>{value?.length || 0} / {maxLength}</div>
      )}
    </div>
  );
}

// ─── IconWithBackground ────────────────────────────────────────
function IconBadge({ color, children, size = 44, radius = 12 }) {
  return (
    <div style={{
      width: size, height: size, borderRadius: radius,
      background: `${color}22`, color: color,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }}>{children}</div>
  );
}

// ─── Pulsing circles — for AI moment ───────────────────────────
function PulsingCircles({ children, size = 128 }) {
  const { c } = useTheme();
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      background: `${c.primary}22`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      animation: 'pulseScale 1200ms ease-in-out infinite alternate',
      position: 'relative',
    }}>
      <div style={{
        width: size * 0.75, height: size * 0.75, borderRadius: '50%',
        background: `${c.primary}55`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <div style={{
          width: size * 0.55, height: size * 0.55, borderRadius: '50%',
          background: `linear-gradient(135deg, ${c.primaryBright}, ${c.primary})`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: c.onPrimary,
          boxShadow: `0 8px 24px ${c.primary}66`,
        }}>{children}</div>
      </div>
    </div>
  );
}

// ─── Section header ────────────────────────────────────────────
function SectionHeader({ title, trailing }) {
  const { c } = useTheme();
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '8px 4px 10px',
    }}>
      <div style={{
        fontFamily: TOKENS.type.body, fontSize: 12, fontWeight: 700,
        textTransform: 'uppercase', letterSpacing: 0.8,
        color: `${c.onSurface}99`,
      }}>{title}</div>
      {trailing}
    </div>
  );
}

// ─── Cell (list item) ──────────────────────────────────────────
function Cell({ leading, title, supporting, trailing, onClick, style }) {
  const { c } = useTheme();
  return (
    <div onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: 12,
      padding: '12px 16px',
      cursor: onClick ? 'pointer' : 'default',
      ...style,
    }}>
      {leading}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontFamily: TOKENS.type.body, fontSize: 15, fontWeight: 500, color: c.onSurface, lineHeight: 1.3 }}>{title}</div>
        {supporting && <div style={{ fontFamily: TOKENS.type.body, fontSize: 13, color: `${c.onSurface}99`, marginTop: 2, lineHeight: 1.35 }}>{supporting}</div>}
      </div>
      {trailing}
    </div>
  );
}

// ─── Glyph icons (stroke, 24px, SF-ish) ─────────────────────────
const Icon = {
  camera: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M3 8.5A2 2 0 015 6.5h2.2L8.5 4.5h7l1.3 2H19a2 2 0 012 2V18a2 2 0 01-2 2H5a2 2 0 01-2-2V8.5z" stroke={col} strokeWidth="1.6" strokeLinejoin="round"/><circle cx="12" cy="13" r="3.6" stroke={col} strokeWidth="1.6"/></svg>,
  gallery: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="14" rx="2.5" stroke={col} strokeWidth="1.6"/><path d="M3 15l5-4 4 3 3-2 6 5" stroke={col} strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round"/><circle cx="8" cy="10" r="1.4" fill={col}/></svg>,
  sparkle: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z" fill={col}/><path d="M19 16l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2z" fill={col}/></svg>,
  check: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M5 12.5l4.5 4.5L19 7.5" stroke={col} strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  chevron: (s=14, col='currentColor') => <svg width={s/2} height={s} viewBox="0 0 7 14" fill="none"><path d="M1 1l5 6-5 6" stroke={col} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  chevronDown: (s=14, col='currentColor') => <svg width={s} height={s/2} viewBox="0 0 14 7" fill="none"><path d="M1 1l6 5 6-5" stroke={col} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  close: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M6 6l12 12M18 6L6 18" stroke={col} strokeWidth="2" strokeLinecap="round"/></svg>,
  plus: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke={col} strokeWidth="2" strokeLinecap="round"/></svg>,
  pin: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M12 22s7-7.2 7-12a7 7 0 10-14 0c0 4.8 7 12 7 12z" stroke={col} strokeWidth="1.6" strokeLinejoin="round"/><circle cx="12" cy="10" r="2.5" stroke={col} strokeWidth="1.6"/></svg>,
  tag: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M3 12V4h8l10 10-8 8L3 12z" stroke={col} strokeWidth="1.6" strokeLinejoin="round"/><circle cx="8" cy="8" r="1.4" fill={col}/></svg>,
  arrowRight: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M5 12h14m-5-5l5 5-5 5" stroke={col} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  arrowLeft: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M19 12H5m5 5l-5-5 5-5" stroke={col} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  bolt: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M13 2L4 14h7l-1 8 9-12h-7l1-8z" fill={col}/></svg>,
  shield: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M12 2l8 3v6c0 5-3.5 9.5-8 11-4.5-1.5-8-6-8-11V5l8-3z" stroke={col} strokeWidth="1.6" strokeLinejoin="round"/><path d="M8.5 12l2.5 2.5 4.5-5" stroke={col} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  eye: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" stroke={col} strokeWidth="1.6"/><circle cx="12" cy="12" r="3" stroke={col} strokeWidth="1.6"/></svg>,
  edit: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M4 20h4l10-10-4-4L4 16v4z" stroke={col} strokeWidth="1.6" strokeLinejoin="round"/><path d="M14 6l4 4" stroke={col} strokeWidth="1.6"/></svg>,
  trash: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" stroke={col} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  link: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M10 14a4 4 0 005.66 0l3-3a4 4 0 00-5.66-5.66l-1 1M14 10a4 4 0 00-5.66 0l-3 3a4 4 0 105.66 5.66l1-1" stroke={col} strokeWidth="1.6" strokeLinecap="round"/></svg>,
  user: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="4" stroke={col} strokeWidth="1.6"/><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7" stroke={col} strokeWidth="1.6" strokeLinecap="round"/></svg>,
  clock: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke={col} strokeWidth="1.6"/><path d="M12 7v5l3.5 2" stroke={col} strokeWidth="1.6" strokeLinecap="round"/></svg>,
  share: (s=24, col='currentColor') => <svg width={s} height={s} viewBox="0 0 24 24" fill="none"><path d="M12 15V3m0 0l-4 4m4-4l4 4M5 13v6a2 2 0 002 2h10a2 2 0 002-2v-6" stroke={col} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/></svg>,
};

Object.assign(window, {
  ThemeCtx, useTheme, AppButton, AppCard, AppChip, AppInput,
  IconBadge, PulsingCircles, SectionHeader, Cell, Icon,
});
