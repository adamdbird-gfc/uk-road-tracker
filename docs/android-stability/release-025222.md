# Android 0.25.222 (268) — 4 November 2026

Owner-authorised private signed release. Integration baseline: `d571aa21b0d2884792bf5bab80574e401d2ad21c`.
PR #257 merged as `566f03f710eca96c728f35d7a92fb706706efae8`; #258 merged as the baseline above. Both heads were verified unchanged; clean temporary combined merge matched GitHub main byte-for-byte. No outstanding review threads. Production changes affect only CoreAchievementEvidence, TownAchievementEvidence and CaptureService; no persistence format/migration or historical rewrite. #250 protections remain present.

## Validation and packaging

Independent CI #257: 41816755374 (826 runnable Android checks, four opt-in NCN skips, five Python recovery checks, 256 MiB memory probe). CI #258: 41815192472 and final-head 41815743449 (837 runnable Android checks, four opt-in skips, five recovery checks). Runtime/tests in final heads match tested source. Shared CI workflow is identical. Full-history correctness, unlock/history and archive hashes (1,605 archives, three calculations) and repeated heap measurements are reused without repeating the volume test. Local combined recovery checks: five pass. Packaging runs compile/lint and all other combined Android regressions, including new evidence/service/memory checks, phantom train/recovery and version release notes. Exact totals, signed APK SHA and artifact receipt are recorded after the run.

Packaging request triggers once on main for this version. Uses established Actions signing secrets; fails unless APK ID is com.roadprints.capture, version 0.25.222/268 and certificate SHA-256 is 5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031 (same as packaged 267). Previous 267 source is PR #252 c5cafa9c5ea9f9200a04d45aead0839b6b5c6625; only its version history is carried forward. Main's historical manual build wrapper is not invoked because it selects obsolete source. No Firebase credentials, uploads or tester notifications. Reference assets/pins unchanged and cache checksum validated. Backend deployment and active Firebase versions not independently observed in this task; neither changed. Phone installed version is owner-reported 267, not directly queried.

## Pixel 10 acceptance — pending

1. With capture idle, download/extract the signed APK and choose Update over 0.25.221 (267). Do not uninstall or clear data. Confirm 0.25.222 (268), archive count and representative old journeys/manual edits.
2. Open Achievements, let calculation finish, reopen ten times and restart the app. Check values, milestones, unlock dates and no repeated celebrations; record refresh time, stalls, crash report and heap/PSS if available. Whole-app ART memory and phone speed remain unmeasured.
3. Record existing location/activity/background permissions and map-context consent. Without changing consent implicitly, test walking-to-car, normal/short urban drives, traffic lights, brief and sustained queues, parking and car-to-walk. Retain raw GPS/context/decision evidence, actual and detected mode, delay and boundaries. Test context-disabled uncertainty separately; manual corrections must survive reopening and matching.
4. On a genuine train journey, confirm arrival terminates normally and no capture persists overnight. Check walking/rail adjacency, stationary drift, bus/cycling and original foot approaches; never manufacture GPS or repair history to pass acceptance.

Issues #255 and #256 remain open until physical acceptance; #259 separately tracks achievement CPU profiling. Short/slow-only drives, unavailable context, rail/road ambiguity and car/bus uncertainty remain limitations. No measured field recall/battery claim. Old phantom recording is not automatically repaired.

Retain previous APK as a known-good reference. Android blocks ordinary version downgrades; do not uninstall/clear storage to roll back. If acceptance fails, preserve diagnostics/data and request a forward fix with a higher version code.
