# Crop pairs (companions and antagonists) for the knowledge base v1 (#16)

Approved by the owner on 2026-11-03; curated data in `src/main/resources/knowledge/crop-pairs.csv`. Only pairs between the 39 approved crops (`docs/crops-v1.md`). Each pair has a source and an evidence level, so Jev can weigh it and the UI can say how solid it is:

- **study**: a peer-reviewed study measured an effect (often in specific conditions);
- **extension**: an evidence-based recommendation from a university extension service, usually for plant-health reasons;
- **traditional**: long-standing gardening advice without reproducible evidence. The main source here says so itself: "There appears to be no research proven reproducible companion planting recommendations".

Pairs are stored as declared, in one direction; the knowledge base never infers the reverse pair on its own (the experiment's rule "companions are only what is declared" stays).

"Cole crops" below means the five *Brassica oleracea* crops: cauliflower, broccoli, Tuscan kale, Savoy cabbage, cabbage.

## Sources

| Id | Source | Kind |
|---|---|---|
| `cornell-1999` | Cornell Cooperative Extension, Chemung County, *Companion Planting*, 1999 ([PDF](https://www.midway.k-state.edu/lawn-and-garden/vegetablegardening/documents/COMPANION%20-%20Cornell.pdf)) | traditional advice, explicitly not proven |
| `uvah-coaker-1984` | Uvah I., Coaker T. H., *Effect of mixed cropping on some insect pests of carrots and onions*, Entomologia Experimentalis et Applicata, 1984 ([link](https://onlinelibrary.wiley.com/doi/abs/10.1111/j.1570-7458.1984.tb03422.x)) | study |
| `rosemary-pepper` | *Intercropping Rosemary (Rosmarinus officinalis) with Sweet Pepper (Capsicum annuum) Reduces Major Pest Population Densities without Impacting Natural Enemy Populations* ([PMC](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7830198/)) | study |
| `agronet-basil-2016` | *Synergistic Effects of Agronet Covers and Companion Cropping on Reducing Whitefly Infestation and Improving Yield of Open Field-Grown Tomatoes*, Agronomy 6(3):42, 2016 ([doi](https://doi.org/10.3390/agronomy6030042)) | study (basil together with insect nets, not basil alone) |
| `purdue-bp80` | Purdue Extension, *Late Blight of Tomato and Potato*, BP-80 ([PDF](https://www.extension.purdue.edu/extmedia/bp/bp-80-w.pdf)) | extension |

We cite facts from these sources; we do not copy their text or tables.

## Companions

| Crop | Companion | Effect | Evidence | Source |
|---|---|---|---|---|
| Carrot | Onion | Fewer carrot fly attacks on carrots, fewer onion thrips on onions | study | `uvah-coaker-1984`, `cornell-1999` |
| Carrot | Leek | Repels carrot fly | traditional | `cornell-1999` |
| Carrot | Rosemary | Repels carrot fly | traditional | `cornell-1999` |
| Carrot | Sage | Repels carrot fly | traditional | `cornell-1999` |
| Sweet pepper | Rosemary | Lower densities of major pests, natural enemies unaffected | study | `rosemary-pepper` |
| Sweet pepper | Basil | Similar needs, grow well together | traditional | `cornell-1999` |
| Tomato | Basil | Less whitefly when combined with insect nets; basil alone not shown | study (weak) | `agronet-basil-2016`, `cornell-1999` |
| Tomato | Onion | Compatible | traditional | `cornell-1999` |
| Tomato | Parsley | Compatible | traditional | `cornell-1999` |
| Tomato | Carrot | Compatible | traditional | `cornell-1999` |
| Cole crops | Sage | Aromatic plant, said to deter cabbage pests | traditional | `cornell-1999` |
| Cole crops | Rosemary | Aromatic plant, said to deter cabbage pests | traditional | `cornell-1999` |
| Cole crops | Celery | Grows well with cole crops | traditional | `cornell-1999` |
| Cole crops | Onion | Get along well together | traditional | `cornell-1999` |
| Bean | Carrot | Thrive together | traditional | `cornell-1999` |
| Bean | Cauliflower | Thrive together | traditional | `cornell-1999` |
| Bean | Cucumber | Beans aid cucumbers | traditional | `cornell-1999` |
| Bean | Cabbage | Beans aid cabbage | traditional | `cornell-1999` |
| Lettuce | Strawberry | Grow well together | traditional | `cornell-1999` |
| Lettuce | Cucumber | Grow well together | traditional | `cornell-1999` |
| Lettuce | Carrot | Grow well together | traditional | `cornell-1999` |
| Lettuce | Radish | Especially good together | traditional | `cornell-1999` |
| Onion | Strawberry | Get along well | traditional | `cornell-1999` |
| Onion | Lettuce | Get along well | traditional | `cornell-1999` |
| Cucumber | Radish | Radish said to repel cucumber beetles | traditional | `cornell-1999` |
| Zucchini, winter squash | Radish | Radish in each hill said to prevent insects | traditional | `cornell-1999` |

## Antagonists

| Crop | Keep away from | Why | Evidence | Source |
|---|---|---|---|---|
| Tomato | Potato | Both host late blight; keep plantings apart and do not follow potatoes with tomatoes | extension | `purdue-bp80`, `cornell-1999` |
| Tomato | Cole crops | Said to dislike each other | traditional | `cornell-1999` |
| Tomato | Fennel | Said to dislike each other | traditional | `cornell-1999` |
| Bean | Onion, garlic, leek | Beans "don't like members of the onion family" | traditional | `cornell-1999` |
| Pea | Onion, garlic | Onion and garlic said to harm peas | traditional | `cornell-1999` |
| Cole crops | Strawberry | Cabbage said to harm strawberry | traditional | `cornell-1999` |
| Cucumber | Potato | Said to be antagonistic | traditional | `cornell-1999` |
| Potato | Zucchini, winter squash | Potato said to harm squash | traditional | `cornell-1999` |
| Fennel | Bean, sweet pepper | Fennel said to harm them | traditional | `cornell-1999` |
| Onion | Sage | Said to harm each other | traditional | `cornell-1999` |

## Notes

- 26 companion rows and 10 antagonist rows (more once "cole crops" is expanded to its five crops).
- Only four rows rest on a study or an extension recommendation; the rest is traditional, and the app will say so.
- Shared pests and diseases within a botanical family (for example tomato, potato and eggplant) are better handled by the rotation rule (#16, step 3) than by listing every pair.
