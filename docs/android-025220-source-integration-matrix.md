# Android 0.25.220 source comparison and integration matrix

Date: 28 October 2026. **Read-only source analysis; this file is documentation only.** Scope: open PR inventory prior to this matrix, Android build source `67e74e7d9e9873552ae98b23397cd9e265fc3517` (private 0.25.220/code266), current `main`, and PR #225 head `c5fdaa5dc9e681493ed40fca48c497c28aa415e1`. The integration branch started from `main`; this document does **not** merge source.

## Comparison method and limitations

GitHub compare of each PR head to the Android build SHA determines **commit ancestry**: `ahead` with zero `behind` means the PR head is an ancestor of the build. `diverged` means both have commits not present on the other branch; this does **not** prove unique functionality. PR changed-file inventories provide source-domain classification, but PR diffs against stacked bases are not equivalent to integration diffs against `main`. Direct blob SHA comparisons establish actual file differences at the two refs. GitHub compare file lists may be capped at 300; no complete automated merge or full semantic code review has been performed. No local checkout, merge simulation, CI or phone APK verification was performed.

## Main comparison

- Build source versus `main`: 657 commits on Android side, 21 on `main` side at the comparison time; diverged. Counts reflect stacked cumulative history.
- PR #225 head is one commit after packaged build, documentation-only: `docs/development-handover.md`, `docs/pedestrian-visual-correction.md`.
- The `main`-only side includes workflow/reference-cache changes, backend matching changes and tests, and merged governance `AGENTS.md` / `docs/DEVELOPMENT_GOVERNANCE.md`. It must be preserved.
- Direct file blob comparison (APK source / `main`): `api.py` 1836196b / 936f43b9; `pedestrian_matching.py` b44a7eed / 8c464d09; `database.py` ffcf43be / fe0d941c; `android-prototype/app/build.gradle.kts` d78fe280 / c6a8d502; `docs/development-handover.md` 2389c52d / d7b3dd78. All five **differ**; no safe automatic overwrite assumption.
- PR #225 changed-file inventory includes Android source in `android-prototype/app/`, backend matcher and test files, CI workflows and documentation. This is **not** an Android-only cherry-pick.
- Production backend PR #226 is recorded as separately merged/deployed; later matching fixes also appear in `main`. Live backend identity remains to be verified independently.

## PR-by-PR matrix

Legend: **Ancestor** = PR head is a verified ancestor of packaged Android source (its commits are included); **Diverged** = independent commits exist, so no closure recommendation without content comparison. **Separate** = distinct NCN data line. These are integration dispositions, **not approvals to close**.

| PR | Workstream | Comparison to Android build | Proposed disposition |
|---|---|---|---|
| #225 | Latest Android pedestrian visual/cache | Head one docs-only commit after build | Android baseline candidate; retain docs |
| #223 | Android walking/editor | Ancestor (+4 commits from PR head to build) | Included in Android source; reconcile with main |
| #221 | Android navigation/performance | Ancestor (+11) | Included; reconcile |
| #220 | Android notification lifecycle | Ancestor (+36) | Included; reconcile |
| #219 | Android contextual ferry actions | Ancestor (+43) | Included; reconcile |
| #218 | Android journey intelligence | Ancestor (+48) | Included; reconcile |
| #215 | Android Disneyland integrity | Ancestor (+70) | Included; reconcile |
| #213 | Android multimodal ferry | Ancestor (+76) | Included; reconcile |
| #212 | Android map contributions | Ancestor (+84) | Included; reconcile |
| #211 | Android history/startup | Ancestor (+88) | Included; reconcile |
| #210 | Android NCN responsiveness | Ancestor (+93) | Included; reconcile |
| #209 | Android NCN UI | Ancestor (+101) | Included; reconcile |
| #207 | Android NCN Firebase beta | Ancestor (+107) | Included; reconcile |
| #197 | Place visit reliability | Ancestor (+252) | Included in Android lineage |
| #196 | Mode transition evidence | Ancestor (+255) | Included in Android lineage |
| #182 | Walking arrival duration | Ancestor (+262) | Included in Android lineage |
| #174 | Route continuity and parked captures | Ancestor (+264) | Included in Android lineage |
| #171 | Progress road detail | Ancestor (+263) | Included in Android lineage |
| #168 | Saved-path provenance | Ancestor (+678) | Included in build history; inspect overlap with backend |
| #172 | NCN Android integration | Diverged (+263/-3) | Three independent commits on PR branch; review files |
| #163 | Place-aware visits | Diverged (+268/-1) | One independent commit; inspect before disposition |
| #187 | NCN completion checkpoint | Separate, diverged (+681/-40) | Preserve data workstream |
| #186 | NCN open completion | Separate, diverged (+681/-39) | Preserve |
| #185 | NCN remainder review | Separate, diverged (+681/-38) | Preserve |
| #184 | NCN partial geometry | Separate, diverged (+681/-37) | Preserve |
| #183 | NCN725 open sections | Separate, diverged (+681/-36) | Preserve |
| #181 | NCN99 scope | Separate, diverged (+681/-35) | Preserve |
| #180 | NCN partial provider review | Separate, diverged (+681/-34) | Preserve |
| #179 | NCN601 open spans | Separate, diverged (+681/-33) | Preserve |
| #178 | NCN alternative review | Separate, diverged (+681/-31) | Preserve |
| #177 | NCN inventory determination | Separate, diverged (+681/-30) | Preserve |
| #176 | NCN membership review | Separate, diverged (+681/-29) | Preserve |
| #175 | NCN reconciliation | Separate, diverged (+681/-28) | Preserve |
| #165 | NCN trial foundation | Separate, diverged (+681/-27) | Preserve; assess dependencies |
| #8 | Pedestrian 429 recovery | Diverged (+749/-4) | Compare to current main matcher before disposition |
| #7 | Primary screen cache | Diverged (+477/-1) | Inspect independent commit |
| #6 | Short walk capture | Ancestor (+482) | Included in Android history |
| #1 | Batch matching coordinator | Ancestor (+764) | Included in Android history |

Note: +N/-M counts above are from PR head to Android build SHA, not counts of unique files or features. #231 is already merged governance; #232 is this documentation-only integration PR and excluded from historical disposition.

## File-domain integration matrix

| Domain / paths | Evidence | Integration policy | Verification |
|---|---|---|---|
| Android `android-prototype/app/**` | Build config differs between packaged SHA and main; PRs #207–225 touch capture, maps, cache, editor, releases | Treat packaged SHA as functional reference; transplant **reviewed** Android changes onto integration branch; do not copy entire repository | Android compile, lint, 810-test baseline comparison, instrumented capture/editor/map checks, pinned NCN/reference checks |
| Backend `api.py`, `pedestrian_matching.py`, `database.py`, `road_distance_evidence.py`, `walking_timing.py` | At least first three differ by exact blob SHA; main has later matching changes | Preserve current main as baseline; selectively reconcile Android client API contracts and any missing backend behaviour | Backend/PostgreSQL tests, walking work-budget/503, Dover drive distance evidence, selection7 continuity, response contract |
| CI/workflows `.github/workflows/**` | Both histories modify Android packaging, reference cache and regression workflows | Preserve main's approved release/cache protections; port only demonstrably missing Android checks | Offline reference verification, pinned assets, signing, no automatic distribution |
| NCN catalogue `docs/data-trials/**`, `scripts/ncn_trial/**` | PR #165 and #175–187 diverged; #187 changes 6 files incl. checkpoint scripts/data | **Separate integration**, do not bulk-merge into Android baseline | Catalogue counts, identity/geometry holds, provenance, non-destructive data review |
| Docs `docs/**`, `AGENTS.md` | Both histories change handover; PR #225 docs-only final commit; governance is main-only | Retain governance from main; merge historical handover notes without replacing current facts | Source SHA and release notes accurately reflect delivered state |
| Production/reference data | Current main includes dataset workflows and deployed backend history | No production mutations or Firebase republish as part of source reconciliation | Deployment inventory and checksum comparison before any proposed release |

## Findings and gating decisions

1. **Confirmed:** the bulk of the active Android PR chain is commit-contained in the recorded 0.25.220 build source. Do not individually merge it into `main`.
2. **Confirmed:** the NCN catalogue branch is genuinely separate; its unique commits must be preserved.
3. **Confirmed:** PR #172, #163, #7 and #8 have commits outside the packaged Android ancestry. They require content-level review; divergent does not mean missing functionality.
4. **Confirmed:** shared backend files differ on both sides, so a full branch replacement or blind merge is unsafe.
5. **Not yet proven:** current phone APK binary matches recorded artifact; complete conflict-free Android integration; backend live deployment SHA; equivalence of individual diverged PR content; green integrated CI.
6. **Proposed next gated work:** pin both SHA baselines, generate complete Android path diff and backend contract inventory, review four divergent legacy PRs, construct integration commits on this branch, run test matrix, present exact diff and risk/rollback to owner. **Requires explicit approval before code integration.**
7. No PR closure, branch deletion, APK build/distribution, backend deployment, production data change or `main` merge authorised by this matrix.
