# AppTheme Color Mapping

SellSnap prototype tokens referenced by `brand/claude-design/project/ui.jsx`, `screens1.jsx`, `screens2.jsx`, `screens3.jsx`, and `sheets.jsx` map directly to `AppTheme.colors` as follows.

| Prototype token | AppTheme token | Light | Dark | Notes |
| --- | --- | --- | --- | --- |
| `primary` | `primary` | `#D0600A` | `#F08030` | Gradient start, brand orange |
| `primaryBright` | `primaryBright` | `#F08030` | `#D0600A` | Gradient end |
| `onPrimary` | `onPrimary` | `#FFFFFF` | `#1A0800` | Text/icons on orange surfaces |
| `background` | `background` | `#FFF8F2` | `#1E0D00` | Screen background |
| `onBackground` | `onBackground` | `#3A1F00` | `#FFEDD8` | Primary text on background |
| `surface` | `surface` | `#FFF8F2` | `#2A1400` | Base sheet/card surface |
| `surfaceLowest` | `surfaceLowest` | `#FFFFFF` | `#160600` | Raised cards and source buttons |
| `surfaceLow` | `surfaceLow` | `#FFF0E0` | `#2A1400` | Filled inputs and soft containers |
| `surfaceHigh` | `surfaceHigh` | `#FFD8B0` | `#472400` | Secondary buttons and stronger tonal surfaces |
| `onSurface` | `onSurface` | `#3A1F00` | `#FFEDD8` | Text/icons on surfaces |
| `secondaryContainer` | `secondaryContainer` | `#FFBF85` | `#5C2E00` | Neutral chip background |
| `onSecondaryContainer` | `onSecondaryContainer` | `#5C2E00` | `#FFBF85` | Neutral chip text/icon |
| `outline` | `outline` | `#D9A070` | `#7A5030` | Dashed/photo borders and dividers |
| `outlineVariant` | `outlineVariant` | `#D9A070` | `#7A5030` | Low-emphasis borders |
| `success` | `success` | `#1B8E5A` | `#4FD28A` | Success states |
| `warning` | `warning` | `#D97706` | `#F59E0B` | Warning state |
| `warningVariant` | `warningVariant` | `#FBBF24` | `#FBBF24` | Warm amber accent |
| `error` | `error` | `#BA1A1A` | `#FFB4AB` | Error state |

Additional app tokens still kept because existing screens use them:

- `surfaceVariant` for highlighted pills and legacy seller surfaces.
- `onSurfaceMuted` and `onSurfaceSoft` for low-emphasis text/icons.
- `onError` for Material error roles.

Removed from `AppTheme.colors` during the SIR-23 audit:

- Legacy aliases: `primaryContainer`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`.
- Unused extras: `surfaceSubtle`, `infoSurface`, `infoSurfaceVariant`.

Material `ColorScheme` container roles are now derived internally from the remaining SellSnap tokens in `AppColors.toMaterial()`.
