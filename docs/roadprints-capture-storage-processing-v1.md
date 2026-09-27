# Roadprints Capture, Storage and Processing v1

Status: foundation for the Android companion; the existing web POC remains the reference implementation.

## Boundary

Roadprints has three independent layers:

```
Capture → Local Journey store → Derived processing
                         ↘ Places and collection visits
```

Capture creates a Journey. Storage makes it durable. Processing enriches it. No layer should require the others to be online at the same time.

## 1. Capture layer

Capture produces a Journey draft and does not perform route matching.

Sources:

- Google Timeline import
- Android live GPS capture
- Manual journey entry
- Future supported imports

The first Android slice should provide:

- Explicit Start and Stop controls
- Clear location permission state
- Local GPS breadcrumbs
- Mode selection, initially driving or walking
- A visible recording state
- Recovery if the app is interrupted
- Save on Stop, even when offline

The first Android release does not require continuous background capture. Background capture can be added later with explicit consent and clear Android permission handling.

Capture output must contain enough information to create the shared Journey record:

- Stable journey ID
- Start and end time
- Timezone
- Mode
- Route points
- Distance
- Source
- Capture status

## 2. Local storage layer

The local store is the source of truth for personal data.

It must persist:

- Journeys
- Journey revisions
- Mileage ledger
- Road and walking processing jobs
- Processing attempts and errors
- Derived matched geometry
- Corrections
- Places and PlaceVisits
- Collection and achievement evidence

Storage rules:

- Write the Journey and its mileage before matching begins.
- Use stable IDs and idempotent upserts.
- Preserve every Journey, including repeats and journeys that cannot be matched.
- Keep raw personal data on the device.
- Survive refresh, app close, device restart and offline periods.
- Never require the original Timeline JSON or route capture to retry processing.

The browser currently uses localStorage and IndexedDB. Android should use an equivalent local database behind the same logical repository interface.

## 3. Derived processing layer

Processing consumes stored Journey IDs and writes derived results back to storage.

Pipeline:

1. Validate the Journey.
2. Deduplicate only for expensive matching; retain every Journey and its mileage.
3. Classify UK support and visibility.
4. Match driving/bus routes through the road queue.
5. Match walking/running/pedestrian routes through the on-foot queue.
6. Save matched geometry and road/path evidence.
7. Derive coverage and unique route progress.
8. Evaluate PlaceVisits, collections and achievements.
9. Recompute only affected summaries after corrections or new evidence.

Processing requirements:

- Road and on-foot queues remain separate.
- Jobs are independently retryable.
- Failed matching must not remove the Journey or mileage.
- Successful results are cached and reused.
- Processing may continue after the user leaves the screen.
- The UI reports imported, matched, retryable and excluded states separately.
- The user can inspect and retry a failed stage without re-importing or re-recording.

## Repository boundary

The future Android app should expose logical operations equivalent to:

- `saveJourney(journey)`
- `getJourney(journeyId)`
- `listJourneys(filters)`
- `saveProcessingJob(job)`
- `claimNextJob(queue)`
- `completeJob(jobId, result)`
- `failJob(jobId, error)`
- `savePlaceVisit(visit)`
- `rebuildAffectedDerivations(journeyIds)`

The current web implementation can continue using its existing functions and IndexedDB stores. The boundary is introduced incrementally; no rewrite of the web matcher is required.

## Android implementation sequence

1. Create a local Journey repository.
2. Build a Start/Stop capture screen.
3. Save a completed capture as the shared Journey shape.
4. Display it in the existing journey history model.
5. Reuse the existing processing contract for route matching.
6. Add PlaceVisit detection and collection evidence later.
7. Consider background capture only after foreground capture is reliable and privacy messaging is clear.
