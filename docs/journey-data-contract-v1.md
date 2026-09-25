# Roadprints journey data contract v1

This contract is the boundary between journey capture, processing and presentation. It is deliberately mode-neutral so the Android capture layer can be added without changing the road and settlement features.

## Journey record

Each recorded journey has:

- `id`: stable client-generated identifier
- `source`: `google_timeline`, `android_background`, or `web_import`
- `mode`: `driving`, `walking`, `running`, `cycling`, `train`, `transit`, `flight`, `ferry`, or `unknown`
- `modeConfidence`: `high`, `medium`, or `low`
- `startedAt`, `endedAt`: ISO timestamps
- `start`, `end`: optional coordinates and human-readable labels
- `distanceMeters`: source distance where available
- `geometry`: optional route geometry
- `processingStatus`: `captured`, `queued`, `processing`, `ready`, or `failed`
- `processingError`: safe, non-personal error code when processing fails
- `roadMatchStatus`: `not_applicable`, `pending`, `matched`, or `failed`

## Mode rules

| Mode | Record in history | Road matching | Foot matching |
|---|---:|---:|---:|
| Driving | Yes | Yes | No |
| Walking/running | Yes | No | Yes |
| Cycling | Yes | No | No initially |
| Train/transit | Yes | No | No |
| Flight/ferry | Yes | No | No |
| Unknown | Yes | No until confirmed | No until confirmed |

Every journey is retained in the archive. A journey only changes road or settlement progress when its mode is compatible with that matcher.

## Processing behaviour

1. Capture stores the journey as soon as it is available.
2. Classification may be confirmed or corrected without changing the original geometry.
3. The coordinator sends only eligible journeys to each matcher.
4. Non-road journeys remain visible in history and can support future transport collections.
5. Low-confidence or unknown journeys are shown as “Needs confirmation”; they are never silently treated as driving.

## Privacy boundary

The web app keeps the local archive on the device. The future Android layer may provide background location, but it must pass through this same contract and explicit permission state. No personal journey geometry is sent to the shared settlement-inventory service.


## Stop and visit events

A journey may contain zero or more stop events. Stops are independent of transport mode, so collections can work for driving, walking, train and future Android journeys alike.

Each stop has:

- `id`: stable identifier
- `journeyId`: the journey on which it occurred
- `placeName`, `latitude`, `longitude`
- `startedAt`, `endedAt`, and optional `dwellSeconds`
- `source`: Timeline import, Android location or manual confirmation
- `placeCategories`: service station, pub, restaurant, station, landmark, etc.
- `confidence`: high, medium or low
- `visitStatus`: `nearby`, `candidate`, `stopped`, or `confirmed`

A nearby place is not automatically treated as a visit. Collection rules decide whether dwell time, geofence evidence, journey mode and user confirmation are sufficient. This prevents a motorway pass-by from incorrectly unlocking a service-station collection.

Stop events are retained even when they do not match a current collection, allowing new collections to be added later without recollecting the journey.
