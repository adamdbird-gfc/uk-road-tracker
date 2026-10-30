# Phantom train capture — 30 October 2026

Scope: critical capture reliability investigation, non-destructive oversized-recording handling and private recovery review. Viewer publication is separate (#251). The authoritative main was independently fetched as `a87d60d4d5b2c4651c571e7f4316e514154734eb`; no conflicting capture work was found among open PRs. #248/#249 remain independent NCN diagnostics. Source Android baseline is 0.25.220 (266); the private supplied export independently confirms that version/code on Pixel 10, API 37. No device connection, APK installation, backend/Firebase version probe or deployment was performed.

## Verified findings

The privately supplied complete export contains 39,120 raw observations, 32,767 derived route observations, and a train label covering about 49 hours. Raw observations continue throughout the intervening days; the longest positive adjacent raw timestamp gap is 25.398 seconds. Replaying the production reliability filter reproduces **all 32,767 derived rows exactly**. This is a cleaned-array count, not a signed-short cap or evidence of lost raw points. The detail dialog previously labelled that derived count simply “GPS points”.

The production GPS stillness detector, replayed as a vehicle detector on the reliable raw observations, identifies the reported long intermediate stop with the existing five-minute threshold. It also identifies later stationary periods. Private timestamps and raw index partitions are retained only in the owner's recovery package, not this repository.

The defect is a combination of train-specific control flow:

- `CaptureService` excluded train mode from `observeWalkingStillness` and its checkpoint replay. Continuous quiet GPS therefore did not establish the normal arrival boundary.
- `StationJourneyModel.holdStill` returned true for any train label, even with no current station. `ownsModeChange` also reserved train activity transitions for the station model. That model requires corroborated walking/alighting; a quick car pickup may not supply its required walking distance.
- `ContextJourneyModel` vetoed train arrivals at destinations and returned an unconditional train pause hold. It did not permit a train-labelled leg to resolve to driving on sustained road-only map/GPS evidence.
- Idle-place suppression runs only when capture is inactive. It could not repair the already-active train. The export has no attached stops/places, and later STILL/vehicle callbacks were retained without ending the train-labelled record.

This explains a viable, deterministic persistence path without assuming the five-minute threshold is wrong or GPS was absent. Missing first-day activity history (only the latest 32 events are kept), live context responses, callbacks/timer execution and lifecycle logs prevent a verbatim replay of every actual transition. No evidence establishes a foreground-service restart, Android background restriction or recognised-place suppression as the incident trigger. Those remain unverified, rather than asserted causes.

The straight endpoint preview is the designed train/plane presentation; it does not mean raw observations were replaced with a straight line. Train matching is not required. Separately, the saved quality result identifies a moving gap in the **filtered** route; current save policy sets unverified route distance to zero. A filtered gap can exceed two minutes even when raw callbacks continue, because unreliable observations are excluded from the derived route.

A Monday-dated export was located and inspected, but it is a morning walking leg from a different app version, not the successful evening train journey. It cannot establish the requested like-for-like comparison. The successful Monday train export is not available in the evidence retrieved; no regression-introduction date is inferred from that anecdote.

## Focused correction

Train captures now participate in the existing vehicle GPS dwell detector, including checkpoint reconstruction. A train label alone no longer vetoes arrival away from station/rail evidence. Actual station/rail pauses remain protected; no maximum journey duration is introduced. Recognised off-rail destinations may end the leg, and sustained road-only map/GPS evidence outside a station may propose driving using the existing direction-independent motion/departure confirmation requirements. IN_VEHICLE alone still cannot distinguish a train from a car and does not relabel the whole journey. When rail/station context is unavailable, a corroborated off-station dwell can now close a train-labelled leg; an unusually long train halt on an unmapped section is therefore a field-acceptance scenario. Raw observations are retained and no rail-to-road route is invented.

`JourneySizeGuard` applies a 20,000-point budget to a **single full matching preparation**, using full-width integer raw/route counts. This is a resource gate, not an NCN rule, recording cap, sampling reduction or maximum journey time. Oversized recordings retain every sample. The detail dialog shows original and derived counts, offers export/review, and blocks whole-recording transport reassignment/matching. The coordinator independently enforces the gate for batch/forced attempts before request construction and retry hashing. Ordinary recordings and all capture evidence remain intact.

## Crash status and recovery limitations

The supplied export contains no exception/ANR/crash stack. The precise reported crash mechanism cannot be verified. Large synchronous detail/edit/storage operations and full-recording matching preparation are resource-risk hypotheses, not a proven OutOfMemoryError or stack-overflow diagnosis. The added budget and actual-dialog regression prevent this oversized record entering those full-matching/reclassification paths, but do not prove the original device crash exception has been reproduced or eliminated. Phone acceptance and, if it recurs, a crash stack remain necessary.

The private recovery package contains the exact original bytes plus eight contiguous review partitions. Together they reproduce every original raw row exactly once: candidate train, uncertain pickup, candidate car, intermediate stay, candidate car, extended home stay/unresolved observations, Friday candidate drive, and services stay. Transport labels and exact transfer/departure times remain review proposals; no route geometry, matching output, inferred distance or completion entitlement is generated. No on-device original, correct subsequent journey or existing completion ledger is changed.

The generic standard-library tool is `scripts/review_capture_partition.py`; it requires an exact input checksum and a human-reviewed contiguous index plan, refuses overlap/gaps/tail loss, creates a new directory only and independently reassembles all output samples. Example:

```sh
python scripts/review_capture_partition.py --input /private/export.json \
  --plan /private/review-plan.json --output /private/new-recovery-review
python -m unittest scripts.test_review_capture_partition -q
```

These outputs are deliberately **not app-importable journeys**. Restoring reviewed sections into the app remains blocked on an explicit reviewed split/import transaction; forcing the whole original through a road matcher is not a safe substitute. Preserve the original archive and use the private review copies for inspection. No automatic road or NCN credit is authorised.

## Validation and delivery

The synthetic regressions cover the short station stop, train-to-road evidence, club/home stays, new independent departure, unchanged five-minute dwell, fifteen-minute dwell, silence/reduced sampling, rail/station protection, context checkpoints, full-width counts around 32,767, immutable original evidence and safe export review. Real Android service tests exercise the Location callback and restored train checkpoint/null-intent path; the oversized-dialog test opens the actual detail/review flow. Existing place suppression, lifecycle, mode-boundary and ferry/road/walking/cycling regressions remain in the full Android suite.

The first CI attempt exposed a stale helper name copied from an older manual validation workflow. It stopped before reference restoration or compilation. The new task-scoped workflow was corrected to use the current baseline's inline hash/size validation; no guard was relaxed. The next run passed compile/lint/full Android tests at `3ab995636df19052c35fd3a93a8ddb6ff3bfb947`. The added XML summary then exposed four existing opt-in national-candidate tests skipped because their required external candidate receipt is absent. The workflow now lists and allows only those four exact cases, requires zero failures/errors and no skips in the capture/restart/oversized UI suites, and does not create a national candidate to activate unrelated tests. Final restart/UI counts and the validated head are recorded in the final PR receipt.

The workflow restores only existing immutable reference-cache entries, fails on cache miss, verifies both datasets, disables SDK downloads, resolves ordinary Java/test dependencies, and runs tests in an isolated network namespace. It never assembles an APK, uploads/distributes a release, publishes a catalogue, contacts Firebase for references or deploys Render. Synthetic fixtures only are public. Private GPS, raw exports and the recovery index plan are not committed.

Final CI [41140869292](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/41140869292) passed at `407f7f6534dde70d301ac8d11a9364f0e4777399`: Android compilation and lint passed; **821 Android tests passed, zero failures/errors**, and the **four existing opt-in national-candidate tests were explicitly skipped** without an external candidate receipt. All 11 new model/service/restart/UI checks ran. The five standard-library recovery partition tests passed. The final source delivery adds only this report and its validation receipt to that tested tree.

Status: source change and recovery review implemented and validated; PR #250 remains open and unmerged for owner review. No APK built/distributed, no source merged, no backend/Firebase deployment, no issue closure and no user-data mutation. Owner phone acceptance and a separately authorised coordinated APK are distinct next steps. No release is implied by a passing source test.
