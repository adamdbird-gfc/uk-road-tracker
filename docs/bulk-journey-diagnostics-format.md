# Bulk journey diagnostics ZIP v1

See the [evidence audit](bulk-journey-evidence-audit.md) for storage and retention findings.

In Roadprints, open **Utilities → Export all journey diagnostics**. Choose **Downloads** or another local folder in Android's file picker. Wait for the saved-journey count. Import the resulting ZIP into Map Laboratory using **Choose journey file**. The ZIP contains precise historical locations and timestamps.

## Archive contract

- Standard single-volume ZIP with UTF-8 JSON, DEFLATE compression, and no encryption. The writer streams a saved record at a time and never calls the matcher or archive migration. Current archive files take precedence over duplicate legacy preference entries.
- `journeys/1.json` to `journeys/N.json` contain objects with `journey`, `stored` and `unavailable`. The `journey` object is the saved record, including ID, source, coordinate order, corrections, unknown fields and explicit nulls. Evidence arrays contain known JSON field paths. A tracked path is unavailable if it is absent, null or empty. Presence alone does not establish precision, quality or completeness.
- The final entry, `manifest.json`, contains `format: "roadprints_bulk_journey_diagnostics"`, `schema_version: 1`, `exported_at_utc`, `app_version`, `app_version_code`, `journey_count`, `journeys`, and `export_complete: true`. Each index item has `journey_id`, `started_at`, `ended_at`, `mode`, `title` and `timezone`. Position in the index identifies the numbered record. The consumer sorts by absolute start time, then ID, with undated records at the end.
- The list of committed records is captured at the start. A save that replaces a record during export cannot change a descriptor already open. A concurrent change may be reflected in a record that has yet to be opened. A disappeared or corrupt record fails the export with its position. Cancellation or a read failure omits the completed manifest. The app attempts to remove the partial document through its provider.
- The Lab checks the ZIP directory, record sizes, checksums and IDs, then decompresses the selected journey on demand. V1 Lab import supports standard ZIP offsets, up to 65,534 total entries, and records up to 128 MiB uncompressed. The archive itself remains in the selected browser tab for the session.

## Route-first display

A saved matched line supplies the sequence and the joins. Without saved match geometry, the Lab displays available recorded geometry and reports that provenance. It retains separate components where no supported join exists, and honours numbered matched-edge removals. For walking routes, a classified road reference can replace a parallel section where both endpoints and ordered geometry support the same corridor. A short join across consecutive saved components requires an ordered recorded span and a corresponding compatible network reference. Ambiguous candidates remain unresolved. Out-and-back travel stays in the sequence. Reference branches never enter the display simply because they are present in the archive.

**Existing interval engine** replays the same journey's recorded coordinates against locally stored references. **Recorded lines** shows the saved input for a third comparison. The legacy archive workflow continues to use its existing reference and cumulative replay behavior. ZIP display applies no permanent road coverage credit. ZIP mode starts with street tiles off. The optional street map discloses the viewed area to OpenStreetMap and requires confirmation. Neither mode sends the ZIP or journey records to a server.

## Owner acceptance

1. Export the full ZIP and compare its journey count with the saved Journeys list. Keep the ZIP in local Downloads.
2. Open the Lab and load the ZIP. Use **Find** with the start time or ID of the journey you want to check. Its ordinal can differ in the full archive.
3. Compare **Route-first** and **Existing interval engine** at the junctions and pedestrian crossing under review. Confirm the route crosses the square, stays connected and has no unsupported terminal nib. Use **Recorded lines** to see what came from Timeline.
4. Open one saved driving, cycling and railway journey with match evidence if available. Any remaining split should correspond to a recorded or matched discontinuity the engine cannot substantiate.
