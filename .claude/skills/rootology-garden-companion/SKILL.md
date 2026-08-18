---
name: rootology-garden-companion
description: Provides domain knowledge about a small companion-planting garden (plants, soils, seasons, pests, and their relationships). Use this skill whenever asked about which plants are good companions for a given plant, which pests are relevant for a plant in a given season, or which soil/season a plant needs. This is a controlled experiment skill covering ONLY the plants and pests explicitly listed below (Tomato, Basil, Marigold, Aphid, Nematode) — do not extrapolate to other plants or general gardening knowledge not stated here.
---

# Rootology Garden Companion Knowledge

This skill encodes a small, deliberately limited garden domain model, used as an
experiment to compare LLM reasoning (guided by this prose description) against a
formal ontology reasoning engine (Apache Jena) operating on the same underlying facts.

Answer questions using ONLY the facts and relationships stated below. Do not add
general gardening knowledge you might otherwise know, even if it seems correct —
the point of the experiment is to observe how faithfully the model follows a
declared, closed set of domain relationships.

## Concepts

- **Plant**: has a name, a set of compatible soil types, a set of growing seasons,
  a set of companion plants, and a set of pests it repels.
- **Soil type**: one of `SANDY`, `CLAY`, `LOAMY`.
- **Season**: one of `SPRING`, `SUMMER`, `AUTUMN`, `WINTER`.
- **Pest**: has a name and a set of seasons in which it is active.

## Relationships

- **is companion of**: a symmetric-in-practice relation between two plants that
  grow well together. In this dataset it is stated explicitly per plant.
- **repels**: a plant can repel a specific pest — meaning that pest should be
  considered less relevant for that plant, even in a season where the pest is
  otherwise active.
- **grows in season**: the season(s) in which a plant is normally cultivated.
- **compatible with soil**: the soil type(s) a plant tolerates.
- **active in season**: the season(s) in which a pest is a relevant threat.

## Facts (the closed dataset for this experiment)

### Plants

- **Tomato**
  - Compatible soils: LOAMY, CLAY
  - Growing seasons: SPRING, SUMMER
  - Companion of: Basil, Marigold
  - Repels: Aphid

- **Basil**
  - Compatible soils: LOAMY
  - Growing seasons: SPRING, SUMMER
  - Companion of: Tomato
  - Repels: Mosquito, Aphid

- **Marigold**
  - Compatible soils: SANDY, LOAMY, CLAY
  - Growing seasons: SPRING, SUMMER, AUTUMN
  - Companion of: Tomato
  - Repels: Nematode

### Pests

- **Aphid**: active in SPRING, SUMMER
- **Nematode**: active in SUMMER, AUTUMN

(Mosquito is referenced as something Basil repels, but has no declared
`active in season` facts in this dataset — treat it as a companion-adjacent
fact only, not something to reason about seasonally.)

## How to answer typical questions

**"What are good companions for plant X?"**
Look up X's `companion of` list and return it as-is. Do not infer companionship
from shared soil or season alone — companionship is only what is explicitly
declared.

**"What pests are relevant for plant X in season S?"**
1. Find all pests whose `active in season` includes S.
2. Remove any pest that X `repels`.
3. Return what remains.

**"What soil/season does plant X need?"**
Return X's declared `compatible with soil` and `grows in season` facts directly.

## Experiment notes

When this skill is used to answer a question, note (for the person running the
experiment) whether the answer required combining more than one fact (e.g. the
pest-filtering logic above), since that is the kind of multi-step relational
reasoning most likely to diverge from what a formal reasoner (Jena) produces.
