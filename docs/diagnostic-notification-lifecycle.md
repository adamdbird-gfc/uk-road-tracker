# Background diagnostic notification lifecycle — 25 October 2026

Baseline: latest fetched private Android development head `6fa85f85` (#219,
0.25.216/262), with all previous shared capture/map/NCN changes retained.

## Confirmed application defect and incident limits

`RoadprintsApplication.onCreate` calls `CrashReporter.install`. Previously install
posted whenever stored crash/match reports were nonempty and
`debug_notification_active` was false. Opening Debug cleared that flag while
retaining reports. A later process start, including a background activity-recognition
callback, could therefore announce the historical report as new without a journey
or match. This same path is verified in the exact delivered 213 source
`5e9f1d56845584559fa8444715d9df0e4f650466`, as well as 216. Notification ID 44 was fixed, but no persistent event identity, creation
clock, delivery receipt or originating worker existed. Report Time was attempt
start time (or formatting time), not proof of notification posting time.

The reported a79da6c5-6c29-4b9e-be0c-ddb791ef79a0 failure text identifies a native
walking quality rejection: 888,224ms gap, 372m displacement; report time
2026-10-24T04:23:56Z. Its appearance on 25 October cannot be definitively attributed
to a new post versus Android retaining/delaying an earlier notification without
OS notification history or contemporaneous posting telemetry. The reproducible
stored-report/startup regeneration defect is confirmed; do not assert an OS cause.

There is no WorkManager, JobScheduler, AlarmManager or scheduled match worker in
the current app. Matching runs in the process-wide coordinator executor from
explicit batch/journey/rematch requests and capture-save handoffs. Archive saves
notify derived NCN/cache consumers, not notification or matching observers.
`CaptureService`'s ongoing recording notification (ID/channel separate from debug)
is unrelated. Foreground/background process startup still runs Application code.

## Changes

- Durable event identity/signature and delivery state, independent of report
  visibility. Startup delivers only a genuinely new pending event; legacy stored
  reports never become new events. Viewing, clearing or dismissing preserves
  acknowledgement/deduplication. Successful matching ends its failure episode,
  allowing a genuinely new later failure to alert.
- Unchanged failure rediscovery does not append a newly timestamped report or
  post again. New evidence, rules or error identity can form a new event.
- Reports and notification extras expose event identity, recorded/creation/posting
  timestamps, trigger, worker/thread and original failure time. Pending permission
  remains distinguishable from a posted alert. A posted receipt means submitted
  to Android, not proof of when an OEM displayed it. Stale dismiss intents cannot
  acknowledge a newer event.
- Delivery is claimed durably before posting. A process death in the tiny
  claim-to-notify window can lose an alert; it cannot repeatedly regenerate an
  historical alert. Reports remain stored for Debug. OS delivery failures are
  recorded without a posting loop. This is at-most-once alerting, not an atomic
  transaction with Android's notification service.
- Deterministic GPS-quality rejection stores a canonical input fingerprint and
  explicit quality-rule version. Unchanged raw/aligned GPS, geometry and mode
  remain held through restarts, label edits, batch scans and manual retry.
  Meaningful input changes or an intentional rule-version increment reopen work.
  Adopt older explicit quality rejections lazily when individually encountered;
  no full-history migration or reassessment.
- Retry-After is checked before queueing and again before execution; a cooldown
  is not recorded as a new failure. Existing bounded transient HTTP/DNS recovery
  and distance-integrity guards remain. Attempts now get fresh identities/times
  before validation, so pre-network failures cannot inherit an old attempt clock.

## Acceptance

Automated notification tests cover legacy reports, idle/background application
restarts, viewing/dismissal/clear, concurrent rediscovery, permissions, claimed
state, new errors and successful-recovery episodes. Coordinator tests cover the
reported-duration moving gap, repeated archive label updates, recreated workers,
manual/batch requests, changed evidence/rules and transient cooldowns.
No original-location/device journey is required. Deterministic tests cannot prove
all Android/OEM notification scheduling.

Final implementation `e4c02a1086d2ca9afac8c4a8c35f5583e8a34382` passed complete
Android compilation/lint, pinned references and all unit tests in
[run 40177903529](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40177903529).
The prior complete run 40177567090 also passed; no exclusions. One private
0.25.217 (263) packaging request follows green tests; artifact verification pending.

No Firebase distribution, backend deployment, historical evidence deletion,
automatic rematching, global cache invalidation or map/NCN reimplementation.
