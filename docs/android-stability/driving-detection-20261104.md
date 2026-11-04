# Driving detection correction — 4 November 2026

Scope #255, independent branch `fix/driving-transition-gate-20261104` directly from verified main `40f3c0d00e171800a157af4b3db5d4125134203b`, targeting main. Source contains #250. Main Android metadata is 266; owner reports installed 267, packaged by open #252 at `c5cafa9c5ea9f9200a04d45aead0839b6b5c6625`. Installed phone, live backend SHA and active Firebase pins are not directly connected/verified. No backend, reference, release, historical journey or manual correction changes.

## Trace and demonstrated defect

1. Android transition callbacks carry activity type/time but no confidence score. IN_VEHICLE intentionally maps to unknown because it includes cars, buses and trains. GPS can also start candidates while callbacks are late.
2. Candidate departure and pending mode changes use existing displacement, freshness, accuracy, duration and mode-compatible evidence checks. Early walking/running labels can be provisional; supported initial foot legs must survive as separate legs.
3. CaptureService's location listener computes `trusted` using the CURRENT mode's foot plausibility. FootTraceValidator limits accepted foot displacement to at most 10 m/s plus bounded accuracy allowance.
4. The SAME `trusted` flag guarded evaluateContextLocation, which is responsible for noticing sustained vehicle movement and resolving the provisional foot label. At 15 m/s with 5 m accuracy and ten-second samples, real 150 m steps fail the foot gate (~103 m allowed). The trusted anchor remains behind, so subsequent movement continues to fail before the transport detector sees it. This is a circular dependency: the label suppresses the evidence required to change that label.
5. ContextJourneyModel already requires measured speed, acceptable accuracy, continuity, sample count, 40 seconds, path and displacement. Foot vehicle movement first becomes unknown; a provisional label is promoted only if no initial foot leg is supported. Unknown becomes driving only with sustained road-only context. Rail-only can become train; road/rail ambiguity remains unknown.
6. Saving retains captured mode and evidence, except failed foot segmentation with vehicle evidence is explicitly downgraded to unknown for review. No separate final automatic classifier rescues this case. Manual updateMode creates a revision, records manual_confirmation, clears obsolete matching results when needed and confirms the chosen transport. Existing coordinator tests protect corrections against in-flight matcher overwrites.

## Minimal correction

Use an independently computed fresh, transport-plausible observation gate ONLY for evaluateContextLocation. It calls the existing CaptureQualityValidator.reliable(...,false). The original `trusted` variable still controls foot distance, lastPoint, GPS dwell, station processing and stop handling. ContextJourneyModel retains its own stricter 50 m accuracy requirement, movement/continuity/extent rules, consent-controlled map evidence and station ownership. No new threshold or direct speed-to-driving conversion. Six additive lines and one gate replacement in one production file.

Confidence: high in the reproduced gate defect and before/after synthetic improvement. Medium/unknown attribution to the owner's 9/10 report: aligned original recordings/activity histories and context settings are unavailable. Do not claim measured 90% field recall or equivalence with Google Timeline.

## Validation and compatibility

33 local production-Java JUnit checks pass across new DrivingTransitionEvidenceTest (7), existing ContextJourneyModelTest and CaptureMovementEvidenceTest. The same ten synthetic GPS fixes stay walking behind the old gate and reach driving with the transport gate and road evidence. No activity callback is needed for that demonstrated path. Existing road/rail ambiguity, rail-only train, cycling, stop/dwell, checkpoint and movement-boundary checks remain unchanged.

Nine new actual CaptureService/Robolectric cases exercise the listener: provisional walking to driving; absent activity callbacks; context disabled; poor/stale fixes; teleports; isolated speed spike; short slow traffic and stops; existing cycling/bus labels; IN_VEHICLE remaining unclassified by itself. Dedicated compilation, lint and full offline Android suite are pending. Existing #250 phantom train/service/recovery/oversized suites are mandatory in the gate. This change does not restore the old train-labelled dwell veto.

## Limits and outstanding evidence

- Map context is OFF by default until consent. Offline destinations do not establish ordinary road-only transport evidence. Disabled, uncached, expired, budget-limited or failed context can leave unknown vehicles requiring manual confirmation. Do not silently enable it or infer cars from all vehicle motion. Inspect the owner's setting and aligned context/decision history before broadening policy.
- Sustained road/rail ambiguity and car-versus-bus distinction cannot be solved by this gate. Explicit bus labels are preserved. Short drives without enough existing confirmation time remain uncertain.
- Speeds below the existing vehicle threshold are not newly classified. Prolonged creeping traffic can resemble walking in the existing model; the new test covers a brief three-fix queue followed by a stop, not a promise to solve all congestion. Further changes need activity/context-supported reproduction, not an arbitrary speed threshold.
- Foot mileage and station inference remain on their previous gate. This avoids relaxing unrelated credit/quality behaviour; later activity callbacks still own station transitions. Do not claim a broader segmentation redesign.
- Battery impact, on-phone latency, field precision/recall and the nine actual journeys are unmeasured. No historic correction/reprocessing.

## Device testing after separately approved packaging

Install over existing data while idle; retain all history. Record map-context consent and location/activity/background permissions. Test walking to car, short urban drive, normal faster drive, traffic lights/brief queue, sustained slow traffic, parked stop, car-to-walk, cycling and bus/train transitions. Include a separate context-disabled run and a road beside rail. For each, save raw evidence and capture decisions, detected mode/time, actual known mode, any split boundaries and manual edits. Compare classification latency and false positives against the same baseline conditions. Confirm original foot approaches survive, train arrivals terminate, unknowns remain reviewable and manual corrections survive reopening/matching. Do not label device acceptance complete until observed results are recorded in #255.
