# Automatic on-foot fragmentation — 4 November 2026

Status: implemented; full Android CI pending. Ref #253 (historical split/join remains separate).

## Verified baseline and scope

Main/release source: `3a89e6074eed420609116f20e2ad64fc123484dd`, Android
0.25.222 (268), confirmed by all seven exports and the owner. #257 merged as
`566f03f710eca96c728f35d7a92fb706706efae8`; #258 as
`d571aa21b0d2884792bf5bab80574e401d2ad21c`. #250 is an ancestor. All open PRs
were checked: none addresses this defect. #253 is an editor backlog item, not
an automatic capture correction. Latest main was independently read through
GitHub and the local clone. Backend deployment and active Firebase reference
versions are unknown and unnecessary to establish these upstream boundaries;
neither is changed or queried with private journey data.

## Evidence and confirmed mechanism

All six 4 November JSON exports, the 7 October export and supplied screenshots
were retrieved. These are saved snapshots, not a complete callback/decision log.
All six have automatic capture, revision 1, usable capture evidence and completed
pedestrian matching. Consecutive end/start timestamps are exactly equal.
Raw snapshots retain the confirmation tail beyond their backdated route end.

An independent Java probe ran the production movement/context algorithms over
the private original samples. All five internal boundaries reproduce exactly:

| Boundary UTC | Saved mode → next mode | Snapshot reason | Reproduced path |
| --- | --- | --- | --- |
| 12:02:41.749 | walking → running | stop_or_manual | activity confirmation |
| 12:17:11.738 | running → walking | context_mode_walking | GPS context |
| 12:21:59.593 | walking → running | stop_or_manual | activity confirmation |
| 12:26:16.000 | running → walking | stop_or_manual | activity confirmation |
| 12:33:23.955 | walking → running | stop_or_manual | activity confirmation |

The sixth export additionally reproduces a GPS context walking boundary at
12:35:03.344, confirmed by its retained 12:36:03 fix. No subsequent journey
snapshot was supplied: do not infer a seventh saved journey or final arrival.

`CaptureService.handleTransition` treated WALKING/ON_FOOT and RUNNING as different
transport modes. It queued `commitPendingActivityMode` after 15 s; that method
used `CaptureMovementEvidence.modeChangeStart` to backdate a supported split.
Running corroboration requires >=5 fixes, >=60 s and >=100 m; walking requires
>=3 fixes, >=20 s and >=30 m. The latest qualifying callbacks before the four
activity boundaries are 12:02:06.086, 12:21:52.813, 12:25:52.243 and
12:33:19.085. Each replay returns the exact stored boundary using the retained
confirmation samples. This confirms the code mechanism, though exact Handler
execution times cannot be recovered from these snapshots.

Independently, `ContextJourneyModel.sample` proposed running from walking at
2.6–4.8 m/s, or walking from running at 0.5–1.8 m/s. Running→walking needed
>=4 samples, >=40 s, >=40 m adjusted path and >=25 m extent outside uncertainty.
The 12:17:11.738 proposal confirms at 12:17:57.695 exactly in replay.
`evaluateContextLocation` calls `prepareStationSplit` and `finishCapture`;
activity confirmation uses the same split/save lifecycle. Retained tails seed
the next capture with a shared endpoint, explaining the immediate restarts.

`finishCapture` writes station/context reasons when available, otherwise
`stop_or_manual`. Activity splits do not set either reason: this value does
**not** prove Stop was tapped or dwell occurred. No user manual intervention
is reported. Frequent STILL→RUNNING callbacks are recorded, often seconds
apart. Existing GPS movement/arrival handling clears these provisional timers;
the five reproduced boundaries require no genuine stationary dwell.

The 7 October snapshot covers 37m10s, matched distance 5,471.5 m, 281 validated
matching points (285 raw samples), stored walking. It has no activity/capture
decision history and is revision 3. Its export app version is the exporting
0.25.222, not evidence of the original recording version. It demonstrates a
successful single saved journey, not the original live capture decisions.

The two splitting policies predate #258; #258 only separated transport-quality
GPS context input from the foot plausibility gate. It did not introduce these
pace thresholds or activity splits. This incident does not establish #258 as
the cause. Existing #258 listener/vehicle tests remain mandatory.

## Minimal correction

Only two production files change. CaptureService treats walking/running activity
changes as the same on-foot category both when scheduling and when committing
a pending transition (including delayed/restored pending state). ContextJourneyModel
no longer proposes boundaries between walking and running. The initial capture
mode remains; activity history still records pace changes. Vehicle/rail/cycling
transitions, foot→unknown vehicle evidence, station handoffs, arrival/destination
dwell, sampling-gap guards, GPS validation and phantom-train termination are
unchanged. No new state, schema, storage edit, historical reconstruction or
achievement calculation change is introduced.

## Validation and limits

- Original private samples reproduce five exact internal boundaries and the final
  export's context boundary; original coordinates are not committed.
- Location-free fixtures retain timing, accuracy, speed and callbacks, replacing
  geometry with cumulative consecutive-fix distance on a straight line. Seven
  snapshots contain 854 raw samples including overlapping confirmation tails.
  Source hashes are retained; these adaptations are not an exact geographic or
  full process-lifecycle replay.
- Standalone production-Java model/evidence checks: 30 pass. Against the old
  context model, the same checks fail three on-foot continuity assertions.
  Local compilation uses ECJ/Java 17 and a compile-only MatchingCoordinator
  seam for an unrelated inspection method; Android CI uses the real app class.
- New listener tests replay the seven snapshots, pace changes/brief STILL noise,
  delayed pending changes and genuine sustained GPS arrival. Full Android CI
  must also run station, driving, phantom train, recovery, archive and achievement
  suites. Two old model tests expecting walk/run splits now enforce continuity.
- CI uses the existing no-APK/cache-only reference/network-isolated workflow;
  only this branch is enabled and affected suites made mandatory. Four existing
  opt-in NCN candidate skips are expected; no NCN work is performed.

Physical Pixel 10/Android 17 acceptance remains necessary: repeat the Wednesday
loop automatically with natural pace changes and a short walk; confirm one
on-foot journey and a genuine arrival after sustained dwell. Also observe the
next walk/train/walk commute and a drive with traffic stops. This source-only
task does not package an APK or repair the six historical recordings.

## Delivery constraints

Branch: `fix/on-foot-fragmentation-20261104`, based directly on verified main.
Leave the focused PR open and unmerged. No APK build/distribution, backend or
Firebase deployment, production/history mutation, issue closure or adjacent
backlog work. A future approved merge must retain `[skip render]` protection.
