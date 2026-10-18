# NCN725: verified open sections — 18 October 2026

NCN725 now has a bounded catalogue of 158 verified source ways. Its wider Great
North Cycleway source hierarchy is incomplete as a built route: five children
are explicitly proposed and remain excluded. This is open-section support,
not certification of the full Darlington–Blyth ambition or Android activation.

| Measure | Result |
| --- | ---: |
| Official explicitly Open Main features | 48 |
| Official open union | 14,988.691 m |
| Supported source union, combined modes | 14,978.887 m |
| Supported on foot | 14,140.278 m |
| Supported cycling | 14,956.100 m |
| Official open extent outside supported source at 10 m | 934.869 m |
| New supported ways | 158 |
| Shared ways with preceding catalogues | 0 |

The official target and source spans use different linework. Similar overall
lengths do not establish route completeness, and these figures are not a user
completion percentage. Mode totals overlap and must not be added. Disconnections
are preserved, and `completion_binding_ready=false` remains explicit.

## Source hierarchy and missing geometry

Captured root 2530039, version 62, has six children. Existing child 12081768 has
181 ways. Proposed children 12081767, 12082799, 12082800, 12082802 and 12082803
contain 104 distinct ways; no exclusively proposed descendant is admitted.
The first proposed child also has an `alternative` membership role, which grants
no parent-route proxy credit.

All 104 ways missing from the root geometry-provider response are exactly the
104 exclusively proposed ways. The provider represents all 181 existing-child
ways. A supplementary capture of child 12081768 independently has complete,
nonconflicting provider geometry for those 181 ways. Geometry presence alone is
not approval: source node identity, mode access and official extent still gate
each admitted vertex span. Source membership IDs have not changed from the
original full membership profile.

Of 285 source ways, 158 have admitted spans, including 16 partly admitted ways;
127 have no admitted span. The audit has 143 hold records: 104 proposed-membership,
three access/directional-policy and 36 extent records. The latter include the
16 partly admitted ways, so hold records must not be added to the accepted count
as if they were disjoint sets of ways. No registry identity conflicts were found.

## Determinations

- Exclude the five proposed children even where their roads or paths physically
  exist. Their absence from the provider is not a reason to reconstruct them
  into a live NCN route.
- Hold ways 187831769 and 1315301908, both cycleways explicitly tagged
  `access=no`, without a mode-specific permission. Do not infer access from the
  highway classification.
- Hold Market Street East way 4609521. Its combination of general and directional
  access/mode tags requires a reviewed directional policy. Its `foot=yes` and
  `bicycle=yes` tags do not automatically resolve that policy.
- Exclude official SegmentID 55920 from the open target: its `OpenStatus` is null.
  This is 459.842 m on the catalogue's projection, held as unknown status rather
  than declared closed. All 48 explicitly open features remain in the research
  target, including the unmatched 934.869 m.
- Preserve the remaining extent holds, including partial source edges. The
  largest per-feature residuals are SegmentIDs 4038 (143.8 m), 13241 (137.5 m)
  and 13238 (124.2 m). No widening of the admission corridor, inferred connector,
  or substitution of a nearby carriageway is made.

The earlier roughly 380 m diagnostic used all represented source geometry.
The current 934.869 m diagnostic uses only spans that pass the complete access
and extent review. The stricter residual is retained rather than reported as a
new physical closure or used to shrink the route target.

The [publisher's Tyne & Wear map description](https://shop.walkwheelcycletrust.org.uk/products/tyne-wear-cycle-map-34)
identifies NCN725 between Gateshead and Chester-le-Street (June 2021 edition).
The [Trust's July 2025 network bucket list](https://www.walkwheelcycletrust.org.uk/our-blog/national-cycle-network-route-collections/the-national-cycle-network-bucket-list/)
also identifies the Angel of the North with Route 725. These support route
identity; they are not proof that every proposed extension is open. Exact
admission uses the current captured official features and source spans.

## Replay, accounting and validation

The capture has seven source relation/full responses, two provider responses and
49 official features. Capture manifest SHA-256:
`0873c9a41e620dc52f121c62ea88826ec192119bced1cec5d9de05bd85422a7d`.
Official decompressed payload SHA-256:
`5e61d9f5909af21b80bc417b011aa35da58d272aa664dbeb1151ba13cb0b122c`.

The preceding registry is explicitly pinned at 188 catalogues and 43,457 distinct
physical ways, digest
`72f480c0e48ba6c993708aeed8ecbcf85e1aa6c81e9ff967bcea31ca64e25b05`.
The partial-review builder now accepts an explicit checkpoint instead of only
the earlier 183-catalogue checkpoint. It verifies the digest, distinct physical
way count, distinct route-folder count, and absence of already-supported selected
keys. Its defaults preserve the preceding batch's replay contract; it does not
learn a new baseline from changed inputs automatically.

The result is **189 supported route keys and 43,615 distinct ways**. There are
59 other numbered-key review cases: 17 partial geometry, 30 source scope and 12
missing membership. Legacy NCN626 and four regional zero-association holds,
within-route gaps, and the Android completion-binding work remain separate.

Validation: 297 NCN tests plus five cycling tests passed; the strengthened
checkpoint test covers changed digests/counts, duplicate route folders, and a
selected key already present in the registry. Route 1 and association fixtures
built successfully. All 31 preceding-batch output files replay byte for byte
unchanged. The new catalogue passes the strict manifest reader. No APK release,
historical completion transfer or app trial expansion is made.
