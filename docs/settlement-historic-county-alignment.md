# Settlement and historic county reference alignment

Released in Android 0.25.163. Settlement reference memberships use the same
92 HCT Definition A county IDs and pinned source SHA-256 as County Collector.
This supersedes the modern county/unitary-authority **display grouping** in
Progress. Administrative `county`, `region` and `nation` metadata remain separate.

## Identity and progress

- Stable settlement codes, boundaries, road inventory counts and road evidence
  are unchanged. Existing road lookup caches are enriched when read; no journey
  import, rematch, cache wipe or personal-data migration is needed.
- Positive polygon overlap determines all county memberships. A shared edge or
  point alone does not qualify. Holes and detached pieces are retained.
- A settlement appears once in Progress under its largest overlapping county
  footprint (WGS84 polygon area); county code breaks ties deterministically.
  The card lists other counties. It remains one settlement for achievement
  counts, map focus and completion percentages.
- County Collector continues evaluating **matched road locations**, independently
  of settlement membership. Membership does not award counties or landmarks.
- Unknown settlement codes are explicitly shown as “Historic county not yet
  mapped”. Neither settlement names, administrative labels nor centres guess
  a historic county.

## Source and scope

The existing public catalogue contains 8,545 ONS Built Up Areas (December 2022)
across Great Britain. Their complete polygons are joined against the original
full-resolution historic county reference, without simplifying either boundary.
Invalid ONS polygons are repaired with Shapely `make_valid`; repaired IDs are
recorded in the output. Every catalogue ID must have a positive overlap before
the reference can be published. Duplicate, extra, renamed or missing IDs fail
generation rather than silently producing a partial alignment.

The generated index maps all 8,545 existing IDs, with 537 settlements spanning
county borders and eight source polygons repaired. No settlement was left
unassigned. Compressed index size: 27,373 bytes.

**Northern Ireland settlements are not yet in the existing settlement catalogue.**
All six NI historic counties exist in the shared county reference and County
Collector, but this change does not invent NI settlement records or claim full
UK settlement coverage. Adding an authoritative NI settlement/boundary source
remains a separate reference-ingestion task.

- Settlement polygons: ONS ArcGIS `BUA_2022_GB/FeatureServer/0`; WGS84 output.
  https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/BUA_2022_GB/FeatureServer/0
- Historic counties: HCT full-resolution Definition A, 6 October 2026; attribution
  and licence in `android-prototype/app/src/main/assets/historic-counties/README.md`.
- ONS attribution: contains National Statistics data © Crown copyright and
  database right 2022; contains OS data © Crown copyright and database right 2022.
  Open Government Licence v3.0. The generated file contains public reference
  county IDs only, never personal travel records.

## Rebuild and validation

Install `pyshp==3.1.6` and `shapely==2.2.0`, then run from the repository root:

```sh
python scripts/build_settlement_historic_counties.py \
  /path/to/UKDefinitionA_WG84_Full_Resolution.zip \
  settlement-catalogue-v1.json /path/to/temporary-page-cache \
  android-prototype/app/src/main/assets/historic-counties/settlements.bin
python -m unittest discover -s scripts -p test_settlement_historic_counties.py
```

The small compressed membership index is committed and packaged offline. Android
CI does not need to download thousands of settlement polygons. The generator
records hashes of the county ZIP, settlement catalogue and ordered source pages.
Android checks the county version/source hash and validates every referenced
county ID. Source changes must rebuild and review the index before publication.
Future settlement sources must keep these stable-code and positive-overlap rules.

Android regression checks cover the complete reference, three same-named
Gillinghams, known English/Welsh/Scottish places, a cross-border settlement,
legacy cache enrichment and explicit handling of unknown/NI codes. Synthetic
topology checks cover holes, border-only touches, cross-border overlap and ties.
