# ADR 0002: navigation by levels, dashboards as the hub

- Status: accepted (tried by the owner on `feature/132-navigation`, merged into `develop` on 2026-11-17)
- Date: 2026-11-17
- Issue: #132 (and #133 for the garden's home)

## Context

Rootology has two levels: a space with its gardens, and a garden. Each level has three or four sections (gardens, home, areas, Ask, waiting questions, knowledge, members) plus pages that only a few people with a role see (curator, comparison, administration). Until now a side menu listed them all, a bottom bar repeated four of them on a phone, and a person with one garden landed straight on its home.

The garden's home (#39, #130) has become the place where a person sees what matters: weather, climate, waiting questions, what grows. The owner wants dashboards where everything that needs a decision is in sight, without a dense screen.

## Decision

Navigate by levels, with each level's dashboard as its hub:

- After sign-in the app lands on the space's gardens (cards and map); choosing a garden opens its home.
- The side menu goes. The level's sections become actions in the top bar, always visible: an icon with a count, the word as tooltip and aria label (shown on a wide screen). Space: Questions, Knowledge, Members. Garden: Areas, Questions, Knowledge.
- Pages for a role, Sources and Privacy move to the avatar's menu.
- The breadcrumb is clickable, and each section has a back arrow to the level above (the parent, never the browser history).
- On a phone the same actions sit in the bottom bar, Home first.
- Ask and Waiting questions become one page, Questions, with a "waiting" filter.

Every dashboard card follows one density rule: at most three rows, one number, a "see all" link, and colour only for what needs attention (a frost, questions waiting).

## Reasons

- **Few sections, shallow depth.** Two levels with three or four sections each fit in a bar of icons; a menu column of 256 px is space the dashboards need.
- **One place per level to start from.** The dashboards already carry the actions a person takes most (ask, add a crop, answer a waiting question).
- **Same pattern on every screen size.** The bar's actions and the phone's bottom bar are the same list, so nothing moves between devices except its position.

## Consequences

- One extra step to go from a garden's section to the gardens overview (breadcrumb or back arrow); moving between sections of the same level stays one click.
- Pages for a role are one click further, in the avatar's menu; they are rare.
- The landing page changes for a person with one garden: one tap more, a predictable start.
- Dashboards must keep to the density rule, or the hub becomes the longest page of the app. Phase 2 (sensors, #22) adds cards, not menu entries.
- The waiting questions lose their own menu entry: their count on the Questions action and on the bell keeps them visible, since a question nobody sees stops the human in the loop.

## When to revisit

- A level grows past five sections, or a person has many gardens (more than about six cards: add search or a list view).
- The owner's use on the trial branch shows that moving between sections is slower than with the menu.
- Sensors bring screens that are used continuously (live readings), which may need their own level.
