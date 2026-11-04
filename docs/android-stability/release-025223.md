# Android 0.25.223 (269) — controlled on-foot release

Owner-authorised integration/release on 4 November 2026. PR #262 final head `78ef06f36e41e36226ff415f878aa99449d8d75a` merged as `efa9d397b621603c8d911a638b24e0ded3b49319`. The verified main/base at release preparation is that merge. New work must start from latest validated main, including this correction.

CI 41836944570 passed at `f883adbc0ae13ef5273126cc1a77bc1a9e1a737f`: 848 runnable Android tests, 852 cases/120 suites, zero failures/errors, four established opt-in NCN candidate skips; five Python recovery checks. GitHub comparison confirms final PR head adds only the report and JSON receipt, preserving runtime/test/fixture/workflow bytes. Main had no newer changes and GitHub reported mergeable; no required status checks were configured. Existing validation receipt remains at on-foot-fragmentation-validation-20261104.json.

One purpose-built signed APK request follows established private-stability-apk.yml with only version/request/base/totals and required continuity-suite updates. Packaging compile/lint/signature/identity/reference gates remain; expected 851 cases excludes only AchievementArchiveVolumeTest (already passed at #262 CI), with four established skips. Certificate SHA-256 must equal `5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`; application ID remains com.roadprints.capture. No Firebase upload or tester notifications. Packaging source/run/artifact/digest and final main will be recorded in the completion receipt after verification.

## Physical acceptance — outstanding

Install over 0.25.222 (268) with capture idle; do not uninstall or clear storage. Confirm 0.25.223 (269), archive count, old journeys/manual corrections and achievements. Test continuous run/walk pace changes with brief STILL noise, genuine sustained arrival, walk/train/walk station transitions, and driving with traffic stops. Export diagnostics for failures. No physical Pixel 10/Android 17 acceptance is claimed.

## Limitations and outstanding capture issues

Historical six-part recordings are not automatically joined; #253 retains historical split/join editor scope. Driving #255 still needs field/context evidence and mode-edit stall investigation; disabled/unavailable map context can require manual confirmation. Achievement #256 retains physical acceptance and large-history CPU/attribution risks; no universal heap guarantee. GPS-gap and excess-match evidence rejection remain protective; precise previous crash was not reproduced. Original journeys/GPS/achievements/schema/reference pins remain unchanged. Installed version is owner-reported 268; backend/Firebase live versions are unobserved and unchanged by this task.

Retain previous APK. Android ordinary downgrade is blocked; any rollback must be a forward fix with higher version code, preserving data. No backend/Firebase deployment or production mutation is authorised/performed here.

## Completed packaging receipt

Run 41848943967 succeeded from merged source c592f59dc05bfe2a573bc715ff2755b4fa253c70. Signed artifact 13333530491 contains Roadprints-0.25.223-269.apk, 36,624,253 bytes, SHA-256 f49eb51a1ebeb2a683b0b0b343d64e3d443c957d789089595090253bd5e40c2e. Identity/certificate checks passed; downloaded report ZIP and every XML total independently verified: 119 suites, 851 cases, 847 passes, four established candidate skips, zero failures/errors; five recovery checks. Physical acceptance remains outstanding. See [final controlled integration record](../controlled-integration-20261104.md) and [machine receipt](../data-trials/controlled-integration-20261104.json).
