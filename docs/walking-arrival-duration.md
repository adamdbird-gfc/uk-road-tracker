# Pedestrian arrival duration repair

A successful walking rematch previously updated distance and geometry but retained an old capture end time, even when new preparation removed a prolonged stationary tail. New automatic captures already use the corroborated arrival boundary; this change also applies validated stationary-tail timing when saving a manually ended capture and when successfully rematching an older capture.

Arrival correction requires a native walking/running capture, resolved timed GPS evidence, at least five minutes of corroborated stationary tail, and an arrival inside the saved journey interval. Full raw confirmation samples can establish arrival when an older filtered route lost the final quiet observations. The candidate must still lie inside the aligned saved route's time interval. Duplicated or nonchronological raw observations are skipped in the derived timing analysis without changing the recording. No timing evidence is inferred from the matched road geometry.

The first end time is retained as `original_ended_at`. `end_time_evidence` records the derived arrival and confirmation timestamps. Estimated GPS arrival stops receive the same boundary and retain their original timestamps. Raw geometry and samples remain unchanged. Repeated rematches are stable; failed matches, imports, untimed routes, and genuinely moving walks do not receive this correction.

Local replay of a supplied older capture derived an 11m 22s journey instead of 95m 10s. This is an evidence-based estimate, not a confirmed arrival time. The uploaded export was not changed or committed. Synthetic tests cover long indoor stays with speed spikes, recovery using full raw confirmation, genuinely moving walks, imports, idempotence, and successful versus failed rematch persistence.

Existing journeys are corrected when successfully rematched after release; there is no automatic archive migration.
