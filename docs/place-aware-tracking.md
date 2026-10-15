# Place-aware tracking

This slice distinguishes an incoming journey, a stay, departure and sustained activity within a place. It does not change Timeline imports, explicit/manual recording, or station-specific handoffs. It does not create a release APK.

## Evidence and fallback

Confirmed arrivals retain bounded mapped footprint geometry (including holes) with the stop. Campus amenities (school, college, university and hospital) and shopping centres take precedence over individual buildings regardless of map feature order. Offices, shops, restaurants and homes continue to use contained building footprints. Site containment is context, never sufficient evidence of arrival by itself.

An unavailable/disabled map lookup leaves a small estimated stay area (at least 60 metres around the supported stop), rather than inventing a named place. A later successful context lookup may refine that stay. Polygon snapshots are limited to 2,000 outer vertices and 100 KB; larger polygons use the estimated fallback. Rejected stops are excluded.

A pedestrian dwell can survive isolated provider speed disagreements while fresh, quiet observations remain inside a tight cluster. Sustained movement, stale quiet observations or displacement still cancels the dwell. The incoming route ends at the beginning of corroborated dwell; confirmation samples remain outside the travelled route.

## During the visit

Automatic idle tracking buffers up to 360 fixes / 20 minutes. Incidental local walking, brief running callbacks, boundary jitter and missing updates do not alone start a journey. Three credible outside observations spanning 30 seconds, beyond the boundary plus accuracy and 10 metres, must also establish actual movement. Stations retain their existing model. Buffered evidence is checkpointed locally every 30 seconds and tied to the originating stop so it cannot connect unrelated visits after a restart.

Sustained activity can qualify without crossing a boundary. Initial thresholds are deliberately conservative, configurable in source and require device validation:

| Activity | Continuous evidence | Adjusted path | Maximum displacement from start |
| --- | --- | --- | --- |
| Running | 2 minutes at 2.2–6 m/s | 220 m | at least 50 m |
| Cycling | 2 minutes at 3–15 m/s | 350 m | at least 50 m |
| Walking | 6 minutes at 0.8–2.2 m/s | 450 m | at least 100 m |

Extent is the maximum displacement during the activity, not final displacement: closed-loop parkruns remain eligible. Missing-speed observations do not vote for an activity. The original candidate points seed a qualifying session. Android running callbacks require running-speed corroboration before splitting an existing walk.

## Shared map service dependency

The companion backend change expands the existing bounded cached map-context feed to school/campus/mall polygons, joins complete multipolygon relation rings, retains holes and public venue names, and avoids clipping site polygons at tile boundaries. Private addresses and private residence names remain excluded. Cache content revision 2 replaces old tiles while preserving request caps, backoff, response and storage limits. The Android cache directory changes to v3 to request the richer feed once available; estimates still work before backend rollout.

## Regression and device checks

Automated cases cover campus/building precedence, school movement with a false running hint, a closed-loop campus run, cycling, sustained walking, brief sprint rejection, real departure, boundary noise, estimated offline stays, saved/reloaded boundaries, mixed-provider dwell and walking-speed rejection of running labels.

For the combined release, validate on Samsung with the screen locked: travel to school, arrive, move between buildings, leave school; validate a sustained parkrun-style loop at a large venue separately. Confirm correct journey endpoints, preserved activity starts and absence of incidental school journeys. Real-world accuracy remains unverified until that device test. No personal route samples are committed.
