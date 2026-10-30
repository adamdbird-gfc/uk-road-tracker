# NCN ordinary target seam development checkpoint

Date: 30 October 2026
Baseline: main at cc088b828982a3ff9399e2bd930978ce02ecba4a (validated PR #239 integration).
Development branch: fix/ncn-ordinary-seam-correction-20261030.
Android source baseline: 0.25.220 (266); installed device version not queried.

Scope: source-supported finite, non-overlapping attribution across adjacent ordinary NCN target sections. Preserve route identity, target geometry, source provenance, travel modes and existing walking/cycling matchers. Do not relax direction gates. Exclude hairpins, branches, parallel paths, deferred bindings and ambiguous correspondence.

Pinned research: PR #240 at 21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7; PR #241 at 24428cb4ad35e563113d7a86f7edc13652dc2844. Diagnostic ceilings and previous reported 44.369 km route-local / 44.388 km unique-network recovery across 217 routes are unverified potential, not delivered coverage.

Recovery: bounded inspection of accessible workspace Git repositories, local seam branches/commit subjects, seam-named files under /workspace and /tmp, and remote seam branch search found no original correction implementation. Existing accessible checkouts are older. Original implementation is inaccessible in this session; reconstruct only the focused correction from pinned evidence after independent read-back of this checkpoint.

Delivery gates: run focused positive/negative tests, immediately commit implementation and tests, independently verify the remote commit, then stop. National assessment, Android consumer tests and CI validation remain for a separate task. No national assessment, full Android CI, merge, deployment, Firebase publication, APK build or diagnostic-branch changes are authorised here.
