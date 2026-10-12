# Wetherspoons location reconciliation — 12 October 2026

## Purpose and checkpoint

Continue the merged regional reviews: #103 South West, #104 South East and #105 London. All 831 pinned snapshot candidates have decisions: 798 map approvals, 29 exclusions, four holds. This pass researches the 13 operating missing-identity leads; it does not establish complete UK coverage or promote any new venue into the production ledger.

The companion file `collections/wetherspoons-location-research-v1.json` records candidate coordinates, evidence, precision, original lead keys, nearby pinned candidates and remaining checks. All coordinates below are research candidates. No exact pub footprint, live OSM pub identity, or visit boundary is asserted.

## Agreed approximate-location behaviour

Adam accepts an approximate Terminal 2 building location for The Belle Vue, provided users explicitly confirm a visit to that particular pub. Terminal presence alone must never award a visit. Apply the same separation between display location and visit evidence when reviewing other shared buildings.

A parent building or holiday-park OSM ID is context only, never a pub alias or proof of pub entry. Directory pub points are candidates, not OSM identities. The Springfield park point is insufficient to locate its pub building and remains a placement gap.

The current Android Wetherspoons collection is map-only. Existing documentation and builder leave visits disabled; this research does not implement a confirmation flow. Any future check-in implementation must enforce the agreed rule. Do not promise that current code already supports it.

## Operating missing venues

| Pub | Locality | Candidate latitude | Candidate longitude | Precision/source |
| --- | --- | ---: | ---: | --- |
| The Belle Vue | Manchester Airport, Terminal 2 | 53.366700 | -2.279470 | Parent building |
| The Red Gown | St Andrews | 56.340730 | -2.800230 | Parent building |
| The Sir Thomas Haggerston | Haggerston Castle | 55.687593 | -1.932566 | Named directory point; footprint unverified |
| The Springfield | Cala Gran, Fleetwood | 53.898140 | -3.019700 | Park only; unresolved pub placement |
| The Alfred Wainwright | Lakeland, Flookburgh | 54.157711 | -2.966190 | Named directory point; footprint unverified |
| The Marshfield Bank Farm House | Woolstanwood, Crewe | 53.095640 | -2.485280 | Named directory point; footprint unverified |
| The White Clover | Hopton, Norfolk | 52.541580 | 1.733490 | Named directory point; footprint unverified |
| The Bluff Inn | Phillack, Hayle | 50.196797 | -5.429489 | Named directory point; footprint unverified |
| The Red Rocks | Devon Cliffs, Exmouth | 50.614886 | -3.366670 | Named directory point; footprint unverified |
| The London Stone | Allhallows-on-Sea | 51.477374 | 0.644724 | Named directory point; footprint unverified |
| The Sir Ronald Wates | Guildford, Stag Hill | 51.241897 | -0.590636 | Named directory point; footprint unverified |
| The Round Coppice | Whiteley | 50.886161 | -1.247982 | Named directory point; footprint unverified |
| The Sir Frank Whittle | Heathrow Terminal 2 | 51.469173 | -0.449672 | Named directory point; footprint unverified |

Full retrieved precision and source URLs are retained in the companion research file; the table rounds only for readability.

### The Belle Vue

Terminal building only; not a pub identity. User accepts approximate terminal pin with explicit pub confirmation.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-belle-vue-manchester/
- Candidate location evidence: https://mapcarta.com/W18515536
- Parent context: way/18515536 (not pub identity).

### The Red Gown

Students’ Association building, corroborated against the named operator address. Main Bar/Club 601 elsewhere in building need identity checks.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-red-gown-st-andrews/
- Candidate location evidence: https://mapcarta.com/W26817288
- Parent context: way/26817288 (not pub identity).

### The Sir Thomas Haggerston

CAMRA directions coordinate; precise footprint not verified.

- Operating evidence: https://www.haven.com/discover/whats-new/haggerston-castle
- Candidate location evidence: https://camra.org.uk/pubs/sir-thomas-haggerston-haggerston-198812

### The Springfield

Park point only, not verified pub building. Former Mash and Barrel lead from secondary sources; requires main-complex/pub placement check before promotion.

- Operating evidence: https://news.haven.com/news/havens-cala-gran-holiday-park-opens-its-doors-to-new-j-d-wetherspoon-pub-the-springfield
- Candidate location evidence: https://mapcarta.com/W160318964
- Parent context: way/160318964 (not pub identity).

### The Alfred Wainwright

CAMRA venue point may represent leisure complex. Contractor confirms conversion of Lakeside Bay Restaurant; reconcile former identity.

- Operating evidence: https://news.haven.com/news/havens-lakeland-holiday-park-opens-its-doors-to-new-j-d-wetherspoon-pub-the-alfred-wainwright
- Candidate location evidence: https://camra.org.uk/pubs/alfred-wainwright-flookburgh-187837

### The Marshfield Bank Farm House

Former Farmhouse Beefeater. CAMRA distinguishes actual pub from separate unoccupied listed farmhouse and adjacent Premier Inn; do not use either as pub footprint.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-marshfield-bank-farm-house-crewe/
- Candidate location evidence: https://camra.org.uk/pubs/marshfield-bank-farmhouse-woolstanwood-136118

### The White Clover

CAMRA named-pub directions point. Norfolk matches operator; erroneous Blackpool phrase in Haven announcement must not change county.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-white-clover-great-yarmouth/
- Candidate location evidence: https://camra.org.uk/pubs/white-clover-hopton-holiday-village-hopton-200203

### The Bluff Inn

Existing Bluff Inn converted to Wetherspoon. CAMRA 19 versus operator 18 Riviere Towans discrepancy retained; do not overwrite address or duplicate former identity.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-bluff-inn-hayle/
- Candidate location evidence: https://camra.org.uk/pubs/bluff-inn-phillack-139081

### The Red Rocks

Named CAMRA pub point; confirm former venue identity and exact premises rather than whole park.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-red-rocks-exmouth/
- Candidate location evidence: https://camra.org.uk/pubs/red-rocks-exmouth-166375

### The London Stone

Correct Allhallows venue, not London Stone in Woking. Haven operator is consistent with franchise; park presence alone is insufficient.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-london-stone-rochester/
- Candidate location evidence: https://camra.org.uk/pubs/london-stone-allhallows-on-sea-198835

### The Sir Ronald Wates

Former Wates House, not university-wide centroid. Reconcile old name/identity.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-sir-ronald-wates-guildford/
- Candidate location evidence: https://camra.org.uk/pubs/sir-ronald-wates-guildford-151525

### The Round Coppice

Unit F4, Whiteley Shopping Centre. Distinguish pub unit from shopping centre or unrelated Round Coppice woodland.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-round-coppice-fareham/
- Candidate location evidence: https://camra.org.uk/pubs/round-coppice-whiteley-199945

### The Sir Frank Whittle

Former Big Smoke Taphouse & Kitchen. Preserve separate Crown Rivers T5 and Star Light T4; directory photo/title still old, current venue body identifies Wetherspoon.

- Operating evidence: https://www.jdwetherspoon.com/pubs/the-sir-frank-whittle-hounslow/
- Candidate location evidence: https://camra.org.uk/pubs/sir-frank-whittle-heathrow-airport-194312


## Identity and duplicate checks

All 13 keys match the existing operating missing-identity leads exactly. No pinned candidate is within 300 metres of any proposed research point. The nearest three pinned candidates are retained per venue for review. This is a proximity screen, not a guarantee: full live OSM matching and the old names of converted venues remain required before production activation.

Likely old identities to investigate include Bluff Inn, Wates House, Big Smoke Taphouse & Kitchen, Lakeside Bay Restaurant and Farmhouse Beefeater. For St Andrews, distinguish the university building, Main Bar and Club 601. Do not create a duplicate merely because a new brand/name is absent from the original Wetherspoons-only snapshot.

Direct live Overpass/OSM lookup was not available in this session. CAMRA directions links expose coordinate query parameters even though following them to Google Maps is blocked. Coordinates were read from the observed link targets, not visually estimated from screenshots. Mapcarta building/park points are explicitly identified as OSM-derived secondary evidence.

## Held entries

- **Art Picture House, Bury:** current operator page still explicitly reports temporary closure and advertised reopening 4 December 2026. Retain hold; future date does not activate.
- **Benjamin Franklin, London:** current operator new-openings page still advertises 2 March 2027. Retain future-opening hold.
- **Court of Requests, Oldbury:** current retrieved operator page now displays Open and ordinary hours, without the temporary notice. Independent actual reopening evidence was not found; retain the existing conflict/hold pending verification.
- **Bottle of Sack, Sutton Coldfield:** current retrieved operator page now displays Open and ordinary hours, while an indexed non-trailing-slash operator result still reports reopening 5 October 2026. Retain the existing conflict/hold pending verification.

Sources:
- https://www.jdwetherspoon.com/pubs/the-art-picture-house-bury/
- https://www.jdwetherspoon.com/new-openings/
- https://www.jdwetherspoon.com/pubs/the-court-of-requests-oldbury/
- https://www.jdwetherspoon.com/pubs/the-bottle-of-sack-sutton-coldfield/

## Future and proposed leads

Piccadilly Hall (3 November 2026) and Wellers Entire (8 December 2026) remain future openings on the current operator new-openings page. William Chambers remains future: its individual page says 3 November 2026 while the new-openings page says Tuesday 26 January (2027 inferred from the next matching January date). Preserve the date conflict and do not activate either from a schedule.

Oast House, Derby remains a reported proposal without established current operating Wetherspoon status. The indexed CAMRA record still identifies Table Table/Whitbread. The prior scrapped Presthaven/Boathouse outcome is retained, not claimed as freshly reverified. Barcelona Sant Jordi is overseas and outside the agreed UK collection.

- https://www.jdwetherspoon.com/new-openings/
- https://www.jdwetherspoon.com/pubs/the-william-chambers-edinburgh/
- https://www.homepages.ucl.ac.uk/~ccaajpa/pubs-spoons.html
- https://camra.org.uk/index.php/pubs/oast-house-derby-143567
- https://www.dailypost.co.uk/news/north-wales-news/holiday-park-scraps-wetherspoons-plan-32958262

## Remaining work and validation

Research now covers all 13 leads. Twelve have named-pub or parent-building coordinate candidates; Springfield has only a park-level fallback and still needs pub/main-complex placement. All thirteen still need production identity/source reconciliation. The two conflicting reopening cases remain outstanding.

Validated: 13 unique lead keys, exact match to the 13 operating leads, UK coordinate ranges, original 831-source count, nearest-candidate proximity screen, no pub alias derived from a parent ID, and all map/visit promotion flags disabled. No app files, version, builder, existing review decisions or packaged assets are changed by this checkpoint. Documentation/research data do not need an Android build.

Delivery remains: reconcile UK catalogue, bundle approved map catalogue in app release (including the existing loader-size requirement), then implement silent validated updates. Visit support is separate and must enforce confirmation for ambiguous shared premises.
