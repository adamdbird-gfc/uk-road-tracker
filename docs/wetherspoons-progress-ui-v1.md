# Wetherspoons Progress UI — 0.25.192 (238)

Installed Spoons collections now open the shared Progress page and have a jump-menu entry. The card summarizes unique visited pubs against the current mapped catalogue. Lazy, collapsible historic-county and town groups expose pub details and history. Accepted visits to pubs absent from a later catalogue remain accessible under Previous catalogue pubs.

Collections uses Show on map followed by View progress for both collections, omits the pub count and uses the journey-delete style for removal. Removal retains existing visit proofs.

Manual check reviews saved nearby Timeline place visits in a styled summary-card slider with Previous/Next and swipe navigation. Opening, navigating or closing never awards a visit. Confirm pub visit and Not a pub visit use the existing evidence ledger. Import from Timeline opens a floating place-only importer; journeys remain untouched. Pub history, removal and live check-in confirmations use the shared Roadprints dialog. Map cards show name, town and visit status with one relevant action.

Existing named Timeline recognition and live GPS/confirmation requirements are unchanged. Catalogue update cadence, background capture, achievements and catalogue completeness are outside this UI slice.

Robolectric coverage checks slider navigation without awards, lazy county/town expansion and restoration, importer mode/close, collection navigation order and compatibility routes. The full Android suite, lint and signed APK verification run in CI.
