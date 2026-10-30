# NCN ordinary target seam development checkpoint

Date: 30 October 2026
Baseline: main at cc088b828982a3ff9399e2bd930978ce02ecba4a (validated PR #239 integration).
Development branch: fix/ncn-ordinary-seam-correction-20261030.
Android source baseline: 0.25.220 (266); installed device version not queried.

Scope: source-supported finite, non-overlapping attribution across adjacent ordinary NCN target sections. Preserve route identity, target geometry, source provenance, travel modes and existing walking/cycling matchers. Do not relax direction gates. Exclude hairpins, branches, parallel paths, deferred bindings and ambiguous correspondence.

Pinned research: PR #240 at 21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7; PR #241 at 24428cb4ad35e563113d7a86f7edc13652dc2844. Diagnostic ceilings and previous reported 44.369 km route-local / 44.388 km unique-network recovery across 217 routes are unverified potential, not delivered coverage.

Recovery: bounded inspection of accessible workspace Git repositories, local seam branches/commit subjects, seam-named files under /workspace and /tmp, and remote seam branch search found no original correction implementation. Existing accessible checkouts are older. Original implementation is inaccessible in this session; reconstruct only the focused correction from pinned evidence after independent read-back of this checkpoint.

Delivery gates: run focused positive/negative tests, immediately commit implementation and tests, independently verify the remote commit, then stop. National assessment, Android consumer tests and CI validation remain for a separate task. No national assessment, full Android CI, merge, deployment, Firebase publication, APK build or diagnostic-branch changes are authorised here.

## Implementation checkpoint

Status: implemented locally; awaiting separate national and Android validation. No national recovery figure is claimed.

The opt-in `ordinary_seams=True` policy partitions a two-candidate ambiguous window at the interior source projection of its degree-two shared vertex. Both candidates retain the existing finite 10 m / 15-degree gates; their outward directions must oppose within 30 degrees. Existing uniquely supported spans remain unchanged. The incoming half ends at the target vertex and the outgoing half starts there; source intervals never overlap. Deferred source/target masks remain authoritative after compilation. Target coordinates, IDs, denominators, admitted path rows and modes are preserved.

Legacy policy and default compilation/readers remain unchanged. New plans explicitly use `bracketed_ordinary_target_seams_v2`, produce new content-derived reference versions and require reader opt-in. `completion_checkpoint --ordinary-seams` is the explicit compiler entrypoint for the later validation task; it has not been run nationally. Preserved numbered-alternative inventories retain their original consumer and contract.

Fixtures are copied byte-for-byte from PR #240 commit 21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7, including attribution. Tests reproduce the retained NCN9, NCN8 and NCN93 source-edge witnesses and the NCN81 hairpin negative. These are public diagnostic edge extracts, not complete original source sections or private journeys; the national task must verify complete immutable source hashes, section/full-way offsets and every changed binding against the pinned inputs. Existing source-offset, identity and mode regressions pass. No changes were made to walking/cycling journey matchers or Android consumers.

Local validation on 30 October 2026, Python 3.12.14 / Shapely 2.2.0:

- `PYTHONPATH=scripts:. python -m unittest discover -s scripts/ncn_trial -t . -p 'test_*.py' -q`: 333 passed, including 11 new seam tests.
- `PYTHONPATH=scripts:. python -m unittest scripts.test_ncn_binding_attainability -q`: 7 passed.
- `PYTHONPATH=scripts:. python -m unittest discover -s tests -p 'test_cycling_path_evidence.py' -q`: 5 passed.
- `PYTHONPATH=scripts:. python scripts/test_ncn_reference.py -q`: 5 passed.
- Total: 350 passed; `git diff --check` passed. No national assessment or Android tests run. A Shapely deprecation warning in existing scope_review.py did not affect results.

Comparison with inaccessible original correction: this is a reconstruction with explicit policy opt-in and unchanged legacy readers/defaults. Exact code equivalence and the previously reported 44.369/44.388 km gains cannot be established. No candidate catalogue or national correction artifact was generated in this session.

Next task: verify complete immutable inputs and full source sections, compile the versioned candidate, prove preservation of all prior supported intervals and deferred masks, produce an additive existing-schema correction artifact, independently reproduce it, validate all mappings and recalculate 218 route capacities/unique-network union. Then run candidate-selected Android NCN/celebration tests with hash/version assertions and CI. Neither the national candidate nor consumer compatibility is validated by these focused tests. Checkpoint/compiler policy opt-in must be threaded explicitly through candidate preparation rather than weakening legacy pinned consumers. Leave this development branch unmerged; no publication, deployment, APK or tester distribution occurred. Live backend, active Firebase and installed APK provenance were not queried and remain unverified by this task.
