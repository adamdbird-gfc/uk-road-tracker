# Remaining 54 numbered-route cases — 19 October 2026

All 54 outstanding source-review cases now have a determination. **25 gain
bounded catalogues of verified open spans; 29 remain evidence holds.** This adds
17,777 distinct physical ways, bringing the supported checkpoint to **219 route
identities and 62,399 distinct ways**. Support means reviewed source spans, not
certification of a whole route or Android activation.

The original 253-key inventory is fully accounted for: 219 supported keys, these
29 holds, and five earlier holds (NCN626 and RCN11/17/71/82). There are no
unreviewed cases left in this particular 54-case queue. The 34 held identities,
within-route source gaps, completion bindings and app activation remain work.

## Supported spans

| Route | Ways | Official open target, km | Walking support, km | Cycling support, km | Unmatched open extent at 10 m, km |
| --- | ---: | ---: | ---: | ---: | ---: |
| NCN 1 | 6890 | 1943.972 | 1822.617 | 1859.564 | 77.460 |
| NCN 151 | 45 | 3.279 | 3.110 | 3.127 | 0.174 |
| NCN 2 | 2403 | 565.506 | 533.067 | 539.960 | 14.909 |
| NCN 21 | 938 | 152.539 | 144.600 | 149.461 | 2.511 |
| NCN 23 | 897 | 137.132 | 133.598 | 133.821 | 2.354 |
| NCN 248 | 5 | 3.435 | 2.423 | 2.423 | 0.981 |
| NCN 267 | 23 | 4.757 | 1.982 | 4.366 | 0.304 |
| NCN 304 | 23 | 17.319 | 17.354 | 17.354 | 0.000 |
| NCN 305 | 58 | 25.539 | 23.531 | 25.413 | 0.154 |
| NCN 334 | 33 | 10.796 | 10.791 | 10.791 | 0.008 |
| NCN 4 | 3174 | 680.116 | 574.957 | 627.612 | 45.617 |
| NCN 41 | 705 | 185.373 | 168.621 | 172.298 | 12.825 |
| NCN 42 | 205 | 98.169 | 95.339 | 96.885 | 0.897 |
| NCN 439 | 38 | 7.725 | 2.446 | 6.990 | 0.512 |
| NCN 461 | 50 | 8.880 | 8.779 | 8.784 | 0.043 |
| NCN 47 | 385 | 184.932 | 177.072 | 178.338 | 6.019 |
| NCN 533 | 7 | 5.268 | 5.234 | 5.234 | 0.024 |
| NCN 604 | 36 | 4.956 | 4.928 | 4.928 | 0.003 |
| NCN 674 | 40 | 10.835 | 10.523 | 10.519 | 0.286 |
| NCN 685 | 21 | 3.596 | 3.407 | 3.407 | 0.085 |
| NCN 70 | 636 | 188.009 | 183.147 | 185.027 | 2.866 |
| NCN 700 | 241 | 61.346 | 59.383 | 59.541 | 2.156 |
| NCN 8 | 955 | 383.185 | 347.812 | 368.284 | 13.691 |
| RCN 70 | 4 | 3.792 | 3.809 | 3.809 | 0.000 |
| RCN 92 | 1 | 0.274 | 0.118 | 0.118 | 0.141 |

Targets use explicitly Open Main features classified TrafficFree or OnRoad;
closures, unknown statuses, alternative/N/A features and ferry geometry remain
in the source evidence and are excluded from this walking/cycling target.
Official open targets and exact source geometry use different linework. These
lengths must not be divided into a user completion percentage or added across
modes. All unmatched official Open Main geometry remains in the research target.
For example, NCN1 still has about 77.46 km outside the supported source's 10 m
corridor; NCN4 has about 45.62 km. Neither shortens its target to make the route
appear complete. Zero corridor residual likewise does not certify every source
membership or logical branch. All catalogues retain `completion_binding_ready=false`.

RCN70 and RCN92 preserve their **regional** identities. Their support is limited
to source spans matching the explicitly Open Main features. No regional route
is relabelled as a national route, and no N/A feature is promoted. The publisher
warns that regional data is not maintained; this bounded data support does not
establish the status of the wider regional routes.

## The 29 held cases

| Hold | Keys | Evidence still needed |
| --- | --- | --- |
| No confirmed numbered-source membership (9) | NCN207, 274, 323, 410, 422, 524, 689, 776, 820 | Current source identity and complete source-node evidence for the publisher's open section. |
| Alternative-only official geometry (1) | NCN253 | Resolve the alternative-only dataset feature against the published route and former regional identity; do not infer a full open-main target. |
| Regional N/A classification (18) | RCN12, 15, 16, 21, 30, 31, 32, 40, 41, 50, 51, 55, 61, 67, 80, 81, 83, 89 | Current authoritative classification and identity before activation. N/A is not proof that a path is closed. |
| No corresponding source edges on the open-main feature (1) | RCN85 | Resolve the position/identity mismatch; nearby same-number relations do not qualify automatically. |

The missing-membership cases were checked against existing numeric profiles,
fresh bounded provider searches and the previously verified physical registry.
The search results include unrelated overseas numbers; they are leads only,
not proof that a UK source is absent. Overpass lookup failures are recorded as
failed attempts and never treated as negative evidence. Local-classification
leads for NCN410 and NCN820 remain distinct from national source membership.

NCN776's roughly 24 m official feature is near a supported NCN777 way. That
proximity does not establish a new alias or completion transfer. NCN253's
[publisher page](https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/route-253/)
identifies a much longer route and its former RCN41 identity, while the current
dataset capture contains one alternative feature. Preserve that discrepancy
rather than creating a tiny substitute for the advertised route.

## Identity and hierarchy determinations

- Missing operator/country metadata no longer forces a route to remain
  unassessed when an explicit review can bind exact source edges to pinned UK
  publisher geometry. The new `official_uk_spans_scope_review` applies only to
  the unchanged source/target number and classification. It checks every
  approved edge against Open Main geometry and the excluded-feature buffers.
  Explicit foreign namespaces remain rejected. This is not a declaration that
  an entire source relation belongs to the UK.
- NCN1 source 192623, NCN2 source 192635 and NCN4 source 192889 are Isle of Man
  number collisions and are excluded. NCN1 source 9067786 explicitly disputes
  its own NCN1 identity and is also excluded. Independently verified membership
  through another selected source chain remains eligible under ordinary gates.
- NCN1's parent includes relation 11319642, an empty station-link placeholder.
  Its JSON omits a members array. An independent, hash-pinned XML response
  confirms the same relation version, timestamp and tags with zero members.
  The explicit review permits this zero-member placeholder without adding any
  ways. Missing records, nonempty children, mismatched versions and incomplete
  XML remain rejected. The historical inventory builder's completeness rule is
  unchanged; no inventory is shortened and no missing link is invented.
- NCN700 source 7024538 is recovered from the numeric-discovery leads and fresh
  source/provider captures. Only its matching Open Main spans qualify. The full
  Bay Cycle Way source and other numbered/shared sections are not substituted
  for the current NCN700 target.
- Proposed or closed membership chains, unresolved branch policies, provider
  conflicts, access restrictions and excluded official features remain active
  gates. RCN85's one Open Main feature does not turn either entire same-number
  source relation into a verified match.

The batch has 17,813 route-way associations across 17,809 distinct ways. Of
those distinct ways, 32 were already in the preceding 194-key registry; 17,777
are new. Four repeated associations occur between new route identities. Shared
ways retain one physical identity. No current node/coordinate fingerprint
conflict with the preceding registry was admitted.

## Evidence and validation

The refresh contains **54 complete paged official route responses, 159 source
relation/full responses and 61 provider-root responses**, plus bounded discovery
captures. All 274 required HTTP captures succeeded. Complete HTTP capture does
not imply complete route membership or approval: each supported hierarchy is
checked independently. Official page counts, unique feature IDs and the exact
assembled feature collection are verified during offline replay.

The independently pinned inputs have SHA-256
`25ca8b2d5ec1d0e97baa0ef7b2689d6cf7bb7ba60786bf0edc719ddd2cb71520`.
The preceding registry is pinned at 194 keys / 44,622 distinct ways with digest
`3d41f286461d157c738726b38eb6dc67d6214eeb4af39df2a18ad3e860c1ae89`.
Queue identities, selected source roots, all raw source responses, the separate
empty-relation receipt and the complete current node chains are pinned.

`remaining_routes_review.py` never edits historical triage flags. It emits an
explicit result for every case, including holds. A retry requires the exact
preceding summary checksum, may select only held cases, and revalidates the
existing catalogues before carrying them forward. A failed catalogue cannot
leave an association bundle or success manifest behind.

Validation covers the full test suite and fixtures, strict reading of all 25
catalogues, unchanged preceding outputs, physical registry accounting and an
independent complete offline replay. Regression cases exercise out-of-extent
scope claims, foreign namespaces, regional identity preservation, official-page
tampering, closed-feature overlap, immutable retry checkpoints, and empty XML
that tries to conceal a real member. The exact test and replay receipts are in
the evidence bundle's `validation.json`.

The repository JSON is a compact index with the full output summary's SHA-256.
The evidence bundle retains all captures, plans, reviews, catalogues, audits,
holds and the offline replay instructions. No APK or Android trial expansion is
part of this data slice; the internal trial remains eight routes.
