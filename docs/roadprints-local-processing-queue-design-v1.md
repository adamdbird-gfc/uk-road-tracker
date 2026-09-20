# Roadprints local processing queue design v1

Status: implementation design for Step 7  
Date: 20 September 2026  
Privacy model: device-local only

## Purpose

Make the road-matching queue genuinely resumable after a refresh, browser restart or interruption, without uploading personal Timeline data or requiring an account.

## Existing behaviour retained

- Completed matched road Journeys are stored in the existing IndexedDB journey archive.
- Travel mileage continues to include repeated Timeline activity.
- Coverage remains deduplicated from matched evidence.
- Corrections, delete controls and import-file duplicate protection remain unchanged.
- On-foot matching keeps its existing local activity archive and resumable handling.

## Gap being fixed

The existing road matcher stores completed Journeys, but keeps still-unmatched parsed road Journeys only in page memory. A restart therefore preserves finished work but cannot resume the remaining road queue without the user selecting the original file again.

## Local record

A new IndexedDB record will store one active road-import queue:

- queue version and creation/update timestamps;
- source file hash and display name;
- compact pending Journey candidates: local identifiers, dates, mode, Timeline distance, repeat metadata and route points;
- pending, completed, failed and retry counters;
- a short rolling throughput sample for a new estimated completion time.

The record never leaves the device. It is deleted only when every pending item has either completed or has been deliberately cleared with the user’s road-data reset.

## Processing behaviour

1. Parse locally and deduplicate as today.
2. Save the compact pending queue before the first match request.
3. Match at the existing bounded concurrency.
4. After each result, atomically checkpoint the queue and archive any successful geometry.
5. On restart, restore completed map data and the pending queue.
6. Resume pending work in the foreground when the user re-enters saved Roadprints data.
7. Keep failed items visible as retryable work; do not silently discard them.

## Estimated completion

After enough completed samples to avoid a misleading early guess, show:

- completed and remaining journeys;
- estimated time remaining;
- a plain-language note that estimates adapt to route complexity and connection speed.

A new session recalculates its estimate from newly observed work; the underlying pending/completed counts remain durable.

## Acceptance criteria

- Interrupting a road import does not require selecting the file again to continue.
- Already matched Journeys are not rematched or duplicated.
- Progress, mileage, coverage and corrections remain intact.
- The ten-journey test file completes normally.
- No Timeline file, Journey, route point, queue record or user identity is stored remotely.
