# Knowledge base sources (#16): comparison

Prepared 2026-11-03 to choose the open data behind the knowledge base v1. Licenses were checked against public pages on that date; they are a starting point, not legal advice. Re-check each one, and its exact terms, before importing any data.

What the knowledge base needs: crops with taxonomy (family, for rotation rules) and Italian names; environmental needs (temperature, soil, pH, water) for "what can I sow now"; sowing and harvest windows; companion and antagonist pairs; pests and diseases with the crops they affect.

## Candidates

| Source | What it gives | License (as found) | Fit |
|---|---|---|---|
| [Wikidata](https://www.wikidata.org/wiki/Wikidata:WikiProject_Botany) | Taxonomy (species, genus, family), names in many languages including Italian, identifiers that link to other databases | CC0 (public domain, commercial use allowed, attribution not required) | **Good backbone**: crop identity, families, Italian labels. Query with SPARQL, cache the result in curated files. |
| [FAO ECOCROP](https://data.apps.fao.org/catalog/dataset/ecocrop) | Environmental requirements of more than 2000 species: temperature and rainfall ranges, soil, pH, light, growing cycle | CC BY 4.0 (attribution to FAO) | **Good** for "can it grow here, now": combine with the garden's location and the weather (#18). |
| [USDA PLANTS](https://plants.usda.gov/) | Taxonomy and plant traits (growth habit, duration, some characteristics) | US government data, public domain | **Useful** for traits; US-centric, so less useful for Italian practice. |
| [OpenFarm (rescued data)](https://github.com/openfarmcc/OpenFarm) | Growing guides: spacing, depth, sun, companions | CC0; the service shut down on 2025-04-21, about 340 crop records survive in a mirror | **Small but clean** seed for companions and growing basics; check the mirror's completeness. |
| [EPPO codes and Global Database](https://data.eppo.int/) | Standard codes and names for pests, diseases and host plants | EPPO Open Data Licence (free account); exact terms for the full database and commercial use to be read | **Good** for pest and disease identifiers and host lists, if the licence allows our use. |
| [AGROVOC](https://en.wikipedia.org/wiki/AGROVOC) (FAO) | Multilingual agricultural thesaurus: shared identifiers for crops and concepts | CC BY 4.0 for the six FAO official languages; **other languages, Italian included, keep their authors' copyright** | **Identifiers yes**; Italian labels only after asking their authors. Wikidata already covers Italian names. |
| [List of companion plants](https://en.wikipedia.org/wiki/List_of_companion_plants) (Wikipedia) | Companion and antagonist pairs with references | CC BY-SA (share-alike) | **Cross-check only**: copying it would put our derived data under share-alike; use its cited sources instead. |
| [Practical Plants](https://practicalplants.org/wiki/practicalplants:copyrights/) | Plant articles with machine-readable data, including interactions | CC BY-SA, with some parts CC BY-NC-SA (non-commercial) | **Avoid** for now: mixed licences, non-commercial parts. |
| [Plants For A Future (PFAF)](https://pfaf.org/user/cmspage.aspx?pageid=11) | Uses and requirements of about 8000 species | CC BY-NC-SA; explicitly not licensed for use in software, apps or websites without an agreement | **Avoid** unless we obtain a licence. |

## Gaps

- **Italian sowing and harvest calendars**: no open dataset found. Proposal: derive windows from ECOCROP temperature ranges plus the garden's climate zone and the forecast, and review a curated calendar by hand, citing the sources used. Inferred windows are tagged `INFERRED` (see the ontology layer in #16).
- **Companion and antagonist pairs**: evidence is uneven and often folk knowledge. Keep each pair with its source and a confidence, so Jev can weigh it and the UI can show it; prefer pairs backed by cited studies.

## Proposed first set

1. Wikidata (CC0) for crop identity, families and Italian names.
2. FAO ECOCROP (CC BY 4.0) for environmental requirements, with attribution in the UI and docs.
3. The rescued OpenFarm records (CC0) plus hand-curated pairs, each with a source, for companions and antagonists.
4. EPPO codes for pests and diseases, after reading the Open Data Licence terms.

Every fact keeps its source and license in the curated files, so the attribution page can be generated from them.

## Decisions (owner, 2026-11-03)

- **Commercial use is a goal.** Even if what is sold is mainly support (setup, sensors) rather than the app itself, the data serves a paid service, so it is treated as commercial use: non-commercial sources (PFAF, the non-commercial parts of Practical Plants) are out, and FAO material under a non-commercial licence needs a licence request.
- **Crops**: a list of typical Italian crops across the four seasons, listed in `docs/crops-v1.md` (39 crops, approved by the owner; months and families still to be verified against the sources on import).
- **Names and missing data**: no external source is final. The knowledge base must be enrichable over time (missing names, crops, varieties, pairs), with provenance and review: see #24.
