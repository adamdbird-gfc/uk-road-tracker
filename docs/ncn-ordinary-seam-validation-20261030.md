# NCN Stage 1.1 ordinary seam independent validation

30 October 2026. Candidate only; no integration, publication, deployment or APK.
Implementation: `c0566c6421a2d2b155f738e0411a526143bc385c`, unchanged from the preserved checkpoint. Validated source main baseline: `cc088b828982a3ff9399e2bd930978ce02ecba4a`. Android source: 0.25.220 (266). PR #240 evidence is pinned at `21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7`; PR #241 brief at `24428cb4ad35e563113d7a86f7edc13652dc2844`.

## National result

Synthetic assessable capacity, not a person's travelled distance. Distances below are kilometres; exact metre values and all 218 routes are in [the receipt](data-trials/ncn-ordinary-seam-validation-2026-10-30.json).

| Measure | Integrated baseline | Seam candidate | Recovery |
|---|---:|---:|---:|
| Route-local assessable capacity | 16,095.985 | 16,141.800 | 45.815 |
| Unique-network assessable capacity | 16,099.974 | 16,145.808 | 45.834 |
| Route-local remaining deficit | 1,334.037 | 1,288.222 | |
| Unique-network remaining deficit | 1,335.159 | 1,289.324 | |
| Unique-network assessability | 92.342135% | 92.605021% | +0.262886 percentage points |

217 routes gain capacity; zero newly attainable routes, zero fully attainable measurable routes. All 218 legacy open-main plans reproduce exactly when the seam policy is disabled. All 219 supported identities, 34 holds, and the separate unmeasurable NCN228 alternative remain unchanged. Stage 1.1 remains partial.

## Integrity and independent proof

The exact six implementation blobs and Git tree were verified after fetching the preserved commit. The [source audit](data-trials/ncn-ordinary-seam-source-integrity-2026-10-30.json) verifies all 1,147 input pins, 219 route manifests and 62,487 route/source rows against the original shipped catalogue. Full coordinates, node identities and modes agree. The complete NCN9, NCN8, NCN93 and NCN81 witness source hashes and edge occurrences were checked against those complete inputs.

The candidate preserves every existing supported binding and every non-binding catalogue table. Independent geometric validation checked all **159,711 additions**, without calling the seam correspondence helper: finite source/target anchors, two active candidates, complete-graph degree two, opposing outward directions, an interior source-supported shared vertex, and no source endpoint extrapolation. Maximum anchor offset: **9.996631938 m**; maximum source/target angle: **14.999283922 degrees**. Affine interpolation between bounded endpoints keeps the complete mapping within the corridor. All 49 deferred-mask routes pass; no modes, source geometry, route identity, target geometry, denominator or network mapping change. Source interval ownership checks pass. The actual existing-schema candidate passes the full target-to-network mapping suite.

## Comparison with earlier figures and diagnostic ceiling

Earlier reported recovery was approximately 44.369 km route-local / 44.388 km unique-network. This commit produces **45.814697795 km / 45.834461832 km**, respectively about **1.445698 km / 1.446462 km more**. The original implementation/assessment artifact is inaccessible; an exact code-level explanation of that difference cannot be established. No equivalence to that unavailable implementation is claimed.

The PR #240 diagnostic projection ceiling was 45.611814215 km / 45.631463349 km. The new measurement exceeds it by **202.883580 m / 202.998482 m**. That probe projected the rejected source windows onto each straight target independently. The implemented policy maps the proven interior source station to the shared target vertex and partitions ownership there; its target anchor spans can extend beyond the probe's original perpendicular-projection spans while remaining inside the same finite 10 m / 15-degree gates. NCN1 is an explicit counterexample: the probe estimated 4,004.221369 m, while this candidate recovers 4,020.893325 m. Thus the historical diagnostic projection quantity is not a strict bound for this vertex-anchor mapping. This discrepancy is retained for review, not hidden or forced to match. The separate geometric audit passes every added binding; gates and denominators were not widened to obtain these figures.

## Reproduction and local tests

- Python 3.12.14, pinned Shapely 2.1.2. 333 NCN research/compiler tests (including 11 seam regressions), 7 offset/attainability, 5 cycling-evidence and 5 reference tests: **350 passed, zero failures/errors/skips**.
- Candidate national asset/mapping suite: **1 passed, zero failures/errors/skips**.
- Exact compact-delta reproduction: **all reference table values match** canonical SHA-256 `cf25e006e79782c9458895bf4b68179b7829fe3b6e7d5ded96be0ccd07929dc0`.
- Original assessment candidate SHA-256: `1b9f419bf82edcef1d1ae8d3a2cf475d2cb0072e9ce4aea890f00f382a0bfa38`. The two-step SQLite replay can have a different byte layout; its complete canonical content is checked before selecting it for tests.

From a checkout and the original archives named in [the provenance index](ncn-historical-reconciliation.md):

```sh
python -m pip install -r requirements-ncn-trial.txt
python -m scripts.verify_ncn_checkpoint_provenance CHECKPOINT_ROOT SHIPPED_DATABASE
python -m scripts.validate_ncn_ordinary_seams --checkpoint CHECKPOINT_ROOT --database SHIPPED_DATABASE --output FRESH_ASSESSMENT_DIRECTORY
python -m scripts.audit_ncn_ordinary_seam_bindings --checkpoint CHECKPOINT_ROOT --integrated FRESH_ASSESSMENT_DIRECTORY/integrated.db --candidate FRESH_ASSESSMENT_DIRECTORY/ncn-national.db --output FRESH_AUDIT_JSON
python -m scripts.reproduce_ncn_ordinary_seams --database SHIPPED_DATABASE --output FRESH_CANDIDATE_DATABASE
```

The compact binary insertion delta is split into **96 checksummed base64 chunks**, about 87 KB per full chunk. Its index pins the existing source-offset artifact, each chunk, reconstructed compressed bytes and complete candidate content. It inserts only new seam bindings, preserving the integrated binding sequence and verifying every prior binding hash. This replaces a 38 MB cumulative artifact for portable replay. No national rebuild is needed for GitHub CI reproduction. The larger original cumulative artifact's hash remains in the receipt for provenance; it is unnecessary for replay from the committed compact delta.

## Android and CI status at this checkpoint

The local workspace has no Android SDK/JDK/Gradle build toolchain. Android compatibility is **pending** the candidate-specific GitHub Actions job. The workflow restores and verifies the immutable shipped pins, reconstructs the candidate, verifies every mapping, selects the candidate only in its disposable checkout, and runs all existing NCN/celebration tests plus two new seam consumer tests, requiring at least 43 tests and zero skips. New tests check source-identified walking/running/cycling across the real NCN9/NCN8 edges, driving exclusion, partial source halves, repeated evidence, edits and deletion. Existing suites retain persistence, thresholds, network deduplication and cache coverage. No production Android Java was changed. No APK conclusion is final until the consumer suite passes.

The source-audit/tooling checkpoint `79591055a8869ccdb6ddc66770c4559ef502ff50` was independently read back before candidate preservation. Candidate receipt/artifacts must likewise be committed and verified before opening the PR and starting CI. Installed APK, live backend and active Firebase versions were not queried and are not inferred from source. No merge, deployment, Firebase publication, APK packaging or tester distribution is authorised by this report.
