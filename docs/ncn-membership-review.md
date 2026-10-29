# Review of six NCN source membership holds — 18 October 2026

All six membership holds from the 187-key assessment have been reconciled against
refreshed provider geometry and the exact pinned OSM source nodes. This is a
reviewed data overlay, not a published-route certification or an Android release.
The ordinary source-span builder still refuses unreviewed membership changes.

| Route | Mapped-linework finding | Supported ways after current extent/access checks |
| --- | --- | ---: |
| NCN196 | Same linework at the representation tolerance | 95 |
| NCN20 | Changed linework, reassessed against the official extent | 464 |
| NCN22 | Same linework at the representation tolerance | 879 |
| NCN517 | Same linework at the representation tolerance | 72 |
| NCN5 | Changed linework; all three source root profiles reviewed | 2,344 |
| NCN51 | Changed linework; removed identities excluded from the new registry | 1,399 |

The six catalogues contain 5,253 way associations. Three ways already occur in
other supported inventories, so the increase is 5,250 distinct supported ways:
37,793 becomes 43,043. The number of numbered keys with supported sections rises
from 175 to 181. There are now 47 source ways shared between route keys. These
counts are not distances or full-route completion percentages.

NCN51's three newly listed replacement ways (1306529955–1306529957) remain
uncredited because their captured OSM tags say `highway=construction`. The seven
added ways on the other five routes pass the walking and cycling checks.
The source notes link to [HS2's Steeple Claydon–Twyford road closure notice](https://www.hs2.org.uk/work-items/notice-of-closure-of-steeple-claydon-to-twyford-road-6-january-2026-to-november-2026/),
which lists an estimated end date of 30 November 2026 and was last updated on
5 December 2025. This is a published schedule, not evidence that access will
actually reopen on that date; eligibility needs a source-status reassessment.

## What the review establishes

Eight refreshed provider relation responses were pinned, including all three
root profiles contributing to NCN5. Every expected way was represented, with no
unlisted, missing, conflicting or invalid provider ways. Refreshed linework was
checked against complete source-node chains with the existing 0.03m numerical
representation tolerance. The maximum observed drift was below 0.005m.

Old and refreshed linework were compared in the same local projection. The
comparison describes changes in mapped geometry; it does not by itself establish
that a real-world route was rerouted. Per-relation residual lengths can overlap
and must not be summed. No representation comparison grants journey credit.

Current explicit source membership, current access/mode tags, branch policy and
the existing official-extent admission rules were then reassessed from scratch.
New way IDs do not inherit a previous way's eligibility. Removed way IDs never
enter the refreshed route registry just because another captured record retains
them. Old journey evidence is not automatically transferred or aliased.

All six fixed inventory plans retain their preceding projection origin and both
physical and canonical denominators, verified within 0.00001m. Unsupported mileage
remains in the denominator. All six plan/association pairs were read back through
the consumer and all six official-scope reports were rebuilt. Every one of the
562 original inventory files was checked against the immutable baseline archive
and remains byte-identical; the overlay is stored separately.

## Reproduction and guarded application

The baseline is PR175 commit
`67b02de6ed2ab76e3eefb18fc6278bac748ccd07`. Its evidence archive is
`roadprints-ncn-national-source-reconciliation-2026-10-18.zip`, SHA256
`59affd0f2456ede05f3bc5e7fb6a7e2a4c587d4f1de1e2c36a7047eb42613588`.
The follow-up archive contains the eight newly pinned provider responses and
six reviewed inventory overlays. Keep both archives for exact replay.

```sh
python -m scripts.ncn_trial.membership_review \
  --research-root BASELINE/research --sources BASELINE/sources \
  --prior-catalogues BASELINE/output/confirmed-catalogues \
  --prior-plans BASELINE/output/completion-plans \
  --refreshed-provider REVIEW/refreshed-provider \
  --network BASELINE/official/ncn-reference-candidate.geojson.gz \
  --expected-network-sha256 331023ba1d8ca175471700b1bdb5d7828984d535792c0c54e7a4b465d48cf3f2 \
  --routes NCN:196 NCN:20 NCN:22 NCN:517 NCN:5 NCN:51 \
  --output REVIEW/output
```

Replay makes no network requests. It verifies capture/input byte hashes, rejects
incomplete source/provider hierarchies, requires the pinned prior membership hold
and retains the explicit UK/source classification checks. Each overlay manifest
pins its new plan and associations and the exact `replaces_plan_sha256`. The `read_reviewed_overlay` consumer verifies an independently pinned manifest,
the exact preceding plan bytes, source identity, reference version and unchanged
denominators before reading the replacement. All six overlays passed this guarded
read as well; a different reference version is rejected. Full published-route
readiness remains false. The broader Android consumer and production dataset
channel are still separate integration work.

Validation: 271 NCN tests plus five shared cycling-evidence tests passed. Nine
new regressions cover stale/unlisted/missing provider evidence, checksum and
classification failures, missing nodes/hierarchy, the unchanged ordinary gate,
removed identity exclusion and changed geometry outside the official extent.

## Remaining data review

The six zero-association inventories remain NCN228, NCN626, RCN11, RCN17, RCN71
and RCN82. Another 66 official keys retain the previous source-scope,
partial-geometry or missing-membership dispositions. Extent, access and branch
exceptions remain within supported inventories too. The Android integration
trial remains at eight routes. This review does not promote the full collection
to beta or alter production data.

The machine-readable follow-up is
`docs/data-trials/ncn-membership-review-2026-10-18.json`.

Attribution: © OpenStreetMap contributors, ODbL 1.0. Official extent: Walk Wheel
Cycle Trust, OGL 3.0 with original source attribution retained in the baseline.
