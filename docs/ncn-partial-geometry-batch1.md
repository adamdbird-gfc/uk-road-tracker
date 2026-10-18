# Five partial-geometry cases — 18 October 2026

NCN250, 99, 647, 83 and 221 now have bounded catalogues of verified open source
spans. They were the five smallest UK-anchored national cases in the original
partial-provider queue. No whole-route certification, user completion transfer,
Android activation or APK release is implied.

| Route | Supported ways | Official open target, km | Supported on foot, km | Supported cycling, km |
| --- | ---: | ---: | ---: | ---: |
| NCN250 | 33 | 10.268 | 9.713 | 9.967 |
| NCN99 | 48 | 22.604 | 11.164 | 11.182 |
| NCN647 | 53 | 20.990 | 17.446 | 17.469 |
| NCN83 | 108 | 55.776 | 53.074 | 55.831 |
| NCN221 | 115 | 13.990 | 13.773 | 13.289 |

These lengths use two geometry bases: the official open target and exact source
paths. They are not completion percentages, cannot be added across modes, and
can differ slightly even for the same physical route. In particular NCN83's
source linework is slightly longer than the official linework; it is not more
than 100% complete. Precise physical-to-official completion accounting remains
an explicit adapter step. Every open target is retained in full, including
portions with no admitted source match.

## Route determinations

- **NCN250:** retain its closed parking-aisle loop as a branch-policy hold.
  The provider splits source way 75127304 into two arcs. This is not independent
  verification of a permitted whole-loop completion rule. The other 33 ways
  acquire verified open spans.
- **NCN99:** the refreshed source relation is version 230, with 53 member ways
  instead of 45. Its eight added IDs exactly explain the previously unlisted
  provider ways. Their current source membership, nodes and provider geometry
  were reassessed before admission; no old evidence is transferred. All 53 ways
  have coherent provider geometry, but access/extent checks admit only 48.
  About 11.363 km of the official open target remains outside the supported
  source's 10 m diagnostic corridor. This substantial residual needs further
  source-scope work; the matched portion is not substituted for the whole target.
- **NCN647:** source way 89666353 is an unresolved roundabout representation.
  Other extent differences remain held, including parts of four accepted ways.
  Its longer source relation is not used as the open route denominator.
- **NCN83:** retain roundabout way 949979263 as a geometry/branch hold. Three
  admitted ways have partly held edges. The full official open target remains
  present even where source and official centreline lengths differ.
- **NCN221:** the super-relation includes child relation 4026393, explicitly
  tagged `state=proposed`. Its 31 exclusively proposed descendant ways are
  excluded, including the geometry missing from the root provider response.
  They are not activated because the roads or paths may physically exist.
  A way independently listed through the existing open membership chain can
  still qualify. Two admitted ways retain partly held edges.

The proposed NCN221 child's provider endpoint returned HTTP 404. That is retained
as a supplementary observation, not used as proof of closure or as an excuse to
guess its geometry. Its complete OSM source hierarchy and explicit proposed
state establish the relevant admission hold. The ordinary Woking child provider
was captured separately for inspection; it does not grant extra membership.

## Evidence and admission contract

The capture contains seven complete OSM relation/full responses, six provider
responses and 152 official features across the five keys. All 152 official
features are explicitly Open Main Route features; no proposed connection is
inferred to join gaps. Each response retains its URL, observation time and exact
byte hash. The optional proposed-child HTTP response has its own receipt.

The standard batch selector still refuses the old incomplete-provider cases.
This pass uses an explicit partial-provider review rather than changing their
old triage flags to complete. The review pins the original triage/profile bytes,
full selected source hierarchy, current UK route identity, provider payloads,
official target geometry, exact approved source vertex spans and the preceding
supported physical registry. Conflicting or absent provider geometry does not
enter the reference. Nearby unlisted provider ways never become source members.

The shared review consumer now distinguishes two policies: a bounded correction
between different source/target labels, and a partial-provider review that must
keep the same label. Neither policy can use the other's identity rule. Normal
membership, exact-node geometry, mode access, branch and 10 m extent checks
remain mandatory. Non-open source membership is inherited through each relation
chain, so a proposed nested extension cannot bypass the open-only policy through
an apparently ordinary parent. An independently open chain preserves legitimate
shared-way membership.

The five new catalogues contain 357 way associations. Three ways are already
supported elsewhere; their full source node/coordinate fingerprints agree.
Those ways are counted once in collection-wide physical identity. The increase
is **354 distinct ways**, taking the supported assessment to **188 route keys
and 43,457 distinct ways**. A mismatch with a previously supported source
fingerprint would hold the new association pending reconciliation; none was
found in this batch.

## Remaining scope and verification

Approximately 0.200 km (NCN221), 0.238 km (NCN250), 3.468 km (NCN647), 0.057 km
(NCN83) and 11.363 km (NCN99) of official open geometry lies outside each
supported source's 10 m diagnostic corridor. These diagnostics locate further
review and grant no credit. Access, missing-provider and branch holds remain
where recorded. No geometry is widened to 25 m for admission.

The other **60** numbered-key source-review cases remain pending: 18 in the original
partial-geometry queue, 30 source-scope cases and 12 without suitable membership.
There are also residual holds within assessed routes, the unchanged legacy
NCN626 inventory and four deferred regional inventories. This batch is not the
end of national reconciliation.

All five catalogues were read through the independently pinned consumer. All
296 NCN tests and five shared cycling-evidence tests passed, with the Route1
and portable-association fixture builds. Eight new regressions cover bounded
selection, conservative partial admission, unlisted/conflicting provider data,
inherited proposed membership, independently open chains, policy identity rules,
physical registry fingerprints and capture/optional-receipt integrity. All 562
original inventory files remain byte-identical to the saved national baseline.

The detailed checkpoint is
[ncn-partial-geometry-batch1-2026-10-18.json](data-trials/ncn-partial-geometry-batch1-2026-10-18.json).
It retains the five review manifests, membership changes, provider omissions,
source-root versions/states, shared identities and exact input hashes. The
evidence archive supplies the new capture, generated open extents, associations,
audit records, validation and offline replay. The Android internal trial remains
at eight routes; these section catalogues are not production progress targets.

Geometry and membership: © OpenStreetMap contributors, ODbL 1.0.
Official extent: Walk Wheel Cycle Trust, OGL 3.0 with the original source
attribution retained in the preceding evidence bundles.
