# National NCN Android integration — 19 October 2026

Branch `integration/ncn-national-beta`, starting at released 0.25.206 lineage
`7bc10f4a` (contains exact corrective source `452258f6`). No old app branch was
merged over its map readiness, recovery, navigation or white progress bar fixes.

## Dependency disposition

| Work | Disposition |
|---|---|
| #171 | Already reconciled in Beta 1; retain latest shared detail UI. |
| #168 | Already reconciled backend/source provenance; no duplicate merge. |
| #165 | Include existing Python source/evidence readers and tests. |
| #172 | Selectively adapt catalogue, matcher, ledger, assessment and golden fixtures; supersede trial UI, fixed-key production reader and restrictive compiler. |
| #175–#187 | Include consolidated Python readers and the pinned #187 output; no blind stacked merges or new geometry admissions. |
| #201 / #202–#205 | Preserve corrective release lineage and existing regression tests. |
| #193 | Defer all new achievements, mileposts and poem mechanics. |

## Data and accounting

Reviewed checkpoint: 219 supported route identities, 62,399 distinct source
ways, 62,487 route-way associations and 34 held identities. Input pin
`a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641`;
plan summary `e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b`.
See `ncn-completion-checkpoint.md`. No live membership/status research is repeated.

`python -m scripts.build_ncn_national --checkpoint <reviewed-bundle> --output
android-prototype/app/src/main/assets/ncn-national` validates pinned inputs and
all portable plans. `scripts/finalize_ncn_android.py` bounds SQLite rows and adds
display-only overviews. The installed database is immutable, hash pinned, copied
atomically and opened read-only. Queries index source ways, hashes, node pairs
and route targets; the complete national graph is never parsed into Android
objects at startup. A bounded path cache serves evidence matching.

Route completion preserves original projection offsets and piecewise non-unit
physical/logical bindings. Overall completion converts covered route intervals
onto a separately noded, unique physical union of the open target geometry.
Overlapping route identities and repeated/mixed-mode journeys union intervals;
route totals are never summed into the network total. Source travel is painted
from actual supported source geometry, not an offset publisher line. Complete
open target gaps remain in denominators and remaining geometry. No connectors
are added across gaps; joining requires identical endpoints with degree two.

Assessment accepts validated walking/running and bicycle provenance only.
Driving, bus, rail, flight, unknown and relabelled car matches earn zero. Each
journey has an atomic fingerprinted checkpoint under the catalogue/accounting
version. Geometry edits and corrections invalidate it; deleted records cannot
return via cache. Reading streams only matched geometry/provenance and metadata,
without raw capture arrays, journey upgrades or rematching. Work runs separately
from core map preparation, checkpoints per journey and publishes only on a
stable archive revision/activation epoch. Explicit Retry resumes failed work.

## UI and limitations

- Collections activation, existing Legend collection selection, activity filters,
  subordinate ordinary road overlays, route identity choice, grouped searchable
  Progress, Jump-to, red signpost charts and shared purple/grey-dashed detail maps.
- Focused maps fit verified reference geometry, retain zoom/pan/Close and offer
  eligible section journeys through the established journey cards/editor.
- Activity preference, group/search preferences and retained screen state survive
  navigation. NCN overlay work does not gate ordinary map readiness or change
  road-layer preferences. No user archive or earned achievement migration.
- **218 open-main targets are measurable. NCN228 is a browseable preserved
  alternative inventory; open-target assessment/percentage and aggregate credit
  are held.** Its source-inventory scope is not relabelled as open-main.
- 34 identities remain excluded. The asset manifest is the explicit hold register.
  176 alternative way occurrences across 49 identities remain conservatively
  unbound, as in the reviewed checkpoint. No shortcut or either-arc reinterpretation.
- This checkpoint contains NCN/RCN numbered identities only. Named routes have a
  visible empty group; verified names/termini are not fabricated.
- Zero-coverage national overlay geometry uses a display-only approximately
  10–20 m simplified overview. Focused maps retain reference vertices. Coverage
  assessment, denominators and source lines never use simplified geometry.
- Unsupported old matched evidence receives no credit and no repeated review
  prompts. A normal explicit rematch remains available through existing editing.
- Catalogue update is versioned with this APK; no remote reference/database
  publication or automatic catalogue update channel is introduced.

## Validation record

Existing 322 Python NCN tests pass. Java source parse passes. Full Android unit
suite/lint, national database readback and packaging are pending at this code
checkpoint. No attached phone/emulator; no real-device test is claimed.

Device acceptance must cover all 33 checks in the development brief, including
large-history first activation, cancellation/process restart, low-memory return,
shared route choices, section-specific journeys, Activity filters and map layer
restoration. Existing motorway/A-road pixel/fit tests and main-map recovery tests
remain in the full suite. Do not equate JVM rendering or source tests with phone
performance/gesture acceptance. APK status must be reported from actual CI.
