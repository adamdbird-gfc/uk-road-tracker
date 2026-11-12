# ADR — Railway Intelligence is core Roadprints functionality

**Date:** 2026-11-12  
**Status:** Accepted product direction; implementation not authorised  
**Scope:** Railway matching, journey mileage, historical reassessment and optional Train Collection

## Context

Roadprints currently has GPS-derived journey distance calculations. Train Collection feasibility work is validating railway network matching and separate station boarding/alighting inference. Network geometry can provide a more faithful train route distance than point-to-point approximations, subject to sufficient evidence. Railway travel follows a constrained network, though junctions, alternative alignments, GPS gaps and incomplete catalogue coverage can leave ambiguity.

Two product approaches were considered:
- **A:** Railway distance accuracy becomes available only after buying the Train Collection, potentially changing existing journey mileage on purchase.
- **B:** Railway matching improves core journey accuracy for everyone; buying the collection unlocks exploration and achievements.

## Decision

**Adopt Scenario B.**

1. **Journey accuracy is core.** Railway network matching and evidence-supported train mileage are available regardless of Train Collection ownership. Purchasing a collection must never be the reason a journey's distance changes.
2. **One authoritative displayed distance.** When a railway route is established with sufficient confidence, its network length is the preferred train distance. Where evidence is partial or ambiguous, use a documented conservative fallback rather than inventing precision or double-counting. Preserve original GPS observations and calculation provenance.
3. **Historical reassessment is automatic.** Reassess existing railway journeys, not only newly recorded journeys, as matching capability and railway catalogues improve. Do not trust historical Train labels blindly or overwrite original evidence. Reassessment must be versioned, incremental, idempotent, resumable, resource-conscious and non-blocking; update dependent totals consistently.
4. **Separate responsibilities.** Core journey measurement owns authoritative mileage; reusable Railway Intelligence owns catalogue access, route matching, evidence/confidence, station events and reassessment; optional Train Collection consumes this evidence for map overlays, network/station progress, achievements and retrospective unlocks.
5. **Collection purchase unlocks experience, not measurement.** Previously recorded and assessed railway exploration can be revealed retrospectively on purchase, without recalculating mileage merely because of entitlement.

## Rationale

Two users on the same train should not receive different measured mileage because one purchased an optional collection. Railway geometry is a means of improving the recording product; railway exploration is a distinct optional experience. The distinction also prevents parallel competing mileage engines and supports retrospective collection value.

## Consequences and constraints

- Plan provenance/versioning for matched geometry, fallback distance, confidence and source catalogue.
- Keep raw journey evidence, user edits and journey identities intact.
- Define policies for partial matching, uncertain mode classification, fragmented records, aggregation and revised historical mileage before rollout.
- Avoid UI stalls, memory pressure and repeated full-archive assessment.
- No production implementation is authorised by this ADR.
- The Stage 2.3 historical Gravesend alighting evidence-fusion investigation remains independent. Station recognition and Android resource feasibility gates must still be validated; acceptance of this product direction does not imply they have passed.

## Future implementation sequence (proposal only)

1. Inspect existing distance/provenance and historical-worker architecture.
2. Finalise confidence and partial-match policies against real evidence.
3. Validate Android performance and regression safeguards.
4. Implement core railway measurement and incremental historical reassessment.
5. Integrate optional Train Collection presentation, progress and achievements.

**Guiding principle:** Journey accuracy is core Roadprints functionality. Railway exploration is the optional collection experience.
