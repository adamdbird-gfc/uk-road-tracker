# All Northern Ireland road-inventory candidates — 15 October 2026

All 527 official NISRA settlement identities now have separate candidate
inventories generated from the same reviewed boundary and OSM snapshots as the
initial three-place trial. The unchanged GB accumulator remains responsible for
which names count. This step does not publish files, modify Postgres, change the
app or create an APK.

## Measured result

- 527 inventories; 19,682 road-name entries across settlements, not a count of
  globally unique streets.
- Inventory files total 398,615 bytes. The complete output including the manifest
  and detailed audits totals 982,995 bytes before compression.
- Audited OSM processing took 79.819 seconds; generation including source hashing
  took 80.778 seconds. Measured peak RSS: 1,136,036 KiB, about 1.08 GiB.
- All three prior trial files match byte-for-byte: Belfast City 3,594; Derry City
  769; Newry 352.
- All selected polygons are covered by the road extract, to the existing numeric
  coverage tolerance. All 527 codes, names, counts and sorted distinct road lists
  pass structural validation.
- Twelve settlements have zero eligible named roads. Independent way-class
  evidence finds unnamed residential/unclassified roads or excluded classes,
  with no eligible named way in any of those twelve. These are not roadless
  settlements. Examples: Knocknagulliagh has named primary Belfast Road;
  Ballytober has named secondary Priestland Road; Broomhill has unnamed
  residential roads; Ardgarvan contains service roads.

## Source overlap review

Retain the official NISRA candidate boundaries; do not trim, merge or create
exclusive street ownership from this audit. As in GB, the counting unit is a
road name within each settlement whose polygon intersects an eligible OSM way.
A road can therefore contribute to more than one settlement. The official
source overlaps are measured and their consequences are explicit:

| Overlap | Eligible named ways with positive-length intersection | Excluded named ways |
|---|---:|---:|
| Belfast City / Metropolitan Lisburn | 10 | 6 |
| Belfast City / Metropolitan Castlereagh | 11 | 8 |
| Belfast City / Metropolitan North Down | 0 | 0 |
| Belfast City / Metropolitan Newtownabbey | 0 | 0 |

The precise sensitivity, aggregated across all overlaps for each settlement:

| Settlement | Original candidate | Hypothetical no-overlap count | Names dependent on overlap |
|---|---:|---:|---|
| Belfast City | 3,594 | 3,588 | Arlington Drive; Church Road; Dunmurry Lodge; Glen Road; Glendale; Oakhurst Avenue |
| Metropolitan Castlereagh | 746 | 745 | Galwally Avenue |
| Metropolitan Lisburn | 357 | 356 | St Annes Road |
| Metropolitan North Down | 4 | 4 | None |
| Metropolitan Newtownabbey | 917 | 917 | None |

These eight settlement/name memberships are retained, not silently removed.
Before publication, test that app attribution can credit each one under the
same non-exclusive intersection rule. This audit alone does not establish
that the client currently credits them correctly. Exact way IDs, road classes,
name evidence and excluded ways are recorded in `overlap-review.json` and the
checkpoint's complete `overlap-and-road-evidence.json`. Point-only contact is
not reported as a positive-length way intersection with an overlap; the candidate counting rule still
retains the existing GB `intersects` semantics.

One included numeric-prefix name is flagged for source-quality review: Derry's
`53A & 53B Limavady Road`. It remains unchanged, consistently with existing exact
OSM name rules; this is a flag, not proof that the source tag is incorrect.

## Reproduction and gates

Use the same pinned dependencies and input hashes documented in
`ni-settlement-inventory-trial.md`. Generate all candidates with:

```bash
python scripts/generate_ni_road_inventory_candidates.py \
  --boundary-dir BOUNDARIES --source-dir SOURCES \
  --output NI_CANDIDATES --baselines THREE_PLACE_TRIAL
python -m unittest discover -s scripts -p 'test_ni*.py' -v
```

All fifteen unique fixture tests pass locally. They cover real shapefile
conversion and PBF generation, repair/identity/source guards, hypothetical
boundary removal, retained candidate membership, excluded/unnamed-way evidence,
point-only touches, full-scope catalogue validation and baseline drift.
CI runs the same fixtures without national downloads.

The checkpoint `roadprints-ni-full-inventory-candidates-2026-10-15.zip`
contains the original NISRA ZIP, all 527 candidate boundaries/catalogue,
all 527 inventories, detailed audits, prior trial files for reproduction and
pinned source metadata. It excludes the large raw OSM PBF and temporary indexes.
The repository holds the reusable tooling; the checkpoint README identifies
its exact commit.

Next: complete NI `N`-code support, aliases and historic county alignment; verify
client credit for the eight memberships above and zero-count handling; review
source-name quality; then stage and verify a coordinated Firebase/Postgres
release preserving all existing GB files and totals.
