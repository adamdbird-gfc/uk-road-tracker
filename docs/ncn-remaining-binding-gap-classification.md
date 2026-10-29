# NCN remaining binding gaps — integration and classification

29 October 2026. Status: **source correction integrated; all 218 measurable routes remain incomplete**.

## Integration receipt

PR #239 was merged with the repository's merge-commit strategy at
`cc088b828982a3ff9399e2bd930978ce02ecba4a`, from final head
`22ff0c320b40327c878278aaed1218f383d48e99`, into main previously at
`d9adc5f65065adc82b16996ec8bcc945b81dd311`.

The merge tree equals the reviewed PR tree. Run [40894305507](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40894305507)
passed reproduction, mapping and 41 Android tests (zero failures/errors/skips) at
`e7a9a65`; the final intervening commit changed only the validation report.
Ten changed files were reviewed: source compiler, read-only audit tool, correction
artifact, tests/fixtures, validation workflow and reports. Android runtime and
published catalogue files are byte-unchanged from the pre-merge main.

Main was unprotected, repository rulesets were empty, and PR reviews had no
blocking submission. The exact head was supplied to the merge API. `[skip ci]`
and `[skip render]` prevented integration from starting automatic CI/deployment.
No catalogue publication, service deployment, APK build/distribution, issue closure
or branch deletion occurred. PR #238 remains open at `767d81c`, unchanged.

## Fresh remaining-gap inventory

This investigation used the integrated code to apply the retained correction to
the exact shipped DB, without rebuilding a catalogue or discovering new routes.
It verified canonical candidate content hash
`936c347468a0f14ed6d2a6d3a31765674e059d48a625c1bce96339032456a57a`, pinned checkpoint
`e837b681…21f0b` and input pins `a706b3ce…d0641` (full hashes in the receipt).
It recalculated foot, cycle and combined capacity and replayed the production
`bind_edges` policy against the original full-precision target vertices for every
route. Replayed retained intervals agreed; no binding fix was made here.

- **218 measurable routes**, all still blocked at 100%.
- **385,605 original unbound intervals**, partitioned into **442,910 cause subintervals**.
- Route-local missing target: **1,334,036.721 m**.
- Canonical unique-network missing target: **1,335,158.868 m**, current assessability **92.342135%**.
- NCN1 remains **92.657884%**; RCN:90 remains **9.433354%**.

The [machine-readable receipt](data-trials/ncn-remaining-gap-classification-2026-10-29.json)
contains every route and representative exact target IDs/positions. The separate
`classified-intervals.jsonl.gz` artifact retains every classified target interval
and geometry. No screen or field-visit audit is required.

## Exclusive route-local cause classification

| Cause key | Affected routes | Subintervals | Missing metres |
| --- | ---: | ---: | ---: |
| deferred_binding_withheld | 49 | 463 | 9,322.324 |
| ambiguous_target_correspondence | 218 | 276,263 | 109,458.931 |
| unadmitted_span_of_known_way | 175 | 5,043 | 101,290.749 |
| direction_gate | 217 | 93,541 | 426,081.836 |
| finite_endpoint_or_alignment | 218 | 44,950 | 69,693.567 |
| no_admitted_source_correspondence | 202 | 22,650 | 618,189.314 |

Affected-route counts overlap; they must not be summed. Interval counts and
metres are an exclusive partition. These are route-local quantities; substituting
them for the unique network deficit is incorrect. Per-network-edge interval unions
are computed separately, including cause-set overlap where shared identities
have different explanations. Approximately 5.4 mm remains a numerical projection
classification residual; it is retained, not treated as completed travel.

Mechanisms and evidence limits:

1. **Deferred binding withheld:** correspondence exists under the current
   uniqueness policy, but the existing explicit deferred-source/target mask
   removes it. This is narrower than the previous nearby-deferred neighbourhood
   estimate. Preserve the 49 routes' reviewed exclusions; geometry near a hold is
   not automatic approval to bind.
2. **Ambiguous target correspondence:** candidate geometry passes existing 10 m
   and 15-degree tests, but multiple target edges compete for the same source
   interval. Distinguish genuine parallel/return paths from ordinary target seams.
3. **Unadmitted span of a known way:** a captured full source way has a geometric
   candidate on a part outside the admitted section. Coordinates are available,
   but membership, scope and access approval for that part are not established by
   the accepted span. This is an unsupported-source-evidence lead, not free credit.
4. **Direction gate:** the finite correspondence appears only when the 15-degree
   test is omitted diagnostically. The production gate was never changed. This
   proves a rejection mechanism, not that all such source travel is eligible or
   that the angle limit should be increased.
5. **Finite endpoint/alignment:** a nearby admitted corridor exists but supplies
   no accepted finite correspondence, even in that direction diagnostic. Endpoint
   support, alignment and small offset/segmentation differences need bounded
   evidence; do not extrapolate a journey or fabricate target connectors.
6. **No admitted source correspondence:** none of those available admitted or
   captured-known-span explanations supports the target piece. Some gaps genuinely
   need further exact source evidence; a nearby road or an existing route number
   does not establish it. No new national extraction was undertaken.

**Eligibility/access and data errors:** no mode-permission conflict was found in
admitted bindings; route-capacity validation enforces those modes. All source
identity/target geometry and production-binding replay assertions passed. No
structural catalogue error was demonstrated. This is not a national claim that
all absent source access is known, or that every official designation is correct.
The eligibility of unadmitted spans and genuine semantic data errors remain
unresolved unless specific retained evidence proves them.

## Prioritised correction opportunities

| Priority | Package | Evidence and dependency |
| --- | --- | --- |
| 1 | Ordinary, source-bracketed target seams | Reusable target-segmentation ambiguity with admitted source evidence. Exclude hairpins, parallel legs, branches and deferred paths; prove a non-overlapping mapping before credit. See the focused brief. |
| 2 | Captured but unadmitted known-way spans | 101.291 km of route-local review leads. Reconcile exact source membership, scope and per-mode access using retained records; admit only specifically justified spans. |
| 3 | Direction and finite-endpoint alignment | 495.775 km combined. Separate publisher generalisation from genuine turns and unsupported endpoints; investigate representative geometries. No global angle/distance relaxation. |
| 4 | Missing source correspondence | 618.189 km. Request targeted exact evidence for identified target spans, grouped by source/cause; do not restart route discovery or discard valid geometry. |
| 5 | Deferred alternatives | 9.322 km. Keep intentional holds until their exact reviewed-path requirements are satisfied. |

Priority 1 is selected for bounded existing-evidence implementation, not because
it is the largest national missing-data class. The largest class is a source
evidence gap, not a demonstrated compiler bug.

The refined seam probe identifies **159,378 potential target intervals / 217 routes**,
with a ceiling of **45,611.814 route-local metres / 45,631.463 unique-network metres**.
It excludes true hairpins/parallel returns and requires source evidence to bracket
an ordinary shared vertex. No new credit was awarded. Even the full ceiling would
complete none of the 218 routes. See the [next development brief](ncn-next-binding-correction-brief.md)
and [seam opportunity receipt](data-trials/ncn-ordinary-seam-opportunities-2026-10-29.json).

## Reproduction and validation

```bash
python -m scripts.ncn_binding_attainability apply --database SHIPPED_DB \
  --patch .github/ncn-reference/stage1-binding-correction.json.gz --output CORRECTED_DB
python -m scripts.classify_ncn_binding_gaps --checkpoint CHECKPOINT_ROOT \
  --database CORRECTED_DB --output FRESH_CLASSIFICATION \
  --integration-commit cc088b828982a3ff9399e2bd930978ce02ecba4a
python -m scripts.probe_ncn_target_seams --checkpoint CHECKPOINT_ROOT \
  --database CORRECTED_DB --output seam-opportunity.json
python -m unittest scripts.ncn_trial.test_gap_classification scripts.test_ncn_binding_attainability -v
```

Fresh local validation: 322 pre-existing research tests, 6 new classifier tests,
7 offset/attainability tests, 5 cycling evidence tests and 5 reference tests passed
(**345 tests**). Full all-route read-only replay/capacity/partition assertions also
passed. Prior 41-test Android evidence was verified, not rerun: this investigation
changes no consumer, production binding policy or data. No phone testing or phone
performance claim. Analysis scripts are not imported by application runtime.

## Decisions and delivery state

No new product decision is required to start the bounded seam-correspondence task.
Do not approve a blanket relaxation or scope reduction. If an exact interval
cannot be supported without either, preserve it and return that concrete choice
to the owner. Stage 1.2/1.3 and mileposts remain outside this work. The new
investigation PR is for review and is not to be merged automatically.
