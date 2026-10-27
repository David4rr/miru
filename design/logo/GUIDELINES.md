# Miru — Brand & Logo Guidelines

## 1. Brand Identity & Overview

**Miru** (Japanese: **見る**, *to see / to look / to watch*) is a high-performance Android anime streaming client powered by a native embedded Rust engine (`libmiru_core.so`), 120Hz fluid motion, AniSkip smart intro/outro skipping, and pitch-black OLED aesthetics.

The brand mark, **The Chrono Eye**, merges the aerodynamic contour of an eye aperture with the universal 30° equilateral play triangle pupil, creating an immediate, unmistakable visual anchor for modern anime streaming.

---

## 2. The Mark: The Chrono Eye

The symbol is constructed on a **256 × 256** coordinate grid using pure vector bezier curves with mathematical symmetry across the horizontal axis:

- **Outer Contour**: Almond eye aperture spanning $x \in [28, 228]$ and $y \in [44, 212]$ with smooth extrema anchors.
- **Inner Counter**: Aperture clearance spanning $x \in [68, 188]$ and $y \in [76, 180]$.
- **Pupil Core**: Equilateral triangle with exact 30° and 150° diagonals pointing right, base at $x = 110$ ($y \in [98, 158]$) and apex at $(162, 128)$.
- **Production Audit**: 100/100 readiness score with zero near-miss angles and pure compound path geometry (`fill-rule="evenodd"`).

---

## 3. Official Color Palette

| Name | Hex | RGB | Purpose |
| :--- | :--- | :--- | :--- |
| **Crunchyroll Orange** | `#F47521` | `244, 117, 33` | Primary brand accent, symbol fill, active states |
| **Dark OLED** | `#090A0E` | `9, 10, 14` | Primary background, surface canvas, dark wordmark |
| **Electric White** | `#FFFFFF` | `255, 255, 255` | Reversed mark, wordmark on dark, high-contrast text |
| **Slate Surface** | `#141519` | `20, 21, 25` | Card containers, bottom navigation bar |
| **Muted Grey** | `#8B8E99` | `139, 142, 153` | Subtitles, metadata, secondary elements |

---

## 4. Clear Space & Minimum Sizes

### Clear Space Rule
Maintain an exclusion zone around the mark equal to $1\times$ the height of the inner pupil triangle ($H_p \approx 60\text{ px}$ on the 256 grid, or $25\%$ of the total mark height). No typography, borders, or graphics may enter this zone.

### Minimum Reproduction Limits
- **Symbol (Digital)**: $16 \times 16\text{ px}$ (browser favicon, notification icon).
- **Symbol (Print)**: $5 \times 5\text{ mm}$.
- **Horizontal Lockup (Digital)**: $24\text{ px}$ height / $68\text{ px}$ width minimum.
- **Stacked Lockup (Digital)**: $48 \times 48\text{ px}$ minimum.

---

## 5. Asset Hierarchy & Variants

All production assets are located in `design/logo/`:

### Master Files
- `miru-symbol-color.svg` — Primary brand mark in `#F47521`.
- `miru-symbol.svg` — Master silhouette mark in `#090A0E`.
- `miru-lockup.svg` — Horizontal lockup with orange symbol and dark wordmark.
- `miru-lockup-dark.svg` — Horizontal lockup with orange symbol and white wordmark (for dark/OLED backgrounds).
- `miru-stacked.svg` — Centered vertical lockup for badges, cards, and packaging.

### Distribution Kit (`design/logo/dist/`)
- **Monochrome & Contrast**:
  - `miru-symbol-black.svg` & PNGs ($32, 64, 128, 256, 512, 1024$).
  - `miru-symbol-white.svg` & PNGs ($32, 64, 128, 256, 512, 1024$).
  - `miru-symbol-mono-f47521.svg` & PNGs.
  - `miru-symbol-square.svg` (social avatars, GitHub organization icon).
  - `miru-symbol-app-icon.svg` (squircle tile on `#090A0E`).
  - `miru-symbol-round-icon.svg` (circular launcher tile).
- **Web & PWA Icons**:
  - `favicon.ico`, `favicon.svg`, `favicon-16.png`, `favicon-32.png`, `favicon-48.png`.
  - `apple-touch-icon.png` ($180 \times 180$).
  - `icon-192.png`, `icon-512.png`, `maskable-512.png`.
  - `site.webmanifest` & `head-snippet.html`.
- **Android App Launcher (`android/app/src/main/res/`)**:
  - `mipmap-anydpi-v26/ic_launcher.xml` (Adaptive Icon).
  - `mipmap-anydpi-v26/ic_launcher_round.xml` (Adaptive Round Icon).
  - `drawable/ic_launcher_foreground.xml` (Vector foreground).
  - `mipmap-mdpi/` ($48 \times 48\text{ px}$).
  - `mipmap-hdpi/` ($72 \times 72\text{ px}$).
  - `mipmap-xhdpi/` ($96 \times 96\text{ px}$).
  - `mipmap-xxhdpi/` ($144 \times 144\text{ px}$).
  - `mipmap-xxxhdpi/` ($192 \times 192\text{ px}$).

---

## 6. Usage Rules & Prohibitions

1. **DO** render the symbol in `#F47521` over `#090A0E` OLED surfaces for brand consistency.
2. **DO** use the single-color white variant when placed over colorful imagery or backgrounds with low orange contrast.
3. **DO NOT** rotate, skew, or flip the mark (the play pupil must always point to the right).
4. **DO NOT** change the proportions between the eye aperture and the pupil triangle.
5. **DO NOT** add drop shadows, outer glows, gradients, or raster filters to the master mark.
6. **DO NOT** stretch the wordmark independently of the symbol.
