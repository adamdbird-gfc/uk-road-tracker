# Stage 9 — approval boundary and production catalogue integration

6 November 2026. **Implemented safety path; production activation BLOCKED. Stage 9 definition of done NOT met.**

## Baseline and preserved work

Starting main: `d3d4188ae66549b046d93ef76c2be9a740a41a03`.
Stage 8 #295 exact head `154914366a4e1f658b5127db52ec899f38a5edc8` was open, cleanly mergeable, no requested reviews or review comments. Final-head workflow 42218257850 succeeded (311 JVM tests, 27 catalogue tests per retained report); three unrelated workflows skipped by branch conditions. Reviewed its 13-file scope and source isolation. Merged with owner authorisation and `[skip render]` at `bdad634c654ece0446f08bb90c4b792057486341`. No Stage 8 components recreated. Physical acceptance remains unverified.

Stage 9 branch: `feat/milepost-live-integration-20261106`, directly from that merge. Existing Stage 1–8 specification, ledger, evaluator, reconciler, deriver, presentation and harness remain authoritative. No archive migration, entitlement duplication or alternative UI.

## Approval checkpoint (not an approved live catalogue)

Revision 2 machine receipt: `reference-data/millennium-mileposts/revision-2/manifest.json`.

- Approved 0; held 889; rejected 0. Holds are missing evidence, not assertions of invalid physical posts.
- Exactly one decision per original candidate in `decisions.jsonl`; four targeted source reviews, 885 retained inventory evidence gaps. This is not 889 individually completed investigations.
- Runtime payload: 365 bytes, SHA-256 `3ef1c32d74c12f8d24c9892fea3c4e960d09f601a4f9cf71a390c7db3572a28e`.
- Decision SHA-256 `f1f36924b3378e1305640372607b48acd8cb84c0089b3b5a0aeeed24e63e022d`.
- `python3 scripts/milepost_approval.py` reproduces and validates the empty approval payload and all decisions. The 889 candidate coordinates/tags remain unchanged outside Android assets.

Existing OSM snapshot `4c0cd419…` is ODbL-1.0, retains typed IDs but no per-object versions or independently established physical/date intervals. MP references alone are not unique identities. Never generate canonical physical IDs by radius or MP number. Future approvals use `roadprints:millennium:*` physical IDs, separate OSM aliases and explicit dated positions. Cross-row duplicate IDs/aliases reject the whole installation; ledger preserves its existing immutable alias/tombstone and rollback safeguards.

## Targeted source evidence and unresolved questions

Fresh primary-source pages checked 6 November; factual findings below are review notes, no photographs/text/official inventory copied into the runtime database.

| Candidate | Evidence | Finding / remaining blocker |
| --- | --- | --- |
| `osm:node:33130857` / MP241 | [Geograph 3645557](https://www.geograph.org.uk/photo/3645557), Malc McDonald; prior repository subject-coordinate comparison | Photographer identifies an NCN1 Millennium Milepost. Historical 10m subject grid and approximately 15.9m candidate separation do not resolve the exact physical position or its valid interval. Hold. |
| `osm:node:35181782` / MP444 | [Geograph 2863732](https://www.geograph.org.uk/photo/2863732), Marathon, 24 March 2012 | Photographer explicitly identifies the Cockerel/McColl design and Alkerden Lane location. Subject grid TQ59447413 is 10m precision; approximately 29.5m candidate separation remains unresolved. Do not choose between camera/subject/OSM positions or average them. Hold. |
| `osm:node:33190372` / MP385 | [Geograph 5455848](https://www.geograph.org.uk/photo/5455848), Oast House Archive; recovered repository assessment | Fresh retrieval failed. Prior 100m subject precision and roughly 10m candidate separation are leads, not resolved unlock coordinates. No dated position interval established. Hold. |
| `osm:node:3826485563` / MP995 | [Geograph 7131219](https://www.geograph.org.uk/photo/7131219), Karl Peet, 21 September 2019; OSM `source=survey` | First-person image record identifies MP995 and Mills, subject WGS84 57.19015,-3.82789 at 10m grid precision, near the OSM survey point. Strong identity/design lead; no precise survey receipt or supported validity interval recovered. Do not extrapolate a single 2019 observation indefinitely. Hold. |

[Geograph 7131112](https://www.geograph.org.uk/photo/7131112) retrieval also failed. [Commissioner design descriptions](https://www.walkwheelcycletrust.org.uk/national-cycle-network/millennium-mileposts/) corroborate the four design identities, not every candidate's position. [OSM survey discussion](https://wiki.openstreetmap.org/wiki/Talk:National_Cycle_Network_Millennium_Mileposts) describes survey corrections and mistaken official type/location records; repeated community listings are not independent surveys. The [Geograph article](https://www.geograph.org.uk/article/National-Cycle-Network/15) provides coarse design/location leads, not precise replacement points.

OSM data reuse: [copyright/licence](https://www.openstreetmap.org/copyright), ODbL-1.0; preserve attribution and share the derived database on those terms. Geograph observations/photos have separate credited CC BY-SA terms; links here preserve attribution, no images or derivative coordinate inventory are shipped. Do not conflate those terms with original artwork rights. The separately restricted official ArcGIS inventory remains unused; no permission grant recovered. Missing official permission is not asserted to prevent independently surveyed OSM approval.

**Smallest corrective action:** obtain a traceable survey/corroboration receipt for one physical post establishing exact accepted WGS84 position, precision/conflict resolution, UK location, classification, observation date and supported position-validity interval; verify any designer association separately; record source rights. Review that receipt, add its approved record, update pinned hash/fingerprint/count and run these same checks. No new architecture or policy session is required. For all awards to be attainable, expand to at least seven distinct posts including all four verified designs. Historical eligibility must stay within supported dated positions. Do not fabricate dates, infer a boundary from a search-area query or loosen the 100m rule to pass a candidate.

## Integration delivered

`MilepostCatalogue` is the sole production asset reader: bounded input, fixed checksum/revision/count/evaluator, approved-only schema, required physical/provenance/coordinate/validity fields, duplicate identity/alias rejection and immutable marker list. It installs through the existing ledger only after full validation, on the background worker after the capture guard. Validation failure performs no install; existing ledger evidence is retained. Catalogue updates use existing revision, fingerprint, alias registry, retirement, invalidation and rescan transactions.

`MilepostIntegration.PRODUCTION` now derives readiness from explicit source-pinned approval count, replacing an unexplained false constant. Count remains zero. No preference, imported state, Intent, debug flag or acceptance fixture can change it. Read-side gate pins include evaluator and marker fingerprint; worker installs the checked asset before reconciliation. Acceptance instances retain their independent constructor and databases. Candidate inventory is never packaged.

The normal AchievementsActivity section, idle/charging scheduler, 2s cooperative processing, capture-start latch, archive/evidence fences, persistent discovery proofs, deletion/correction fallback, seven stable achievement IDs and deterministic Harrow letter logic are preserved. New loader-to-ledger test exercises the existing archive/deriver, persistence/reopen, repeat ordinal and deletion path using test-only records. This is synthetic integration evidence, **not a genuine production unlock**. No production record, real journey or device was approved/tested here. No claims of activated six awards/parent or real GPS success.

## Validation and delivery status

Local: 31 Python tests passed (27 catalogue, 3 approval, 1 source isolation), zero failures/errors/skips. Android SDK/Gradle/adb are unavailable locally; Android compilation, unit/lint, acceptance and memory checks run through the existing CI workflow, with no APK packaging. See PR for exact final-head result and metrics.

No APK/version bump/distribution, backend/Firebase publication, production-data edit or device acceptance. Actual installed APK/source, live backend SHA and device-active NCN revision are unknown. Published NCN revision 4 is prior recorded evidence, not a fresh device verification. Main ends at the Stage 8 merge unless independently advanced; Stage 9 remains unmerged for review. Issues #283/#286 remain deferred; #294 retains its physical-acceptance gaps.

## Stage 10 handover

Production single-line artwork belongs at `MilepostPresentationView.PlaceholderArt`, using `MilepostPresentation` stage/cipher results and `MilepostPresentationStore` saved cursor/artwork version. Keep six removable thematic layers, central icon changes, deterministic non-sequential letters, opaque identity veil until stage 7, static reduced-motion stages and capture/lifecycle cancellation. Never embed hidden identity in locked labels/descriptions or accessibility output. Existing spoiler tests remain mandatory. No final portraits, medallions or animation assets implemented here. Resolve approval records before claiming Stage 9 production delivery; artwork work can use the preserved isolated harness in the meantime.

Resume from the latest published Stage 9 commit/PR; do not repeat Stage 8 reconciliation or overwrite the 889 candidates. Outstanding blocker is catalogue evidence, followed by Stage 11 real-device ART/PSS, capture and accessibility acceptance.
