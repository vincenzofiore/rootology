# ADR 0001: the front end stays on Vaadin Flow

- Status: accepted
- Date: 2026-11-11
- Issue: #78

## Context

Rootology's UI is written in Vaadin Flow 25.3: server-side Java views, Lumo's components, Vaadin Push for the bell, and one client-side Lit element (`src/main/frontend/garden-map.ts`, MapLibre GL JS) where a browser library was needed. The UI is about 6,800 of the 19,300 lines of main code.

The question is whether a modern application needs a single-page front end (React or similar, with a REST or GraphQL API) instead, and whether to plan that switch.

What the app needs today: forms, grids, a map, cards, live notifications, a phone layout, dark mode, two languages, for a few invited users, written and run by one developer.

## Decision

Keep Vaadin Flow. Do not plan a switch to a single-page front end. Where a screen needs a browser library, add a small client-side element driven by a Java class, as `garden-map.ts` and `ui/GardenMap` do.

## Reasons

- **Security stays on the server.** Views call the services directly; membership, roles and space filters are checked there (`SpaceService.requireContributor`, `@PreAuthorize`, `CrossSpaceAccessTest`). A single-page front end needs a public API, so every endpoint becomes an attack surface with its own authorisation, CSRF and rate limits, and the OWASP review (#41) would have to be redone.
- **One stack, one developer.** No second build, no API contract to version, no client state to keep in step with the server.
- **Cost.** A switch means rewriting a third of the code and building the API, for no feature a user would notice.
- **A modern look is design work, not a framework.** Dark mode, the map, the answer cards and the phone layout were all built in Flow.
- **The escape hatch exists.** Client-side elements cover what Flow lacks; Vaadin also lets React views live beside Flow views in the same app (since 24.4), so a later change could be screen by screen.

## Consequences

- **No offline use.** Every interaction goes to the server; a garden with poor signal cannot use the app. This is the main limit for phase 2 (#22).
- **Latency per interaction.** Each click is a round trip; fine on today's screens, to watch on slow mobile networks.
- **Memory per session** on the server; irrelevant for a few users.
- **No native app or web push.** Notifications on the phone go through Telegram (#38) and email.
- **Commercial components.** Some Vaadin components (for example Charts) need a paid subscription; charts for sensors (#21) should use an open library in a client-side element, like the map. Check the licence before adding any Vaadin component that is not in the free set.

## When to revisit

Reopen this decision if one of these becomes a real need:

- using the app offline in the field, with sync later;
- a native phone app, or web push beyond Telegram;
- a public API for third parties or for devices (sensors could post to a small dedicated endpoint without changing the UI);
- interactions that a server round trip makes visibly slow (live sensor dashboards, drawing on the map).

Even then, prefer moving only the screens that need it (React views beside Flow, or more client-side elements) over a full rewrite.
