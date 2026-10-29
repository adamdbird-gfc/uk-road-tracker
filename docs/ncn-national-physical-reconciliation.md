# Nationwide physical correspondence checkpoint — 16 October 2026

All 253 official numbered keys now have a route-scoped geometry report. The 305
accepted source-relation detail requests completed without request or parse
failures; no route comparison failed. The result contains 77,153 distinct accepted
source way representations after holding two cross-profile representation
conflicts (ways 2169047 and 23626936). These are provider-geometry diagnostics,
not approved completion bindings or a claim that all ways have current node chains.

| Disposition | Numbered keys |
| --- | ---: |
| Close bidirectional correspondence candidate | 48 |
| Partial or different extent requiring review | 79 |
| Offset-sensitive correspondence requiring review | 60 |
| Partial source geometry | 23 |
| Source-scope review required | 30 |
| Missing suitable source membership | 13 |
| Total | 253 |

The close category requires at least 98% of both physical unions to fall within
the other's 10m diagnostic corridor, complete expected provider-way representation,
complete member lists and a UK metadata anchor. It is still a candidate state:
proximity and route-number agreement cannot approve an alternative or infer access.
The 3m/10m/25m series is retained to expose offset sensitivity. A short source
fragment cannot establish full official extent. Missing official coverage and
source linework outside the reference remain separate, rather than a single match
percentage. No national distances are summed across overlapping route identities.

The agreed alternative policy applies throughout the reconciliation: explicitly
identified equivalent paths may share logical completion, distinct continuations
remain required, walking/cycling eligibility stays explicit, and nearby roads
receive no credit through proximity. The reviewed Route177 overlay remains the
bounded implementation of that policy. This national report does not silently
generate approved branch bindings from geometry. The 60 offset-sensitive keys are
a review queue, not 60 asserted copies of the Route177 case.

Waymarked Trails supplies Web Mercator line representations without the OSM node
chains needed by saved cycling matching. Each base way is checked against the
previously captured current OSM relation/member inventory. Foreign/classification
changes, unlisted ways, absent geometry and conflicting representations stay held.
The parser preserves explicit opposing roundabout arcs when the provider supplies
roundabout/circular tags, direction +1/-1 and common endpoint coordinates. The
97 distinct affected roundabout ways remain line parts, never straight chords or
new source-node identities. Other differing representations are not auto-joined.

Per-route spherical azimuthal-equidistant projections avoid Web Mercator's
latitude-dependent distance scale. Measurements remain approximate diagnostics,
not published completion denominators. Spatially indexed local corridors avoid
rebuilding a whole-route buffer for every official section. Reports are written
atomically and reused only if their source/official inputs, scope/completeness
metadata and implementation fingerprint match. The completed fetch manifest is
captured before processing, so a concurrent download cannot mislabel input hashes.

The evidence archive retains the pinned official geometry and attribution, all
305 raw compressed provider responses with URLs/timestamps/hashes, all 253 detailed
route reports, extraction diagnostics and the final summary. It is named
roadprints-ncn-route-catalogue-assessment-2026-10-16.zip. Reproduction uses
scripts.ncn_trial.network_reconciliation with the archive's numbered inventory,
member profiles, source-lead summary, official-network snapshot and geometry root.
The uncompressed official snapshot SHA256 remains
331023ba1d8ca175471700b1bdb5d7828984d535792c0c54e7a4b465d48cf3f2.

Validation: all 224 local checks pass (219 NCN +5 shared collector). Fourteen new
checks cover explicit membership, nested/reverse duplicates, invalid and absent
geometry, classification changes, directional roundabout representations,
cross-profile conflicts, missing-route retention, partial extent/scope flags,
long-route projection and safe checkpoint reuse/invalidation. Existing saved-path,
logical-alternative and activation regressions remain passing. No private journeys,
database rows, Firebase release, production deployment or APK were involved.

Next: turn supported candidate sections into explicit physical-span associations,
review the offset/extent exceptions with the shared alternative policy, obtain
current source-node contracts for saved-cycle matching, then connect the reviewed
catalogue to Android activation, map/progress and the initial achievements gate.
Named itineraries and mileposts remain separate inventories.
