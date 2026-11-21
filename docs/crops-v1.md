# Crops for the knowledge base v1 (#16)

The crop selection was approved by the owner on 2026-11-03. It was written from general Italian horticultural practice: the **choice of crops is final**, while the **months and families are still to be verified** crop by crop against the sources (for example the temperature ranges in FAO ECOCROP, families in Wikidata) when they are loaded into the knowledge base. New crops arrive through knowledge enrichment (#24).

Months are Vasto's (central Italy, open field) and are the base of the derivation: the app moves them to each garden's own frost dates with `frost-shift-v1` (#62, `docs/climate-profile.md`), not by a fixed north/south rule. "Seedbed" means sown in a protected seedbed and transplanted later. The botanical family drives rotation rules in the ontology layer.

## Spring-summer crops (sown or planted in spring, harvested in summer)

| Italian | English | Scientific name | Family | Sowing / planting | Harvest |
|---|---|---|---|---|---|
| Pomodoro | Tomato | *Solanum lycopersicum* | Solanaceae | Seedbed Feb-Mar, transplant Apr-May | Jul-Sep |
| Peperone | Sweet pepper | *Capsicum annuum* | Solanaceae | Seedbed Feb-Mar, transplant May | Jul-Oct |
| Peperoncino | Chili pepper | *Capsicum annuum*, *C. frutescens* | Solanaceae | Seedbed Feb-Mar, transplant May | Jul-Oct |
| Melanzana | Eggplant | *Solanum melongena* | Solanaceae | Seedbed Feb-Mar, transplant May | Jul-Sep |
| Patata | Potato | *Solanum tuberosum* | Solanaceae | Plant tubers Mar-Apr | Jun-Jul |
| Zucchina | Zucchini | *Cucurbita pepo* | Cucurbitaceae | Sow Apr-Jun | Jun-Sep |
| Cetriolo | Cucumber | *Cucumis sativus* | Cucurbitaceae | Sow Apr-Jun | Jul-Sep |
| Melone | Melon | *Cucumis melo* | Cucurbitaceae | Sow Apr-May | Jul-Aug |
| Anguria | Watermelon | *Citrullus lanatus* | Cucurbitaceae | Sow Apr-May | Jul-Aug |
| Zucca | Winter squash | *Cucurbita maxima*, *C. moschata* | Cucurbitaceae | Sow Apr-May | Sep-Oct |
| Fagiolo, fagiolino | Bean, green bean | *Phaseolus vulgaris* | Fabaceae | Sow Apr-Jul | Jun-Sep |
| Basilico | Basil | *Ocimum basilicum* | Lamiaceae | Sow Mar-May | Jun-Sep |

## Autumn-winter crops (sown or planted in summer, harvested in autumn and winter)

| Italian | English | Scientific name | Family | Sowing / planting | Harvest |
|---|---|---|---|---|---|
| Cavolfiore | Cauliflower | *Brassica oleracea* var. *botrytis* | Brassicaceae | Seedbed May-Jul, transplant Jul-Aug | Oct-Feb |
| Broccolo | Broccoli | *Brassica oleracea* var. *italica* | Brassicaceae | Seedbed Jun-Jul, transplant Aug | Oct-Dec |
| Cavolo nero | Tuscan kale | *Brassica oleracea* var. *palmifolia* | Brassicaceae | Seedbed May-Jul, transplant Jun-Aug | Oct-Mar |
| Cavolo verza | Savoy cabbage | *Brassica oleracea* var. *sabauda* | Brassicaceae | Seedbed May-Jul, transplant Jun-Aug | Oct-Feb |
| Cavolo cappuccio | Cabbage | *Brassica oleracea* var. *capitata* | Brassicaceae | Seedbed Mar-Jul (spring and autumn types), transplant Apr-Aug | Jun-Dec |
| Cime di rapa | Turnip tops | *Brassica rapa* (cime di rapa group) | Brassicaceae | Sow Aug-Sep | Oct-Jan |
| Finocchio | Florence fennel | *Foeniculum vulgare* var. *azoricum* | Apiaceae | Sow Jun-Aug | Oct-Jan |
| Radicchio | Radicchio | *Cichorium intybus* | Asteraceae | Sow Jun-Jul | Oct-Feb |
| Indivia, scarola | Endive, escarole | *Cichorium endivia* | Asteraceae | Sow Jun-Aug | Sep-Jan |
| Porro | Leek | *Allium porrum* | Amaryllidaceae | Seedbed Feb-Apr, transplant May-Jul | Oct-Mar |
| Spinacio | Spinach | *Spinacia oleracea* | Amaranthaceae | Sow Feb-Mar and Sep-Oct | Apr-May, Nov-Feb |
| Valerianella (songino) | Lamb's lettuce | *Valerianella locusta* | Caprifoliaceae | Sow Aug-Oct | Oct-Mar |

## Autumn-sown crops (harvested in spring and early summer)

| Italian | English | Scientific name | Family | Sowing / planting | Harvest |
|---|---|---|---|---|---|
| Fava | Broad bean | *Vicia faba* | Fabaceae | Sow Oct-Nov (south), Feb (north) | Apr-Jun |
| Pisello | Pea | *Pisum sativum* | Fabaceae | Sow Oct-Nov or Feb-Mar | Apr-Jun |
| Aglio | Garlic | *Allium sativum* | Amaryllidaceae | Plant cloves Oct-Dec | Jun-Jul |
| Cipolla | Onion | *Allium cepa* | Amaryllidaceae | Sow Sep-Oct (autumn types) or Feb-Mar | May-Aug |
| Carciofo | Artichoke | *Cynara cardunculus* var. *scolymus* | Asteraceae | Perennial; plant offshoots Aug-Sep | Nov-May |
| Fragola | Strawberry | *Fragaria* × *ananassa* | Rosaceae | Plant Aug-Oct | Apr-Jun |

## Several seasons (staggered sowing)

| Italian | English | Scientific name | Family | Sowing / planting | Harvest |
|---|---|---|---|---|---|
| Lattuga | Lettuce | *Lactuca sativa* | Asteraceae | Sow Feb-Sep, every few weeks | Most of the year, except hard winter |
| Rucola | Rocket | *Eruca vesicaria* | Brassicaceae | Sow Mar-Sep | Apr-Nov |
| Ravanello | Radish | *Raphanus sativus* | Brassicaceae | Sow Feb-Oct | Mar-Nov |
| Carota | Carrot | *Daucus carota* | Apiaceae | Sow Feb-Jul | May-Nov |
| Bietola | Chard | *Beta vulgaris* var. *cicla* | Amaranthaceae | Sow Mar-Sep | May-Dec |
| Sedano | Celery | *Apium graveolens* | Apiaceae | Seedbed Feb-Apr, transplant May-Jun | Aug-Dec |
| Prezzemolo | Parsley | *Petroselinum crispum* | Apiaceae | Sow Feb-Aug | Most of the year |

## Perennial herbs

| Italian | English | Scientific name | Family | Planting | Harvest |
|---|---|---|---|---|---|
| Rosmarino | Rosemary | *Salvia rosmarinus* | Lamiaceae | Plant in spring or autumn | All year |
| Salvia | Sage | *Salvia officinalis* | Lamiaceae | Plant in spring or autumn | All year |

## Frost groups (#62)

Every crop has a `frost_group` in `crops-v1.csv`, which the window rule `frost-shift-v1` uses to move the
central-Italy windows to a garden's own frost dates. The groups were approved by the owner on 2026-11-10:

| Group | Crops | What the rule does |
|---|---|---|
| TENDER | Tomato, sweet pepper, chili pepper, eggplant, zucchini, cucumber, melon, watermelon, winter squash, bean, basil | The first open-field sowing or transplant does not start before the first safe month; the whole chain moves together, by at most two months. |
| HARDY | Potato, celery, cabbage, leek | As TENDER, but may start one month before the first safe month. |
| AUTUMN | Cauliflower, broccoli, Tuscan kale, savoy cabbage, turnip tops, fennel, radicchio, endive | The chain moves one month earlier when the last safe month is October, two when it is September or earlier; never later. |
| OVERWINTER | Spinach, lamb's lettuce, broad bean, pea, garlic, onion, artichoke, strawberry, rosemary, sage | Autumn sowings move one month earlier at most; spring sowings behave as HARDY; spring harvests start later only where the first safe month is after April. |
| LONG | Lettuce, rocket, radish, carrot, chard, parsley | The sowing starts at the first safe month minus one and ends a month before the last safe month; the harvest starts with the sowing and ends by that month. |

The CENTRO rows of `sowing-windows.csv` are **Vasto's** own windows (42.1/14.7, no altitude correction:
last frost 16 February, no autumn frost). They are the base of the derivation, not "the garden's zone":
the app derives every garden's windows from its own climate profile, and only a zone named in the question
uses that zone's reference site (NORD = Milan, CENTRO = Vasto, SUD = Palermo). The curated NORD row
(broad bean, February) applies only when the question names NORD.

## Annual crops added on 2026-11-18 (#139)

Approved by the owner by group (vegetables, annual herbs, physalis). Families, Wikidata and FAO ECOCROP ids were
checked on 2026-11-18; the months are Vasto's, editorial like the rest of the list, and still to be verified
(`verified=no`). Romanesco and catalogna are not crops of their own: they are names of broccoli and radicchio,
to be added as global names through knowledge enrichment (#24).

| Italian | English | Scientific name | Family | Sowing / planting | Harvest |
|---|---|---|---|---|---|
| Barbabietola | Beetroot | *Beta vulgaris* (Conditiva group) | Amaranthaceae | Sow Mar-Jul | Jun-Nov |
| Rapa | Turnip | *Brassica rapa* var. *rapa* | Brassicaceae | Sow Aug-Sep | Oct-Dec |
| Pastinaca | Parsnip | *Pastinaca sativa* | Apiaceae | Sow Mar-May | Oct-Feb |
| Sedano rapa | Celeriac | *Apium graveolens* var. *rapaceum* | Apiaceae | Seedbed Feb-Mar, transplant May-Jun | Oct-Dec |
| Patata dolce | Sweet potato | *Ipomoea batatas* | Convolvulaceae | Plant rooted slips May-Jun | Sep-Oct |
| Cavolini di Bruxelles | Brussels sprouts | *Brassica oleracea* var. *gemmifera* | Brassicaceae | Seedbed Apr-May, transplant Jun-Jul | Nov-Feb |
| Cavolo rapa | Kohlrabi | *Brassica oleracea* var. *gongylodes* | Brassicaceae | Sow Mar-Aug | May-Nov |
| Borragine | Borage | *Borago officinalis* | Boraginaceae | Sow Mar-May | May-Oct |
| Portulaca | Purslane | *Portulaca oleracea* | Portulacaceae | Sow May-Jul | Jun-Sep |
| Scalogno | Shallot | *Allium cepa* var. *aggregatum* | Amaryllidaceae | Plant bulbs Oct-Dec and Feb-Mar | Jun-Jul |
| Cipollotto | Spring onion | *Allium cepa* | Amaryllidaceae | Sow Feb-Apr and Aug-Sep | Apr-Jun, Oct-Dec |
| Cece | Chickpea | *Cicer arietinum* | Fabaceae | Sow Feb-Apr | Jun-Aug |
| Lenticchia | Lentil | *Lens culinaris* | Fabaceae | Sow Feb-Apr | Jun-Jul |
| Cicerchia | Grass pea | *Lathyrus sativus* | Fabaceae | Sow Feb-Apr | Jun-Jul |
| Fagiolo dall’occhio | Cowpea | *Vigna unguiculata* | Fabaceae | Sow May-Jun | Aug-Sep |
| Fagiolo di Spagna | Runner bean | *Phaseolus coccineus* | Fabaceae | Sow Apr-Jun | Jul-Oct |
| Mais dolce | Sweet corn | *Zea mays* (Saccharata group) | Poaceae | Sow Apr-Jun | Jul-Sep |
| Okra | Okra | *Abelmoschus esculentus* | Malvaceae | Seedbed Mar-Apr, transplant May | Jul-Oct |
| Aneto | Dill | *Anethum graveolens* | Apiaceae | Sow Mar-Jun | May-Sep |
| Coriandolo | Coriander | *Coriandrum sativum* | Apiaceae | Sow Mar-May and Sep | May-Jul, Oct-Nov |
| Cerfoglio | Chervil | *Anthriscus cerefolium* | Apiaceae | Sow Feb-Apr and Aug-Sep | Apr-Jun, Oct-Dec |
| Alchechengi | Cape gooseberry | *Physalis peruviana* | Solanaceae | Seedbed Feb-Mar, transplant May | Aug-Oct |

## Perennials (#139)

`lifecycle` in `crops-v1.csv` marks the crops that stay in place for years: artichoke, strawberry, rosemary and
sage. They are left out of the rotation rule, and from their second year the frost alert judges them on
`frost_hardiness`, the lowest air temperature an established plant stands (ECOCROP's "killing temperature during
rest" is missing for most of them, and its absolute minimum is where growth stops). The first values are
editorial, to be reviewed by the owner:

| Crop | Frost hardiness | Source |
|---|---|---|
| Carciofo | -5 °C | rootology-editorial |
| Fragola | -12 °C | rootology-editorial |
| Rosmarino | -10 °C | rootology-editorial |
| Salvia | -15 °C | rootology-editorial |

### Citrus (2026-11-20)

Five citrus in a new family, Rutaceae, all `TENDER` (planted in spring after the frosts), with a pruning window
(`PRUNE`, "Quando si pota il limone?", question kind `PRUNING_TIME`). Months for CENTRO (Vasto), editorial:

| Crop | Wikidata | ECOCROP | Planting | Harvest | Pruning | Frost hardiness |
|---|---|---|---|---|---|---|
| Limone | Q500 | 714 | Apr–May | all year (mostly Nov–May) | Mar–Apr | -3 °C |
| Arancio | Q3355098 | 720 | Apr–May | Dec–Apr (depends on the variety) | Mar–Apr | -5 °C |
| Mandarino | Q125337 | 718 | Apr–May | Nov–Feb | Mar–Apr | -6 °C |
| Clementina | Q460517 | 718 (mandarin, a parent) | Apr–May | Oct–Jan | Mar–Apr | -5 °C |
| Kumquat | Q124344319 | 1110 (Fortunella japonica) | Apr–May | Dec–Mar | Mar–Apr | -10 °C |

Whether a citrus survives the winter at a given site ("il limone resiste a Torino?") needs the site's typical
winter minimum, which the climate cells do not hold yet (#147). Until then the frost alert judges a
growing citrus on the real forecast, and a garden whose spring comes later than Vasto's loses the winter
harvests (`frost-shift-v1`), so those questions wait for a person.

### Perennial herbs (2026-11-20)

Seven perennial herbs, planted in spring or autumn (`OVERWINTER`, like rosemary and sage) except marjoram, which is
tender. A pruning window only where there is a real gesture to remember; for marjoram and chives a pruning
question waits for a person. Months for CENTRO (Vasto), editorial:

| Crop | Wikidata | ECOCROP | Planting | Harvest | Pruning | Frost hardiness |
|---|---|---|---|---|---|---|
| Timo | Q148668 | 2441 | Mar–May, Sep–Oct | all year | Jun–Jul (light trim after flowering) | -15 °C |
| Origano | Q134283 | 2369 | Mar–May, Sep–Oct | May–Sep | Feb–Mar (the dry stems) | -15 °C |
| Menta | Q160114 | 7710 (var. crispa) | Mar–May, Sep–Oct (best in a pot) | Apr–Oct | Feb–Mar (to the ground) | -20 °C |
| Maggiorana | Q22694 | 2368 | Apr–May | Jun–Sep | none | -5 °C |
| Erba cipollina | Q51148 | 810 | sown Mar–Apr; clumps Mar–Apr, Sep–Oct | Mar–Oct | none | -25 °C |
| Alloro | Q26006 | 1333 | Mar–Apr, Oct–Nov | all year | Mar–Apr (after the frosts) | -10 °C |
| Lavanda | Q42081 | 7172 | Mar–May, Sep–Oct | Jun–Jul (flowers) | Aug–Sep (after flowering) | -15 °C |

Lavender, mint and thyme used to be the tests' examples of an unknown crop; papaya, mango and feijoa took their
place. The experiment's case hard-3 ("Which plants are companions of Lavender?") is unchanged: it reads the
suite's own graph, which never sees this catalog.

### Berries (2026-11-21)

Eight berry shrubs, all `OVERWINTER`, planted in autumn or at the end of winter, with two new families (Ericaceae,
Grossulariaceae). Months for CENTRO (Vasto), editorial:

| Crop | Wikidata | ECOCROP | Planting | Harvest | Pruning | Frost hardiness |
|---|---|---|---|---|---|---|
| Mirtillo | Q468695 | 10704 (Vaccinium corymbosum) | Oct–Nov, Feb–Mar | Jun–Aug | Feb–Mar (still dormant) | -25 °C |
| Lampone | Q12252383 | 1877 (ssp. idaeus) | Oct–Nov, Feb–Mar | Jun–Jul, Sep–Oct (everbearing) | Aug (canes that fruited), Feb–Mar (thinning) | -25 °C |
| Mora | Q13541716 | none (9359 is empty) | Oct–Nov, Feb–Mar | Jul–Sep | Sep–Oct (canes that fruited), Feb–Mar (side shoots) | -20 °C |
| Ribes rosso | Q146661 | 9285 | Oct–Nov, Feb–Mar | Jun–Jul | Dec–Feb (dormant) | -25 °C |
| Ribes nero | Q146604 | 9282 | Oct–Nov, Feb–Mar | Jun–Jul | Dec–Feb (dormant) | -25 °C |
| Uva spina | Q41503 | 9290 | Oct–Nov, Feb–Mar | Jun–Jul | Dec–Feb (dormant) | -25 °C |
| Goji | Q196332 | none (7443 is empty) | Oct–Nov, Mar–Apr | Jul–Oct | Feb–Mar | -20 °C |
| Aronia | Q162774 | none (3116 is *Amelanchier alnifolia*) | Oct–Nov, Feb–Mar | Aug–Sep | Feb–Mar (light, the oldest stems) | -30 °C |

Blueberry and aronia were first proposed with a January–February pruning; `frost-shift-v1` moves an `OVERWINTER`
window that starts before Vasto's first safe month minus one, so Vasto itself would have moved it. Their pruning
is February–March, still in dormancy. Without an ECOCROP record, blackberry, goji and aronia cannot be judged
by the frost and light rules: they stay in the answers with the "not known" notes. Currants and gooseberry
suffer Vasto's summer heat (part shade): a gardening tip, not a fact of the base.

### Pome and stone fruit (2026-11-21)

Nine fruit trees, all `OVERWINTER`, in the Rosaceae family. Planting and pruning come from the Abruzzo integrated
production rules (`abruzzo-dpi-2024`, Allegato A, chapters "Drupacee" p. 110 and "Pomacee" p. 124: planting "in
autunno inoltrato o inizio inverno"; winter pruning "in settembre-ottobre o da gennaio fino alla fioritura", not in
late autumn; summer pruning in July), cited as facts, no text reused. The September–October winter pruning is left
out to keep one clear answer. Harvest months are editorial (`rootology-editorial`): the CREA variety lists give
ripening per variety in days from a reference cultivar (Redhaven ripens 5–10 July in central Italy), not months
per crop. Months for Vasto:

| Crop | Wikidata | ECOCROP | Planting | Harvest | Pruning | Frost hardiness |
|---|---|---|---|---|---|---|
| Melo | Q18674606 | 1407 | Nov–Dec | Aug–Oct | Jan–Mar, Jul | -25 °C |
| Pero | Q146281 | 1832 | Nov–Dec | Jul–Oct | Jan–Mar, Jul | -25 °C |
| Cotogno | Q43300 | 5117 | Nov–Dec | Sep–Oct | Jan–Mar, Jul | -20 °C |
| Pesco | Q13189 | 1796 | Nov–Dec | Jun–Sep | Jan–Mar, Jul | -20 °C |
| Nettarina | Q3408651 | 1796 (peach, same species) | Nov–Dec | Jun–Sep | Jan–Mar, Jul | -20 °C |
| Albicocco | Q37453 | 2398 | Nov–Dec | Jun–Jul | Jan–Mar, Jul | -20 °C |
| Susino | Q44120 | 16203 | Nov–Dec | Jul–Sep | Jan–Mar, Jul | -25 °C |
| Ciliegio | Q165137 | 8965 | Nov–Dec | May–Jun | Jan–Mar, Jul | -25 °C |
| Amarena | Q131517 | 8970 | Nov–Dec | Jun–Jul | Jan–Mar, Jul | -30 °C |

A January pruning needed a change to `frost-shift-v1`: an `OVERWINTER` pruning window that starts before August now
moves only by how much a site's first safe month is later than Vasto's (March), so it stays in January at Vasto
and moves to March–May at Aosta. The same change moves thyme's light trim to August–September at Aosta.
Late frosts on the flowers (March–April), the real risk for fruit trees, are not modelled yet.

### Other fruit trees (2026-11-21)

Nine more fruit trees. From the Abruzzo integrated production rules (`abruzzo-dpi-2024`, cited as facts): kiwi
(actinidia) planting "in autunno inoltrato o inizio inverno" and winter pruning "da dicembre fino a febbraio"; table
grape planting in autumn, winter or spring and dry pruning "dalla caduta foglie fino a prima del pianto"; olive
planting preferably in spring (in autumn only near the sea, left out since the rule cannot tell the coast). The other
months are editorial. Winter prunings are January–February, inside the DPI's ranges: a window starting in December
would move to November at cold sites. Frost hardiness is ECOCROP's killing temperature during rest (`fao-ecocrop`),
except black mulberry (ECOCROP's -5 °C is not credible) and jujube (no ECOCROP record), editorial. Olive, fig,
pomegranate and loquat are `HARDY` (planted from a month before the site's first safe month); the others
`OVERWINTER`. Months for Vasto:

| Crop | Wikidata | ECOCROP | Group | Planting | Harvest | Pruning | Frost hardiness |
|---|---|---|---|---|---|---|---|
| Olivo | Q37083 | 1553 | HARDY | Mar–Apr | Oct–Dec | Feb–Apr | -10 °C |
| Uva da tavola | Q30046 | 2160 | OVERWINTER | Nov–Mar | Aug–Oct | Jan–Feb, May–Jun | -20 °C |
| Fico | Q36146 | 1071 | HARDY | Feb–Mar | Jun–Sep | Feb–Mar | -12 °C |
| Kaki | Q158482 | 945 | OVERWINTER | Nov–Dec | Oct–Nov | Jan–Feb | -18 °C |
| Melograno | Q13188 | 1829 | HARDY | Mar–Apr | Sep–Nov | Feb–Mar | -10 °C |
| Kiwi | Q343300 | 889 (*Actinidia chinensis*) | OVERWINTER | Nov–Dec | Oct–Nov | Jan–Feb, Jun–Jul | -9 °C |
| Nespolo del Giappone | Q41505 | 1002 | HARDY | Mar–Apr | May–Jun | Jun–Jul | -10 °C |
| Gelso nero | Q161040 | 2349 | OVERWINTER | Nov–Dec | Jun–Aug | Jan–Feb | -15 °C |
| Giuggiolo | Q11181633 | — | OVERWINTER | Nov–Dec | Sep–Oct | Jan–Feb | -25 °C |

## Notes

- 99 crops in 27 families (39 from 2026-11-03, 22 annuals from 2026-11-18, 5 citrus and 7 perennial herbs from 2026-11-20, 8 berries, 9 pome and stone fruit and 9 other fruit trees from 2026-11-21).
- Candidates for later: nuts (almond, walnut, hazelnut, chestnut), flowers and green manures of #139.
- The seasons in this file are a starting point for the ontology's sowing windows; in the app they become a garden's own calendar from its climate profile (#62), refined with the weather (#18) and, later, sensor readings (#21).

## Italian articles (#111)

Every crop has an `italian_article` in `crops-v1.csv` (il, lo, la, l’, i, gli, le), so a sentence of the app names
the crop as Italian does: "Quali parassiti minacciano la fragola questo mese?", "Quando raccogliere le cime di
rapa?". `KnowledgeValidator` refuses any other value. English names take no article.
