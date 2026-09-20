# Shared local-road catalogue batch pipeline v1

## Purpose

Build compact, shared local-road inventories for Roadprints settlements without collecting or retaining personal travel data.

The input is public OpenStreetMap Great Britain road reference data filtered to the existing Roadprints eligibility classes:

- `residential`
- `unclassified`
- `tertiary`
- `living_street`

The output is one deduplicated road-name catalogue and count per official settlement boundary. It supports local-road percentages and settlement-boundary map focus. It does not contain users, Timeline files, journeys, route geometry, corrections or achievements.

## Batch behaviour

The administrator explicitly starts the GitHub Actions workflow and selects a batch file under `.github/reference-inventory-batches/`.

A batch:

- contains 1–100 official settlement codes;
- is rejected if it contains duplicates or invalid codes;
- loads all selected official boundaries once;
- uses a spatial index so each road is tested only against intersecting candidate boundaries;
- replaces a settlement catalogue only after the source scan has completed;
- records source key, source version, licence and completion status;
- deletes the raw PBF and filtered intermediate files whether the job succeeds or fails.

The current source is downloaded once for each bounded batch. No raw source is stored in Render Postgres.

## Kent/Medway pilot

`.github/reference-inventory-batches/kent-medway-pilot.txt` is the first regional validation batch. It includes established towns for parity checking plus Maidstone and Reigate, which already prove the shared-catalogue display and map-focus route.

Acceptance checks:

1. Every requested settlement reaches `ready` or a visible, retryable failure state.
2. New shared totals display in Roadprints without new user data leaving the browser.
3. Map focus opens the official shared boundary and returns to Progress.
4. Existing Gravesend and Gillingham behaviour remains unchanged.
5. Raw source cleanup runs on success and failure.

## Scale path

This is the bounded reference-ingestion runner, not the final always-on worker. It is intentionally suitable for validation on the current low-cost service tier.

Once the regional pilot is proven, the same batch contract can be executed by a database-backed worker or workflow service. The worker will queue regional batches, checkpoint progress and process the national catalogue without using browser jobs or storing personal travel data.
