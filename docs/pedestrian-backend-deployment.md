# Pedestrian backend deployment — 28 October 2026

Isolated from PR223 onto current main a94996f7. Only pedestrian matching, evidence preparation, request timing, focused backend tests and the diagnostic replay helper are included. No Android files, version changes, database migrations, credentials or NCN reference changes are included.

The source is the tested backend implementation through f04f6139 on PR223. The matcher family remains preloaded_pedestrian_network_v4 for existing NCN readers; pedestrian_selection_version=5 identifies the corrected selector. This retains competing arrival directions and stationary history and defers source hashes until path selection. Geometry/evidence and HTTP phases receive coordinate-free timing diagnostics.

Local isolated suite: 100 tests passed, five database integration tests skipped locally. PostgreSQL CI must pass before merge. The Render service uk-road-tracker-api tracks main with automatic deployment on commit. Prior live commit: 7dd2c58c628ec819ace2b645d18aa296ddb238b4 (dep-dbegppdg1s2s739dhv30).

The private 117-point Charing Cross payload is replayed directly against /match-walking before and after deployment. Raw diagnostic and live response coordinates remain private. Deployment ID, exact commit and live geometry comparisons will be recorded after rollout verification.

The existing Android 0.25.219 (265) has explicit saved-journey rematching, persists processing_result separately from source/raw geometry and displays matched GeoJSON. No new APK is requested by this backend-only deployment. Physical phone acceptance and geographically ambiguous retraces remain separate from endpoint verification.
