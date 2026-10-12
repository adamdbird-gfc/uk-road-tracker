# Bundled UK pub map — 0.25.188 (234)

The installed collection previously had 37 pins and a 64 KiB file-reader limit.
This release packages 811 reviewed map locations: 798 existing approvals and 13
individually reconciled supplemental identities. The asset is 386,087 bytes and
loads offline from the APK. The reader retains a 2 MiB bound, caches unavailable
assets as empty, and never retries parsing an oversized asset on every screen.

Three current members remain held off the active map: Art Picture House,
Court of Requests and Bottle of Sack. Benjamin Franklin is a separate future
opening. The 814-name membership comparison includes the three current holds;
it is not the number of active map pins. Coverage/completion and visit awards
remain disabled. Silent catalogue updates and pub check-ins are later work.

## Supplemental identities and position evidence

`collections/wetherspoons-supplemental-map-reviews-v1.json` is an explicit,
dated map-approval ledger, separate from the research checkpoint. Stable
`spoons-curated-*` IDs do not depend on operator name changes or parent buildings.
Each record has operating/location evidence, county, position precision,
attribution, confirmation requirement and disabled visit/footprint approval.

Thirteen bounded live OSM map requests were completed on 12 October 2026.
The Round Coppice is node/3853400712; Bluff Inn is way/203820853; Wates House
is node/285197198. Names/localities and the Wates House conversion were checked
against the earlier individual operator/CAMRA evidence. Their point or pub
building positions are OSM-derived. Shared or old-name features are not silently
assigned as aliases: Main Bar at St Andrews, Sizzlers at Hopton, Mash & Barrel at
Devon Cliffs, Huffkin of Hoo at Allhallows and Big Smoke at Heathrow are contextual
leads, not automatically equivalent identities. The remaining entries use their
individually reviewed display points, with approximate labels and source credit.

The Belle Vue uses Terminal 2 as approximate building context, not a pub alias.
The Red Gown uses the Students' Association building. Springfield uses Adam's
dropped-pin point 53.898169, -3.019847 on its shared main complex, not park-wide
coordinates. Historic county grouping is retained. No pub footprint is approved
for automatic visits, even where a physical building polygon is available.

Parent OSM IDs cannot become pub aliases. Existing pinned OSM identities and
all prior active/excluded/held decisions remain untouched. The builder rejects
duplicate IDs/aliases, unsupported park-only positions and any visit-enabled
supplement. Individual reviews expire after 90 days at rebuild. Raw research
files do not qualify as approved supplements.

## Rebuild and checks

Use the pinned main-branch candidate file used by the regional review
(`catalogue_version` is retained in the asset) and the release ledgers:

```sh
python3 scripts/build_wetherspoons_map.py --catalogue /path/to/uk-wetherspoons-candidates-v1.json --reviews collections/wetherspoons-map-reviews-v1.json --supplemental collections/wetherspoons-supplemental-map-reviews-v1.json --as-of 2026-10-12 --output android-prototype/app/src/main/assets/collections/uk-wetherspoons-map-v1.json
python3 -m unittest discover -s scripts -p test_wetherspoons_map.py -v
```

Local validation: 13 catalogue regression tests pass; 811 unique active IDs;
exact 798/13 split; all four existing held IDs absent; all venue visit flags false;
null completion target; and 386,087-byte asset below the 2 MiB reader bound.
Android regression tests cover loading the real asset above the old 64 KiB
limit, refusing an oversized stream and retaining Springfield's coordinates
and approximate/source labels. Android test/lint/signing status is supplied by
CI for the actual release commit, not inferred from Python checks.

Pub popups now show each entry's position precision and correct source credit.
The map banner describes reviewed UK locations without a misleading completion
claim. Recording, journey matching, Timeline import and Services evidence are
unchanged. The release includes all earlier station-review fixes.
