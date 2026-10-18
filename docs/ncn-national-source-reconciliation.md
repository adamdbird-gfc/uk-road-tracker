# National NCN source reconciliation — 18 October 2026

Follow-up: [the six membership holds have been reconciled](ncn-membership-review.md).
The figures below preserve the initial national assessment checkpoint.

This extends the captured node-backed source assessment beyond the original 48
close-correspondence candidates. The same conservative source-span checks apply
to the offset-sensitive and differing-extent queues. Selecting a review queue
never approves its missing geometry, source classification or alternative paths.

## Assessed source scope

| Measure | Previous checkpoint | This pass |
| --- | ---: | ---: |
| Numbered keys assessed against node-backed sources | 48 | 187 |
| Keys with supported-section catalogues | 47 | 175 |
| Distinct supported source ways | 3,999 | 37,793 |
| Captured full-source relation snapshots | 53 | 308 |

All original 48 catalogue admission counts are preserved. These figures describe
source sections available for assessment, not completed user journeys or fully
certified published routes. The Android internal trial remains at eight routes.

Six keys have changed current membership and are held: NCN5, 20, 22, 51, 196 and
517. Exact added/removed way IDs and source versions are retained in the audit.
Six further inventories currently have no admitted sections: NCN228 and 626;
RCN11, 17, 71 and 82. Their unsupported mileage remains in the inventory totals.

The other 66 official numbered keys retain their national review dispositions:
30 need source-scope review, 23 have partial source geometry and 13 have no
suitable source membership. None is silently removed from the national inventory.
There are also unresolved sections and branches inside the assessed 187 keys.

The expanded catalogues identify 44 source ways shared between route keys. These
are explicit source identities, not proximity matches. They must not inflate
collection-wide physical distance when the broader Android adapter is connected.

## Evidence and accounting contract

- Every selected numbered key needs complete provider geometry, complete source
  member lists and an explicit UK metadata anchor from the pinned national audit.
- Source capture retains request URLs, observation times, independent byte hashes
  and the exact physical-report/membership-profile input hashes. Existing pinned
  snapshots retain their original observation dates; this is not a claim that
  every source was fetched on 18 October.
- The capture is resumable with two concurrent requests and a 20 MB per-response
  limit. A partial capture or incomplete response manifest cannot enter assessment.
- Current membership and geometry must still agree with the assessed source.
  Membership changes hold the route and retain exact added/removed way IDs.
- The existing 10m official-extent admission check is unchanged. A diagnostic
  improvement at 25m does not admit another source edge. Nearby unlisted roads
  never enter the catalogue.
- Current mode policy remains separate for walking and cycling. Conditional and
  directional restrictions, unresolved branches and differing geometry remain held.
- Fixed inventory plans retain unsupported source mileage in their denominators.
  They are captured source inventories, not certified full published routes.
- Tagged simple two-gate roundabouts retain their existing either-arc policy.
  Unreviewed general loops and alternatives are not inferred from proximity.
  The separately reviewed NCN177 overlay is not replaced by this batch.
- Route totals overlap. No national completion distance is obtained by adding
  them; the Android physical/logical adapter remains a separate integration step.
- Stale successful batch markers are removed before rebuilds. A held route loses
  earlier generated association files, preventing an old catalogue from appearing
  as a new success.

## Validation checkpoint

All 187 fixed source-inventory plans were read back through the consumer with
independently pinned plan/association hashes. All original 48 physical totals,
canonical totals and unsupported lengths remain unchanged within 0.00001m.
There are still ten fully bound source inventories; this is not full published
route certification. The plans contain 86 route-specific two-gate group
occurrences; route groups can share physical ways and are not national mileage.

The local checks passed: 262 NCN tests, five shared cycling-evidence tests and
the existing Route1/portable-association fixture builds. Ten new regressions
exercise explicit queue selection, nested capture, resume, input integrity,
changed membership evidence, incomplete hierarchy rejection and unchanged
geometry/alternative admission rules.

All 187 official-scope reports also completed. 167 keys have official open-main
linework outside the captured source's 10m diagnostic corridor by more than 1m;
177 have source geometry outside the qualifying official corridor by more than
1m. These are overlapping review counts, not distinct missing routes, travelled
distance or proof that offset geometry describes another path. The 3/10/25m
series and exact exception linework remain available for review. No diagnostic
corridor grants journey credit.

## Remaining data boundary

1. Reassess the six changed source memberships against coherent updated provider
   geometry and current member inventories. Added ways are not admitted just
   because an existing route number is retained.
2. Work through the pinned scope/partial-geometry/missing-membership queues for
   the other 66 keys, retaining explicit source classification and UK identity.
3. Review the located extent, access and branch exceptions within the assessed
   187 keys. The per-route compressed exception GeoJSON retains official feature
   identities and source way IDs where applicable.
4. Carry the explicit shared-way and route-specific alternative contracts into
   the broader Android physical/logical adapter. The first eight-route compiler
   continues to reject these cases; this data pass does not bypass that gate.

The machine-readable checkpoint is
`docs/data-trials/ncn-national-source-reconciliation-2026-10-18.json`. The complete
pinned source and generated evidence is retained in
`roadprints-ncn-national-source-reconciliation-2026-10-18.zip`.

## Reproduction

Use the pinned national assessment inputs, the source capture and generated
catalogues supplied with this checkpoint. The official network SHA256 is
`331023ba1d8ca175471700b1bdb5d7828984d535792c0c54e7a4b465d48cf3f2`.

```sh
python -m scripts.ncn_trial.source_capture --research-root RESEARCH \
  --output OUTPUT/sources --reuse PREVIOUS/sources --states \
  close_bidirectional_correspondence_candidate \
  offset_sensitive_correspondence_review partial_or_different_extent_review
python -m scripts.ncn_trial.confirmed_paths --research-root RESEARCH \
  --sources OUTPUT/sources --network OFFICIAL.geojson.gz \
  --expected-network-sha256 OFFICIAL_SHA256 --output OUTPUT/catalogues
python -m scripts.ncn_trial.inventory_completion --research-root RESEARCH \
  --sources OUTPUT/sources --catalogues OUTPUT/catalogues \
  --output OUTPUT/completion-plans
python -m scripts.ncn_trial.scope_review --plans OUTPUT/completion-plans \
  --network OFFICIAL.geojson.gz --expected-network-sha256 OFFICIAL_SHA256 \
  --output OUTPUT/scope-review
```

`PREVIOUS/sources` is optional. Reusing it preserves original pinned observations;
checksums are verified before any bytes are reused. Use fresh output directories
when changing the selected set of route keys. No private journey, APK, Firebase
release, production dataset publication or database change is part of this work.

Geometry and membership: © OpenStreetMap contributors, ODbL 1.0. Official extent:
Walk Wheel Cycle Trust, OGL 3.0 with the original snapshot attribution retained.
