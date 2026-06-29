# UX Constitution — Blue Steel

> Design authority for all frontend visual and interaction decisions. Every AI agent building UI must read this document before touching any component. For implementation patterns, see the four UX skill files referenced in §10.

---

## §1 Design Philosophy & Grid System

### Three Laws

1. **Less is More.** Whitespace, typography, and visual rhythm take precedence over decoration. Every element earns its place by reducing cognitive load.
2. **Contextual Clarity.** The user never loses context. Traditional modals are forbidden. Toast notifications are forbidden. All interactions use Focused Overlays anchored to the originating element.
3. **Technical Precision.** Every rule in this document is deterministic. Tailwind classes, z-index values, and animation durations are explicit — not approximate.

### Grid System

| Unit | Value | Tailwind base |
|---|---|---|
| **Macro grid** | 8pt (8px) | `p-2` = 8px, `p-4` = 16px, `gap-4` = 16px |
| **Micro grid** | 4pt (4px) | `p-1` = 4px, `gap-1` = 4px |

All layout spacing must be a multiple of 4px. Non-grid values (`p-3.5`, `gap-2.5`) are forbidden.

### Progressive Density

| Context | Density | Padding rule |
|---|---|---|
| Onboarding, Settings, Input Mode forms | Low | `p-6` containers, `p-8` page padding |
| Query Mode | Medium | `p-4` containers, `p-6` page padding |
| Exploration Mode (Timeline, Entities, Spaces, Relations) | High | `p-2` list items, `p-4` containers |

---

## §2 Typography Hierarchy

Font stack: `ui-sans-serif, system-ui, Inter, sans-serif`

| Role | Tailwind Classes | Size | Weight | Line-height |
|---|---|---|---|---|
| Display | `text-3xl font-bold tracking-tight` | 30px | 700 | 1.2 |
| Heading 1 | `text-2xl font-semibold` | 24px | 600 | 1.3 |
| Heading 2 | `text-xl font-semibold` | 20px | 600 | 1.4 |
| Heading 3 | `text-base font-medium` | 16px | 500 | 1.5 |
| Body | `text-sm` | 14px | 400 | 1.6 |
| Caption / Label | `text-xs text-slate-500` | 12px | 400 | 1.5 |
| Code / Monospace | `font-mono text-sm` | 14px | 400 | 1.6 |

**Rules:**
- Prose content (session summaries, query answers) is capped at `max-w-prose` (65ch).
- Never use `font-bold` below Heading 2 — use `font-semibold` or `font-medium` for emphasis.
- All text must pass WCAG AA contrast (≥4.5:1) against its background.

---

## §3 Elevation System (Material Design 3)

Elevation communicates interaction priority and navigation depth. Higher elevation = more actionable.

| MD3 Level | Context | Tailwind Shadow | Ring |
|---|---|---|---|
| 0 — Flat | Background canvas, page background | `shadow-none` | — |
| 1 — Tonal | Cards, list items, static surfaces | `shadow-sm` | — |
| 2 — Elevated | Hover state, active list item, inline edit | `shadow-md` | — |
| 3 — Overlay | Focused Overlays, dropdown menus | `shadow-lg` | `ring-1 ring-slate-200` |
| 4 — Navigation | Sidebar (floating), top bar | `shadow-xl` | — |

**Rules:**
- Every interactive surface starts at elevation 1 minimum (`shadow-sm`). `shadow-none` is reserved for background regions only.
- Apply `transition-shadow duration-200` to all interactive elements so elevation transitions are smooth.
- When an element receives focus via a Focused Overlay, its shadow jumps to `shadow-xl ring-2 ring-blue-500/50`.

---

## §4 Interaction Behaviors — Focused Overlays

### The Rule
Traditional modals (`Dialog`, `AlertDialog` centred in viewport) are **forbidden** (D-082). Every contextual action — UNCERTAIN card resolution, annotation creation, entity detail expansion — uses a **Focused Overlay**: the element expands or elevates in place, surrounded by a dimmed, blurred backdrop.

### Overlay Specification

| Property | Value |
|---|---|
| Overlay content z-index | `z-50` |
| Backdrop z-index | `z-40` |
| Backdrop style | `bg-slate-900/30 backdrop-blur-sm` |
| Enter transition | `duration-200 ease-out` |
| Leave transition | `duration-150 ease-in` |
| Focused element shadow | `shadow-xl ring-2 ring-blue-500/50` |
| Focused element position | Anchored to original position — never re-centred |

### Escapism Rule (Mandatory Accessibility)
Every Focused Overlay **must** implement both:
1. **Backdrop click** — clicking outside the focused element closes the overlay
2. **ESC key** — pressing Escape closes the overlay

These are not optional enhancements. axe-core keyboard navigation tests are required for every overlay.

---

## §5 Loading & Feedback Mechanisms

### Skeletons (Mandatory for Primary Content)

Spinners are forbidden in primary content areas. Every data-fetching view renders a skeleton that matches the final layout.

| Element | Skeleton Classes |
|---|---|
| Text line (body) | `h-4 w-3/4 rounded bg-slate-200 animate-pulse` |
| Text line (secondary) | `h-3 w-1/2 rounded bg-slate-200 animate-pulse` |
| Avatar / icon placeholder | `h-10 w-10 rounded-full bg-slate-200 animate-pulse` |
| Button placeholder | `h-9 w-24 rounded-lg bg-slate-200 animate-pulse` |
| Card placeholder | Replicate card structure with above blocks at real dimensions |

**Rules:**
- Skeleton dimensions must match final rendered dimensions within ±2px. Derive heights from DTO field types (string → `h-4`, longer text → two lines, etc.).
- Skeletons are derived from TypeScript DTOs in `src/types/` — if the DTO changes, update the skeleton.
- Loading spinners (`<Loader2 className="animate-spin" />`) are only permitted inside buttons (e.g., a submit button in loading state).

### Inline Banners (Mandatory — No Toasts)

Toast notifications are **forbidden** (D-083). System feedback is delivered via `InlineBanner` components that push content naturally within the layout.

| Variant | Background | Border | Text |
|---|---|---|---|
| `success` | `bg-green-50` | `border-green-200` | `text-green-800` |
| `warning` | `bg-amber-50` | `border-amber-200` | `text-amber-800` |
| `error` | `bg-red-50` | `border-red-200` | `text-red-800` |
| `info` | `bg-blue-50` | `border-blue-200` | `text-blue-800` |

**Rules:**
- Banners inject above the content they describe (not at page top by default, unless page-global).
- Enter animation: `animate-in slide-in-from-top-2 duration-200`.
- `success`, `warning`, `info` auto-clear after 8 seconds. `error` banners never auto-clear — the user must dismiss explicitly.
- Use `role="alert"` and `aria-live="polite"` on every banner.

---

## §6 Navigation & Sidebar Logic

### Sidebar Structure

```
┌──────────────────────────────┐
│  Campaign Switcher           │  ← always at top
├──────────────────────────────┤
│  [Icon] Input Mode           │  ← upload icon
│  [Icon] Query Mode           │  ← search icon
│  [Icon] Exploration Mode     │  ← compass icon
│    ├── Timeline              │
│    ├── Entities              │
│    ├── Spaces                │
│    └── Relations             │
├──────────────────────────────┤
│  [Icon] Settings             │  ← at bottom
│  [Icon] User / Logout        │  ← at bottom
└──────────────────────────────┘
```

### Sidebar Dimensions

| State | Width | Content |
|---|---|---|
| Collapsed | `w-16` (64px) | Icons only — no text labels |
| Expanded | `w-64` (256px) | Icons + text labels |

**Rules:**
- Sidebar state is stored in Zustand `uiStore.sidebarExpanded` and persisted to `localStorage`. Never local component state.
- Transition: `transition-all duration-200 ease-in-out` on the sidebar `<nav>`.
- Active route: `bg-blue-50 text-blue-600 border-r-2 border-blue-500 font-medium`.
- Inactive route: `text-slate-600 hover:bg-slate-100 hover:text-slate-900`.
- The accent border (`border-r-2 border-blue-500`) only appears on the active route — never as a decorative element.
- Role gating: Input Mode nav item hidden (not just disabled) for `player` role.

---

## §7 Color Palette — Blue Steel

### Foundation Palette

| Role | Tailwind Token | Hex |
|---|---|---|
| Page background | `bg-slate-50` | `#f8fafc` |
| Surface (cards, panels) | `bg-white` | `#ffffff` |
| Surface variant (hover bg) | `bg-slate-100` | `#f1f5f9` |
| Border (brushed metal) | `border-slate-200` | `#e2e8f0` |
| Border (strong) | `border-slate-300` | `#cbd5e1` |
| Text primary | `text-slate-900` | `#0f172a` |
| Text secondary | `text-slate-500` | `#64748b` |
| Text disabled | `text-slate-400` | `#94a3b8` |

### Accent — Electric Blue

| Role | Tailwind Token | Hex |
|---|---|---|
| Primary action, active state | `bg-blue-500` / `text-blue-500` | `#3b82f6` |
| Primary action hover | `bg-blue-600` | `#2563eb` |
| Primary action pressed | `bg-blue-700` | `#1d4ed8` |
| Subtle accent background | `bg-blue-50` | `#eff6ff` |
| Accent border / ring | `ring-blue-500` / `border-blue-500` | `#3b82f6` |

### Semantic Colours

| Role | Tailwind Token | Hex |
|---|---|---|
| Destructive action | `text-red-600` / `bg-red-600` | `#dc2626` |
| Destructive subtle bg | `bg-red-50` | `#fef2f2` |
| Warning | `text-amber-600` | `#d97706` |
| Warning subtle bg | `bg-amber-50` | `#fffbeb` |
| Success | `text-green-600` | `#16a34a` |
| Success subtle bg | `bg-green-50` | `#f0fdf4` |
| Info | `text-blue-600` | `#2563eb` |

**Rules:**
- `blue-500` is used **only** for primary actions and active/focus states. Secondary actions use `slate-700` text on transparent backgrounds.
- Never use raw hex values in JSX — always use Tailwind tokens.
- "Brushed metal" borders (`slate-200`/`slate-300`) replace harsh black borders throughout.

### Dark Mode

Dark mode is **class-based** (`.dark` on `<html>`, toggled by `useApplyTheme`). Most surface and
text colours are driven by semantic CSS-variable tokens in `src/index.css`, which are re-coloured
under `.dark` — so `bg-card`, `text-foreground`, `border-input`, `ring-ring` etc. adapt
automatically. **Raw palette classes (`bg-green-50`, `text-red-700`, …) do NOT adapt** and are the
main source of dark-mode bugs: always pair them with a `dark:` counterpart (same hue, inverted
lightness) or use the semantic feedback tokens below.

**Dark palette (what `.dark` ships — document, don't diverge):**

| Role | Hex | Light counterpart |
|---|---|---|
| Page background | `#020617` (slate-950) | `#f8fafc` |
| Surface / card | `#0f172a` (slate-900) | `#ffffff` |
| Surface variant / muted | `#1e293b` (slate-800) | `#f1f5f9` |
| Border | `#1e293b` (slate-800) | `#e2e8f0` |
| Border strong / input | `#334155` (slate-700) | `#cbd5e1` |
| Text primary | `#f8fafc` (slate-50) | `#0f172a` |
| Text secondary | `#94a3b8` (slate-400) | `#64748b` |

**Contrast targets:** body text ≥ **4.5:1** (WCAG AA); AAA ≥ 7:1 is encouraged for primary text
(our `#f8fafc` on `#020617` ≈ 19:1). Large text (≥18.66px bold / 24px) and non-text UI affordances
≥ **3:1**. Secondary text `#94a3b8` on `muted #1e293b` is ≈ 5.7:1 — the **thin floor**; never place
text on a background lighter than `muted` without re-checking, and never use a body-text colour
darker than `#94a3b8` on dark surfaces.

**Elevation in dark mode:** depth comes from **lighter tonal surfaces + hairline rings**
(`ring-1 ring-foreground/10`, theme-adaptive), **not** drop shadows — `shadow-*` is near-invisible
on `#020617`. Cards and dropdowns already use this ring approach; prefer it over `shadow-sm` in
dark contexts (this is the dark-mode reading of §3's "minimum elevation" rule).

**Accent ramp inverts in dark:** hover/pressed go **lighter**, not darker — `blue-400 #60a5fa`
(hover) and `blue-300 #93c5fd` (pressed) — because a darker blue recedes into a dark surface. The
focus ring stays `blue-500 #3b82f6` in both themes.

**Semantic feedback colours (token-backed, dark-aware):** use the `--color-{success,warning,error,info}`
tokens in `src/index.css` instead of raw `green-*/amber-*/red-*/blue-*` classes — they flip under
`.dark` automatically, so **no `dark:` variant is needed**. Each role has **three** parts:

| Part | Utility | Use for | Light → Dark |
|---|---|---|---|
| **base** | `text-{role}` / `border-{role}` | coloured text/icon/border on a **neutral** surface | `*-600` → `*-400` |
| **subtle** | `bg-{role}-subtle` | tinted background fill | `*-50` → `*-950` |
| **foreground** | `text-{role}-foreground` | text placed **on** `-subtle` (AA-safe; base is too light here) | `*-800` → `*-300` |

| Role | base | subtle | foreground |
|---|---|---|---|
| Success | `#16a34a` → `#4ade80` | `#f0fdf4` → `#052e16` | `#166534` → `#86efac` |
| Warning | `#d97706` → `#fbbf24` | `#fffbeb` → `#451a03` | `#92400e` → `#fcd34d` |
| Error | `#dc2626` → `#f87171` | `#fef2f2` → `#450a0a` | `#991b1b` → `#fca5a5` |
| Info | `#2563eb` → `#60a5fa` | `#eff6ff` → `#172554` | `#1e40af` → `#93c5fd` |

- **Status chip / banner** (text on a tint): `bg-{role}-subtle text-{role}-foreground border-{role}/30`.
- **Coloured text on the page/card** (e.g. an error message, a counter at the limit): `text-{role}`.
- Pairing `text-{role}` with `bg-{role}-subtle` is **wrong** — the base is too light on the tint
  (e.g. green-600 on green-50 ≈ 3.2:1, fails AA). Always use `-foreground` on `-subtle`.
- Raw `*-50`/`*-700` palette pairs with no `dark:` variant are forbidden on a dark page (glaring,
  eye-straining, borders vanish). Reserve raw palette classes for cases the token roles don't model
  (e.g. the annotation theme, the destructive-action token).

---

## §8 Tailwind v4 Configuration

Blue Steel uses **Tailwind CSS v4** which replaces `tailwind.config.js` with a CSS-based `@theme {}` block in the global stylesheet. There is no JavaScript config file.

### Global Stylesheet Structure (`src/index.css`)

```css
@import "tailwindcss";

@theme {
  /* Typography */
  --font-sans: ui-sans-serif, system-ui, Inter, sans-serif;
  --font-mono: ui-monospace, SFMono-Regular, Menlo, monospace;

  /* Blue Steel Colour Tokens */
  --color-surface: #ffffff;
  --color-background: #f8fafc;
  --color-border: #e2e8f0;
  --color-border-strong: #cbd5e1;

  --color-accent: #3b82f6;
  --color-accent-hover: #2563eb;
  --color-accent-pressed: #1d4ed8;
  --color-accent-subtle: #eff6ff;

  /* Border Radius — MD3 style */
  --radius-card: 1rem;        /* rounded-2xl = 16px */
  --radius-button: 0.5rem;    /* rounded-lg = 8px */
  --radius-input: 0.5rem;     /* rounded-lg = 8px */
  --radius-badge: 9999px;     /* rounded-full */
  --radius-icon-btn: 9999px;  /* rounded-full */

  /* Elevation shadows */
  --shadow-tonal:   0 1px 2px 0 rgb(0 0 0 / 0.05);           /* shadow-sm */
  --shadow-elevated: 0 4px 6px -1px rgb(0 0 0 / 0.1);         /* shadow-md */
  --shadow-overlay: 0 10px 15px -3px rgb(0 0 0 / 0.1);        /* shadow-lg */
  --shadow-nav:     0 20px 25px -5px rgb(0 0 0 / 0.1);        /* shadow-xl */
}
```

### Radius Token Map

| Element type | Token | Tailwind class |
|---|---|---|
| Cards, modals | `--radius-card` | `rounded-2xl` |
| Buttons (regular) | `--radius-button` | `rounded-lg` |
| Inputs, textareas | `--radius-input` | `rounded-lg` |
| Chips, badges | `--radius-badge` | `rounded-full` |
| Icon buttons | `--radius-icon-btn` | `rounded-full` |
| Skeleton blocks | — | `rounded` |

---

## §9 AI-Agent Implementation Constraints

These rules are **never optional**. Any deviation is a bug.

1. **No modals.** Replace every `<Dialog>` or `<AlertDialog>` with the `FocusedOverlay` pattern. See `skills/ux-focused-overlay/SKILL.md`.
2. **No toasts.** Replace every `toast()` call with an `InlineBanner` component. See `skills/ux-inline-feedback/SKILL.md`.
3. **All overlays must close on ESC and backdrop click.** No exceptions. axe-core keyboard nav test required.
4. **Every skeleton must match final rendered height within ±2px.** Measure both and align.
5. **Grid spacing must be multiples of 4px.** Use `p-1` (4px), `p-2` (8px), `p-3` (12px), `p-4` (16px), `p-6` (24px), `p-8` (32px) only.
6. **Radius rules are fixed:** cards → `rounded-2xl`, buttons → `rounded-lg`, badges/icon-buttons → `rounded-full`.
7. **`blue-500` is the only accent colour.** It appears exclusively on primary actions and active route highlights. All other interactive elements use slate tones.
8. **No interactive surface uses `shadow-none`.** Minimum is `shadow-sm`.
9. **All text must pass WCAG AA (≥4.5:1)** contrast against its direct background.
10. **`components/ui/` is read-only.** Wrap or extend in `components/domain/` only.
11. **No inline `style={{}}` props.** Use Tailwind utility classes or CSS custom properties via `@theme`.
12. **Sidebar state lives in Zustand `uiStore`.** Never derive it from local component state or `localStorage` reads in components. See `skills/ux-navigation-logic/SKILL.md`.

---

## §10 UX Skill Reference

| Skill | File | Trigger keywords |
|---|---|---|
| `ux-focused-overlay` | `skills/ux-focused-overlay/SKILL.md` | overlay, modal, dialog, contextual expand, UNCERTAIN card, backdrop |
| `ux-inline-feedback` | `skills/ux-inline-feedback/SKILL.md` | alert, banner, feedback, success message, error message, form submission result |
| `ux-navigation-logic` | `skills/ux-navigation-logic/SKILL.md` | sidebar, navigation, active route, mode switch, campaign switcher |
| `ux-skeleton-crafting` | `skills/ux-skeleton-crafting/SKILL.md` | loading state, skeleton, placeholder, data fetching UI |
