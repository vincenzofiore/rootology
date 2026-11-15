# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

A few invited people, mostly the owner's friends: mixed experience, many beginners and a few experienced growers. They grow a vegetable garden, a terrace, a balcony or pots, in Italy for the most part, and speak Italian or English. They use Rootology in two situations: quick questions from a phone while in the garden (standing, one hand busy, bright light, short answers wanted), and calmer planning from a computer at home (calendars, areas, history). A space can be shared: owners and members ask and answer, guests only look.

## Product Purpose

Rootology answers questions about one's own garden (what to sow now, when to sow or harvest, which plants go together, which pests threaten what grows) from verifiable facts with their sources, and says when it does not know. Success means both: answers people can trust, and fewer mistakes in the garden (sowing at the right time, avoiding frost and disease). Neither comes first.

## Positioning

The facts come from a knowledge graph where every fact has a source; the language model only understands the question and phrases the answer, it never invents facts or chooses the values. A calibrated decision layer decides with a real confidence, and an uncertain question waits for a person of the space instead of getting a guess; who answered is recorded. The answer uses the garden's own context (position, climate profile, weather forecast, areas, the month) without the person having to say it.

## Operating Context

- The garden is the working context: questions asked inside a garden use it; outside a garden a question can name a place, and the app asks for the place or the garden with one tap when it needs one.
- Waiting questions, notifications (in the app, email, Telegram), daily weather alerts for frost and late blight.
- A prototype for a few real users, invite-only, deployed at rootology.app; the long-term direction is precision agriculture with field sensors.

## Capabilities and Constraints

- Web app built with Vaadin Flow (server-side Java, Lumo theme); styles live only in `styles/rootology.css`. A screen that needs a browser library gets a small client-side element (ADR 0001).
- Every UI text in Italian and English (Italian by default); URLs and routes always in English.
- Light and dark mode follow the device, with a manual switch; every screen must work on a phone (360 px), in dark mode and with a keyboard.
- Terminology: "orto" (vegetable garden, never "giardino"), "area" for a growing unit (open field, raised bed, tank, pot, greenhouse), "space" for the shared container of gardens and people.
- Questions cost money (Claude and the decision layer): a daily limit per person applies.

## Brand Commitments

- Name: Rootology. Slogan "Ask. Know. Grow.", in English in every language, always under the wordmark.
- The sprout in its circle is the third "o" of the wordmark; the icon is the white sprout on green.
- Brand direction "Orto" (`docs/brand`), designed with the owner, no external designer. Drawings in one line style, few words. The radish mascot is for promotion only, outside the app.
- Prefer a clear icon with a value or a count over a sentence; the word stays as tooltip and aria label. The same icon and pattern for the same action everywhere; confirm before anything destructive.

## Evidence on Hand

- The quality suite (golden questions with answers written by hand, live runs recorded in `src/main/resources/quality/live-runs/`) and the original experiment (`docs/experiment-log.md`).
- No testimonials, customers, metrics of adoption or press: do not invent any.

## Product Principles

1. Trust before speed: say "I don't know, a person will answer" rather than guess.
2. Show only what helps the decision at hand; easy to understand at a glance.
3. The context does the work: the garden, its place and the season are applied without being asked.
4. Every fact shows where it comes from.
5. Small, readable, inspectable: a prototype for a few real users, with the basics of real software.

## Accessibility & Inclusion

Usable on a phone outdoors and with a keyboard; touch targets of 44 px on coarse pointers; light and dark themes; the word behind every icon is its tooltip and aria label.
