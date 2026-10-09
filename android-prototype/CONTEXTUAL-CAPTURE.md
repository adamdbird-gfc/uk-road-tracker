# Contextual capture (0.25.174)

Capture uses departure, travel, pause and arrival evidence rather than requiring Android activity callbacks for every boundary. Decisions remain suggestions: users can correct modes in Journeys.

## Preserving legs

- Sustained vehicle movement separates a walking leg even when IN_VEHICLE is missing. Near a station, rail evidence gets time to resolve the departure first; unavailable evidence preserves an Unknown vehicle leg.
- Sustained walking after vehicle travel starts a separate leg at the first supported walking fix. Station alighting retains the initial walking points.
- A missed split containing genuine vehicle evidence is retained as Unknown, rather than discarding travel during pedestrian cleanup. Raw samples remain available.
- Pending place/mode and station evidence is checkpointed. Existing checkpoints remain readable.

## Place and route context

- Bundled railway stations remain usable offline. Public OpenStreetMap ways supply road/rail geometry and building, parking and outdoor footprints.
- Map requests are asynchronous, cover rounded geographic tiles and contain no user/journey identifiers. Tiles are retained locally for seven days, with a 64-tile bound, a 4 MB response bound and at most one request per minute. Fetch failure does not block capture or establish arrival.
- Building/parking arrival requires sustained stationary fixes and an accuracy circle within the footprint, away from a mapped vehicle road. A POI label or missing map geometry alone is insufficient.
- Road/rail/outdoor pauses extend the normal stop timer up to 15 minutes. Train pauses remain open until supported alighting. This cap lets long ambiguous stops become reviewable boundaries rather than unbounded recordings.
- A mapped destination dwell ends the route at arrival, not when the confirmation timer expires. Raw dwell samples remain available for service-area evidence.

## Mode suggestions

- Rail-aligned sustained departure after a station dwell can establish a train leg without activity callbacks. Road/rail ambiguity remains Unknown.
- Sustained road-aligned vehicle movement suggests Driving. No bus-route absence heuristic is used.
- Sustained running speed splits walking/running legs; cycling is not changed to driving based on speed.
- Train calls and brief traffic-light stops are not arrivals. Poor GPS and out-of-order fixes do not establish contextual boundaries.

## Service-area test

Existing collection logic recognises stops automatically from original timed capture samples: at least three minutes, four fixes, accuracy at most 50 m, slow movement and proximity to the actual service-area site points. Driving past does not qualify. A collection test unlock exposes the result in Progress; there is no unrestricted manual tick-off.

## Validation

Synthetic tests cover station dwell/departure with missing activity labels, alighting, through trains, road/rail ambiguity, destination footprint accuracy, traffic lights, outdoor pauses, walk/run transitions and restart recovery. Uploaded movement logs are used only for local replay, never committed. Full Android tests, lint and stable signing verification precede publication.

Real-world accuracy depends on GPS and public map coverage. Missing context preserves uncertain travel instead of claiming a definitive mode or destination.
