# Disneyland capture and pedestrian evidence — 24 October 2026

Baseline: private 0.25.213 (259), signed source 5e9f1d56845584559fa8444715d9df0e4f650466,
branch handover 3015b18e2c258be86e720582d7391e65f5c6f733. Candidate 0.25.214 (260).
No merge from main: preserve 212 map-contribution/compact-record and NCN caches.

## Confirmed evidence and causes

All nine original diagnostic exports remain unchanged in the owner's private files.
Regression fixtures retain their capture/route coordinates, accuracy and speed,
with relative timestamps and source SHA-256; identifiers/activity/stop history and
absolute times are omitted from Git. They are not a substitute for the originals.
Five 504s, one connection failure and three partial matches are confirmed.
Submitted payloads are small (16–185 retained points), not oversized JSON.

The map-context backend explicitly accepts UK areas only. Android also defaults
online context off. No Disneyland reference was bundled; theme_park was absent
from destination classification. Consequently anonymous 60m estimated stops,
rather than an encompassing resort site, governed departures. Even an available
site allowed sustained internal walking/exercise by design. This correction
retains exercise behaviour for schools/parks while making theme parks require
a confirmed boundary departure (including vehicle-like attraction callbacks).

100m-accuracy, speed-unavailable recurring fixes already exist in raw capture
samples (17 at 2.7810452/48.8693617 and 21 at 2.7771372/48.8729185).
Existing save validation removed these, explaining 7,471m→931m and 18,754m→2,833m;
these are not newly introduced distance corrections. Both fused and native
location callbacks feed capture. Original exports lack per-fix provider/receipt
metadata, so the producing provider/cached-fix cause cannot be established.
Do not attribute it to Android, the exporter or provider without further evidence.

Pedestrian fallback performs sequential eight-point chunks, up to three radii,
recursive NoMatch subdivision and polite upstream delays within 55 seconds.
This can amplify small noisy payloads; it is a confirmed code mechanism, not
proof of the exact production timeout bottleneck. No selected Render workspace
is available; no production log correlation or backend rollout is claimed.

## Implementation

- Pin the actual OSM Disneyland Paris resort polygon, way 205732748, fetched
  24 October 2026 via the public OSM API. Complete 594-node closed ring, no
  hand-drawn expansion. Source/version/licence in the asset; © OpenStreetMap
  contributors, ODbL-1.0. https://www.openstreetmap.org/way/205732748
- Use the small offline reference independently of optional UK context. Do not
  spend context-network budget on unsupported foreign areas. No road/NCN
  reference or cache identity changes.
- Moving pedestrian entry: three well-inside, ≤25m-accuracy fixes spanning 30s;
  preserve the approaching recording through its first supported inside fix.
  Entry confidence is distinct from stationary dwell. Restart re-confirms entry.
- Theme-park stays suppress ordinary sustained walking/running/cycling and
  vehicle-like attraction hints. Existing three outside-fix/30s, movement,
  freshness and accuracy+10m edge-margin safeguards govern departure. Departure
  seeds only the outside recording, not hours of internal walking. Existing
  checkpoints/stop IDs persist visit state; refinements now persist name/identity
  as well as geometry.
- Entirely internal automatic captures are retained atomically under
  `destination-internal-evidence/<capture-id>.json`, outside JourneyStore,
  matching and progress. Ordinary in-place movement uses the existing bounded
  checkpoint buffer (20min/360 fixes). A browsable long-term internal-movement
  journal remains a follow-up. Historical journeys are neither rewritten nor
  deleted. Matching refuses entirely internal retained captures.
- Retain rejected live samples in raw evidence but prevent stale, poor-accuracy
  or implausible observations from voting for capture distance/context/stops.
  Walking preparation removes nonmonotonic/stale and >50m fixes before stillness
  and dynamic-programming validation. Existing distance-ratio checks remain.
- Split new automatic pedestrian captures at moving GPS gaps >120s and beyond
  75m/combined uncertainty. Never count the unsupported interval as distance.
  Existing saved gaps remain blocked from matching; no historical rematch/migration.
- Pedestrian request simplification has 3m tolerance, preserves bends/loops and
  limits straight chords to 60m/eight source intervals. Raw/saved GPS unchanged.
  Existing bounded network retry, atomic duplicate protection and durable failed
  state remain; 504/rate-limit workloads are not immediately repeated.
- Backend native walking only: share a 16-subdivision budget across recursive
  fallback chunks. Exhaustion is an omitted section; verified siblings survive.
  If no geometry succeeds, return retryable 503, never a terminal off-path result.
  Keep the 55s deadline; no timeout increase. Backend change awaits rollout.
- Mark inherited stop associations explicitly `gps_continuity_from_arrival=false`.
  Their historical arrival is context, not GPS coverage of intervening time.

## Scope limits and acceptance

Eight fixture routes lie entirely within the actual mapped footprint. The long
22 October evening route extends beyond it toward the eastern hotel area;
58/185 retained fixes are outside. Do not silently enlarge the boundary or hide
that supported departure. Further hotel/connecting-ground scope needs mapped
site evidence and field acceptance. Existing UK/NCN geography and source-identity
gates remain; no catalogue rebuilding or global historical reassessment.

No phone is connected. Automated service/geometry/regression tests cannot prove
Android 16/17 background behaviour or Pixel timing. Install over 213 without
clearing data; validate resort entry/stay/edge drift/ride hints/departure,
background restart, ordinary UK exercise and driving, offline retry, map startup
and warm-tab timings, and NCN incremental reuse. Private packaging only; no
Firebase upload, distribution, notifications or Play publication.
