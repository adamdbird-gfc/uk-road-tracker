# Multimodal capture corrective beta — 21 October 2026

Baseline: `b9b65eb58e8b4ed4d0c6f5c06fe972c5368bb861`, following exact installed
0.25.212 (258) source `5d5febdf35f8ac451565b110fbab1e7531fa8116`.
Candidate: 0.25.213 (259), PR #213 stacked on #212. Private development only.

## Evidence and conclusions

The supplied 122410 diagnostic is **not a recorded Dover–Calais crossing combined
with driving**. Its earliest timed sample, at 09:00:10 UTC, is 1.876818 E,
50.977405 N near Calais; its subsequent samples proceed inland. No Dover or
supported central-Channel interval is present. The separate movement export ends
at 06:24 UTC and cannot establish Android's activity reports during the crossing.
No evidence establishes internal splitting/recombining, lost persisted boundaries,
or a manual confirmation as the cause of missing ferry capture. Do not turn a
missing interval into a synthetic ferry route or invent embarkation times.

The stored ~258.09 km is consistent with the filtered route and sustained road
speeds over nearly three hours. An independent approximate equirectangular sum
is 257.82 km, maximum retained step ~268 m. The raw mixed-provider feed totals
~2,141.73 km with a ~22.27 km excursion: raw distance is emphatically not suitable
coverage evidence. Original samples are retained; filtered geometry is authoritative
for this comparison. These are offline calculations, not device measurements.

For the 060948 coach recording, the log at 05:19:50 UTC explicitly states
`place_visit_departure: sustained_activity_within_place; mode=cycling`, followed
by `journey_capture_started: Mode: cycling`. Recent activity events are walking
and still; IN_VEHICLE arrives at 05:22:26. There is no bicycle transition in that
window. The code allowed the cycling exercise-speed fallback without a bicycle
hint. The final confirmed-bus snapshot alone would not establish that cause.
Approximate raw/retained sums are 49.04/9.20 km, versus stored 9.22 km.

Matching findings:

* Cycling: reported HTTP 504 is the backend's existing 55-second bicycle request
  deadline. It uses the dedicated, serialised/paced bicycle graph, eight-point
  router windows and bounded radii. This does not prove bad GPS or an unavailable
  endpoint. Avoid repeating the identical long request four times immediately.
* Bus correction: aligned capture preparation v2 retained 142/270 points and
  rejected the returned distance under the unchanged max(1.4 × submitted GPS,
  submitted GPS + 300 m) rule. The old export omitted returned distance and ratio;
  it cannot establish the exact detour/ratio. New diagnostics persist those values
  **before rejection**, plus raw, retained and submitted distances and outlier
  indices. Rejection remains intact; do not call this journey successfully matched.
* France: `UnknownHostException` / unable to resolve the API host at the recorded
  attempt. This is client-side DNS failure, not a 504 or evidence that the backend
  attempted a ferry route. Earlier section results exist, but no complete match
  was committed. Original geometry remains.

Live /health verification from this execution environment timed out. Render MCP
has no selected workspace, so service logs were not read and live endpoint health
is not claimed. No production service configuration/deployment was changed.

## Implementation and boundaries

* Unhinted cycling/running-speed movement at a place becomes Unknown. A fresh
  bicycle/running activity hint and existing sustained GPS support can identify
  those modes. Stationary drift still does not qualify. Transition API confidence
  is not provided: diagnostics say so instead of inventing a percentage.
* Offline Dover–Calais corridor v1 combines known terminal context with at least
  four reliable moving fixes, three minutes and 1.5 km in a conservative central
  marine gate. Implausible jumps and long gaps reset the marine chain. The Channel
  Tunnel alignment, coastal roads and isolated offshore fixes do not qualify.
  This is a **route-scoped conservative recogniser, not a national water mask or
  universal ferry detector**. Other ferry routes retain manual transport correction.
* Existing durable capture handoff splits road → ferry and ferry → Unknown at
  recorded terminal transitions. These approximate transitions are not surveyed
  berth times. Missing origin/arrival support stays review-only. Ferry geometry
  displays separately in teal on the main map and as recorded geometry in history;
  it bypasses road/cycling matching and contributes no road/NCN evidence.
* Journey details offer REVIEW FERRY SPLIT. Supported complete intervals show UTC
  boundaries before explicit correction. Deterministic child IDs, original revision
  check, immutable revision-keyed evidence backup and a replayable AtomicFile
  journal retain the original source and prevent duplicate correction. One endpoint
  is shared at each split; unknown intervals are never interpolated. Existing route
  edits must be restored before a saved split. RESTORE ORIGINAL RECORDING replaces
  derived legs with the preserved original. This removes subsequent derived-leg
  edits, as the review states. Backups travel in normal app backups; Delete All
  removes them. There is no automatic bulk historical migration.
* The supplied Calais recording offers evidence review, **no guessed split**.
  The missing crossing cannot be recovered from this file. Nothing has remotely
  edited the owner's on-device archive.
* HTTP status/failure categories distinguish DNS, connection, client timeout,
  backend timeout/unavailability, rate limit, request and integrity rejection.
  One short retry is allowed for DNS/connection/eligible transient failure;
  backend deadline/429 requires later retry. Mode/geometry changes prevent stale
  failures overwriting the corrected journey. Raw capture evidence is never replaced.
* Pinned 92 county envelopes provide a cheap negative overseas gate before UK road
  identities/counts are created; canonical/polygon evidence remains responsible for
  positive coverage. This is not an international boundary expansion. French roads
  remain in history/matched display. Existing old overseas cached contributions,
  if any, need explicit Retry/rematch to rebuild; this user's France match failed.
* Driving/ferry/train/unknown matching status/results are not NCN movement evidence;
  their metadata/mode identity is retained without invalidating eligible ledgers.
  Walking/cycling corrections still invalidate the relevant evidence. No global
  startup scan, catalogue update or broad recalculation was added. The one-time
  hash-rule transition can refresh fingerprints once after upgrade.
* Original capture mode and classification timestamp survive checkpoints; legacy
  checkpoints without that history export null rather than guessing from the final mode.
  Bounded 32-entry activity history, capture decisions, 16 manual confirmations,
  ferry review evidence, request bytes, distance integrity and failure category are
  included through the existing streaming diagnostic exporter. Older decisions
  that were never stored cannot be reconstructed. Existing section diagnostics
  remain bounded by prepared request sizes; raw GPS is intentionally retained.

## Verification and device acceptance

Targeted tests cover the scoped real port/Calais fixtures, supported ferry
transitions, tunnel/coastal/isolated fixes, intermittent reception and long gaps,
missing terminals, slow coaches/cycling hints/stationary movement, manual mode
persistence, split idempotence/restore/stale review/journal replay, foreign road
exclusion, main-map ferry cache/display, NCN invalidation and matcher categories
and unchanged distance limits. Existing full Android suite/lint and map synthetic
benchmark must pass before one signed private package is requested.

Keep the supplied 212 device measurements as baseline: reused map 9.17 s;
evidence 1.43 s; coverage 3.74 s; first record rebuild ~22 s; NCN ~1.4–1.8 s.
Those are owner-reported 212 results, not measurements of 213.

Next on Pixel 10/API 37 and Samsung: install over existing app without clearing;
cold/warm start and Map → Journeys → Map; filters/reset/retry; correct a slow coach
and confirm the correction survives restart/rematch failure; inspect the Calais
record's no-split review; replay a supported ferry fixture through capture (a real
crossing remains required for acceptance); confirm teal ferry history/map and no
cross-Channel road line or UK progress increment from France; test split review,
restore and relaunch during correction on a backed-up test archive; export Debug
Journey with the next bus distance rejection. Record timings/ANR behaviour and NCN
changed/reused counts. No actual device acceptance is claimed here.
