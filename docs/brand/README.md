# Brand

Drawn for Rootology as plain SVG (no generated or third-party artwork).

- `rootology-o.svg`: the mark (#87), the sprout drawn in one line inside a circle, so that it is the third "o"
  of Root·o·logy: a thin circle with the sprout filling it, half again as tall as the lower-case letters and set
  a little below the baseline (version E, chosen 2026-11-12). `Wordmark` draws it inline in the accent colour,
  with the slogan under the name in the bars of the app and of the public pages.
- `rootology-icon.svg` (and `rootology-icon-512.png`): the app's icon, the white sprout on a green disc; it is the
  favicon in `META-INF/resources/icons/` (SVG and 32 px PNG). The home-screen icons (`rootology-touch.svg`,
  180, 192 and 512 px PNGs) put the white circle and sprout, in the mark's proportions, on a full green square
  (iOS fills transparency with black). The PNGs are rendered from the SVGs with headless Chrome (Playwright), at
  their exact size. After redrawing them, raise `AppShell.ICONS_VERSION` and the `?v=` of `manifest.json`, or
  browsers keep showing the old favicon.
- `how-it-works.svg` in `src/main/resources/brand/`: the welcome page's drawing, Ask (a person), Know (a robot,
  the AI, with the mark on its chest) and Grow (the garden) in a circle of arrows, inlined by `WelcomeView` with a translated description.
- `features/*.svg` in `src/main/resources/brand/`: the welcome page's four features, in the same line style and
  colour roles (figures in soil brown, plants green, weather blue, pests amber; `rootology-draw-*` in
  `rootology.css`), every robot with the app's mark on its chest: `sources` (a robot studying a botany book,
  books and the weather), `unsure` (the robot shrugs under a question mark on its right, a farmer and a teacher with her finger
  raised under a lit bulb), `warns` (a thoughtful robot looks at a weather station in the snow and imagines a
  frozen seedling) and `spaces` (an adult and a child water a seedling). The drawings carry no words, so they
  serve every language; `BrandDrawing` fills `{label}`, their description for screen readers, with the
  translation.
- Direction "Orto" (#87, chosen 2026-11-12): sprout green `#2f7a3b` (dark `#6fbf6a`) as the accent, soil brown
  `#8a5a3b` (dark `#c9a27f`) for drawings, neutrals leaning towards the green; titles and the wordmark in Fraunces
  (SIL Open Font License 1.1, served from `META-INF/resources/styles/fonts/` with its licence, no font host);
  running text on the system font; the slogan "Ask. Know. Grow.", English in every language. Drawings in one
  hand-drawn line style, rounded caps, in the text's colour: the area kinds in `META-INF/resources/icons/areas/`;
  the empty-state drawings (sprout, notebook, bell, watering can) in `META-INF/resources/icons/empty/`, in soil
  brown (`EmptyState.Drawing`).
- Pages and messages (#87): every page starts with its title on the left and its main action on the right
  (`PageHeader`) and fades in briefly, never for a device that asks for less motion; short messages
  (`Toasts`) carry an icon and a coloured edge for done (green), a problem (red) or information (accent).
- State colours (`--rootology-frost`, `--rootology-pest`, `--rootology-waiting` in `rootology.css`, each with a
  10 % tint): the same colour for frost, pests and waiting questions on every screen.
- `mascot-radish.svg` (and `mascot-radish-1024.png`): the radish mascot, kept for promotion only; it is no longer
  in the app.

The Apache-2.0 licence of the repository grants no trademark rights (its section 6): the name and these marks
identify Rootology.
