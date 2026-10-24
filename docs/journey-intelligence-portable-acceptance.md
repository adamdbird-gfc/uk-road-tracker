# General journey intelligence — portable acceptance

Baseline is private Android 0.25.214 (260), PR #215, final handover source
81ca8e2e52004190d4bcb5e78a93c0eec04bc44f. Nine original private diagnostics
were read and their hashes checked against the existing anonymised fixture;
no original evidence was replaced. Candidate 0.25.215 (261).

## Shared behaviour

Destination decisions use verified type, geometry, confidence, movement and
visit evidence. No policy branch compares a place name or Disneyland coordinates.
The pinned Disneyland polygon remains reference data; its bounding box is now
computed from the data rather than coded into behaviour.

Resorts/theme parks, malls, airports/terminals, hospitals, campuses and public
event/sports sites share uncertainty-aware containment and sustained departure.
Arrival in access-style destinations requires three precise inside observations
over 30 seconds; campuses/hospitals retain dwell confirmation so walking through
public grounds is not automatically treated as arrival. Through-road and station
context guard genuine travelling. Mere proximity never establishes a visit.

Complex stays keep incidental facility movement and boundary excursions internal.
Explicit running/cycling hints still need sustained measured exercise. Purposeful
walking within managed sites needs sustained, coherent displacement; existing
campus exercise remains available. Manual recording remains an explicit override.
Exercise intent is retained through capture and matching rather than being rejected
by the internal-movement safety net. Automatic intent is separate from mode labels.

Same-identity mapped relation components are snapshotted together, including
holes, with bounded size. They survive saved stop/restart. No convex hull or
connector is fabricated between separate parts. A short excursion through a
mapping gap is handled by departure hysteresis; a sustained supported departure
is recordable. Where polygon support is absent, local estimated-stop uncertainty
and corroborated movement remain; the estimated radius is never enlarged to
pretend to cover a destination. Boundaries are not inferred from display names.

Confirmed departures recover a recent credible boundary observation from the
existing buffer, not the old arrival fix or an unrecorded interval. Precise,
continuous walking displacement can corroborate unavailable/incorrect zero
speed reports. It cannot infer running/cycling/vehicle intent. Repeated coordinates,
poor accuracy, time disorder and unsupported gaps retain raw evidence without
creating distance/progress. Historical records are not migrated or rematched.

Place identity now reports recognised geography, estimated GPS, coordinate-backed
user confirmation, a label without position, or unknown evidence. A user label
never creates an OSM/airport identity or upgrades geographic confidence. Original
endpoints/evidence survive editing; the same model is attached by capture and
place review. Authoritative identities require appropriate mapped support.

Client retry handling distinguishes backend overload/rate-limiting 503s from
transient connectivity errors, honours Retry-After and stores a next-retry time.
Existing atomic duplicate protection and distance-integrity checks remain.

## Deterministic acceptance

Portable tests use unrelated names, translated/metric-scaled geometries and
centres in Kent, Scotland and Northern Ireland. Six destination types cover
hours of facility movement, edge drift, foot/vehicle departure, connected
components and persisted visit state. Purposeful running, straight walking,
short local starts, missing/zero speed and stationary repeated fixes are tested.
All nine real raw traces are revalidated at three unrelated centres. Existing
service, capture, network/504, duplicate, history, foreign-coverage, map cache and
NCN suites remain. No physical return to Disneyland is an acceptance gate.

Simulation is deterministic evidence for these cases, not proof of every device,
provider or ambiguous travel intention. Ordinary future journeys provide optional
further observations. Online context remains opt-in and its service remains
UK-scoped; unknown overseas places retain conservative estimated evidence unless
mapped offline/reference data is available. A world destination catalogue and a
browsable internal-movement journal remain separate follow-ups.

## Backend delivery

The owner selected My Workspace. Production service uk-road-tracker-api auto-
deploys main. #217 passed database-backed walking/context and road-recovery CI,
merged as 7dd2c58c628ec819ace2b645d18aa296ddb238b4. Render reports deploy
`dep-dbegppdg1s2s739dhv30` live at that source. Live live health confirms policy version 2 and database ready. A public six-point
NCN walking reference matched 6/6 points and 30.7m with real geometry, using
the preloaded pedestrian network (2,030ms server work, 8.02s client round trip).
A second public Scottish NCN trace matched 8/8 points and 34.3m (887ms server
work, 5.49s round trip). An overseas synthetic four-point trace exercised the
actual fallback router: HTTP 200, 3/4 supported points, real 42.3m geometry,
one upstream call, 1,037ms server work / 5.64s round trip. The unmatched point
remained unmatched. Render runtime counters independently corroborate all three
checks. No personal GPS, journey IDs or persisted journeys were submitted.

Production logs confirm earlier 55-second application deadline failures; they
lack sufficient per-stage evidence to identify the exact historical upstream
cause. Native fallback now has request-local wall-time/call limits and a bounded
extra-subdivision budget. Verified siblings survive limited sections; empty
exhaustion stays recoverable. Health advertises walking_work_version=2 and new
coordinate-free counters expose future work without retaining route payloads.

Preserve compact main-map records, per-journey coverage and NCN ledgers. No global
cache invalidation or history reassessment. Only optional public context tiles
use a new content revision to obtain richer destination tags. Private APK only;
no Firebase upload/tester distribution or Play publication.

## Verified private Android delivery

Delivered 0.25.215 (261), PR #218, exact signed source
`d793d8b102ee85f1b1b51f62baa7dfed5ed19608`, packaging 40129351156.
759 Android tests in 102 suites passed, zero failures/errors/skips; lint and
stable signing passed. Downloaded XML independently confirms totals; APK and
artifact ZIP hashes match. APK 36,589,905 bytes, SHA-256
`a5a92be337bc0345dbe4722c2bcb46fd883bcb4659fd10611efc4fa8144f32be`.
One signed APK only, no Firebase distribution. Deterministic portable acceptance
and live backend verification are complete; no return visit is required.
The historical sparse-departure regression was retained and fixed in shared
code, with a new equivalent across all six types. No test was excluded.
