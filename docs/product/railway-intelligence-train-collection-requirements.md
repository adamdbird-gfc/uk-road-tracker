# Roadprints — Complete Railway Intelligence and Train Collection Requirements

**Date:** 12 November 2026  
**Status:** Consolidated owner product requirements; implementation not authorised  
**Repository:** adamdbird-gfc/uk-road-tracker  
**Related architecture decision:** [ADR — Railway Intelligence is core Roadprints functionality](../architecture/adr-railway-intelligence-core.md)

## 1. Purpose and scope

Roadprints will record railway travel accurately for every user and optionally let Train Collection owners explore the UK railway network, visited stations, operator-served corridors and railway heritage achievements. This document is the consolidated requirements baseline across **three app areas**: (A) core Railway Intelligence, (B) optional UK Rail Network Collection and (C) Railway Achievements. Requirements are distinct from proposed names, candidate catalogues and outstanding investigations. No production code, release or purchase integration is authorised here.

### Core product invariants

- Evidence-led, automatic and retrospective: no manual train/operator identification or special recording session.
- Purchasing Train Collection reveals optional exploration features; it **never changes measured journey mileage**.
- Preserve raw observations, journey identities, user edits, existing NCN/Mileposts data and automatic capture.
- One journey may satisfy multiple independent achievements, without double-counting unique network coverage.
- Never turn an uncertain route, station event, operator identity or historical association into a confirmed claim.

## 2. Area A — Core Railway Intelligence (all users)

**RI-01 — Network-constrained railway distance.** Use GPS observations plus versioned railway geography to infer supported routes. Where sufficiently supported, railway network length is authoritative for displayed journey mileage; where ambiguous, apply conservative documented fallback and retain provenance/confidence. Never invent unmatched mileage.

**RI-02 — Repeated travel versus unique coverage.** Core journey mileage accumulates on every trip; collection network coverage counts each equivalent geographic corridor once.

**RI-03 — Boarding and alighting evidence.** Combine evidence across consecutive journey records where appropriate: station approach, dwell, rail departure/arrival, pedestrian handoff and track continuity. Confirm boarding/alighting only when evidence supports it; station passage or a train stop is not a visit. Keep provisional states where unresolved.

**RI-04 — Historical reassessment.** Reassess eligible historical railway journeys automatically as catalogue and matcher improve, including mislabelled/fragmented records. Make work incremental, idempotent, resumable, versioned, nonblocking and resource-conscious; update derived totals consistently.

**RI-05 — Independent technical gates.** Gravesend boarding fusion demonstrated an isolated prototype possibility, but historical Gravesend alighting remained provisional. Station-event reliability and Android memory/performance feasibility remain blocked pending validation. No product decision waives these gates.

## 3. Area B — UK Rail Network Collection (optional purchase)

**TC-01 — Map selector.** Purchased collection appears as **UK Rail Network** under existing **Collections on the Map**. Selecting it hides other collection overlays.

**TC-02 — Map styling.** All eligible rail corridors black; uniquely travelled corridors green. Eligible station pins black; confirmed boarding/alighting pins green. Tapping pins shows station name and qualifying visit dates. Viewport/zoom-efficient rendering is required.

**TC-03 — Corridor equivalence.** Direction does not matter. Parallel tracks/fast and slow lines representing one geographic corridor count once; genuinely diverging branches count separately. Unique coverage uses supported geometry only.

**TC-04 — Network progress.** Show unique miles covered, eligible network miles and percentage for the entire UK railway network. Distinguish these from cumulative journey mileage.

**TC-05 — Station progress.** Show confirmed stations visited, eligible station count and percentage. Passing through or stopping without boarding/alighting does not qualify. Historical evidence is eligible.

**TC-06 — Operator-served network progress.** For each eligible passenger operator, show unique miles explored against its versioned eligible served-corridor network and percentage. Shared track may credit multiple operators independently (e.g. a verified Southeastern/Thameslink shared corridor). **Never assert which operator's train the traveller rode.** Historical service associations, catalogue scope and operator eligibility require validation.

**TC-07 — Entitlement and retrospective value.** Purchase unlocks map, collection progress and achievements, including qualifying history; it does not change underlying journey measurements.

## 4. Area C — Railway Achievements

### Shared award rules

**AC-01.** All unlocks require evidence appropriate to the category. Historical journeys qualify. Direction is irrelevant unless expressly specified. One-time awards preserve qualifying date and evidence/provenance. Multiple awards may result from the same journey. Do not require a GPS point directly on a bridge if matched railway route confidently establishes crossing.

**AC-02 — Architectural Icons.** Unlock by confirmed **boarding or alighting** at the named station, not passing through. Proposed first shortlist (candidate, pending architectural/heritage review): London St Pancras International; London King's Cross; York; Wemyss Bay; Bristol Temple Meads; Edinburgh Waverley; Newcastle Central; Huddersfield; Great Malvern; Dunrobin Castle; Manchester Victoria; Cardiff Central. Consider additional distinctive Welsh representation. Proposed collective: **The Great Railway Architects** for all selected stations.

**AC-03 — Historic Journeys and Railway Legends.** Celebratory homage names do not claim travel on the original locomotive/service:
- **Isambard, We Thank You!** — board at London Paddington and alight at Bristol Temple Meads, or reverse, as one supported railway journey (changes permitted). Independently qualifies for both station icons if included.
- **The Rainhill Trials** — board at a Liverpool station and alight at a Manchester station, or reverse, via any rail route; does not require the original 1830 alignment. Name proposed with Stephenson's Rocket as potential artwork/story.
- **All Aboard the Hogwarts Express!** — board at London King's Cross and alight at any station in Scotland. Franchise naming/artwork requires intellectual-property review.
- **The Flying Scotsman** — supported railway journey between **two distinct stations in Scotland**. This deliberately differs from King's Cross-to-Scotland Hogwarts criterion.
- **Record Breaker** — traverse verified railway geometry at Mallard's 1938 Stoke Bank record location; exact geometry needs historical verification.
- **Where It All Began** — traverse a verified surviving operational section of the original Stockton & Darlington Railway alignment; exact eligibility needs historical research.

**AC-04 — Engineering Marvels.** Unlock by confidently traversing structure geometry, irrespective of boarding/alighting station. Founding four: **The Forth Dimension** (Forth Bridge); **Twenty-Four Arches** (Ribblehead Viaduct); **A Touch of Magic** (Glenfinnan Viaduct); **Brunel's Masterpiece** (Royal Albert Bridge). Further candidates: Barmouth Bridge, Conwy Railway Bridge, Welwyn/Digswell Viaduct and Ouse Valley Viaduct. Proposed collective: **Masters of Engineering**.

**AC-05 — Scenic Railways.** **100% of each explicitly defined scenic corridor** required. Coverage may accumulate over multiple journeys, years and either direction. Confident route matching can resolve GPS gaps, but no unverified segments or arbitrary 80% threshold. Founding four: **Highland Wanderer** (West Highland Line), **Across the Dales** (Settle–Carlisle), **Cornish Coastline** (St Erth–St Ives Bay Line), **To the Edge of the Highlands** (Inverness–Kyle of Lochalsh). Define West Highland main/Mallaig versus Oban branch unambiguously. Further candidates: Cambrian Coast, Heart of Wales, Far North and Dawlish Sea Wall. Show per-route unique coverage and remaining segments.

**AC-06 — Trainspotter.** Unlock when user has covered **at least one qualifying network section served by each eligible passenger operator**, not when they have ridden each operator's trains or completed each operator's entire network. Shared eligible corridors may count for several operators. Operator eligibility (including heritage/open-access/defunct operators) remains to be decided.

**AC-07 — Overlapping achievements.** For example a Paddington–Temple Meads journey may unlock both station icons and **Isambard, We Thank You!**; a Scottish railway journey may unlock **The Flying Scotsman** and engineering/scenic progress independently. No award may be based solely on assumed service identity.

## 5. Evidence models and acceptance examples

| Observation | Expected result |
|---|---|
| Train passes through York without boarding/alighting | Rail corridor may turn green; York station pin remains black |
| Traveller boards at King's Cross and alights at Edinburgh | Hogwarts achievement qualifies; King's Cross/Edinburgh station icons qualify independently; Flying Scotsman does **not** qualify merely from those endpoints |
| Traveller boards Edinburgh and alights Glasgow | Flying Scotsman qualifies |
| Train traverses Ribblehead Viaduct without stopping | Engineering Marvel qualifies if geometry match confident; no Ribblehead station visit inferred |
| Settle–Carlisle completed in multiple trips | Scenic award only at 100% verified unique designated corridor |
| Ten-mile track shared by two verified operator networks | Ten unique miles added to each operator-network progress; no operator-train identification |
| Uncertain Gravesend pedestrian handoff | Provisional alighting only; no station visit or visit-dependent award |
| Collection purchased after years of journeys | Retrospective progress/awards appear; core journey distances unchanged solely due to purchase |

## 6. Outstanding definitions and release gates

1. Validate station boarding/alighting evidence fusion against real data; resolve Gravesend uncertainty.
2. Validate Android memory, battery, map performance and incremental historical workers.
3. Version UK rail geography, corridor equivalence, station identifiers and eligible-network denominator.
4. Establish authoritative operator-to-corridor mappings and operator eligibility over time.
5. Verify exact historical landmark geometry (Mallard, Stockton & Darlington), bridge crossings and station architectural attributions.
6. Define exact scenic route polylines/endpoints, branch policies and completion semantics.
7. Finalise achievement wording, selected station list, art and intellectual-property clearance.
8. Specify entitlement, progress migration, award dates, corrections/revocations and UI details before implementation.

## 7. Delivery boundary

This is a **requirements capture**, not a production-ready technical design or implementation approval. Follow the linked ADR for the agreed core-versus-optional architecture. Validate blocked feasibility gates and unresolved catalogue definitions before any staged production work.

**Guiding principle:** accurate railway measurement for everyone; evidence-led railway exploration and British railway heritage as the optional Train Collection.
