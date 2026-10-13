# Shared venue collections — 0.25.196

Services and Wetherspoons share the visit ledger, stationary-stop detection,
manual check-in, uncertainty review, history/removal, map/clusters/Legend,
searchable grouped progress, catalogue updates and achievement interpreter.
Existing Services and pub facades remain compatibility/migration entry points;
new comparable venue collections do not need those adapters.

## Registration and future reuse

`collections/venue-collections-v1.json` is the reviewed source of truth. A
collection defines stable ID, labels/copy, pin colour/symbol, bundled asset and
revision, catalogue format/identity pattern, grouping, conservative qualification
policy and achievement list. Run `python3 scripts/generate_venue_collections.py`
after editing it; both Android workflows reject a stale compiled registry.

No new live brand is registered. A restaurant fixture verifies the same catalogue
projection, goal interpreter and card metadata; the earlier framework fixture
verifies named native stop qualification without copied capture/UI code.

Shared goal measures are distinct supported history, selected-venue count, any
selected venue, catalogue percentage and satisfied groups (one or more eligible
venues per group). Goals select stable members or reviewed metadata. The 48
Services and 21 Wetherspoons achievement IDs, targets, levels, labels and sourced
eligibility are retained. Independent regression fixtures retain expected named
pub/site IDs. Definitions/calculators no longer duplicate production lists in
collection-specific Java classes. Last orders, Pub crawl and full-county/national
pub completion are absent until their reference data/rules are ready.

Services retain their site-point policy; named pub premises need supported
arrival/dwell identity. Fresh genuine manual location and named confirmation are
available for both. Shared/approximate or explicitly confirmation-required sites
are reviewed. Stationary samples still require four accurate fixes over at least
three minutes, bounded gaps/drift and low speed. Catalogue display points alone
are insufficient for automatic premises credit. Policy is compiled with the app;
a silent data release cannot change thresholds, access or achievement rules.

## Compatible data formats and updates

`services_v1` keeps the deployed `services` rows and multiple site points.
`reviewed_map_v1` keeps the reviewed UK venue release envelope/projection. Each
collection has isolated state, revision, next-check preference and atomic cache.
The previous Wetherspoons cache and preference names remain valid on upgrade.
Both use the same bounded HTTPS manifest/payload reader, integrity validation,
monotonic revisions, offline fallback and retry/success cadence. The Services
baseline publication packages the existing deployed data; it is not a new
operating-status survey. Unknown operator metadata remains unknown.

Publish a compatible reviewed catalogue with:

```sh
python3 scripts/publish_venue_catalogue.py --collection services --catalogue /path/to/approved-services.json --revision 2026101302 --directory collections/published/services
```

The same command accepts another registered collection ID. Reviewed pub releases
retain their existing map approval contract; the original publisher is retained
for compatibility. Published files are immutable, manifests cannot redirect to
other collection data, and data changes cannot manufacture visit evidence.
Reviewed replacements preserve stable IDs; removed pins do not erase supported
historical proofs. Source correction/removal still recalculates evidence and
protects rejected sessions. Missing legacy dates remain unavailable.

## Wetherspoons status reconciliation

Revision 2026101301 contains **813** reviewed locations. Fresh operator pages on
13 October 2026 explicitly show Court of Requests and Bottle of Sack open;
previous held decisions were replaced with dated reviews retaining their pinned
OSM IDs/positions and historic counties. Art Picture House remains temporarily
closed; Benjamin Franklin remains future. No advertised date auto-activates them.

Sources:
- https://www.jdwetherspoon.com/pubs/the-court-of-requests-oldbury/
- https://www.jdwetherspoon.com/pubs/the-bottle-of-sack-sutton-coldfield/
- https://www.jdwetherspoon.com/pubs/the-art-picture-house-bury/

The 813-map baseline remains explicitly partial: `coverage_complete=false`,
`completion_target=null`. It is not a certified active UK/county denominator.
Historical former-pub/trading-date evidence and a Pub crawl rule still belong to
#82. This release completes #77's shared infrastructure and #116/#117; it does
not mark those deferred #82 content requirements delivered.

## Verification

The combined release must pass Android tests, lint and stable signing before
merge. Regression checks include isolated Services updates/offline cache,
cross-collection manifest rejection, existing achievement/earned-proof retention,
fixture reuse and reopening holds. Local checks: shared registry generation,
20 Wetherspoons catalogue tests, four shared publisher tests and four Services
metadata tests. Actual Android CI totals are recorded in the release note.
