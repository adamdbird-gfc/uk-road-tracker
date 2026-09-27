# Roadprints Journey Contract v1

Status: approved foundation for the web POC and future Android app.

## Purpose

A Journey is the source-of-truth record for one travel activity. Matching, road discovery, mileage, achievements and personal catalogues are derived from Journeys; a failed matcher must never delete or invalidate the Journey.

The same shape must be usable for:

- Google Timeline imports
- Future Android live capture
- Manual journeys
- Corrected or reviewed journeys

## Privacy and storage

- Timeline JSON and personal Journey records remain local to the device during the current transition.
- Browser storage/IndexedDB is the source of truth and must survive refresh, closing and reopening the app.
- A backend may provide reference data or a data-free handshake, but must not receive raw Timeline JSON or historical personal Journeys.
- Android should use local-first storage and support offline capture.

## Required record

```json
{
  "journey_id": "stable-device-or-source-id",
  "revision": 1,
  "source": {
    "type": "timeline",
    "source_file_fingerprint": "optional-file-hash",
    "source_fingerprint": "stable-activity-fingerprint"
  },
  "started_at": "2026-09-27T08:10:00Z",
  "ended_at": "2026-09-27T08:24:00Z",
  "timezone": "Europe/London",
  "mode": "walking",
  "route_geometry": {
    "type": "LineString",
    "coordinates": []
  },
  "start": {
    "lat": 51.0000,
    "lng": 0.0000,
    "label": null
  },
  "end": {
    "lat": 51.0100,
    "lng": 0.0100,
    "label": null
  },
  "distance_meters": 1200,
  "stops": [],
  "scope": {
    "country": "GB",
    "uk_supported": true,
    "visible_in_history": true,
    "eligible_for_coverage": true
  },
  "processing": {
    "import": "complete",
    "road_matching": "not_required",
    "foot_matching": "pending",
    "last_error": null,
    "attempts": 0
  },
  "derived": {
    "road_matches": [],
    "coverage_contributions": [],
    "service_visits": [],
    "achievement_evidence": []
  },
  "corrections": [],
  "created_at": "2026-09-27T08:25:00Z",
  "updated_at": "2026-09-27T08:25:00Z"
}
```

## Modes

Initial supported values:

- `driving`
- `bus`
- `walking`
- `running`
- `pedestrian`
- `cycling`
- `train`
- `ferry`
- `flight`
- `transit`
- `unknown`

Cycling and rail journeys are retained in history but remain hidden from current road-coverage screens until their presentation is defined. Overseas journeys are retained with their route, date, distance and mode, but are excluded from UK discovery and coverage.

## Processing rules

1. Save and deduplicate the Journey immediately during ingestion.
2. Record its Timeline distance immediately, including repeated routes.
3. Create a separate matching job only when route geometry is eligible.
4. Match road, walking and running journeys through separate queues.
5. Cache successful matching results and link repeated journeys to their representative route.
6. Retry failed stages independently with durable checkpoints.
7. Never require the original JSON file to retry or resume.
8. Treat matching as derived coverage work; it must not control whether mileage or history exists.
9. Sparse or invalid journeys remain visible in the import summary and retain their mileage, but are not sent to a matcher.
10. Manual correction may exclude or restore derived coverage evidence; it must not invent a journey or manually tick a road as travelled.

## State vocabulary

Import and matching states are independent:

- `pending`
- `processing`
- `complete`
- `failed_retryable`
- `failed_permanent`
- `not_required`
- `excluded`
- `needs_review`

The user-facing import experience should report:

- Timeline imported and mileage saved
- Road routes matched
- Walking routes matched
- Routes waiting to retry
- Sparse or unsupported journeys retained but excluded from matching

## Place and PlaceVisit records

Journeys describe movement. Collections and achievements require separate place records and visit evidence.

### Place

A Place is a catalogue item that may be collected or used as achievement evidence. Shared catalogue data is separate from personal Journey data.

```json
{
  "place_id": "service-station-norton-canes",
  "catalogue": "service-stations",
  "category": "service_station",
  "name": "Norton Canes",
  "location": {
    "lat": 52.6500,
    "lng": -1.9500
  },
  "aliases": [],
  "source": "Roadprints reference catalogue",
  "source_version": "v1",
  "eligibility": {
    "uk_only": true,
    "collection_enabled": true
  }
}
```

Places may represent service stations, Wetherspoons, McDonald's, parkrun venues, landmarks, high streets or future collections. A Place must not be treated as visited merely because a Journey passed nearby.

### PlaceVisit

A PlaceVisit is personal evidence that a Journey or Timeline stop may represent a visit.

```json
{
  "visit_id": "stable-visit-id",
  "place_id": "service-station-norton-canes",
  "journey_id": "journey-123",
  "source": "timeline_stop",
  "arrived_at": "2026-09-27T10:20:00Z",
  "departed_at": "2026-09-27T10:42:00Z",
  "dwell_seconds": 1320,
  "observed_location": {
    "lat": 52.6501,
    "lng": -1.9501
  },
  "detection_radius_meters": 100,
  "confidence": 0.94,
  "state": "candidate",
  "collection_eligible": true,
  "user_confirmed": false,
  "created_at": "2026-09-27T10:43:00Z",
  "updated_at": "2026-09-27T10:43:00Z"
}
```

Visit states distinguish:

- `nearby` — the route passed close to a Place.
- `candidate` — location and dwell evidence suggest a possible visit.
- `confirmed` — the user or strong evidence confirms the visit.
- `rejected` — the user says the Place was not visited.
- `excluded` — the visit is outside the relevant collection scope.

When multiple nearby Places are plausible, the UI should allow the user to choose between them rather than silently assigning a visit. A Journey may produce multiple visit candidates, but a collection achievement should use only confirmed or explicitly eligible evidence.

Personal PlaceVisit records remain local alongside Journeys. Shared Place catalogue records may be updated independently without changing the user's history.

## Android requirements

Native capture should create the same Journey record locally, with:

- incremental route points
- pause/resume and interrupted-capture recovery
- offline storage
- explicit permission and capture status
- later matching and catalogue enrichment as derived stages
- no requirement for continuous background capture in the first release
