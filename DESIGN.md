---
name: Rootology
description: A garden assistant that answers from sourced facts and says when it does not know. Ask. Know. Grow.
colors:
  sprout-green: "#2f7a3b"
  sprout-green-text: "#2a6e35"
  garden-paper: "#fcfdfa"
  leaf-shade: "#1e2a20"
  turned-earth: "#8a5a3b"
  frost-blue: "#1f6fb2"
  pest-amber: "#a35a00"
  waiting-violet: "#6e40c9"
  error-red: "#e21d12"
  success-green: "#158443"
typography:
  display:
    fontFamily: "Fraunces, Georgia, 'Times New Roman', serif"
    fontWeight: 600
    letterSpacing: "-0.005em"
  body:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
  label:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 500
rounded:
  pill: "999px"
spacing:
  xs: "0.25rem"
  s: "0.5rem"
  m: "1rem"
  l: "1.5rem"
components:
  button-primary:
    backgroundColor: "{colors.sprout-green}"
    textColor: "{colors.garden-paper}"
  chip-suggestion:
    textColor: "{colors.sprout-green-text}"
    rounded: "{rounded.pill}"
  chip-filter:
    textColor: "{colors.leaf-shade}"
    rounded: "{rounded.pill}"
---

# Design System: Rootology

## Overview

**Creative North Star: "The Trusted Agronomist"**

Rootology looks and behaves like a good agronomist you trust: sober and precise, it says what it knows, what it does not know and where it knows it from. The interface stays out of the way of the answer; the brand lives in precise details (the sprout in the wordmark, Fraunces in the titles, the line drawings) rather than in decoration.

Within that sobriety the character is warm and tactile, a thing made for the garden: earthy neutrals that lean towards the green of the sprout, soft rounded shapes, small one-line drawings, comfortable touch targets for a phone held in one hand outdoors. Warmth comes from colour temperature, type and drawings, never from noise. Every screen works in light and dark, follows the device, and is checked on a 360 px phone and with a keyboard.

The confirmed anti-reference is anything playful or childish: saturated colours, mascots inside the app (the radish is for promotion only, outside it), the tone of an app for children.

**Key Characteristics:**
- Sprout green is the one accent, used where the person acts.
- States have fixed colours and meanings everywhere: frost blue, pest amber, waiting violet, Lumo's success green and error red.
- Flat, softly bordered surfaces; depth by tone, not by shadow.
- A clear icon with a value or a count rather than a sentence; the word stays as tooltip and aria label.
- Fraunces for titles and the wordmark; the system font for everything a person reads quickly.

## Colors

Green-leaning neutrals with a single sprout-green accent and a small set of state colours, each defined as a light/dark pair (`light-dark()` on `:root`, overriding Lumo's tokens).

### Primary
- **Sprout Green** (#2f7a3b light, #6fbf6a dark; `--lumo-primary-color`): primary buttons, links, the chosen chip, focus. Text in the accent uses **Sprout Green Text** (#2a6e35 / #8fd488).

### Tertiary
- **Turned Earth** (#8a5a3b / #c9a27f; `--rootology-earth`): sparing warm accents in drawings and details.

### Neutral
- **Garden Paper** (#fcfdfa light, #111611 dark; `--lumo-base-color`): the page.
- **Leaf Shade** (#1e2a20 light, #050805 dark; `--lumo-shade`): text and the base of every contrast step; Lumo's contrast percentages are built on it, so borders and fills lean green.

### State colours
- **Frost Blue** (#1f6fb2 / #79c0ff; `--rootology-frost`): frost days, frost warnings.
- **Pest Amber** (#a35a00 / #e3b341; `--rootology-pest`): pests and diseases.
- **Waiting Violet** (#6e40c9 / #b392f0; `--rootology-waiting`): a question waiting for a person, its edge, its hourglass and its count.
- **Success Green** (#158443 / #238636) and **Error Red** (#e21d12 / #da3633): Lumo's own, kept for success and errors.
Each state colour has a `-10pct` tint for backgrounds.

### Named Rules
**The One Accent Rule.** Sprout green marks where to act; it is never decoration.
**The Fixed Meaning Rule.** A state colour means one thing everywhere: violet is always "waiting for a person", amber always a pest, blue always frost.

## Typography

**Display Font:** Fraunces (OFL, served from the app's origin; fallback Georgia, serif)
**Body Font:** the system font stack (Lumo's)

**Character:** a warm, slightly bookish serif for titles and the wordmark over a plain, fast system face for everything read at a glance on a phone.

### Hierarchy
- **Display / Headline** (Fraunces 600, Lumo's heading sizes, letter-spacing -0.005em, `text-wrap: balance`): page titles (`ui.PageHeader`), card titles, the wordmark and the slogan.
- **Body** (system 400, 1rem): answers, notes, forms; long text pages use the `rootology-prose` column.
- **Label** (system 500, Lumo's small sizes): chips, badges, meta lines (dates, "asked by"), tabular figures for counts.

### Named Rules
**The Serif-for-Titles Rule.** Fraunces appears only in h1–h4, the wordmark and the slogan; never in running text, buttons or fields.

## Layout

Lumo's spacing scale (xs 0.25rem, s 0.5rem, m 1rem, l 1.5rem). Every page starts with a `ui.PageHeader`: the title on the left, the main action on the right. Ask puts the question on the left and the history column on the right on wide screens; on a phone they take turns. Below 640 px padding shrinks, a bottom bar replaces the side menu inside a garden, and words beside icons hide (`rootology-wide-only`). On coarse pointers touch targets are at least 44 px. Wide grids scroll instead of overflowing.

## Elevation & Depth

Flat by default. Depth comes from tone: cards sit on a 5 % contrast fill with a 10 % contrast border; hover deepens the fill to 10 %. Shadows appear only on floating elements (Lumo's small, medium and large shadows for menus, popups and the map's controls).

### Named Rules
**The Flat-By-Default Rule.** Surfaces are flat at rest; a shadow means something floats above the page.

## Shapes

Soft and rounded: cards and panels use Lumo's large radius, fields and small controls Lumo's medium and small radii, chips and badges are full pills (999px), avatars and markers are circles. A waiting question carries a 3 px violet edge on its leading side, its one structural accent. Area kinds, empty pages and the welcome page use hand-drawn one-line icons and drawings.

## Components

### Buttons
- **Primary:** Lumo primary, filled sprout green with paper text; one per screen region, for the main action.
- **Tertiary:** text in sprout green for secondary actions; row actions are icon buttons (`ui.RowActions`) with the word as tooltip and aria label, and every delete asks first.

### Chips
- **Suggestion chips** (suggested questions, place and garden choices): pill, sprout-green 10 % fill, 50 % green border, green text; they act at once.
- **Filter chips** (history filters): pill, neutral 20 % border on a transparent fill, small text; the chosen one takes the green fill and border and a check.

### Cards / Containers
- **Corner Style:** Lumo's large radius.
- **Background:** contrast 5 % fill, contrast 10 % border, `lumo-space-m` padding.
- **Shadow Strategy:** none (see Elevation).
- Answer cards: the answer sentence, values as badges, a confidence bar, coloured callouts for notes, sources with icons, "Why this answer?" folded.

### Inputs / Fields
- Lumo text fields and combo boxes on a contrast fill; one question field with its Ask button beside it.

### Navigation
- Side menu with Vaadin icons, a "Space / Garden" breadcrumb, the bell with its count and a person menu at the top right; inside a garden on a phone, a bottom bar (home, Ask, areas, waiting).

### Signature Components
- **Wordmark:** Root·o·logy with the sprout in its circle as the third o, always with "Ask. Know. Grow." under it.
- **Weather card:** seven days with MET Norway's icons, frost days highlighted in frost blue, the credit line.
- **Waiting question card:** violet edge, hourglass, who asked and when, the values to answer with.

## Do's and Don'ts

### Do:
- **Do** use sprout green only where the person acts or chooses.
- **Do** keep each state colour to its one meaning, in light and in dark.
- **Do** prefer an icon with a value or a count, with the word as tooltip and aria label.
- **Do** check every screen at 360 px, in dark mode and with a keyboard.
- **Do** keep styles in `styles/rootology.css`; no inline styles on components.

### Don't:
- **Don't** make it playful or childish: no saturated palettes, no mascot inside the app, no toy-like shapes.
- **Don't** set running text, buttons or fields in Fraunces.
- **Don't** add shadows to resting cards or decorate with colour.
- **Don't** use "giardino" for the vegetable garden: it is "orto".
