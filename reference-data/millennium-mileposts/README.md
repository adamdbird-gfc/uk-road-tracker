# Millennium Milepost candidate reference, schema 1

Status: **offline production foundation; not published, installed on Android, or eligible for discovery**. Authority: [feature specification](../../docs/millennium-mileposts-specification.md). Stage 1 deliberately implements the source-inventory boundary, not physical verification or awards.

| Concept | Meaning | Schema 1 |
| --- | --- | --- |
| Source candidate | Typed OSM object asserting a post/design at a point | 889 records; all unverified |
| Validated physical marker | Independently corroborated physical identity, dated location/presence and UK boundary decision; multiple source aliases may represent one post | None; no physical IDs assigned |
| Achievement-eligible marker | Validated physical marker admitted by a reviewed eligibility policy, with sufficient temporal/spatial evidence | None; `eligible=false` is mandatory |

An OSM ID identifies a **source object**, not a verified physical post. MP references are non-unique text. No rounded-coordinate, radius, geography or MP-number identity joins are performed. Unknown design is retained explicitly; known design tags supply only provisional attribution. Original `artist_name` is retained as a source assertion and never overrides the physical design tag. The superseded geographical personality allocation is not an input. An unreviewed UK-area query never proves UK scope, present-day existence, or historical presence.

## Reproduce and validate

From repository root, with Python 3.12 and standard library only:

```sh
python -m unittest scripts.test_milepost_catalogue -v
python scripts/milepost_catalogue.py --output /tmp/milepost-reproduced
cmp reference-data/millennium-mileposts/revision-1/catalogue.jsonl /tmp/milepost-reproduced/catalogue.jsonl
cmp reference-data/millennium-mileposts/revision-1/manifest.json /tmp/milepost-reproduced/manifest.json
```

`source/osm-mileposts.json` is the exact original Overpass response, independently recovered from the 15 October feasibility archive. `source/provenance.json` pins those bytes, query and OSM snapshot timestamp. `extracted_at=null` is intentional: the archive contains a local ZIP entry time, not an independently established UTC extraction instant. Never substitute the OSM snapshot timestamp for a claimed extraction time. The original evidence manifest/audit retain archive cross-checks; official inventory rows, images and historical geographical assignments are not imported.

`revision-1/catalogue.jsonl` is deterministic UTF-8 canonical JSON, one candidate per line, keys sorted and typed IDs ordered lexicographically. IDs, coordinates, original design strings and **all original OSM tags** survive without transformation. OSM object versions are null because `out center tags` did not return metadata; do not invent them. The build epoch is fixed to the verified baseline commit timestamp `2026-11-05T20:01:51Z`, not the wall clock. Reusing a revision with different bytes or metadata fails. A future source refresh requires a separately reviewed source pin and increasing revision; this entry point intentionally builds only the preserved revision 1.

`record.schema.json` and `manifest.schema.json` describe the wire format (JSON Schema 2020-12). The executable validator additionally enforces cross-field source/design/hash consistency, strict JSON (no duplicate keys or nonfinite numbers), canonical ordering, count totals and conservative size limits. Unknown schema/reader versions fail closed. Recognised tag counts are Mills 446, Rowe 265, McColl 135, Dudgeon 43; these were independently counted from raw OSM before adding the acceptance assertion.

## Safe tooling consumption

```python
from scripts.milepost_catalogue import CatalogueCache, ROOT, read_json, MAX_MANIFEST
manifest = read_json(ROOT / 'revision-1/manifest.json', MAX_MANIFEST)
cache = CatalogueCache('/tmp/roadprints-milepost-offline-cache')
cache.install(ROOT / 'revision-1/catalogue.jsonl', manifest)
reader = cache.open()
for candidate in reader:
    assert candidate['eligible'] is False
```

This is an offline reference-tooling consumer/cache, **not an Android downloader or feature activation**. Every open verifies hash, length, schema and every row before returning a reader. Lookups/stream scans recheck integrity; records stream one at a time and the LRU retains at most 32 serialized rows. Caller edits cannot mutate a cached record. Limits: source 8MiB, catalogue 16MiB, manifest/row 32KiB, 10,000 records. The cache copies in 64KiB blocks into an immutable hash generation, fsyncs files, then atomically replaces a single active/previous pointer with a revision high-water mark. It retains prior generations; it does not delete data. Single-writer use is required; concurrent publisher/Android activation is outside this offline adapter. This filesystem contract assumes local atomic rename; power-loss/directory-fsync guarantees are not claimed.

A corrupt/unsupported active generation falls back to the verified previous generation; no valid generation means unavailable. Invalid input never changes the pointer. A new revision cannot decrease or reuse the recorded high-water mark even after fallback. Rollback is an explicit higher revision built from retained source, not a silent revision decrement. No discovery/achievement API, network request or app startup hook exists.

Lookup is O(catalogue size) on an LRU miss; it is appropriate for offline tooling and schema fixtures, not a future per-GPS-point Android spatial query. The future Android adapter should stream into an indexed store/spatial index off the main thread and share these fixtures; it must not parse all rows into a retained object graph or run a catalogue scan for every GPS point.

## Stable future assignments

There are **zero existing approved assignments**. Schema 1 cannot encode a non-null physical ID, verified design/identity/location, coastal exception or eligibility=true. Such input fails validation rather than being overwritten by a source refresh. This is a deliberate safety gate, not a claim that arbitrary future approval migrations already exist.

Before supporting validated physical markers, introduce a reviewed schema and immutable physical-ID/alias registry. Preserve previously approved physical identities and designer assignments separately from OSM source assertions. Missing, moved, retagged or deleted OSM objects become review/tombstone events; they must not silently delete/reassign a physical ID, move a historical position, or change verified designer attribution. Explicit migrations need pinned before/after evidence and regression fixtures. Validity intervals, reviewer/evidence, boundary dataset/revision and coastal exception rationale must be supplied before approval. Repeated source aliases must resolve to one physical ID; proximity alone cannot merge two posts. An unknown-design physically eligible marker may later support Harrow only under the specification; schema 1 awards nothing.

## Existing hosting and future publication / rollback

No new hosting system is needed. Use existing `roadprints-data-822cc` Firebase Hosting and `firebase-reference-publication` concurrency group. The intended manifest route is `collections/published/millennium-mileposts/manifest-v1.json`; the content path is the manifest's independent `collections/published/millennium-mileposts/<sha256>/catalogue.jsonl`. **Neither path exists as a publication added by this PR.** Files live under `reference-data/`, outside the existing auto-publication path filters. No NCN request, pin, manifest or national database changes.

A later separately authorised publication must:

1. Review publication purpose, source licence/attribution, compatible schema/evaluator and candidate/validated/eligible counts. A candidate-only inventory is never a discoverable marker list.
2. Reproduce the exact approved revision from pinned source and verify hash/byte/count/schema records. Retain the predecessor manifest, payload and complete Hosting file/config listing. Assign a strictly increasing revision.
3. Extend the **existing** clone-based reference publisher with this separately validated small collection, the same Hosting identity, shared writer lock, quota/transfer budget and immutable cache headers. `publish_ncn_reference.py` is NCN-specific and must not be called with this manifest or weakened to accept it without an independent adapter and tests. Keep every existing NCN/settlement/collection path and hash unchanged.
4. Upload immutable content into a clone, expose a preview, verify served exact bytes and manifest and complete file/config preservation, then promote that exact verified Hosting version. Respect existing no-retry transfer accounting and prepared rollback gates. PR validation stays read-only with no publication secrets.
5. Verify live metadata and subsequent actual Android install/activation separately. Installation must retain a compatible predecessor and atomically activate only verified bytes. A successful server publication does not prove any device consumed it.
6. If rollback is required, publish a higher revision referencing retained known-good compatible data and preserve all original immutable payloads. For this JSONL format each row carries its revision, so a rollback build updates row revisions and recomputes the content hash deterministically. Reverify preview/promotion and actual device activation; never rewrite user discoveries or raw journeys.

The Hosting adapter and Android installer are future implementation dependencies. This session provides the catalogue, validation, offline consumption/cache and procedure only; it does not provide or run a publication command. Utilities follow-up: [#272](https://github.com/adamdbird-gfc/uk-road-tracker/issues/272).
