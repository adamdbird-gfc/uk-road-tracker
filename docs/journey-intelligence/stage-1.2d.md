# Stage 1.2D · Native journey validation in Gravesend

## Decision

Phase A screened the accessible archive exports and replayed the available evidence. Android integration is deferred pending a labelled native drive through the tagged trial area. The only accepted native candidate is on the broader, untagged comparison reference and has no independent route label. The matcher, Android app and reference asset are unchanged. No changes were merged into main, no new reference data was published, and no APK was built or distributed.

## Real evidence

Screened 93 available per-journey diagnostics exports. Thirteen distinct confirmed Android Driving captures contained original observations, totalling 14,527 fixes. The other 80 exports included 74 native exports in another mode or with unconfirmed transport, plus six from other sources. This is an audit of available exports, not a count of eligible trips in the full on-device archive.

| Evidence | Outcome |
| --- | --- |
| Tagged central Gravesend reference | Zero fixes from the 13 native Driving captures inside the trial boundary. Zero eligible pairs to label or replay in the trial. |
| Broader, untagged Gravesend comparison | Three native drives supplied 296 adjacent pairs touching the extract. The matcher held 295 and accepted one unlabelled 13.458 m interval. No fix came within 25 m of the three targets. |
| Ordnance Road | Both exact Timeline files matched the Stage 1.2C copies byte-for-byte. Their legacy lines contain eight exact repeated subsegments. Tagged provisional alignment produced 144.244 m, including the previously documented Canal Road seam artefact, and zero covered metres. The imports have two and three original Timeline positions, with 180 s gaps and no original accuracy stream. |
| Albion Terrace and Milton Place | The owner observations and screenshots do not establish the precise road, branch or contributors. Neither has labelled native Driving evidence. A historical route or access change near Milton Place remains possible and requires independent evidence. |
| Nearby non-Driving fixes | Native Walking, Running and Train exports supplied 22 points within 25 m of the target roads. They remain excluded by the Driving input gate. Proximity alone does not identify a footpath, a cycleway or a lane. |
| Movement diagnostics | Ten separate logs checked. Two had location rows inside the tagged area, but none within 25 m of a target road. The in-area rows show walking/running or waiting for movement and cannot establish a driving route. |

In the broader control, hold reasons were invalid fixes (141, including excluded providers), reported accuracy (122), insufficient motion (21), distance gaps (7), no alignment (2), disconnected sections (1) and incomplete alignment (1). These are per-pair counts, including pairs with a single endpoint inside the extract. The acceptance count measures what the matcher emitted. Real precision, accepted-coverage recall and false-alignment rates are unavailable because the accepted candidate is unlabelled and there are zero labelled native pairs in the target area. Keep unresolved, unlabelled and incorrect results distinct.

The Stage 1.2C simulated sweep reproduced 536 correct, 58 unresolved and zero incorrect in all 594 pairs. The generated nine-mile tests still demonstrate full and repeat coverage, a retained seven-mile gap, later supported gap fill, and exact section/offset contributors. Other controls cover parallel roads, junction discrimination, access, direction, confidence and the tile boundary. Genuine turns, opposite-direction repeats, adjacent footpaths and historical network changes lack independently labelled Driving examples for this trial. The tagged compiler omits dedicated footway and cycleway classes from alignment competitors, so this snapshot cannot prove discrimination against adjacent paths. Test that case with independent route labels before widening the Driving policy.

## Replay and reference

The new offline audit reads the app's native `raw_capture_samples` wire format with original coordinates, fix time and reported accuracy. It accepts complete schema-v1 diagnostics from confirmed Driving only, and GPS or fused providers for candidate coverage. It preserves the positions of rejected rows in the sequence so they cannot bridge a gap. It does not fall back to filtered samples, matched geometry, imported Timeline positions or screenshot geometry. Repeated exports with identical raw samples count once, and differing original rows for one ID require explicit revision selection. Output is limited to aggregate counts and local export names. Private inputs and operator output are excluded from Git.

The tagged asset is 28,503 bytes compressed and 152,996 decoded. The exact recovered OSM response rebuilt it byte-for-byte. The transport SHA-256 is `a6d6863fe3d89209fbc9d619fbea2147130749cff80dbbdc32668e355995b537`. The decoded dataset fingerprint is `9c3ce6b58789df13f2019b69be50cb95aa56eacdf612b1606973cfaf08fe4aba`. It contains 1,505 nodes and 1,540 atomic edges, 985 eligible for trial Driving coverage. The connected junctions and direction for the field itinerary below were checked in that snapshot. Tests keep section and contributor identities scoped to a reference and a policy version. Boundary pairs remain unresolved where competitors may lie outside the extract. Multi-tile seam behaviour and temporal correspondence require evidence before expanding the reference.

## Performance

Development-host Python medians from five runs. The repeat-heavy case reuses prepared spans on a single 500 m synthetic corridor and measures projection preparation alone.

| Measure | Compared result |
| --- | --- |
| Tagged load and index | 11.4 ms, 1.99 MB traced peak and 0.99 MB retained |
| Native audit | 338 ms to parse and screen 93 exports totalling 51.2 MB, reference already loaded |
| Repeat-heavy geometry | 1,664 paths / 34,944 vertices to 1 path / 21 vertices |
| Repeat-heavy preparation | 5.84 ms for independent lines, 0.32 ms for cumulative geometry |
| Insert 1,664 prepared contributions | 66.60 ms |
| Add another prepared repeat | 0.057 ms |

Android cold/warm Map preparation, Java heap, cache lifecycle, taps, frames and stalls are unmeasured. Build 0.25.242 (288) is the owner-reported baseline, also supported by the Ordnance exports. The earlier packaging receipt maps source `620db84` to package commit `31e6bdd` from #316. Live Render provenance, active Firebase pins, signing and in-place installation were outside this offline phase and were not independently verified.

If the field replay supports integration, use an archive change token and a separate derived cache keyed by dataset fingerprint, matching policy and input evidence revision. Recompute only affected contributions and covered sections off the UI thread. Pin features to a viewport and release inactive reference blocks. An identical repeat should update contributors without invalidating the geometry. Feed displayed section and offset into exact contributor lookup, then load only the selected journeys via existing navigation. Compare repeated Map entry, filter changes, replacement/removal, revision invalidation and section taps on the Pixel 10. Keep national distribution deferred until a bounded multi-tile pilot measures seams, cache behaviour and data-refresh cost.

## Smallest field repeat

Use the current installed Roadprints app and ordinary automatic capture. Follow posted restrictions and omit any segment that is closed or inaccessible.

1. **First drive.** From Albion Road, take Albion Terrace onto Ordnance Road, then continue onto Milton Road. At the junction with Canal Road, stay on the Albion Terrace/Ordnance Road route. That gives the matcher an adjacent branch to withhold.
2. **Second drive.** On a separate saved journey, take Ordnance Road from the Milton Road end towards Albion Terrace. Return to Milton Road by Albion Road, then enter Milton Place from the Ordnance Road/Milton Road junction towards East Terrace. The tagged snapshot marks that part of Milton Place in this direction only. If Milton Place is inaccessible, record the turn you actually took.
3. Once parked, open each saved journey in **Journeys**, choose **Debug Journey**, and share the two original JSON exports privately. Include the approximate time of each drive, the street order and direction, and any detour or branch you took. For the second drive indicate where you entered and left Ordnance Road. A short text note from memory after the drive is sufficient. A known partial traversal can be collected later if both drives cover the full street and the gap still needs a real example.

Use those labels to check expected intervals and nearby alternatives before examining predictions. Count incorrect placements and withheld intervals across all labelled sections. Report precision among accepted placements and recall against all travelled labelled intervals. If the nearest path still lacks a clean negative example, collect one short separately labelled pass next to the road before broadening the policy.

## Verification and lineage

45 focused tests passed: 14 coverage, three attribution contract, eight road-distance, 15 road-recovery and five native audit tests. The private Ordnance fixtures were enabled. No Android suite ran because Phase A did not justify touching the Android runtime. The review branch stacks on draft #318 at `3a04004a927c22a851fe4db76f29289157f34445`, then #317, #316 and the railway predecessors. Validate and integrate that lineage in order before retargeting to main. No issue was closed.

```sh
python3 scripts/audit_native_road_coverage.py /private/roadprints-journey-*.json
python3 scripts/audit_native_road_coverage.py --reference gravesend-network-v1.json.gz /private/roadprints-journey-*.json
python3 -m unittest discover -s scripts -p 'test_audit_native_road_coverage.py'
```
