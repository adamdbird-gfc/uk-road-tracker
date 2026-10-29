# NCN Stage 1.1 — verified correction and remaining blockers

29 October 2026. PR #239, branch `fix/ncn-stage1-attainability`, targets main
`d9adc5f65065adc82b16996ec8bcc945b81dd311`. Planning PR #238 at `767d81c`
is unchanged and unmerged. Status: **partial correction; Stage 1.1 not complete**.

## Corrected defect

The catalogue compiler treated section-local source offsets as offsets from the
start of the whole source way for open-main plans. Proper sub-sections therefore
credited the wrong portion of a journey. `compile_path_bindings` verifies the
source occurrence against its coordinates, then translates its anchors to the
full-way positions. Explicit vertex indices disambiguate repeated geometry only
when their coordinates agree. Unsupported modes and changed source sections fail.

Candidate: **2,422 source rows across 183 routes; 56,226 binding records**.
No target anchor, eligible mode, geometry, route identity, source node/hash,
denominator, held admission or alternative contract changes. Android runtime,
walking/road matchers, overlays and original journey data are unchanged.

## Attainability before and after

The correction restores evidence alignment; it does not create new source
correspondence. The mathematical target capacity remains unchanged:

| Scope | Before | After | Remaining unbound |
| --- | ---: | ---: | ---: |
| NCN:1 | 92.657884% | 92.657884% | 142,728.708 m |
| RCN:90 | 9.433354% | 9.433354% | 2,657.684 m |
| Canonical unique network | 92.342135% | 92.342135% | 1,335,158.868 m |

All **218 measurable routes remain blocked** for full completion. Summed
route-local deficit is **1,334,036.721 m**; this is a different measure from the
network deficit. No 100% real-world NCN claim is made.

The [receipt](data-trials/ncn-stage1-attainability-2026-10-29.json) enumerates every
route, both modes, combined capacity, candidate pins and unchanged exclusions.
The separate evidence archive retains **385,605 exact unbound interval records**
with route key, target key, low/high metres, target coordinates and nearby admitted
source section IDs. These are machine records, not manual acceptance tasks.

Diagnostic neighbourhood totals (leads, not automatic binding approvals):

- 522,329.865 route-local metres have no admitted source within the existing 10 m bound.
- 799,584.338 m have nearby source but no unique finite correspondence; source/target
  alignment, angle, finite endpoints and ambiguity require specific evidence.
- 12,122.518 m intersect deferred alternative neighbourhoods; existing review
  exclusions remain intact.

RCN:90 has only four admitted source sections against its 2.9 km retained target.
The missing extent cannot safely be credited merely because a road is nearby.
NCN1 combines partial-source offset defects with genuine unbound and deferred
target intervals; repairing its 247 affected source rows does not resolve those gaps.

## Candidate reproduction and compatibility

Candidate DB SHA-256:
`c2d0927abb3bc9e89645d4bf042f8eacc96bfbd08d3de3328c253a7589b2fc1e`.
Canonical reference-content SHA-256:
`936c347468a0f14ed6d2a6d3a31765674e059d48a625c1bce96339032456a57a`.

The [correction artifact](../.github/ncn-reference/stage1-binding-correction.json.gz)
applies only to shipped DB `973ad286…7093`; each original binding row is checked.
Local replay produced identical candidate bytes. Different SQLite writer versions
may produce different file layout: every table's canonical content must still
match, and the disposable CI manifest is regenerated for those actual bytes.
The production manifest is never replaced by this task.

```bash
python -m scripts.ncn_binding_attainability apply \
  --database SHIPPED_DATABASE \
  --patch .github/ncn-reference/stage1-binding-correction.json.gz \
  --output CANDIDATE_DATABASE
python -m scripts.ncn_binding_attainability gate \
  docs/data-trials/ncn-stage1-attainability-2026-10-29.json
```

The completion gate deliberately fails while any eligible route/network interval
remains unassessable. The existing schema is retained: no APK is expected for this
data correction, subject to candidate Android consumer validation. No publication
or real-device acceptance has occurred.

## Validation and interrupted-run handoff

Local results: 322 existing NCN research tests, 5 cycling evidence tests, 5 reference
tests and 7 new focused tests passed. Candidate correction replay matched the
recorded DB hash. Synthetic full-source capacity is test machinery, not real-user
travel. Exact 25/50/75/100 arithmetic and candidate matcher/ledger/achievement
checks are included in the Android validation workflow.

Initial CI run **40875207936** failed before candidate reproduction because the
workflow invoked removed `scripts/prepare_ncn_reference.py`. Its Android steps
were skipped. The workflow now directly validates restored DB and county hashes.
No local validation process remained running when the stalled UI was investigated.
Follow the latest PR check results; until candidate Android checks pass, do not
claim APK compatibility or full validation. No device timings are claimed.

## Safe next action and owner decisions

Review this partial correction independently of remaining scope. Keep all valid
target geometry and existing evidence gates. A focused follow-up can reconcile
the exact interval records against additional exact source/access evidence;
do not repeat historical catalogue recovery or invent nearby-road bindings.

No narrowing/withholding workaround has been implemented. Owner input is required
only if proposing to exclude valid published scope; that is not recommended here.
The recommended course is to retain the scope and its explicit blocked status
while obtaining the missing evidence. Source integration, Firebase publication,
APK distribution and device acceptance remain separate approvals. No merge,
deployment, publication, APK build/distribution or issue closure was performed.
