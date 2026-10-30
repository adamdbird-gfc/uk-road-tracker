# Private capture update 0.25.221 (267)

Prepared directly from verified main `40f3c0d00e171800a157af4b3db5d4125134203b`, containing merged #250 head `a71f70700553d107d44c014942b3d4cc17f3a16c`. Only version metadata and bundled release notes change beyond that runtime. No matcher, reference, NCN, viewer or journey data changes.

Main’s old packaging wrapper still selects an obsolete historical branch. This scoped push-request workflow instead checks out its own immutable triggering commit on `release/phantom-capture-025221`. It retains the successful #250 cache-only checksum verification, SDK download ban, lint and isolated full Android tests, then assembles once with the existing repository signing secrets. Missing secrets/cache or a different certificate/package/version fail closed. No Firebase credentials, distribution or tester notifications are present. The workflow runs only when the exact versioned request path is pushed to this branch; opening/merging this PR cannot build an APK.

The established 0.25.220 packaging run 40690390058 verified certificate SHA-256 `5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`; this build requires the same certificate and application ID `com.roadprints.capture`, with higher version code 267.

Download the signed APK only after the run succeeds and verify its receipt. Install over the existing app; do not uninstall or clear storage. The 49-hour original recording remains untouched. Oversized records are protected from full matching and wholesale mode changes; export/review is supported. Eight proposed recovery partitions are still review-only: there is no verified on-device import. The exact original crash remains unproven. Real Pixel 10 capture and upgrade acceptance remain outstanding.

A lower-version APK cannot normally be installed as a rollback. Keep the existing original export and known-good APK; if a regression is found, stop tracking and prepare a reviewed forward update rather than uninstalling the app. No destructive rollback is authorised.

Source integration, signed artifact availability, owner installation and device acceptance must be reported separately. PR remains open; no merge, Render deployment, Firebase publication or unrelated issue closure is authorised here.

Initial run 41145374481 correctly blocked before assembly because the new version lacked a bundled release-note entry (`WhatsNewActivityTest`). Added accurate notes without weakening the test and issued the distinct `0.25.221-notes-corrected.json` request; the original failed request remains in history. No APK was assembled in the failed run.
