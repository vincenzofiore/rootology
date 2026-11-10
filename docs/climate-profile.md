# The climate profile of a garden (#62)

Every garden with coordinates gets its own sowing and harvest calendar from the climate of its site, instead
of an Italian zone. Two rules do the work: `frost-season-v1` turns the site's frost dates into two safe months,
and `frost-shift-v1` moves the curated central-Italy windows only where those months make them impossible.
This page describes the file, how it is made, both rules, what they produce in twelve places, and their limits.

## The file

`src/main/resources/knowledge/climate-cells-v1.csv.gz` is a gzipped CSV with one row per land cell of Europe
(about 128,000 cells, at least 25 % land), 0.1° apart:

| Column | Meaning |
|---|---|
| `lat`, `lon` | The centre of the cell, WGS84. |
| `elevation_m` | The mean land elevation of the cell from the Copernicus DEM GLO-90, metres. |
| `land_fraction` | The share of the cell that is land, 0 to 1. Sea is 0; Alpine cells may be 0.98 because of lakes and DEM gaps. |
| `fd` | Frost days per year (ETCCDI FD, minimum air temperature below 0 °C), median over 1996–2025. |
| `gsl` | Growing season length in days (ETCCDI GSL), median over 1996–2025. |
| `last_frost_p50`, `last_frost_p80` | The last frost of the season, median and 80th percentile, as `MM-DD`. Empty when the cell has no such frost. |
| `first_frost_p50`, `first_frost_p20` | The first frost of the season, median and 20th percentile, as `MM-DD`. Empty when the cell has no such frost. |
| `frost_years` | How many of the 30 years have at least one frost. |

The two percentiles are the "1 year in 5" dates: in four years out of five the last frost is no later than
`last_frost_p80`, and in four years out of five the first frost is no earlier than `first_frost_p20`. They are
the cautious dates the app uses, not the medians.

The file's own header carries the notices:

> Contains modified Copernicus Climate Change Service information. Land cells (at least 25% land) and mean land
> elevation from the Copernicus DEM GLO-90: produced using Copernicus WorldDEM-90 (c) DLR e.V. 2010-2014 and
> (c) Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA.

The sources are in `sources.csv`: `agera5` (Copernicus C3S licence, attribution), `copernicus-dem-90m`
(Copernicus DEM licence, free and commercial use allowed, attribution) and `geonames-gtopo30` (GeoNames,
CC BY 4.0; GTOPO30 is public domain).

## How `tools/climate` makes it

The tool is outside the Maven build and CI, and needs a CDS account (`~/.cdsapirc`, never in the repository):

- `agera5_frost.py years A B` downloads AgERA5 v2.0 day by day, month by month, and keeps per cell the yearly
  indices: frost days, growing season length, last and first frost. Global files: no area parameter.
- `combine A B --dem --out` writes the CSV: the median and 1-in-5-years dates, and, with `--dem`, the cell
  elevation and land fraction.
- `dem_cells.py tiles` reads the Copernicus DEM GLO-90 tiles from the public bucket `copernicus-dem-90m` with
  `rasterio`, and keeps per cell the mean land elevation and the land fraction.

Periods: 1996–2025 for the app, 1991–2020 to check against the ISPRA normals.

## `frost-season-v1`

The rule gives each site two months, after the altitude correction:

1. **First safe month**: the month of the last frost (1 in 5 years), or the next month when the frost is after
   the 15th. January when the site has no last frost.
2. **Last safe month**: the month of the first frost (1 in 5 years), or the previous month when the frost is on
   or before the 15th. December when the site has no first frost.

The altitude correction is 6.5 °C per 1000 m, and 0.15 °C per day of the frost season. It moves the last frost
later and the first frost earlier by the same number of days. It is capped at 500 m of difference (with a note),
and it is not applied when the garden's altitude is not known or is -9999 (a note), and when the garden is on
the coast, below its cell, with a land fraction below 0.9 (a note). Frost-free dates are never shifted.

The garden's altitude comes from GeoNames `gtopo30` at about 1 km, requested when a garden is saved or its
coordinates change. Saving never depends on it: a failed lookup leaves the altitude unknown, and the profile
says so.

## `frost-shift-v1`

The curated CENTRO windows of `sowing-windows.csv` are **Vasto's**, not "the garden's zone". The rule moves each
window by `d` months, where `d` depends on the crop's frost group (see `docs/crops-v1.md`):

| Group | Shift `d` |
|---|---|
| TENDER | `clamp(firstSafe - start of the crop's first open-field window, 0, 2)`, applied to every window; the seedbed is used when the crop has no field window. |
| HARDY | The same with `firstSafe - 1`. |
| AUTUMN | `clamp(lastSafe - 11, -2, 0)` for every window: October is -1, September or earlier -2. |
| OVERWINTER | Harvests `clamp(firstSafe - 4, 0, 2)`; windows starting August or later `clamp(lastSafe - 11, -1, 0)`; other windows `clamp(firstSafe - 1 - start, 0, 2)`. |
| LONG | Sowings become `max(start, firstSafe - 1) .. min(end, lastSafe - 1)`; harvests start with the sowing plus `max(0, firstSafe - 1 - sowing start)` and end at most at `lastSafe`. |

Always:

- a year-round window (11 months or more) stays as it is;
- TENDER, HARDY and OVERWINTER harvests end at most at `lastSafe`;
- a winter harvest (starting October to February) is not derived when its `d` is not 0;
- a window whose end falls before its start is never derived;
- nothing ever moves earlier in spring than Vasto: the clamps above guarantee it.

A window that is not derived is not a window for that site: the question escalates, as for any missing fact.

A garden's profile applies to every garden with coordinates, CENTRO included. A garden abroad with a profile
gets its derived windows and the `WINDOWS_FROM_CLIMATE` note; `OUTSIDE_ITALY` stays only for a garden abroad
with no profile. A zone named in the question uses that zone's reference site, with the cell's own values and no
altitude correction: NORD = Milan (45.46, 9.19), CENTRO = Vasto (42.11, 14.71), SUD = Palermo (38.12, 13.36).
The curated NORD row (broad bean, February) applies only when the question names NORD, and then it wins over
the derived broad bean sowing. Vasto itself (CENTRO named in the question, or the default outside a garden)
gets the curated rows as they are, tagged curated, not inferred.

## Twelve places

From the table the owner reviewed (2026-11-10), with the gardens' GeoNames altitudes:

| Place | First / last safe month | Same / changed / not derived (of 92) | Tomato transplant | Sown or planted in November |
|---|---|---|---|---|
| Vasto (reference) | Mar / Dec | 92 / 0 / 0 | 4-5 | Garlic, Broad bean, Pea, Rosemary, Sage |
| Roma | Mar / Nov | 89 / 3 / 0 | 4-5 | the same five |
| Bari, Palermo | Jan / Dec | 92 / 0 / 0 | 4-5 | the same five |
| Milano, Bologna, Firenze, L'Aquila, Lyon | Apr / Nov | 77 / 15 / 0 | 4-5 | the same five |
| Torino | Apr / Oct | 48 / 37 / 7 | 4-5 | Garlic |
| Aosta (585 m) | May / Oct | 17 / 65 / 10 | 5-6 | Garlic |
| Cortina (1247 m) | Jun / Sep | 8 / 73 / 11 | 6-7 | Garlic |

The surprises of the old one-month zone shift are gone: no window reverses, the tomato stays in April–May on
the plains, the northern cabbages move earlier, garlic in Turin stays in September–November, and the seedbed →
transplant → harvest chain stays in order.

## Limits

- **0.1° cells.** A cell is about 11 km; a garden's valley or hill may differ. The nearest land cell within
  0.15° is used, not only the rounded one; no cell within reach means no profile, and a note.
- **1 km altitude.** GeoNames GTOPO30 is coarse, especially on coasts, and only moves the dates by whole
  months through the two safe months.
- **Coasts.** A garden below its cell and on a cell with less than 90 % land keeps the cell's dates, so a
  coastal garden is not made colder by the cell's inland hill.
- **Basins too mild.** The altitude correction cannot see a basin's cold air lake: L'Aquila has the same months
  as Florence. The 500 m cap reduces the error but does not remove it, and the note says the correction was capped.
- **The south is never earlier than Vasto.** Frost dates do not justify moving southern sowings earlier when
  Vasto and Palermo do not freeze anyway. Earlier sowings in the south would need temperature, not frost dates.
- **Ground frost with air minima above 0 °C.** The frost dates come from air minima: a ground frost can still
  happen on a clear night with the air at 1–2 °C, and the profile does not see it.
