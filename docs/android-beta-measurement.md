# Roadprints beta measurement — schema 1

Firebase project: roadprints-822cc. Android app: com.roadprints.capture.

## Collection choices

Usage analytics and diagnostics are separate, optional and disabled by manifest before SDK startup.
The privacy screen appears once when the welcome screen resumes; choices remain available at
Utilities → Privacy and diagnostics. Dismissing the screen leaves both off. No consent event
is sent for declining. Advertising storage, user data and personalisation remain denied.
Advertising ID permissions are removed from the merged manifest. Usage revocation resets the
local Analytics identifier and stops custom events. Diagnostics revocation disables SDK
collection and discards unsent crash reports. These choices do not delete previously uploaded data.

Firebase Analytics supplies its own installation identifier. No Roadprints account, journey ID,
GPS, journey title, settlement, road name, source filename, or exact journey timestamps are added
to custom events. Sanitised non-fatal reports contain only a fixed error category, without the
original exception message/cause. Opt-in fatal crash reports include SDK stack traces; local
movement diagnostics remain separate. Do not export raw route payloads as Crashlytics keys/logs.

## Event contract

All events include test_cohort (internal or beta) and event_schema (1). SDK metadata includes
app version, OS and device. No per-GPS-fix events. There is no historical replay on opting in.

| Event | Trigger | Parameters |
|---|---|---|
| measurement_enabled | Usage changes from off to on | common only |
| screen_view | An allowlisted activity resumes | screen_name |
| capture_started | Location updates registered for a confirmed capture | mode, source, capture_type |
| capture_start_failed | Permission prevents capture starting | error_category |
| capture_saved | Captured journey persisted locally | mode, source, gps_points |
| capture_save_failed | Saving fails and live capture is kept for retry | mode, source |
| match_started | Journey marked processing for one logical attempt | mode, source, attempt_type |
| match_completed | Logical attempt saves its validated result | mode, source, attempt_type, duration_ms |
| match_failed | Logical attempt fails after internal retries | mode, source, attempt_type, error_category, duration_ms |
| import_started | Timeline file processing starts | common only |
| import_completed | Timeline importer returns success | duration_ms, added, skipped, invalid |
| import_failed | Timeline importer returns failure or throws | result totals or error_category |
| diagnostic_test | Tester sends a non-fatal test (usage enabled) | common only |

`source`: capture / timeline / other. `attempt_type`: match / rematch. Mode uses a fixed allowlist.
Error category: timeout / rate_limit / server / validation / network / permission / other.
Unknown parameter keys, free text and unknown event names are dropped centrally.
Match duration includes preparation and internal retries; one logical attempt emits one outcome.
A rematch or later retry is another logical attempt. Separate source and attempt_type in reports.

## Diagnostics and performance

Crashlytics keeps the existing local crash handler in its uncaught-handler chain. A non-fatal
"Send diagnostic test" action validates connectivity without crashing either device. A controlled
fatal crash still needs validation in a dedicated test build before public beta.
Custom Firebase Performance traces: journey_match, timeline_import, map_data_load,
progress_data_load. There is deliberately no HTTP instrumentation Gradle plugin to collect
request URLs. The map/progress traces measure uncached data preparation, not full first-frame
rendering; cache hits do not generate those traces. App startup/screen traces supplied by the SDK
may also be available with diagnostics enabled.

## Initial GA4 configuration and dashboard

Register event-scoped dimensions: mode, source, capture_type, attempt_type, error_category,
test_cohort; user-scoped test_cohort is also available. Register duration_ms as a custom metric
with milliseconds. Inspect events in DebugView using a development device or in Realtime.
Exclude internal cohorts from external beta adoption and retention reports.

Start with: setup-to-first-captured-match funnel (only for consented users); logical matching
success rate by mode, source and version; capture starts vs persisted saves; import outcomes;
7/28-day return cohorts based on foreground app engagement; crash-free users and performance
percentiles. Treat in-flight attempts separately when computing rates.

These metrics describe observed app behaviour, not missed journeys. A manually reported missed
journey plus local diagnostics is still needed to evaluate capture recall. No claim that Analytics
alone measures detection accuracy. Background capture/matching events must not be treated as
foreground retention. New-road discovery, achievements, permission funnels, purchases and
missed-journey feedback remain the next instrumentation slice; they are not yet emitted here.

## Verification

Android unit tests enforce default denial, independent consent, revocation, privacy allowlists,
SDK error isolation and one-time presentation. CI runs the full Android tests, lint and signed
APK verification using the existing stable signing key. Live delivery to the user's Firebase
console must be checked after installing this build and explicitly choosing sharing options.
