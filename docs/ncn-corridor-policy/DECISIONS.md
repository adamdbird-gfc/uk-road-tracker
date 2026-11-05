# NCN corridor policy decision register — 5 November 2026

Owner decisions from the 4 November twelve-location review, consolidated under issue
[#265](https://github.com/adamdbird-gfc/uk-road-tracker/issues/265).
Baseline: `92ab97489c8c228c3cdaaf35ac9c3a14ddad700d`; published catalogue: revision 4.
Status: policy recorded; **no new completion approved by this register**.
This register supersedes the conflicting policy interpretations in the frozen
[4 November draft](../ncn-viewer/parallel/POLICY.md). Original observations and
receipts remain historical evidence. Investigation, implementation, integration,
publication and retrospective awards are separate states.

## Governing decisions

1. **Journey recording and network attribution are independent.** Preserve the
   entire validated journey, match its actual roads/paths and retain non-NCN travel
   as ordinary travel. Failure to qualify for NCN never discards valid travel.
2. **Physical travel takes precedence over mapping imperfections.** Minor offsets,
   alignment differences, historical node movement, alternative representations
   and ordinary seams can be corrected only within verified physical correspondence.
3. **Genuine equivalent corridors may qualify.** Establish route identity, geometry,
   connectivity, relevant modes and exact target span. Proximity alone is insufficient.
   Alternatives consume the same canonical target, never extra unique-network distance.
4. **Shared numbered routes receive separate route credit, one network contribution.**
   Verify each membership and the exact shared extent; union canonical intervals.
5. **Access tags do not enforce journey rights.** `access=private` alone rejects
   neither genuine travel nor physical equivalence. Record mode/restriction evidence
   separately; private frontage does not automatically become designated geometry.
6. **Prevent false completion.** No untravelled loops/hairpins, unrelated neighbours,
   unverified shortcuts/alternatives or whole-way grants from partial eligibility.
   Preserve direction, topology and identity guards; exceptions require finite proof.

## Twelve stable case decisions

| ID | Reviewed case | Owner decision | Attribution status / next action |
| --- | --- | --- | --- |
| COR-01 / NAT-01 | Derry NCN92, 30268009 | Investigate candidate cycleway equivalence and finite correspondence | Candidate only; verify finite admission, ordered target anchors and connectors |
| COR-02 / NAT-02 | Highland NCN7, 463641622 | Investigate road membership, connectivity and geometry | Candidate only; distinguish admitted end section from unadmitted road |
| COR-03 / REF-deal | Deal NCN1 designated cycleway, 4471747 | Accept existing 561.888736534 m revision 4 correction | Already published; never count again |
| COR-04 / NAT-03 | Deal Chapel Lane NCN1, 4471919 | Investigate bounded minor-geometry correction | Implemented finite endpoint candidate, unpublished; see [delivery audit](delivery/DELIVERY.md) |
| COR-05 / REF-23385711 | Cumbria NCN6/NCN7, 23385711 | Investigate genuine shared-route membership | OSM NCN7 membership and supplied GPX overlap verified; revision-4 NCN6/NCN7 extent conflict blocks dual attribution; see [delivery audit](delivery/DELIVERY.md) |
| COR-06 / REF-1289002662 | East Sussex RCN90, 1289002662 | Accept historical source-version compatibility principle; verify target correspondence | Same-feature node movement accepted as evidence, not a target grant |
| COR-07 / REF-1050757473 | London Mudlarks Boulevard NCN1 | Retain complete journey; attribute only verified NCN1 section | Existing finite admission retained; non-NCN continuation stays ordinary travel |
| COR-08 / REF-59306268 | Newry Canal Towpath NCN9 | Accept existing matching; no correction | Positive control; no new recovery |
| COR-09 / REF-31252441 | Mid-Wales NCN8 | Accept existing matching; no correction | Positive control; no new recovery |
| COR-10 / REF-44807950 | Wolverhampton NCN81 | Retain exclusion of automatic whole-way equivalence; preserve complete journey | Negative attribution control; no untravelled hairpin credit |
| COR-11 / REF-159311690 | Bradford NCN66, 159311690 | Investigate bounded direction-gate correction | Candidate only; do not widen the national 15-degree threshold |
| COR-12 / NEG-DEAL-PRIVATE | Deal Wellington Parade, 77611523 | Investigate physical equivalence separately; private access is not automatic journey rejection | Equivalence unverified, therefore no new credit; stable historical ID retained |

## Explicit discrepancies with earlier evidence interpretation

- The 4 November draft and viewer say Wellington Parade is rejected because it
  is private. That rationale conflicts with COR-12 and governing decision 5.
  **Superseded:** private access as a decisive test. **Retained:** different source
  identity, only one observed shared node, and no verified ordered target mapping.
  It remains a negative *unverified equivalence* control, not a negative journey control.
- The draft's mandatory public-permission/lawful-connector gates are replaced by
  physical connectivity and explicit mode/route evidence assessment. Restriction
  observations remain evidence; they do not invalidate travel or alone settle equivalence.
- Older reports/AGENTS handovers describing Deal as unpublished predate successful
  revision 4 publication. The current integrity receipt and this register take precedence.
- Source/reference geometry equality establishes feature compatibility, not publisher
  target correspondence. Bradford's unchanged source alone does not approve its target.
- No owner instruction to investigate is an instruction to award completion.

## Accounting and delivery boundaries

The previous screen is 38,608 review pieces / 396.125 km plus 9,261 unresolved
pieces / 217.178 km = 47,869 pieces / 613.303 km **gross route-local screening**.
These are not validated gains or recoverable unique-network distances.

Any proposed binding must pin reference version, source identity/hash/finite
sections, exact target coordinates/intervals, modes, direction, endpoints and
canonical network mapping. Measure the delta against revision 4 coverage by union,
including all other sources/routes. Preserve the denominator and existing positives.
Historical valid evidence may be eligible after a separately approved correction;
no automatic recalculation or entitlement change is authorised here.

No APK/release, Firebase publication, replacement catalogue, live entitlement change,
capture change or Canal Explorer implementation. Canal Explorer remains a separate
future collection idea. PRs remain unmerged for owner review.
