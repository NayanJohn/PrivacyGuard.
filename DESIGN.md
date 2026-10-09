---
name: Spatial Sentinel
colors:
  surface: '#0e1323'
  surface-dim: '#0e1323'
  surface-bright: '#34394a'
  surface-container-lowest: '#080d1d'
  surface-container-low: '#161b2b'
  surface-container: '#1a1f30'
  surface-container-high: '#25293a'
  surface-container-highest: '#2f3446'
  on-surface: '#dee1f9'
  on-surface-variant: '#bbc9cd'
  inverse-surface: '#dee1f9'
  inverse-on-surface: '#2b3041'
  outline: '#859397'
  outline-variant: '#3c494c'
  surface-tint: '#2fd9f4'
  primary: '#8aebff'
  on-primary: '#00363e'
  primary-container: '#22d3ee'
  on-primary-container: '#005763'
  inverse-primary: '#006877'
  secondary: '#d0bcff'
  on-secondary: '#3c0091'
  secondary-container: '#571bc1'
  on-secondary-container: '#c4abff'
  tertiary: '#68f5b8'
  on-tertiary: '#003824'
  tertiary-container: '#46d89d'
  on-tertiary-container: '#005a3d'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#a2eeff'
  primary-fixed-dim: '#2fd9f4'
  on-primary-fixed: '#001f25'
  on-primary-fixed-variant: '#004e5a'
  secondary-fixed: '#e9ddff'
  secondary-fixed-dim: '#d0bcff'
  on-secondary-fixed: '#23005c'
  on-secondary-fixed-variant: '#5516be'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#0e1323'
  on-background: '#dee1f9'
  surface-variant: '#2f3446'
typography:
  display-lg:
    fontFamily: Sora
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.03em
  headline-lg:
    fontFamily: Sora
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Sora
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Sora
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  title-md:
    fontFamily: Sora
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 26px
  body-lg:
    fontFamily: Sora
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Sora
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Sora
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Sora
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Sora
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Sora
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system establishes an atmospheric, tactile spatial interface for modern privacy analysis on mobile. Operating under the philosophy "Understand Before You Trust," the UI moves away from clinical, fear-inducing security dashboards toward an empowering, deeply layered sanctuary.

### Design Movement: Atmospheric Spatial Glassmorphism
The aesthetic blends hyper-refined glassmorphism, ambient spatial illumination, and organic rounded geometry. Elements do not sit on a flat canvas; they float as volumetric optical layers over an infinite deep-space substrate. 

### Key Characteristics
- **Translucent Depth**: Surfaces use physical material simulation—frosted backdrop filtration, refractive top-edge specular glares, and hairline boundary lines.
- **Diffused Bioluminescence**: Primary controls and severity indicators emit colored photonic glows that cast soft, multi-layered light onto background plates.
- **Friendly Authority**: Deep technical security insights are presented with warm, rounded clarity, anchored by the friendly presence of Guardy—the ambient shield guardian mascot.

## Colors

The palette establishes an immersive nocturnal environment accented by coherent neon luminous bands. Deep navy provides non-fatiguing visual comfort, while vibrant spectral accents direct gaze hierarchy and encode vulnerability levels.

### Surface System
- **Canvas Base**: `#0B1020` (Void Canvas)
- **Deep Recessed Surface**: `#0E1528`
- **Mid-Tier Structural Glass**: `rgba(19, 26, 46, 0.75)` with `backdrop-filter: blur(24px)`
- **Elevated Interactive Glass**: `rgba(26, 36, 64, 0.65)` with `backdrop-filter: blur(32px)`
- **Floating Overlays & Sheets**: `rgba(33, 46, 82, 0.82)` with `backdrop-filter: blur(40px)`
- **Specular Hairline Stroke**: `rgba(255, 255, 255, 0.12)` linear fade to `rgba(255, 255, 255, 0.03)`

### Radiant & Feedback Accents
- **Primary Kinetic Glow (Cyan)**: `#22D3EE` (Focus, active scanning, safe permission flows)
- **Secondary Atmospheric Aura (Violet)**: `#8B5CF6` (Guardy energy, systemic background monitoring)
- **Safe / Low Severity**: `#10B981` (Validated trust, harmless app requests)
- **Warning / Medium Severity**: `#F59E0B` (Unusual frequency, caution recommended)
- **Heightened / High Severity**: `#F97316` (Excessive privilege, tracking vectors)
- **Hazard / Critical Severity**: `#EF4444` (Active leak, unvetted telemetry, critical exposure)

### Text & Contrast Balance
- **Text High-Contrast**: `#F8FAFC`
- **Text Subdued**: `#94A3B8`
- **Text Ambient**: `#64748B`

## Typography

The type system relies on **Sora**, chosen for its geometric foundation, distinct humanist details, and generous apertures. Its rounded structure maintains maximum legibility across frosted, translucent glass backgrounds while delivering a warm, futuristic tone.

### Typographic Principles
- **Clarity over Density**: Generous line heights ensure readability against variable-luminance backdrops.
- **Optical Weight Offsetting**: Headings leverage semi-bold and bold weights to avoid being washed out by background glass refractions or glow effects.
- **Status Micro-Labels**: Uppercase styling is reserved strictly for `label-sm` with expanded tracking to provide immediate legibility on pill-shaped status tags.

## Layout & Spacing

The layout is built for Android mobile viewports using a fluid grid that prioritizes thumb reach, physical ergonomics, and comfortable spatial margins.

### Grid & Ergonomics
- **Grid Architecture**: 4-column fluid mobile grid with `16px` gutters and `16px` outer margins. On wider foldables or tablets, the system expands to an 8-column layout with `24px` margins.
- **Spatial Padding Rules**: Glass containers enforce internal padding of at least `space-lg` (`24px`) to preserve optical breathing room between internal content and edge specular highlights.
- **Touch Target Fidelity**: All touch elements adhere strictly to the 48x48dp interactive boundary rule, offset by transparent spatial hitboxes to maintain aesthetic lightness without sacrificing accessibility.

## Elevation & Depth

Visual hierarchy uses physical multi-plane layering, ambient colored shadows, and variable blur filters rather than pure black shadows.

### Spatial Plane Hierarchy
1. **Background Layer (Depth 0)**: `#0B1020` base with soft, organic radial gradient patches of Cyan (`#22D3EE10`) and Violet (`#8B5CF612`) slowly rotating behind the viewport.
2. **Structural Panels (Depth 1)**: Flat glass panels hovering 4dp above background. Rendered with `backdrop-filter: blur(20px)`, a continuous `1px` stroke of `rgba(255, 255, 255, 0.08)`, and an ambient shadow: `0 8px 32px 0 rgba(0, 0, 0, 0.35)`.
3. **Interactive Floating Cards (Depth 2)**: Glass panels elevated 8dp. Adds an inner rim highlight via top-biased gradient borders (`rgba(255, 255, 255, 0.2)` fading to `rgba(255, 255, 255, 0.02)`), with a composite shadow: `0 12px 28px -4px rgba(0, 0, 0, 0.45)`, overlaid with a directional colored underglow matching the panel's threat context (e.g., `0 8px 24px -6px rgba(34, 211, 238, 0.15)`).
4. **Modals, Sheets, and Flyouts (Depth 3)**: Forefront elements elevated 16dp. Background content receives a 12px depth blur. Panels feature high-specular glass (`rgba(255, 255, 255, 0.18)` stroke) and a multi-tiered halo: `0 24px 48px -12px rgba(0, 0, 0, 0.6)` and `0 0 32px 2px rgba(139, 92, 246, 0.12)`.

## Shapes

The design uses deeply rounded corners to soften security metrics, reinforce the spatial glass aesthetic, and integrate seamlessly with Android device hardware contours.

### Corner Radius Standards
- **Primary Content Panels & Cards**: `24px` radius. Creates smooth optical flow and accommodates edge specular highlights without sharp-angle distortion.
- **Pills, Badges & Action Buttons**: Fully rounded pill shapes (`9999px`), maintaining an approachable feel.
- **Embedded Modules & Input Fields**: `16px` radius (`rounded-lg`), ensuring crisp nesting inside `24px` parent cards.
- **Interactive Toggles & Checkboxes**: Rounded squares (`8px` radius) to match thumb geometry.

## Components

### Buttons
- **Primary Glowing Action**: Pill-shaped (`height: 52px`). Filled with an electric horizontal gradient from Cyan (`#22D3EE`) to Violet (`#8B5CF6`). Text is high-contrast deep navy (`#0B1020`), `label-lg`. Casts a continuous diffused drop-glow (`box-shadow: 0 4px 20px rgba(34, 211, 238, 0.35)`). Active state applies a subtle scale reduction (0.97) and intensifies the ambient light.
- **Glass Secondary**: Transparent glass fill (`rgba(255, 255, 255, 0.06)`), 1px frosted boundary, white typography. Upon tap, background shifts to `rgba(255, 255, 255, 0.12)` with a soft white specular ring.

### Floating Glass Cards
- Enclosed in a `24px` rounded body with a `1px` perimeter border featuring top-to-bottom white gradient transparency.
- Internal padding is fixed at `24px`.
- Interactive cards elevate smoothly on tap, brightening their perimeter stroke and intensifying their contextual colored drop-glow (e.g., Critical Red glowing softly beneath high-risk apps).

### Risk & Severity Chips
- Fully rounded pills (`height: 28px`, horizontal padding `12px`).
- Surfaces use tinted glass: background color set to 15% opacity of the severity hex (e.g., `#EF444426` for Critical), border set to 30% opacity, and inner label text set to full-saturation color.
- A glowing 6px status bead sits immediately left of the text label.

### Lists & Audit Rows
- Row items do not use harsh divider lines; they are separated by `8px` empty spatial gaps or housed in micro-glass wells (`rgba(19, 26, 46, 0.4)` with `16px` corner rounding).
- Trailing chevrons and metadata use `text-subdued` (`#94A3B8`).

### Input Fields
- Recessed glass fields (`rgba(11, 16, 32, 0.6)`) with `16px` rounding and an inset border of `rgba(255, 255, 255, 0.08)`.
- Active focus smoothly highlights the boundary with Cyan `#22D3EE` and projects an interior ambient shadow glow (`0 0 16px rgba(34, 211, 238, 0.2)`).

### Mascot Integration: Guardy
- Guardy is rendered as a clean, translucent glass shield entity with internal bi-color core refraction (Cyan core shifting to Violet aura).
- Eyes are clean, expressive glowing white apertures that adapt to systemic safety states: wide and relaxed during neutral states, narrowed and focused during real-time network sweeps, and warning-angled when high-risk permissions are detected.
- Placed dynamically within spatial headers, casting an ambient glow onto adjacent analytical metrics.