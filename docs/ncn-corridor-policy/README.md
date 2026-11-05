# NCN corridor review — controlled policy consolidation

5 November 2026 · issue [#265](https://github.com/adamdbird-gfc/uk-road-tracker/issues/265)
· [PR #274](https://github.com/adamdbird-gfc/uk-road-tracker/pull/274)
· base `92ab97489c8c228c3cdaaf35ac9c3a14ddad700d`.

**Delivered:** all twelve owner decisions, a superseding access/journey policy,
five bounded source/target investigations, four Stage 2 dispositions, reproducible
measurements and focused regression evidence. **No new correction or completion
gain approved; no production implementation.** PR remains unmerged for owner review.

| Deliverable | Preserved result |
| --- | --- |
| [Policy decision register](DECISIONS.md) | All twelve cases and six governing principles; explicit earlier-policy discrepancies |
| [Stage 1 report](STAGE1.md) | Chapel Lane and Bradford candidates held for finite target ownership/accounting; East Sussex historical compatibility verified, nearest crossing excluded |
| [Stage 2 register](STAGE2.md) | Derry, Highland, Wellington Parade and Cumbria: finite evidence, gaps and next actions |
| [Impact report](IMPACT.md) | Zero verified new metres; screening figures separate from gains; revision 4 Deal gain not repeated |
| [Regression receipt](VALIDATION.md) | 134 local tests passed; actual CI status retained on the PR |
| [Exact compact evidence](evidence.json) / [measurements](findings.json) | Fifty existing pieces; exact source/target geometry, node IDs, original origins, finite source spans and adjacent target topology |
| [Baseline record](baseline.json) | Main/open PRs, fresh revision 4 live identity, historical receipts and delivery limitations |

The earlier [national viewer](../ncn-viewer/parallel/Roadprints_NCN_Corridor_Review.html)
and [complete evidence register](../ncn-viewer/parallel/REGISTER.md) are preserved
historical evidence. Their private-access-only rejection rationale is superseded
by the owner register; their measurements have not been rewritten.

## Reproduce the focused findings

Python 3.12; `python -m pip install -r requirements-ncn-deal.txt`.
No original archive or online service is needed for these commands:

```sh
python docs/ncn-corridor-policy/analyse.py --output /tmp/corridor-findings.json
cmp /tmp/corridor-findings.json docs/ncn-corridor-policy/findings.json
python -m unittest discover -s docs/ncn-corridor-policy -p 'test_policy_evidence.py' -v
python -m unittest scripts.test_ncn_deal_correspondence scripts.test_ncn_source_compatibility scripts.ncn_trial.test_open_completion scripts.test_ncn_binding_attainability scripts.ncn_trial.test_ordinary_seams scripts.ncn_trial.test_coverage scripts.ncn_trial.test_inventory_completion scripts.ncn_trial.test_extent_composition -v
```

To independently reconstruct the compact fixture, restore the original checkpoint
`libfile_99ba17ccc32c81919dc203e36ac496f5` and the complete prior corridor evidence
`libfile_8fa6e75900dc8191962d677a9e4def61`. These are existing project artifacts,
not requests to rediscover the national network or rebuild a catalogue.
The script verifies the original checkpoint/plan/association/extent pins and the
uncompressed candidate-register SHA-256 before selecting exactly fifty records.

```sh
PYTHONPATH=. python docs/ncn-corridor-policy/export_evidence.py --checkpoint CHECKPOINT_ROOT --candidates EVIDENCE_ROOT/national-candidates.json --output /tmp/corridor-evidence.json
cmp /tmp/corridor-evidence.json docs/ncn-corridor-policy/evidence.json
```

The original discovery tests additionally require `national-candidates.json` or
its gzip under `docs/ncn-viewer/parallel/results/`, as explained in the existing
REGISTER. Restoring it is sufficient; do not run `discover.py` again.

## Delivery and next action

No matcher, journey, Android, achievement, catalogue, published geometry or
entitlement change. No APK/build/distribution, backend/Firebase deployment,
publication/recalculation, issue closure or Canal Explorer implementation.
Installed phone version/live backend not inspected for this offline task;
physical-device acceptance remains unperformed. Revision 4 manifest still matches
its original successful publication; only its 1,079 bytes were fetched.

A new controlled publication is **not justified**. First obtain the compact
revision 4 current path bindings and target/canonical mapping rows for Chapel Lane
(4471919 + connected 169004647) and Bradford's three incident targets. Verify a
finite non-overlapping physical mapping independently, then implement/test a narrow
pin-specific correction in a separate PR. No broad national threshold change.
