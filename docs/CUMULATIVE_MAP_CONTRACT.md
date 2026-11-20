# Roadprints cumulative Map product contract

**Status:** Proposed by owner, pending review and merge.  
**Scope:** Main cumulative Map's accepted-travel geometry and its relationship to saved journey maps.  
**Product owner clarification:** 20 November 2026.  
**Normative language:** MUST = release requirement; MAY = permissible implementation choice.

## Purpose and source of truth

The main Map is a **cumulative geographic representation of accepted saved journey geometry**. It is neither a stack of individual journey strokes nor merely a diagram of credited reference roads.

> **Every accepted journey line must be represented in the cumulative view with equivalent geographic shape.**

**Represented** is intentional. The same travelled physical path can be covered by many journeys yet needs only one visible cumulative stroke. Conversely, geographically distinct travelled paths must remain distinct. This is a contract for observable output, **not** a demand to retain every source point or draw every journey separately.

For each eligible saved journey, the accepted, correction-aware route shown on its individual journey map is the evidence to be represented. User-removed matched sections must stay removed. Treat the journey's accepted shape and its correction history as authoritative input; never substitute unaccepted recorded traces, speculative links or neighbouring catalogue edges as though they had been travelled.

## Invariants

1. **Completeness:** Every eligible accepted saved journey line MUST be accounted for in cumulative visible coverage. If processing is held, incomplete or deferred, diagnostics MUST say so; silently dropping a supported fragment is not acceptable.
2. **Idempotence:** Repeating the same physical route MUST NOT multiply the visible strokes or inflate unique physical coverage. Repeated visits may still have separate journey/contributor identities.
3. **Shape fidelity:** Consolidated geometry MUST preserve the geographic shape and physical alignment of accepted travel, including material bends, roundabouts, junction turns, forks and diverging lanes. A straight shortcut across a curved accepted path is not equivalent.
4. **Distinct physical paths:** Parallel carriageways, genuinely different roundabout arcs, slip roads and nearby but different paths MUST remain separate if evidenced by accepted journeys. Proximity alone is insufficient to merge them.
5. **No invented connectors:** A GPS or evidence gap, separate disconnected accepted sections, or unsupported interpolation MUST NOT be bridged merely to make a continuous cumulative line.
6. **Corrections remain authoritative:** Removed matched sections and other saved corrections MUST remain excluded. Adding another journey may legitimately cover the same place through independently accepted evidence, but cannot restore a section for the corrected journey.
7. **Union, not stacking:** The cumulative Map MUST show the geometric union of physically equivalent travelled paths, without displaying duplicate overlays for each journey.
8. **Coverage accounting is separate:** Canonical OSM road, road completion and NCN credit MAY use reference geometry for attribution; those calculations MUST NOT silently replace or distort the Map's accepted-travel visual geometry. Do not grant coverage credit merely because a rendered path lies near a reference edge.
9. **Controlled representation:** Simplification, clipping, deduplication and aggregation MAY be used only where the visible shape remains equivalent at meaningful inspection zoom levels, without losing supported branches, falsely joining gaps or merging distinct roads. No particular numerical tolerance is approved by this contract; engineering must propose and test it.
10. **Non-destructive and usable:** Original journey files and correction evidence MUST be preserved. The Map MUST remain responsive and stable on the owner's Pixel 10 (Android 17), retain its established camera/tab navigation and existing road-facts loading overlay. Performance limitations must be surfaced, not hidden by dropping correct geometry.

## Illustrative acceptance guardrails

### A. Repeated commute on one physical road

Several accepted journeys cover the same physical path. The main Map shows a single geographic line for the shared path; it does not draw N visibly overlaid strokes. Contributor history may identify all journeys separately.

### B. Different travelled paths through a junction (roundabout example)

The owner's observed case: west-to-east and east-to-west journeys share their physical approaches but traverse distinct clockwise and anticlockwise roundabout arcs. The main Map shows one representation of each shared physical approach and **both** travelled arcs, with no straight shortcut through the central island and no duplicate overlays.

A subsequent journey using other entrances and exits adds **only arcs or approaches not already represented by accepted travel**. Once accepted journeys together cover the entire circulating roundabout path, the cumulative roundabout is visually complete. A third use of already covered arcs must not create additional visible paths.

A complete-looking circle is **not** grounds to infer travel over an unvisited arc. Direction by itself is not a reason to draw two lines when both directions use exactly the same physical path; separate arcs here are supported by actual distinct accepted shapes.

### C. Separate carriageways

Two accepted journeys use distinct adjacent carriageways. Both must remain visible and individually selectable/attributable where appropriate. They may not be merged on the basis of proximity.

### D. Accepted route with a gap

Two accepted fragments separated by an unsupported gap remain disconnected in the cumulative view. No straight-line bridge may be drawn.

### E. Corrected journey

An individual journey's stored correction removes a false matched section. That section must not reappear in cumulative coverage merely because an uncorrected raw trace or reference-road centreline contains it.

### F. Detailed bends

A journey map shows a curved street. The main Map may simplify its points, but must keep the bend's position and observable shape, rather than substituting a materially different straight chord.

These scenarios are **illustrative guardrails, not an exhaustive catalogue or architectural design**. The same principles apply to every type of accepted road, footpath, cycleway, junction, interchange, turning, overlapping corridor and other geographic configuration. Engineering MUST validate the general behaviour, including cases not described here. Do not optimise the cumulative-map algorithm specifically for a roundabout fixture or hardcode special-case topology solely to satisfy these examples.

## Engineering and release checks

- Trace the data lineage from **journey's accepted correction-aware geometry** to cumulative storage, union and rendering, and identify where coordinate shapes or physical paths can change.
- Compare saved journey Map geometry with cumulative geometry across a representative, varied set of real and synthetic travel patterns (including the illustrative guardrails A–F), using coordinate-based assertions, not only screenshot counts, segment counts or source-level algorithm descriptions.
- Verify union is idempotent, reversible/order-independent for equivalent inputs and preserves all supported distinct paths, regardless of local road topology.
- Assert no speculative connectors, no adjacent-road credit, no resurrection of removed matched sections and no change to original journey files.
- Maintain road/NCN accounting checks separately from cumulative **display** geometry.
- Record completeness holds and evidence-based exclusions; no hold may silently appear as fully recovered.
- Validate Map readiness, heap, GC, tab retention and loading overlay after visual-correctness checks. Pixel 10 owner acceptance is required to call release successful.
- Prefer small, bounded PRs and no broad refactor solely because this contract reveals implementation differences.

## Scope boundary and current known question

The current cumulative Map implementation has used canonical road-section endpoints for some local-road display and separately simplified accepted international geometry. That can create a visible difference from individual journey maps. **This document does not itself declare a specific code change safe or assert that all geometry is presently wrong.** An engineering audit must establish which output violates the contract and fix it with regression evidence.

Do not reframe the requirement as *every journey must be drawn independently*: that would contradict the product owner's explicit distinction between **represented** and **displayed as a separate stroke**.

## How to cite in every Map brief

Include:

> **Authoritative Roadprints cumulative Map contract:** `docs/CUMULATIVE_MAP_CONTRACT.md` (repository `adamdbird-gfc/uk-road-tracker`). Read before designing, editing or accepting any cumulative Map change. Report any proposed deviation to the owner before implementation.

This contract supplements `docs/DEVELOPMENT_GOVERNANCE.md`. Proposed changes to the contract require product-owner review; changing an implementation is not permission to silently change its acceptance criteria.
