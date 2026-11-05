# Consistent offline NCN geometry diagnostics

30 October 2026. Implemented for review; no operational integration or publication.
Authoritative main: `a87d60d4d5b2c4651c571e7f4316e514154734eb`.
Scope: [bounded task](ncn-consistent-diagnostics-scope-20261030.md).
Decision: [next practical action](ncn-development-decision-20261030.md).

The new additive CLI, `python -m scripts.ncn_geometry_diagnostics --output NEW_FILE`,
reports the nine frozen PR #247 cases. It uses the existing projectors, finite-pair
predicate and ordinary-seam topology helper. It does not call an admission
compiler, matcher, catalogue writer or network service. Output must be a new
file; existing inputs, receipts and catalogues cannot be overwritten by this CLI.
The existing source-compatibility CLI and its output format remain unchanged.

## Three independent meanings

| Report field | Meaning |
| --- | --- |
| `operational_result` | Preserved pinned source presence, bindings, section extents, historical compatibility and restrictions. A record of existing evidence, not a new admission replay |
| `diagnostic_comparison` | Recomputed measurements in native AEQD, native EQ and explicitly derived first-source-vertex local EQ. Hypothetical measurements cannot replace the operational record |
| `attribution_eligibility` | Not assessed; no entitlement, zero newly assessable metres and zero completion credit |

Each case links its immutable input snapshot and SHA-256; source/reference and
target-group geometry hashes, feature/provider IDs, route identity, access/modes,
history and known section restrictions are retained. Each target-edge pair has
source/target geometry hashes, source edge/index, target key where retained,
continuous finite-segment distance, maximum endpoint distance, planar direction
and finite-pair result. Unknown target keys remain null, never fabricated.
The exact Deal target identity remains in the separately pinned Deal fixture.

Each measurement carries projection formula, radius, origin, origin evidence,
origin status and units. Native origins are verified against pinned metadata for
Deal/London/East Sussex; inferred for Newry/Mid-Wales/NCN81; unavailable for
Bradford/Cumbria. Inference does not become verified metadata. Unavailable native
origins produce null measurements and null projection-change results. Their
common local measurements remain separately available and explicitly diagnostic.
The common origin is verified as an explicit coordinate-derived convention, not
as an original operational route origin. No case claims an exact full admission
replay: masks, all competing/excluded features and metadata are incomplete.

## Predicates and interpretation

The existing finite `edge_interval` uses finite along-target extent, the unchanged
10 m perpendicular corridor and 15 degree undirected direction gate. Nonzero
pieces cannot establish whole-edge admission. Source-local stations are in the
reported measurement space; never write them over stored source offsets.
Deal additionally reports continuous outside length against the same preserved
approved-feature union buffered by 10 m, with the existing 1e-5 m numerical outside
length allowance. This is a necessary corridor test, not the complete admission
policy. Other cases report this field as null rather than inventing an approved
union. Source/reference Hausdorff and minimum distances are continuous planar
measurements. Source/reference nearest-segment headings and endpoint direction
are descriptive; no sampled distance is presented as a continuous bound.

All whole-edge, identity, access, topology, deferred-mask, source-section and
mode restrictions remain authoritative. Native AEQD/EQ comparison and the common
local comparison use identical retained WGS84 coordinates. Witness targets that
were reconstructed from pinned metric pairs retain that limitation. Reprojection
can differ from original metric witness coordinates by floating-point noise;
regressions require agreement within 1e-7 m/degrees and identical finite presence.
Raw legacy receipts are separately reproduced byte-for-byte with their original
scripts. This tool does not rewrite them.

`further_investigation.supported` identifies a projection discrepancy, documented
version movement or provenance limitation suitable for a specific case question.
It grants no credit and starts no investigation. It does not establish a general
blocker to ordinary development or a publication-readiness review.

## Reproduction and validation

Use Python 3.11+ and Shapely 2.2.0 for the exact committed output. The validation
receipt records actual Python/GEOS/package versions. Other GEOS builds may need
numeric comparison of full precision output rather than byte equality.

```sh
python -m scripts.ncn_geometry_diagnostics --output /tmp/fresh-ncn-report.json
cmp /tmp/fresh-ncn-report.json docs/data-trials/ncn-consistent-geometry-results-20261030.json
PYTHONPATH=.:tests python -m unittest scripts.test_ncn_geometry_diagnostics scripts.test_ncn_source_compatibility scripts.test_ncn_binding_attainability scripts.ncn_trial.test_open_completion scripts.ncn_trial.test_ordinary_seams scripts.ncn_trial.test_network_geometry scripts.ncn_trial.test_saved_paths scripts.ncn_trial.test_cycling_saved_paths -q
PYTHONPATH=.:tests python -m unittest discover -s tests -p 'test_*path_evidence.py' -q
```

15 new tests cover all nine cases, missing/inferred origins, rejected origin
claims, projection changes without decision changes, pinned metric equivalence,
input immutability, repeatable output, checksum failure and synthetic checks at
and either side of 10 m/15 degrees. Synthetic boundaries are tests of the existing
predicate; they are never represented as surveyed locations or eligible recovery.
The previous 95 focused and 15 walking/cycling evidence tests remain green:
125 tests total, zero failures/errors/skips. The original audit still passes
158 geometric orientation/partition invariants. Its full results, cases and
Deal-only result, original Deal result and compatibility controls/results all
reproduce byte-for-byte. See the [receipt](data-trials/ncn-consistent-geometry-receipt-20261030.json).

PR #247 was open, non-draft, clean/mergeable, targeted main and matched exact head
`efca603f73f6877fb64d3c8bf37796ee31f2635e`. Its seven additions were confined to
research/docs. Reviews, inline threads, checks, statuses and active rulesets were
empty; main reported unprotected with no required checks. The expected-head merge
used `[skip ci] [skip render]`. The resulting complete main tree
`c7e0ba326b82b72c2e4f05cff1d2f2991e5d94c3` equals the validated PR head tree.
Both merge parents were verified and the original research branch remains at its
validated head. Early scope commit `f90ac1d5ace510d71f13f1410055c2a8795335f4` and
implementation checkpoint `5246e847db37f417ea74ec4d38ddd14c71a3a61c` were independently
read back before completing delivery. The final PR targets main and remains open
and unmerged. No issues were closed. No fresh CI or Android/device suite is
claimed. Android source baseline remains 0.25.220 (266); installed APK, live
backend and active Firebase pins were not queried and remain unknown. No APK,
distribution, deployment, publication, national assessment, candidate catalogue
or journey-data changes occurred. All existing tracked files remain byte-identical
to authoritative main; only new diagnostic implementation/tests/docs/results are
added. Integration and any future publication require their separate authorisation.
