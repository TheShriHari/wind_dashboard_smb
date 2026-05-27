---
name: AeroMetric Industrial
colors:
  surface: '#131315'
  surface-dim: '#131315'
  surface-bright: '#39393b'
  surface-container-lowest: '#0e0e10'
  surface-container-low: '#1b1b1d'
  surface-container: '#1f1f21'
  surface-container-high: '#2a2a2b'
  surface-container-highest: '#353436'
  on-surface: '#e4e2e4'
  on-surface-variant: '#c6c6cd'
  inverse-surface: '#e4e2e4'
  inverse-on-surface: '#303032'
  outline: '#909097'
  outline-variant: '#45464d'
  surface-tint: '#bec6e0'
  primary: '#bec6e0'
  on-primary: '#283044'
  primary-container: '#0f172a'
  on-primary-container: '#798098'
  inverse-primary: '#565e74'
  secondary: '#bcc7de'
  on-secondary: '#263143'
  secondary-container: '#3e495d'
  on-secondary-container: '#aeb9d0'
  tertiary: '#dec29a'
  on-tertiary: '#3e2d11'
  tertiary-container: '#231500'
  on-tertiary-container: '#957d5a'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#dae2fd'
  primary-fixed-dim: '#bec6e0'
  on-primary-fixed: '#131b2e'
  on-primary-fixed-variant: '#3f465c'
  secondary-fixed: '#d8e3fb'
  secondary-fixed-dim: '#bcc7de'
  on-secondary-fixed: '#111c2d'
  on-secondary-fixed-variant: '#3c475a'
  tertiary-fixed: '#fcdeb5'
  tertiary-fixed-dim: '#dec29a'
  on-tertiary-fixed: '#271901'
  on-tertiary-fixed-variant: '#574425'
  background: '#131315'
  on-background: '#e4e2e4'
  surface-variant: '#353436'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  body-base:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  data-lg:
    fontFamily: JetBrains Mono
    fontSize: 32px
    fontWeight: '500'
    lineHeight: 40px
    letterSpacing: -0.03em
  data-sm:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-caps:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  container-padding: 24px
  grid-gutter: 16px
  card-gap: 20px
---

## Brand & Style

The design system is engineered for high-stakes, 24/7 industrial monitoring environments. It evokes a "Mission Control" atmosphere—authoritative, precise, and technologically advanced. The aesthetic combines **Dark Industrial Minimalism** with **Glassmorphism**, prioritizing data legibility against a deep, layered backdrop. 

The emotional response is one of calm control amidst complex data. By utilizing translucent surfaces and subtle glowing indicators, the UI mimics high-end physical hardware consoles found in modern energy operations centers. The visual language is "Technical Premium," characterized by sharp execution, purposeful spacing, and high-contrast status signaling.

## Colors

The palette is anchored in deep, "Night Navy" and "Charcoal" tones to minimize eye strain during long shifts and to provide maximum contrast for critical alerts.

- **Base Surfaces:** Use `#0f172a` for the primary background and `#1e293b` for secondary containers.
- **Status Accents:** These are the only high-chroma elements. 
    - **Emerald (#10b981):** Represents optimal performance. Use for "Running" states and positive trends.
    - **Pulse Red (#ef4444):** Reserved for "Failed" states or critical safety breaches.
    - **Warning Orange (#f59e0b):** Used for network latencies, maintenance windows, or non-critical sensor fluctuations.
- **Overlays:** Glass surfaces utilize a semi-transparent navy with a 20px-40px backdrop blur to create depth without clutter.

## Typography

The typography system prioritizes clarity and technical precision. 

- **Inter:** Used for all interface labels, headings, and instructional text. Its neutral, high-legibility grotesque style ensures information is processed quickly.
- **JetBrains Mono:** Employed specifically for sensor readouts, timestamps, coordinates, and wind speed values. The monospaced nature prevents "layout jump" when live data values fluctuate rapidly.
- **Hierarchy:** Use `label-caps` for section headers and KPI titles. `data-lg` should be used for the primary metric within cards (e.g., "14.2 m/s").

## Layout & Spacing

The layout follows a **Fixed Dashboard Grid** model designed for 16:9 and 21:9 industrial displays.

- **Grid:** Use a 12-column layout with a 16px gutter.
- **Density:** High information density is preferred, but elements must be grouped into logical "Modules" or "Pods" to avoid cognitive overload.
- **Responsiveness:** On smaller screens (Tablets), the 12-column grid collapses to 6 columns. On mobile, the grid becomes a single-column stack, prioritizing the "Global Health" status and critical alerts at the top.
- **Safe Areas:** Maintain a 24px margin around the entire viewport to ensure UI elements do not bleed into hardware bezels.

## Elevation & Depth

Depth is conveyed through **Glassmorphic Stacking** rather than traditional drop shadows.

- **Level 1 (Base):** Deep Navy (`#0f172a`). No transparency.
- **Level 2 (Cards/Modules):** Semi-transparent Navy (`rgba(30, 41, 59, 0.7)`) with a 1px solid border (`rgba(255, 255, 255, 0.1)`).
- **Level 3 (Modals/Popovers):** Higher transparency with an increased backdrop blur (60px) and a subtle inner glow on the top edge to simulate light catching the glass.
- **Glow Effects:** Critical status indicators (Emerald, Red, Orange) should utilize a `box-shadow: 0 0 15px [color]` to simulate a glowing LED light on the hardware console.

## Shapes

The design system uses a **Soft/Technical** shape language.

- **Standard Elements:** Use a 0.25rem (4px) radius for most UI components (input fields, buttons, small cards). This maintains a precision-engineered look.
- **Large Containers:** Use 0.5rem (8px) for main dashboard panels to soften the overall grid.
- **Status Pips:** Small indicators and "on/off" lights should be perfectly circular to mimic physical LEDs.
- **Progress Bars:** Use flat ends (0px radius) for bar charts and gauges to emphasize the industrial, data-driven nature.

## Components

- **KPI Cards:** Glass containers with a `label-caps` title at the top, a large `data-lg` value in the center, and a sparkline at the bottom.
- **Buttons:**
    - *Primary:* Solid `#1e293b` with a 1px highlight border.
    - *Action:* Outlined with the specific status color (e.g., a "Reset" button outlined in Red).
- **Status Lights:** Small 8px circles with a CSS "pulse" animation for "Failed" states and a steady glow for "Running" states.
- **Input Fields:** Darker than the container background, using a subtle inner shadow to look "recessed" into the glass panel.
- **Data Tables:** Zebra-striping using `rgba(255, 255, 255, 0.02)` for alternate rows. No vertical borders; only horizontal lines for a clean, scanable look.
- **Gauges:** Radial or semi-circle gauges should use thin strokes (2px) with the status color used for the active progress segment.