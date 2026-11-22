# Pests and diseases for the knowledge base v1 (#16)

Approved by the owner on 2026-11-03; curated data in `src/main/resources/knowledge/pests.csv` and `pest-hosts.csv`. The most common pests and diseases of the 39 approved crops in Italian vegetable gardens.

- **Identity**: every organism is identified by its EPPO code, checked one by one on the public EPPO Global Database pages. The EPPO Codes Open Data Licence allows commercial use, with attribution to EPPO and the download date. That licence covers the codes only, not the content of EPPO datasheets, so hosts and periods below do not come from EPPO.
- **Hosts and periods**: from general Italian horticultural practice (editorial, like the crop list), to be verified. Months are the usual period of activity or risk in central Italy, open field; many depend on weather (wet springs for downy mildews, hot dry summers for spider mites). They are a prior for the decision layer, later refined by the weather (#18) and sensors (#21).
- Only hosts among the 39 crops are listed. "Cole crops" are the five *Brassica oleracea* crops.

## Insects, mites and molluscs

| Italian | Organism | EPPO | Main hosts | Months |
|---|---|---|---|---|
| Afidi | Aphids (*Aphididae*) | 1APHIF | Lettuce, cole crops, bean, broad bean, pea, sweet pepper, eggplant, potato, zucchini, cucumber, melon | Apr-Jun, Sep-Oct |
| Dorifora | Colorado potato beetle (*Leptinotarsa decemlineata*) | LPTNDE | Potato, eggplant, tomato | May-Aug |
| Cavolaia | Large white butterfly (*Pieris brassicae*) | PIERBR | Cole crops, turnip tops | May-Oct |
| Tignola delle crucifere | Diamondback moth (*Plutella xylostella*) | PLUTMA | Cole crops, rocket, radish | May-Oct |
| Altiche | Flea beetles (*Phyllotreta*) | 1PHYEG | Rocket, radish, turnip tops, young cole crops | Apr-Sep |
| Mosca della carota | Carrot fly (*Chamaepsila rosae*) | PSILRO | Carrot, celery, parsley | May-Jun, Aug-Oct |
| Mosca della cipolla | Onion fly (*Delia antiqua*) | HYLEAN | Onion, leek, garlic | Apr-Jun, Aug-Sep |
| Tripide della cipolla | Onion thrips (*Thrips tabaci*) | THRITB | Onion, leek, garlic, cabbage | May-Sep |
| Tripide occidentale | Western flower thrips (*Frankliniella occidentalis*) | FRANOC | Sweet pepper, tomato, lettuce, strawberry, cucumber | May-Sep |
| Mosca bianca delle serre | Greenhouse whitefly (*Trialeurodes vaporariorum*) | TRIAVA | Tomato, eggplant, bean, zucchini, cucumber | Jun-Oct |
| Mosca bianca del tabacco | Tobacco whitefly (*Bemisia tabaci*) | BEMITA | Tomato, eggplant, sweet pepper, melon | Jul-Oct |
| Ragnetto rosso | Two-spotted spider mite (*Tetranychus urticae*) | TETRUR | Bean, cucumber, melon, eggplant, tomato, strawberry | Jun-Sep |
| Nottua del pomodoro | Cotton bollworm (*Helicoverpa armigera*) | HELIAR | Tomato, sweet pepper | Jul-Sep |
| Tignola del pomodoro (Tuta) | Tomato leafminer (*Phthorimaea absoluta*) | GNORAB | Tomato, eggplant, potato | May-Oct |
| Elateridi (ferretti) | Wireworms (*Agriotes*) | 1AGRIG | Potato, carrot, lettuce | Mar-Jun, Sep-Oct |
| Limaccia grigia | Grey field slug (*Deroceras reticulatum*) | DERORE | Lettuce, strawberry, young cole crops, young beans | Mar-Jun, Sep-Nov |

## Diseases

| Italian | Organism | EPPO | Main hosts | Months |
|---|---|---|---|---|
| Peronospora del pomodoro e della patata | Late blight (*Phytophthora infestans*) | PHYTIN | Tomato, potato | May-Sep, in wet periods |
| Peronospora delle cucurbitacee | Cucurbit downy mildew (*Pseudoperonospora cubensis*) | PSPECU | Cucumber, melon, zucchini, watermelon, winter squash | Jul-Sep |
| Peronospora della lattuga | Lettuce downy mildew (*Bremia lactucae*) | BREMLA | Lettuce | Mar-May, Sep-Nov |
| Oidio delle cucurbitacee | Cucurbit powdery mildew (*Podosphaera xanthii*) | PODOXA | Zucchini, cucumber, melon, watermelon, winter squash | Jun-Sep |
| Muffa grigia | Grey mould (*Botrytis cinerea*) | BOTRCI | Strawberry, lettuce, tomato, bean | Mar-Jun, Sep-Nov, in humid weather |
| Alternariosi | Early blight (*Alternaria solani*) | ALTESO | Tomato, potato, eggplant | Jun-Sep |
| Ruggine dell'aglio e del porro | Leek rust (*Puccinia allii*) | PUCCAL | Garlic, leek, onion | Apr-Jun, Sep-Oct |
| Ernia del cavolo | Clubroot (*Plasmodiophora brassicae*) | PLADBR | Cole crops, turnip tops, radish, rocket | Aug-Nov (soil-borne, acid and wet soils) |

## Notes

- 24 organisms: 16 insects, mites and molluscs, 8 diseases.
- Lamb's lettuce, fennel, chard, spinach, artichoke, the herbs and some others have no listed pest yet: the list covers the most common problems, not every crop. Missing ones can be added through knowledge enrichment (#24).
- The relation "pest affects crop in these months" is what the "relevant pests this week" question (#17) will use, together with the weather.
- Control methods (#140) are in `pest-controls.csv`, see below.
- Sources used: EPPO codes (identity; EPPO Codes Open Data Licence, commercial use allowed, attribution to EPPO with the download date); editorial practice for hosts and months (to verify, `verified=no`).

## Control methods (#140, 2026-11-22)

`pest-controls.csv` lists what a home gardener can do against each of the 24 organisms: 89 methods, at least one
per organism. A method belongs to the pest, not to a pest-crop pair: aphids are handled the same way on lettuce or on
broad beans (owner, 2026-11-22). A column for crop-specific exceptions can be added when a source gives one.

- `kind`: PRACTICE (cultural measures, mesh, rotation, cultivars), NATURAL_ENEMY (to encourage, or released as
  biological control: the agent's scientific name in `agent`), SERVICE_PLANT (a service crop sown nearby, its id in
  `agent`; none yet, they come with the service plants), SUBSTANCE, MICROORGANISM, TRAP.
- Substances and microorganisms are only those allowed in organic production by Regulation (EU) 2021/1165, Annex I
  (`eu-2021-1165`, consolidated text of 2026-01-01): copper compounds (at most 28 kg/ha over 7 years), sulphur,
  fatty acids, azadirachtin, spinosad, ferric phosphate, potassium hydrogen carbonate, and approved microorganisms
  such as *Bacillus thuringiensis*. The validator refuses a substance or microorganism that does not cite it. No
  synthetic product, no brand; the answer always says to read the label.
- Natural enemies released as biological control are those of EPPO Standard PM 6/3(5) (`eppo-pm6-3`), with the
  target pests the standard gives; the EPPO database itself is not imported (its licence is unclear).
- Practices come from RHS advice pages (UK) and UC IPM guidelines (California), cited as facts; months and
  thresholds specific to those climates are left out. A few long-standing practices without a source we could
  check (Colorado beetle, *Tuta absoluta* traps) are editorial and `traditional`.
- `evidence`: `study` where the source reports its own trials (carrot fly mesh), `extension` for advisory services,
  `traditional` otherwise. Ferric phosphate is `traditional` here: the RHS advises against any slug pellets for
  wildlife, and none of our sources measures it.
