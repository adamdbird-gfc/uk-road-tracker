# Roadprints national mapping sources and network coverage investigation

Date: 30 October 2026. Baseline: main at `1030b726c4640e04a9c6051667886812daf39fcc`. Android source baseline: 0.25.220 (266).

## Checkpoint 1 — scope and verified starting evidence

Status: investigation in progress. This early checkpoint preserves the task before any lengthy investigation. No replacement architecture has been selected.

The merged PR #242 receipt confirms measurable unique-network length 17,435,132.494 m; assessable capacity 16,145,808.088 m; remaining unbound capacity 1,289,324.406 m; assessability 92.605021%. The independent geometric audit passed. These are synthetic reference capacity figures, not personal travelled mileage or a measurement of missing physical paths.

The investigation will inventory physical geometry, route/road identity, evidence, matching/attribution and published catalogues across roads, walking, cycling, NCN, settlements and Android reference delivery before comparing alternatives. Existing pins and bounded public samples will be preferred. Historical gap categories will not be presented as post-seam measurements without an exact reconciliation. Absence of an admitted NCN binding will not be classified as physical network absence.

Planned report sections: existing source/pipeline inventory; post-seam deficit evidence and limits; official-source comparison (OSM, OS, Walk Wheel Cycle Trust and targeted additional sources); bounded sample table; architectural options A/B/C; licensing/operational implications; practical recommendation and smallest safe experiment; outstanding evidence and decisions.

Research-only controls: no application/backend code changes, new candidate catalogue, Firebase asset changes/publication, Render deployment, APK build/distribution, matcher change, merge or large national dataset download. Installed APK and live backend/reference versions are not inferred from the source baseline.

Starting repository evidence: [merged seam receipt](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/data-trials/ncn-ordinary-seam-validation-2026-10-30.json), [validation report](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/ncn-ordinary-seam-validation-20261030.md), [development governance](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/DEVELOPMENT_GOVERNANCE.md). Current-source and sample findings will replace this provisional outline as the investigation proceeds.


## Checkpoint 2 — initial architecture inventory (research continues)

The baseline is a mixed-source system, not one national routing graph. GB A-road coverage uses OS Open Roads-derived v5 tiles (index generated 9 September 2026; 1,781 identities, plus a narrowly configured OSM fallback). Motorway v1 is an OSM/Overpass-derived 100 m anchor catalogue (generated 14 September 2026; 63 records, one explicitly skipped A601(M)). Driving and cycling use separately configured external OSRM services; their active graph snapshots are not established by a URL or profile alias. Walking first uses preloaded Geofabrik/OSM ways in PostGIS, then the foot router as fallback. The importer preserves highway/name/ref/surface/foot/access but not bicycle tags or original node IDs. B/local road discovery is journey-evidence based, not an exhaustive national canonical denominator. Settlement inventory generation counts distinct names of residential/unclassified/tertiary/living_street ways intersecting preserved official boundaries, not every physical path.

Small live metadata reads confirm Firebase's NCN manifest remains revision 1, database hash `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`; the seam correction is source-integrated but not published. The live settlement manifest has 9,072 inventories; the repository root's historical 121-inventory manifest is not the active national total. Backend health reports database ready but does not expose graph build provenance.

Bounded current OSM full-way reads for NCN9 Newry Canal Towpath (`59306268`), NCN8 rural road (`31252441`) and NCN81 towpath hairpin (`44807950`) reproduce the exact retained full geometry hashes. These three defects therefore do not establish missing physical geometry. Small bbox samples also find an existing rural road beneath a historical NCN6 no-correspondence example (OSM relation currently says NCN7), an NCN66-member shared path in the Bradford direction-gate example, and an NCN1-member cycleway at Deal in the unadmitted-known-way example. These are physical/identity comparison leads, not automatic bindings or measured completion gains. Full results, licences, exact source references and evidence limits will follow.
