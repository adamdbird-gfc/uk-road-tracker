# Stage 10 — OSM acceptance, original artwork and cipher

6 November 2026. Scope: source implementation and validated PR; Stage 11 owns final integration, device acceptance and APK. No backend/Firebase publication, APK packaging or tester distribution in Stage 10.

## Baseline and reconciliation

Starting main `bdad634c654ece0446f08bb90c4b792057486341`. PR #296 exact reported head `7b1f2bbe8df79bc83e7a034befe0ab3f29ce776c` was open, non-draft, mergeable, with no review submissions or outstanding inline threads. Exact-head workflows 42247510886 and 42247510715 succeeded; three unrelated branch-conditional jobs skipped. Merged with explicit owner authorisation and `[skip render]` at `14d3042d25af7cc91dac9ea62d83015c8ed99358`. Stage 10 branches directly from that merge; Stage 9 components and Stage 8 acceptance harness are preserved.

Remote branch `feat/milepost-stage10-osm-artwork-20261106`; PR #297 targets main. Local initial checkpoint d58e8724 and remote initial checkpoint c075dc17 have the identical tree `ff991b9b7e621dbf085cfeaa4a2bb9ef96957bb9`; GitHub connector persisted the latter because shell git had no write credentials. Subsequent checkpoints use the remote history. No history rewriting or branch deletion.

## Owner-approved OSM acceptance policy

`osm-source-acceptance-1` supersedes the independent-survey blanket prerequisites in Stage 9 and the original specification. `verified` in runtime identity/location/designer fields now means accepted under this published source policy, not independently surveyed by Roadprints. OSM is the intended geography source. No photograph, per-object edit version, independent coordinate precision survey or verified installation date is required in the absence of a specific conflicting fact.

Reproduction: `python3 scripts/milepost_approval.py --write`; validation: run without `--write`. Revision 1 and revision 2 remain immutable historical receipts. The input inventory is checksum-validated against its revision 1 manifest before decisions. Exactly one machine-readable revision 3 decision is retained per input. Full unchanged original OSM tags accompany every approved runtime record.

Checks applied to every row:

- Positive typed OSM node identity, uniqueness and explicit `ncn_milepost` classification. Unsupported design values may approve the location but leave attribution unknown. Non-post and removed classifications reject. Future way/relation inputs require a point-resolution policy; no implicit polygon centres.
- Finite WGS84 coordinates and a UK anomaly envelope, in addition to the pinned source's **GB administrative-area query**. The envelope alone never establishes UK scope; the source query and original object supply scope for this edition. Conflicting country tags hold. Query provenance is retained; no Roadprints border survey claimed.
- Duplicate identity, coincident-node and same-design/same-reference nodes within 100m hold conflicting rows. The proximity test is a review trigger alongside matching identity metadata, never a merge rule. Nearby distinct nodes with distinct references are not merged. MP reference is non-unique metadata, never the canonical identity. Repeated MP898 (12.47km apart), MP658 (63.14km) and MP881 (96.16km) do not imply duplicates; references remain unchanged. The four different-design posts less than six metres apart remain distinct.
- Explicit unresolved location notes hold. Missing direction arms, an incorrect mileage/route number or absence of a photograph do not make a physical post invalid.
- Stable project physical ID initially anchored to the typed source node; source aliases remain separate in the existing immutable alias registry. Future replacement nodes, moves and duplicates require an explicit mapping/tombstone update, not a new award inferred by radius.
- The explicit `ncn_milepost=mills/rowe/mccoll/dudgeon` value supplies designer evidence under OSM's documented tagging convention. Conflicting named artists leave attribution unknown, preserving geographical discovery. No attribution derives from region, MP number, paint or proximity. Known spelling aliases John Mills/Jon Mills and Rowe/Andrew Rowe do not conflict with their explicit design tags.

### Time policy and retrospective matching

A source snapshot is not an installation or survey date. `source_assumed_stable` applies the accepted source position to eligible journey history from the Millennium programme's 2000 policy floor, unless an explicit source movement anomaly is present. This is a declared **product assumption**, not a factual claim that each post occupied that position throughout those years. It permits retrospective recognition without fabricating survey dates. A future evidenced move/removal must replace the policy bounds and invalidate/reconcile affected proofs through the existing ledger.

For six approved records whose notes explicitly report movement, corrected source-node location or late appearance, `snapshot_forward_known_movement` limits matching to journeys on/after the source snapshot. Their present location is approved; earlier journeys remain pending catalogue evidence rather than being credited at the new position. `valid_from`/`valid_until` are evaluator policy bounds, not asserted observations. The year-9999 upper bound encodes open-ended source acceptance, not a forecast of physical presence. Journey timestamps and movement validation remain mandatory. Source uncertainty never enlarges 100 metres.

### Revision 3 receipt

| Field | Value |
| --- | --- |
| Input | 889 |
| Approved / held / rejected | 887 / 2 / 0 |
| Approved attributed / unknown | 887 / 0 |
| Approved designs | Mills 445; Rowe 265; McColl 134; Dudgeon 43 |
| Payload bytes | 1,167,726 |
| Payload SHA-256 | `1f7468a5ed054c5822c88614650fe33814554502a57779fc1b7375749d35c2ce` |
| Marker fingerprint | `022118567034f1c648c7e158b0f44cb3969b3327618d739fad19a47b26ebc535` |
| Source snapshot | 15 October 2026, 17:21:06 UTC |
| Raw source SHA-256 | `4c0cd419196408f1abad27a61bfc84e25bd74e4a2ae9e2b835dcd0688fb39fc6` |
| Source | Overpass GB area query, retained revision 1 manifest and raw-source archive receipt |

Holds: `osm:node:416710572` reports unclear current status/movement for library construction; `osm:node:9825206150` explicitly says exact location uncertain. Both have `explicit_unresolved_location` reasons. Earlier Stage 9 photograph-coordinate differences of 10–30m are not contradictory survey evidence under this policy and do not hold the four reviewed records.

ODbL-1.0 applies to the derived catalogue and decision inventory. The full derived data is committed in `reference-data/millennium-mileposts/revision-3/`, including source query/hash and attribution; the same payload is bundled. The normal screen visibly credits OpenStreetMap contributors and links to copyright/licensing. No official ArcGIS coordinates, photographs, sculpture reliefs or third-party graphic files are included. Source policy links: [OSM tag convention](https://wiki.openstreetmap.org/wiki/Key:ncn_milepost), [OSM attribution](https://www.openstreetmap.org/copyright).

## Activation and architecture

`MilepostCatalogue` pins revision, bytes hash, approved count and semantic marker fingerprint. Production readiness derives from 887 approved records. Source activation is **implemented on this branch**, not installed or released to users. Loader-to-ledger uses the existing install, alias, correction/deletion and reconciliation transactions; existing 100m geodesic, eligible native evidence, no drift/vehicle/gap rules remain unchanged. Unsupported historical Timeline evidence remains pending (#283); no source geometry is converted into journey evidence. Reinstallation of the same verified catalogue avoids redundant parse work on normal processing runs.

`MilepostIntegration.allowed` restricts the normal production path to the production application ID. The `.milepostacceptance` application cannot schedule/read/process the production ledger even with the now-enabled source gate. Synthetic fixtures still operate exclusively through their isolated constructor, package and databases. No preference, Intent or debug setting can change the source pins. Idle/charging scheduler and capture-priority guards remain; discoveries become visible after a successful stable assessment, not merely after opening the screen. Fresh existing NCN state remains necessary for the atomic combined achievement receipt, including Grimshaw.

## Artwork and animation

23 original vector masters plus Android VectorDrawable exports, generated by `scripts/milepost_artwork.py`, version/hash manifest and rendered review sheet in `artwork-v2/`:

- Six gold contribution medallions: tree/fossil, nautical anchor, cockerel, cycling tracks, path bridge and exploration compass. Correct name/biography only after earning.
- Six-part collective time emblem.
- Four simplified post forms plus neutral unknown: branching tree, directional fingerpost, cockerel head and Tracks column. References are the commissioner's [documented characteristics and map key](https://www.walkwheelcycletrust.org.uk/national-cycle-network/millennium-mileposts/), consulted 6 November. Geometry is original; no traced reference graphic or protected relief reproduced. Post icons appear in bounded discovery details with design labels.
- Seven central thematic emblems: four-season quadrants, recognisable world landmasses, inclusive life stages, twelve celestial divisions, twenty time divisions/hourglass, enigmatic geometric lock, final Harrow compass.
- Inner/outer cipher rings, card layer and anonymous question-mark veil. Charcoal cards, navy background, gold strokes and restrained purple ring accent. No app logo/navigation/typeface redesign.

Locked cards have the same anonymous question mark, no portrait, silhouette, name or identifying accessibility label, including after the collection is introduced. No historical photographic likeness is used. The final emblem commemorates Harrow's exploration/puzzle contribution.

Native 1.1-second coordinated transition: next card enters; earlier offset edges remain; two rings rotate in opposite directions; central emblem fades in; unresolved native-text glyphs scramble; the existing non-sequential positions settle; then cursor/completed stage persist. Cipher letters are native TextViews around the visual ring with the fixed word gap, accompanied by the readable native line. Previously resolved glyphs remain unchanged. No glyph accessibility chatter; one completed-stage announcement. Final identity cannot settle before stage seven. No continuous animation.

Next is explicit within the existing optional consolidated-summary/replay experience. No mandatory historic discovery queue. Every new stage uses the same transition and may be replayed. Interruption, capture start, loss of freshness, Later, Skip, replay reset and lifecycle detachment cancel immediately without persisting a partial stage. Restart restores the last completed static cursor. System-disabled animators, touch exploration or the persisted static preference use the identical settled state without motion. Animation writes only presentation state.

No bitmap assets are decoded by the production feature. Current theme, small vector drawables and native text only; no retained per-frame snapshots. Artwork references and review sheet are engineering visual checks, not physical-device accessibility/frame-time acceptance.

## Validation and Stage 11 handover

In progress; exact final-head CI results and measured memory probes must be appended before marking PR ready. Initial compile failure was in a test's direct call to the hidden Android duration-scale setter; corrected to Robolectric reflection, with production APIs unchanged.

Actual installed APK version/source, live backend commit and device-active NCN pins remain unknown; no connected device. Previously reported Android 0.25.223 (269)/NCN revision 4 are historical context, not fresh installation evidence. No real private archive or physical GPS pass was supplied. Real-coordinate tests use production OSM locations with synthetic **test-only journeys**, not claimed real visits.

Stage 11: merge this exact validated head with deployment suppressed when authorised; verify installed/catalogue provenance; run isolated and normal-path device acceptance on Pixel and Samsung; measure ART/PSS, 256MiB heap, frame timing, capture continuity, TalkBack, 200% text, static/system motion and process-death restoration; demonstrate a genuine eligible archived or captured visit and correction/deletion; confirm all six plus collective attainability; build one private installable APK with established signing/pins only after final acceptance. No Stage 10 APK is authorised. Keep #283 historical recovery and #286 deferred work explicit; #294 retains device gaps.
