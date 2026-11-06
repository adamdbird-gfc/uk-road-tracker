# Millennium Mileposts: authoritative feature specification

Date: 5 November 2026. Priority P2. Status: **specified; implementation gated by evidence and separately authorised development**.
Source baseline: `24278f5256bf367039b9a3adf986d619754ccb15` (main, merged PR #276).
This document is the single product/behaviour authority for this feature. Companion files supply verification, tests, artwork and delivery detail:

- [Historical and data-source register](millennium-mileposts/verification-register.md)
- [Acceptance-test matrix](millennium-mileposts/acceptance-tests.md)
- [Artwork and animation brief](millennium-mileposts/artwork-brief.md)
- [Phased implementation plan](millennium-mileposts/implementation-plan.md)
- [Read-only evidence receipt](millennium-mileposts/readiness-receipt.json)

## Stage 10 owner-policy amendment — 6 November 2026

The Stage 10 brief supersedes the earlier blanket physical-survey, dated-position and portrait/silhouette prerequisites. [Stage 10 source acceptance and production contract](millennium-mileposts/stage-10-production.md) is authoritative for OSM approval, stable identity mapping, explicit retrospective source assumptions and record-specific movement restrictions. A valid explicit OSM design tag is acceptable attribution evidence; unknown/conflicting designs may count for Harrow but never a designer. The fixed six identities, earning conditions, 100m/evidence safeguards and cipher letter positions below remain unchanged.

Locked cards remain fully anonymous question marks at every point: **no silhouette or other identity clue**. Unlocked art is an original symbolic commemorative emblem. Stage 1–7 centre emblems change with each discovery; the existing optional reveal uses native short, interruptible card/ring/glyph animation with a static equivalent. No copied likeness, poetry or source sculpture detail is required. Earlier contradictory artwork/evidence wording below is historical design context and must not restore the superseded blanket hold or locked silhouettes.

## Scope and precedence

This session specifies only. No production feature, reference publication, entitlement changes, APK, deployment or merge is authorised. The owner's 5 November brief supersedes the six-way geographical assignment in historical PR #238, including its all-geographies distribution and one-person-per-post model. Preserve historical records; never import those allocations into runtime. Actual design determines the four designer awards. Any seven eligible distinct UK posts determine Harrow. A post may contribute to its designer and Harrow together.

Preserved decisions: 100m passing distance; walking/running/cycling only; sufficient historical journeys count; no duplicate or drift awards; six named individuals and one collective reward; mystery presentation; no poetry or stanzas. Disc identity/series is not an unlock prerequisite. No new sub-achievement names or regional puzzles. Seven across different regions qualifies exactly like seven in one place.

## Verified source architecture

Paths below are under `android-prototype/app/src/main/java/com/roadprints/capture/`:

| Existing component | Observed behaviour | Integration consequence |
| --- | --- | --- |
| `AchievementStore` | Streams `JourneyStore.forEachAchievementEvidence`; general values, contributions and SharedPreferences unlock/level history; publishes only against unchanged journey revision | Add a separately committed discovery snapshot, not another retained archive-wide geometry tree |
| `NcnProgressStore` | Single worker, per-file evidence tokens, cancellation and atomic checkpoint; validates activation epoch and archive revision before publishing | Reuse this lifecycle pattern for incremental work; milepost and NCN assessments remain independent |
| `NcnMatcher`, `NcnLedger` | Conservative source-identity route evidence and combined interval union | John Grimshaw consumes existing complete route result; post proximity never creates NCN coverage |
| `NcnAchievements.prepare/percent` | Measurable positive-length routes; combined coverage; 25/50/75/100 with arithmetic-noise tolerance only | Read true route level 4, never rounded display 100%; no denominator change |
| `AchievementCelebrations` | Separate durable `seen_levels`, acknowledgement cannot award a badge | Extend presentation state separately; do not bind entitlement to replay completion |
| `AchievementsActivity` | Background calculation, revision-tagged results, sequential full-screen celebration queue, animator-enabled check | Add summary/resume/skip and explicit active-capture guards; lifecycle guards alone are insufficient |
| `CollectionMapClusters`, `RoutePreviewView` | Shared screen-grid clustering/hit testing, generic circular venue pins; visited generic pins currently gold | Adapt renderer for milepost outlines/orange/purple; do not change other collection colours |
| `CaptureQualityValidator`, `WalkingCaptureEvidence`, `TravelAchievementRoutes` | Reliability/gap checks; pedestrian stillness preparation; corrected matched parts | Helpers are not a ready-made qualifying post evaluator: `TravelAchievementRoutes.eligible` also accepts vehicles; `displaySections` is display-only |
| `api.py`, `cycling_path_evidence.py` | Existing matching/evidence service, distinct from local achievement evaluation | Preserve evidence contracts; no new server-authoritative achievement system is needed |

Memory evidence: [#256 investigation](android-stability/achievement-memory-20261104.md), integrated through PR #257, identified retained parsed full-road coordinate trees plus serialized identity copies in core/town evidence. Correction removed redundant graphs; a 256MiB synthetic probe retained about 47MB versus 167MB, not full ART/UI heap. `AchievementArchiveVolumeTest` covers 1,605 journeys / 802,500 vertices and three calculations. No universal memory bound or physical-device acceptance is established. Preserve this correction; do not funnel point recognition through sampled landmark route grids or duplicate full-road JSON retention.

Baseline Android source is 0.25.223 (269), not a fresh installed-device check. Live backend SHA and connected device version are unknown. Live manifest read this session confirms NCN revision 4, database SHA `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f`. Bundled fallback remains revision 1 (`973ad286…`). #276's new corridor candidate is integrated source, not a published replacement. No large NCN database was downloaded for this task.

## Achievement contract

Stable IDs below are proposed new internal IDs; implementation must check for collisions before reserving them. Display names are fixed product decisions.

| ID | Display name | Exact earning condition |
| --- | --- | --- |
| `millennium-jon-mills` | Jon Mills | At least one eligible discovered post with verified `designer=mills` |
| `millennium-andrew-rowe` | Andrew Rowe | At least one eligible discovered post with verified `designer=rowe` |
| `millennium-iain-mccoll` | Iain McColl | At least one eligible discovered post with verified `designer=mccoll` |
| `millennium-david-dudgeon` | David Dudgeon | At least one eligible discovered post with verified `designer=dudgeon` |
| `millennium-john-grimshaw` | John Grimshaw | At least one eligible measurable `NCN:` route at true 100% under established combined calculation |
| `millennium-charlie-harrow` | Charlie Harrow | Seven distinct eligible physical mileposts anywhere in the UK |
| `millennium-in-honour-of-time` | In Honour of Time | All six individual achievements earned |

Do not make Grimshaw conditional on mileposts, all routes, regional routes or network completion. Consume a stable NCN snapshot and record route key, catalogue and recognition date. A 99.96% route displayed as 100% does not qualify. Existing held/unmeasurable/excluded route handling and floating-point tolerance remain authoritative.

An eligible post of unknown/unverified design may count toward Harrow if its identity, location, UK scope and physical eligibility are verified; it cannot award a designer until attribution is verified. Unverified physical candidates award nothing. A verified design is attribution, not a guess from latitude, paint colour, MP number, disc or nearest neighbour.

## Discovery evidence contract

One discovery means an eligible canonical physical post within **distance <=100.0m** of a supported, moving, validated walking/running/cycling journey section. Use local metric/geodesic point-to-finite-segment distance, with independent tests at UK latitude extremes. No endpoint extrapolation, whole-road footprint, route bounding box, cell proximity or line bridging across disconnected parts. Proximity needs no dwell, disc scan or manual check-in and need not lie on a currently completable NCN segment. Do not add an NCN-route-membership gate to milepost discovery.

Eligibility requires completed supported journey evidence; validated effective transport mode for the actual section; retained travel geometry supported by sufficient original observations/provenance; corrections applied; and no stationary/drift, unreliable fix, unresolved gap or vehicle section underpinning that proximity. A manual mode label alone cannot turn vehicle evidence into cycling. Full-road geometry and user-drawn routes are not proof of passage. A failed or unresolved recording does not qualify merely because a router draws a plausible line.

Native recordings: apply the existing timestamp/accuracy/plausibility/gap rules plus actual movement/stillness evidence, and retain the accepted source-section range. Current `CaptureQualityValidator` limits reliable fixes to 50m accuracy on foot, 100m otherwise; reject unsupported gaps rather than interpolate. Its generic non-foot speed bound is not proof of cycling. The adapter must verify cycling provenance and actual movement. Do not treat a `displaySections` result as award validation.

Historical/imported journeys: reuse validated matching and retained original evidence if it proves the local section and mode. Missing live-fix timestamps do not categorically disqualify an otherwise independently validated historical section. A coarse Google Timeline activity interval, inferred straight connector, place visit or uncorroborated simplified line is insufficient. Return `insufficient_evidence` with a reason; keep history intact and retry on evidence upgrade. The first evaluator PR must pin real supported archive schemas and synthetic positive/negative equivalents before enabling any awards. No new matcher requests or archive rewriting is necessary for initial assessment.

Distance remains 100m; accuracy must never enlarge it. Unresolved coordinate precision or source conflicts with potential to change the result block that record pending verification. Retain measured distance/method and quality decision. Do not call a verified pass a GPS-drift award, and do not silently treat all `processing_status=complete` records as drift-safe.

## Persistence, corrections and ordering

Proposed Android-owned SQLite store (additive, feature-specific, no production schema change in this session):

| Entity | Required content/invariants |
| --- | --- |
| `catalogue_install` | schema/revision, content SHA, evaluator compatibility, verification state, active and previous known-good revision |
| `journey_assessment` | journey ID, immutable evidence token/hash, evaluator version, catalogue hash, result/reasons and checkpoint; key changes invalidate only affected work |
| `discovery_evidence` | physical marker ID, journey ID/token, section index/range, event time and precision, recognition time, accepted mode, nearest passage coordinate, post coordinate, distance, catalogue and policy version; unique proof key |
| `discoveries` | unique physical marker ID, selected earliest valid proof, first-recognition time, current qualifying state; aliases collapse before counting |
| `achievement_state` | seven stable IDs, current earned state, first-earned recognition and event date precision, proof/reference revisions; parent derives from the six individuals |
| `presentation_state` | summary batch acknowledgement, revealed stage, replay stage/cursor, dismissed/paused state and artwork version; cannot change achievement/discovery rows |

Retain all sufficient proofs (compact references, not route coordinate trees) so removing one contributing journey can fall back to another. Updates/deletions/corrections re-evaluate affected proofs, then derive counts/awards atomically. Preserve prior recognition history and annotate unsupported current qualification; do not silently retain a currently false count or erase historical records. This follows the existing distinction between current progress and prior milestones. Catalogue removals carry tombstones/aliases and reasons; never reuse retired physical IDs.

For a stable evidence revision, choose earliest qualifying event time, then journey ID, section index, physical marker ID as deterministic tie-breakers. Record unknown event time explicitly; use journey start only with `date_precision=journey`, never fabricate an exact passing time. Persist the discovery ordinal assigned at first recognition to keep presentation stable if earlier history is imported later. The earliest-proof metadata may update; the shown letter state must not reshuffle. A journey passing several posts sorts by valid passing time then the same tie-breakers.

One transaction publishes proofs, discovery counts, six awards and parent after checking journey/catalogue/evaluator revision tokens. If inputs change, discard staged publication and retry; incomplete scans show last complete result with an updating indication. Crash after commit but before presentation cannot duplicate awards. Replay writes only presentation state. No changes to existing collection access/payments or achievement entitlement gates are implied.

## Harrow state machine

The skins are presentation stages, not achievements. `n=min(7, distinct_eligible_discoveries)`; regions, route IDs and disc types have no role.

| n | Visible Harrow artwork stage | Newly resolved letter positions (zero-based, spaces excluded) |
| --- | --- | --- |
| 0 | Hidden `?`; no cipher or thematic hints | none |
| 1 | Seasons | 0, 7 |
| 2 | Continents | 1, 8 |
| 3 | Ages of Man | 2, 9 |
| 4 | Zodiac | 3, 10 |
| 5 | Centuries | 4, 11 |
| 6 | Final Mystery | 5, 12 |
| 7 | All thematic covering removed; silhouette, Charlie Harrow, biography | 6 |

Target string is `CHARLIE HARROW`, with 13 letter positions and a fixed space after position 6. Let `R(n)` be the union of all positions up to n. Resolved positions always show the target. For unresolved positions render a versioned deterministic substitution, e.g. each target letter advanced 7 places modulo 26; no randomness, device/time seed or per-frame speech. Intermediate animation may move only unresolved glyphs; final state must equal the same substitution at every restart. This is an artistic cipher, not a reconstruction of the original Time Trail solution. The final letter completes the exact target; duplicate visits never advance it.

Composition: six independently removable themed transparent layers plus a separate silhouette. For stages 1–6 use the prefix of the six layers at fixed z-order; each layer contributes a separate visible ring/panel, with a central opaque identity veil so no portrait leaks early. On stage 7 remove all six layers and veil, exposing the silhouette. Layers must work alone, in every prefix and in final removal; artwork may not assume previous animation frames. Cache only the current composition. See the production brief for masks, naming and transitions.

## Mystery and bulk-history presentation

Initially the six cards show `?`, no names, biographies, conditions or accessibility spoilers. After the first individual award, remaining individuals become anonymous silhouettes; In Honour of Time appears with six-part progress. Harrow may have partial artistic progress after discoveries even before his individual award: show the partial themed card and cipher after the experience has been introduced, but keep his name/biography hidden until seven. If only an unknown-design post is found before any individual award, retain generic hidden cards and offer a spoiler-safe discovery count/summary; partial stages are available later through replay. Grimshaw can be the first individual award and introduce the collection.

Bulk processing earns every legitimate award immediately on stable evaluation, including all six and the parent in one import. Present **one non-modal consolidated summary** with new unique posts, individual awards, parent if earned, date range/precision and any evidence still assessing. No forced card-by-card queue across imported collections. Provide `View discoveries`, `Replay reveal` (optional), `Later` and `Skip celebrations`. Skipping acknowledges only that summary batch; future discoveries remain eligible for summaries. Existing general celebration pending state must not resurrect the same bulk batch.

Replay can run 0→current stage, or resume from saved completed stage, and does not award anything. At >=7 it can replay the full reveal at any later time. Later suspends without losing cursor; skip exits to the final earned state and acknowledges the current batch. Back/rotation/process restart saves the last completed stage; a partially animated transition resumes from its preceding static state. New history arriving during replay updates the final target without rewinding resolved letters or starting a second modal flow.

Never start or continue celebrations while active journey recording is detected. Check before opening, before each transition and on capture start/lifecycle resume; pause/dismiss immediately and save cursor. Evaluation runs at lower priority or yields to capture; a summary badge may wait until recording ends. No sounds/haptics required to understand the result. Reduced-motion/animators-disabled mode swaps static stages with explicit Next/Skip/Replay; TalkBack announces one completed stage and progress, not scrambled glyphs. Touch, screen reader and keyboard users can inspect/skip without timing tasks.

## Inventory and map contract

Version mileposts independently from the large NCN route database. Immutable manifest: `schema_version`, increasing `revision`, generated/source dates, SHA-256, byte size, supported evaluator versions, attribution/licences and eligible/candidate counts. Validate before atomic activation; retain known-good revision. Unknown schema/corrupt hash fails closed without erasing discoveries. NCN version is recorded only for Grimshaw evidence.

Each record requires stable `physical_marker_id`; typed OSM aliases (`osm:node:<id>`, potentially way/relation aliases); OSM snapshot/version where available; MP reference as non-unique text; WGS84 surveyed/corroborated point; coordinate source, precision and observation date; presence/validity interval; design enum `mills/rowe/mccoll/dudgeon/unknown`; separate designer/location/identity verification states and source references; UK boundary decision; eligibility/reason; review date; provenance/licence and tombstone/replacement links. Keep source object identity, physical identity and disc identity separate. Never derive identity from rounded coordinates or MP reference alone.

Recovered 889 rows are all unique OSM nodes, not verified distinct physical posts. Automated reconciliation can nominate likely duplicates but cannot merge solely by radius. Two nearby real posts count twice; two OSM aliases for one real post count once. Unknown designer does not mean unknown physical identity. Moved posts retain identity with dated positions; qualify old journeys only against supported historical location/availability. An undated current observation cannot establish when a missing/moved post was present; hold uncertain historical matches.

UK scope: England, Scotland, Wales, Northern Ireland and UK islands; no automatic Crown Dependency or Republic of Ireland admission. OSM UK-area query is not a spatial audit. Coastal clipping/boundary generalisation may place a real UK post just outside a land polygon. Record `boundary_status=coastal_exception_verified`, boundary dataset/revision, reviewer, rationale, evidence and actual coordinate; do not snap coordinates inland or expand all boundaries. Unresolved/out-of-scope rows stay in research only. Geography supports provenance, never personality assignment or Harrow quotas.

Map: milepost-shaped silhouette pins, orange undiscovered and purple discovered, neutral outlined unavailable/unverified entries if explicitly shown with clear status. Only eligible records appear as normal discoverable pins. Use distinctive shape and check/status labels alongside colour. Reuse the same clusters for drawing and hit testing; mixed clusters show total and discovered/total, not an all-discovered purple state when only one is visited. Tap cluster zooms or opens a stable accessible list at maximum zoom. Details include location, verified design/status, attribution link, discovered date and contributing journey. Hidden personality conditions/names remain spoiler-safe. Minimum 48dp touch targets, text scaling, TalkBack list alternative; do not rely on tiny Canvas pins for access.

## Readiness and limits

Behaviour is specified; production readiness is conditional on a verified eligible inventory, historical evidence adapters, source/portrait rights and measured device budgets. The original geographical audit/matrix was not recovered in the source archive; no regional counts are claimed. Original raw source hashes and inspection counts are in the receipt. There is no minimum national verified count invented here, but Harrow requires at least seven independently eligible posts and all four designer awards require verified attribution opportunities before presenting the complete experience as attainable.

No further owner product-policy decision is required for this specification. Evidence/licensing work and technical fixture results are engineering gates. Portrait resemblance or any requested departure from these fixed rules can be reviewed with concrete artwork/evidence later. Merging the documentation and commencing production development remain separate authorisations.
