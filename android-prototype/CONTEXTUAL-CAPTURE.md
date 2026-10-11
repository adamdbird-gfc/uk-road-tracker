# Contextual capture (0.25.176)

Capture uses departure, travel, pause and arrival evidence rather than requiring Android activity callbacks for every boundary. Decisions remain suggestions: users can correct modes in Journeys.

## Preserving legs

- Sustained vehicle movement separates a walking leg even when IN_VEHICLE is missing. Near a station, rail evidence gets time to resolve the departure first; unavailable evidence preserves an Unknown vehicle leg.
- Sustained walking after vehicle travel starts a separate leg at the first supported walking fix. Station alighting retains the initial walking points.
- A missed split containing genuine vehicle evidence is retained as Unknown, rather than discarding travel during pedestrian cleanup. Raw samples remain available.
- Pending place/mode and station evidence is checkpointed. Existing checkpoints remain readable.

## Place and route context

- Bundled railway stations remain usable offline. Public OpenStreetMap ways supply road/rail geometry and building, parking and outdoor footprints.
- Map requests are asynchronous, cover rounded geographic tiles and contain no user/journey identifiers. Tiles are retained locally for seven days, with a 64-tile bound, a 4 MB response bound and at most one request per minute. Fetch failure does not block capture or establish arrival.
- Online context is off until enabled in Utilities > Tracking settings. The choice describes the approximate-area requests sent to Overpass; bundled station recognition and movement capture remain available without it. The preference is checked before queueing a request and again before a queued network lookup begins.
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

## Arrival reliability update (0.25.181)

- Corroborated vehicle stops tolerate isolated reported speeds below 6 m/s when observations remain inside the stop uncertainty area. The original dwell deadline survives. Sustained speed without fresh quiet fixes, real displacement, silence and poor fixes cannot establish arrival; pedestrian stillness rules are unchanged.
- GPS dwell completion uses the supported arrival sample for both the saved end time and travelled-route boundary. Confirmation observations remain in raw capture evidence, outside route distance and matching.
- Activity labels during GPS-corroborated stillness do not queue a walking leg. Context mode suggestions require net displacement as well as elapsed time and path length. Transition origin survives new checkpoints; old checkpoints retain dwell but restart pending mode proposals safely.
- An initial pedestrian label can resolve to an uncertain vehicle mode within two minutes when sustained vehicle evidence exists and no supported initial walking leg exists. Genuine walking before boarding remains a separate leg. Vehicle type still requires evidence or user confirmation.
- Road/rail/outdoor pause holds and station-owned train transitions remain in place. Offline GPS dwell does not claim a named destination.
- Synthetic regression cases cover noisy stops, resuming driving, walking away, pedestrian loops, no-GPS intervals, checkpoint recovery, false departure labels and genuine walks to transport. Private traces remain local and are not included in commits.

This update needs fresh real-device validation; it does not establish that every recognised-place requirement in #76 has been delivered.

## 0.25.182: persistent place boundary evidence

Automatic supported arrivals now populate local `stops` and `places`. GPS dwell remains an estimated stop; footprint/station evidence is supported, and only an explicit user review confirms a place. A venue visit/collection unlock is not inferred from this classification. Arrival time excludes the confirmation wait.

The last supported stop is retained locally across restarts. A subsequent candidate must contain fresh observed fixes in that stop's uncertainty area before its departure is linked. The last measured still fix anchors departure; a walk to transport is retained. No old arrival coordinate or missing journey is inserted into route geometry. Candidate evidence used for the boundary is retained with the departure. Pending departure metadata is checkpointed and preserved through genuine station handoffs.

Journey details offer a place review: correct/confirm its name or reject the stop. Original coordinates/provenance remain available. Background matching cannot overwrite a newer review; rejecting or deleting the origin invalidates its cached use. Existing preference backup includes the compact last-stop record. Place reviews do not change travelled geometry or award collection credit.

Mapped polygon holes remain excluded from destination interiors. The shared service may serve the same area contract from a bounded Postpass query, preserving public OSM object identity without retaining names/addresses. Recording and estimated GPS arrivals remain available without online context. New tests cover evidence classification, departure anchoring, review persistence, deletion and courtyard holes. Real-device validation remains #68.
