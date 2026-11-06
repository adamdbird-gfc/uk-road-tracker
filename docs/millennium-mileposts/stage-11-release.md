# Stage 11 — normal Android integration and owner APK

6 November 2026. Authorised scope: validate merged Stage 10, resolve integration defects, advance version, merge the release changes and package one signed private owner APK. No store, beta tester, backend or Firebase deployment. Physical acceptance remains pending owner execution.

## Starting baseline and product state

Authoritative main `b0484b3d4c2292306629c7d69f9a436931ef889e` is the verified merge of #297, with no subsequent commits when this task began. #296 and #295 are incorporated. Stage 10's final 326 Android checks and memory receipts remain preserved; Stage 11 reruns them and broadens ordinary regression coverage. Installed owner version/source, live backend SHA and device-active NCN pins remain unknown. Source pins are verified independently of phone state.

Release target: **0.25.224 (270)**, application ID `com.roadprints.capture`, existing stable signing certificate SHA-256 `5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`. Existing min/target/compile SDKs remain 26/35/35. Android 17 compatibility must be confirmed on the Pixel 10; SDK compatibility and host tests alone do not establish device acceptance. No identity, local data filename, backup policy or migration changes are made. Install as an update; do not uninstall or clear data. The established private development build is signed debug, matching previous owner builds; it is not a Play production release.

## Catalogue, awards, presentation and limitations

Reproduced revision 3 approval: 889 input / 887 approved / 2 held / 0 rejected; payload SHA-256 `1f7468a5ed054c5822c88614650fe33814554502a57779fc1b7375749d35c2ce`, 1,167,726 bytes. Held nodes 416710572 and 9825206150 are absent from the packaged runtime payload. Immutable source pins, retained OSM tags, ODbL and visible attribution remain. Location qualification and supported designer attribution remain separate; unknown attribution cannot award a designer.

The normal production pipeline uses genuine retained journey evidence, 100m eligibility and persistent SQLite proofs. Trustworthy walking/running/cycling evidence qualifies; drift, vehicle, unreliable geometry and unsupported Timeline evidence cannot qualify. Repeated journeys do not duplicate physical credit. Corrections and deletion reconcile support; first-recognition history is retained separately from current entitlement. #283 historical recovery and #286 remain deferred.

Four attributed design discoveries earn the four designer awards; Grimshaw requires existing NCN route completion; seven distinct discovered physical posts earn Harrow; all six individuals earn In Honour of Time. All six and collective derive through the existing architecture. Current NCN evidence is required for the atomic combined receipt. Nothing awards these by merely installing the APK or opening the screen.

All 23 original vector assets and native seven-stage cipher are unchanged from tested Stage 10. Stages 0–7: anonymous mystery, Seasons, Continents, Ages of Man, Zodiac, Centuries, Final Mystery, Harrow identity. Locked identities remain concealed. Cards stack, rings rotate independently, native cipher letters scramble and resolve using the authoritative non-sequential mask; final name is hidden until stage seven. Replay, reduced motion, lifecycle cancellation and capture-priority guards remain. No synthetic sandbox classes/assets are permitted in the distributed APK; the packaging verifier audits DEX, catalogue and drawable entries.

## Build and reproducibility

`.github/workflows/milepost-stage11-apk.yml` runs only for the version-specific release request on main. It restores existing pinned reference bytes, compiles normal/debug/release/acceptance/instrumentation, lints, runs established focused 256MiB regressions and Stage 8 harness, the independent 64MiB streaming probe, Python checks and the complete ordinary Android suite. Four established national-candidate tests may skip because no external candidate receipt is selected; all other skips/failures block packaging. The ordinary full suite uses normal repository heap settings; report its results separately from the focused 256MiB probes.

After checks, the established secrets provide the stable signing key; missing credentials fail closed. Certificate and package/version checks precede artifact upload. `receipt.json` records exact merged source SHA, run ID, file bytes, APK SHA-256, catalogue pins and unperformed device acceptance. Build prerequisites and source pins are recorded; byte-for-byte reproducibility across fresh runners is not claimed. One generated APK is retained unchanged with its receipt, package and certificate evidence for 90 days through GitHub Actions. GitHub sign-in is required for the artifact download. No Firebase upload or existing tester distribution occurs.

## Pixel 10 installation and acceptance

1. Before installing, note existing app version and journey count. Download the successful workflow's `roadprints-mileposts-0.25.224-270` artifact, extract `Roadprints-0.25.224-270.apk`, then open it on the Pixel 10. Accept Android's update prompt. Do not uninstall or clear data. If Android refuses it, record the exact error and stop; a different installed certificate is an installation blocker, not a reason to delete data.
2. Verify 0.25.224 in What's New or Android App info and confirm existing journeys/settings/achievements remain. Optional workstation verification: `adb shell dumpsys package com.roadprints.capture` should show versionCode 270 / versionName 0.25.224.
3. Open normal Achievements to refresh existing NCN assessment. Leave Achievements (which schedules milepost work), plug in the phone, and allow it to become idle with screen off. Reopen Achievements after background processing. First catalogue/large-history work can require several OS retry slices. Opening the screen does not force immediate discovery work.
4. A fresh milepost section/anonymous cards confirms a completed current catalogue assessment. If absent, first check charging/idle scheduling, NCN readiness and absence of active capture; absence alone does not prove catalogue corruption. For exact on-device installation checks use the diagnostic commands below.
5. Inspect anonymous locked awards and any genuine unlocked design awards. No encounter means no discovery; this is expected. Historical discovery only uses retained trustworthy evidence, so imported route lines alone may remain pending. Do not seed data or manufacture progress.
6. For available Harrow stages, open the normal summary/replay, advance through earned stages, check distinct emblems/card stack/rings/letter mask, leave mid-transition, reopen and verify last completed progress. The full name may appear only at seven genuine posts. Repeat in static/reduced-motion mode, with TalkBack, large text and Android animations disabled. Replay cannot award progress.
7. Open/close Achievements and replay ten times. Start a normal walk/run/cycle capture during presentation; confirm animation stops and capture continues. Complete the journey and compare raw timestamps/points, mode continuity and normal matching with expected behaviour. Never delete useful journeys just to test revocation on the owner's live archive.
8. After a genuine eligible visit, allow idle/charging processing, then restart Roadprints and confirm the same discoveries persist without duplicate credit. Record version, journey ID/time/mode, expected OSM node and observed outcome if it fails. Destructive correction/deletion testing belongs on a preserved test archive, not the owner's only copy.

## Optional workstation diagnostics (read-only evidence)

These require owner's ADB access. Do not run acceptance-fixture seeding against the owner app.

```sh
adb shell dumpsys package com.roadprints.capture
adb shell dumpsys jobscheduler com.roadprints.capture
adb shell run-as com.roadprints.capture ls -l databases
adb shell dumpsys meminfo com.roadprints.capture
adb shell dumpsys gfxinfo com.roadprints.capture
adb logcat -d -s Roadprints AndroidRuntime
```

`milepost-discoveries.db` exists after installation/processing. For exact revision/count verification, preserve a consistent private copy of the database with its `-wal` and `-shm` sidecars **after closing the app and only when capture is inactive**; do not publish this archive. On an equipped workstation, SQLite read-only inspection of `catalogue` verifies revision 3, the pinned SHA and marker fingerprint; `SELECT COUNT(*) FROM marker WHERE eligible=1` should be 887. No database edits are needed. Android does not ship a guaranteed sqlite CLI; do not assume an in-phone SQL command is available. Logs can contain private journey details; share only relevant excerpts.

Record ART heap/native/PSS at baseline, after each repeated entry/replay and after backgrounding; obtain frame statistics and actual capture continuity observations. Host JVM probes are not ART/PSS or real GPS measurements. No physical-device results, TalkBack experience or Android 17 installation acceptance were performed in Work. Final device acceptance remains pending owner confirmation.

Rollback: retain the prior known-good APK and original data. Android may reject installing an older version code over a newer app. Do not uninstall to downgrade; use a reviewed forward-version fix with the same signature if required.
