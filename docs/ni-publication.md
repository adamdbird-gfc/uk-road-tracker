# Verified NI settlement publication — 15 October 2026

The integrated preview completed successfully in Actions run 38705653281 at
08:46:34 UTC. Its immutable version is `3d258a98fc0a74c1`; 1,055 added/replaced
files passed exact-byte/header checks, and 17,097 GB/collection files plus two
Firebase-managed initialization files retained their Hosting hashes.

The publication request promotes that exact version without rebuilding or
uploading reference files. It pins the entire Hosting file map and serving
configuration from the successful preview, and accepts only the known GB
baseline or an idempotent retry of the same UK version. It checks all 1,055 NI
assets and the manifest on live before any NI registration is committed.

NI metadata, names, official-name aliases, valid MultiPolygon boundaries and
19,682 road-count entries are registered under `nisra_sdl_2015`. Source names
remain verbatim; administrative county/region stay null. The original GB
catalogue is not edited or reassigned to NISRA. County memberships remain in
the already-prepared Android offline index for the next APK.

Registration runs in one transaction with reference-table locks. It checks the
existing GB catalogue and ready totals, rejects identity/source conflicts,
checks boundary/centre validity and all NI postconditions, and fingerprints
complete non-NI shared reference rows before and after. IDs, timestamps,
boundaries, aliases, road references and inventory counts remain unchanged.
Repeating the operation inserts no duplicate rows and does not rewrite an
existing NI identity. A conflict rolls back the complete registration.

The publication workflow runs real PostGIS integration tests for successful
registration, repeat-run identity preservation, overlap matching, zero-count
settlements, read-only preflight, GB total drift, wrong-source identities,
invalid geometry and rollback after partial insertion. Public completion
results and bounded verification progress are retained as Actions evidence.
No APK build or distribution is requested by this data-publication slice.
