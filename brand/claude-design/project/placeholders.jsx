// Placeholder imagery — striped SVG product shots with mono labels.
// Used throughout preview carousel & gallery picker.

window.Placeholder = function Placeholder({ label = 'product', hue = 28, style, radius = 16 }) {
  const id = React.useMemo(() => `ph-${Math.random().toString(36).slice(2, 9)}`, []);
  const bg1 = `hsl(${hue}, 36%, 86%)`;
  const bg2 = `hsl(${hue}, 32%, 78%)`;
  const stripe = `hsl(${hue}, 30%, 72%)`;
  const text = `hsl(${hue}, 40%, 32%)`;
  return (
    <div style={{
      position: 'relative', overflow: 'hidden', borderRadius: radius,
      background: `linear-gradient(135deg, ${bg1}, ${bg2})`,
      width: '100%', height: '100%', ...style,
    }}>
      <svg width="100%" height="100%" style={{ position: 'absolute', inset: 0 }}>
        <defs>
          <pattern id={id} width="14" height="14" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <line x1="0" y1="0" x2="0" y2="14" stroke={stripe} strokeWidth="1"/>
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill={`url(#${id})`} opacity="0.55"/>
      </svg>
      <div style={{
        position: 'absolute', inset: 0,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: 'ui-monospace, "SF Mono", Menlo, monospace',
        fontSize: 11, fontWeight: 500, color: text, letterSpacing: 0.5,
      }}>{label}</div>
    </div>
  );
}

// Product 'hero' — a soft silhouette that looks more real than a stripe.
// Paired with stripes gives the page photographic feel.
window.ProductShot = function ProductShot({ kind = 'jacket', hue = 28, style, radius = 16, showLabel = false }) {
  const id = React.useMemo(() => `ps-${Math.random().toString(36).slice(2, 9)}`, []);
  const sil = {
    jacket: <path d="M40 38 L60 28 L80 22 L100 22 L120 28 L140 38 L150 60 L145 140 L140 155 L130 158 L60 158 L50 155 L45 140 L40 60 Z M60 28 L80 50 L90 46 L100 50 L120 28" stroke="currentColor" strokeWidth="2" fill="none" strokeLinejoin="round"/>,
    phone:   <rect x="60" y="20" width="70" height="140" rx="12" stroke="currentColor" strokeWidth="2" fill="none"/>,
    chair:   <path d="M40 30 L150 30 L145 100 L120 100 L120 160 L115 160 L115 100 L75 100 L75 160 L70 160 L70 100 L45 100 Z" stroke="currentColor" strokeWidth="2" fill="none" strokeLinejoin="round"/>,
    shoe:    <path d="M30 110 C30 120, 35 140, 55 140 L150 140 C165 140, 170 130, 168 118 C165 105, 145 100, 130 95 C115 90, 100 80, 85 65 L70 55 C60 55, 45 60, 38 75 C33 90, 30 100, 30 110 Z" stroke="currentColor" strokeWidth="2" fill="none" strokeLinejoin="round"/>,
    bag:     <path d="M50 60 L130 60 L138 150 L42 150 Z M70 60 C70 40, 110 40, 110 60" stroke="currentColor" strokeWidth="2" fill="none" strokeLinejoin="round"/>,
    lamp:    <path d="M65 25 L115 25 L135 70 L45 70 Z M90 70 L90 150 M65 150 L115 150" stroke="currentColor" strokeWidth="2" fill="none" strokeLinejoin="round"/>,
  }[kind] || <circle cx="90" cy="90" r="55" stroke="currentColor" strokeWidth="2" fill="none"/>;
  return (
    <div style={{
      position: 'relative', overflow: 'hidden', borderRadius: radius,
      background: `radial-gradient(ellipse at 50% 35%, hsl(${hue},30%,92%) 0%, hsl(${hue},30%,78%) 70%, hsl(${hue},35%,70%) 100%)`,
      width: '100%', height: '100%', ...style,
    }}>
      <svg viewBox="0 0 180 180" width="100%" height="100%" preserveAspectRatio="xMidYMid meet"
        style={{ position: 'absolute', inset: 0, color: `hsl(${hue},40%,38%)`, opacity: 0.85 }}>
        <defs>
          <pattern id={id} width="10" height="10" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <line x1="0" y1="0" x2="0" y2="10" stroke={`hsl(${hue},30%,64%)`} strokeWidth="0.8"/>
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill={`url(#${id})`} opacity="0.35"/>
        {sil}
      </svg>
      {showLabel && (
        <div style={{
          position: 'absolute', left: 10, bottom: 10,
          padding: '3px 8px', borderRadius: 6,
          background: 'rgba(255,255,255,0.6)', backdropFilter: 'blur(6px)',
          fontFamily: 'ui-monospace, "SF Mono", monospace', fontSize: 10,
          color: `hsl(${hue},50%,28%)`,
        }}>{kind}.jpg</div>
      )}
    </div>
  );
};
