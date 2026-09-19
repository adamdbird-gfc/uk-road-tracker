# Shared reference catalogue — Step 5 baseline

**Status:** concluded for the initial backend baseline  
**Checked:** 19 September 2026

This document records the reference data that Roadprints may store centrally at this stage. It does not change the browser application or migrate personal data.

## Loaded and verified

| Area | Status | Validation |
| --- | --- | --- |
| Shared reference schema | Live | Source/version registry, settlements, aliases, boundaries and road references |
| Great Britain settlements | Live | 8,545 catalogue records with WGS84 centres |
| Pilot settlement boundaries | Live | Gravesend, Gillingham (Medway) and Haverhill |
| Settlement aliases | Live | 4 aliases |
| Great Britain A-road reference index | Live | 1,781 unique A-road references from the versioned canonical dataset |
| A-road digit check | Passed | 99 one-/two-digit and 1,682 three-/four-digit references; no five-digit GB A-road references |
| A(M) routes | Excluded from A-road index | They remain motorway references |

The Great Britain A-road index stores the road identity and provenance only. Its geometry remains in the existing canonical road tiles until the later matcher/coverage pipeline. The current app continues to use its existing behaviour.

## Northern Ireland

Northern Ireland is deliberately **not loaded** into this initial catalogue.

The official OSNI 50K Transport Lines download was inspected. It is useful physical-network data but has no road-number attribute, so it cannot reliably create a complete A-road index, confirm route-number lengths, or replace the separate NI network. No old limited NI subset has been promoted.

NI road data may be added only after obtaining and validating a separate authoritative numbered source. NI settlements and boundaries likewise require their own authoritative source and must never be inferred from Great Britain data.

## Deliberate exclusions

- No Timeline files, Journeys, route traces, user accounts, identities, progress or corrections are stored in the shared database.
- No settlement-wide local-road inventories are generated here.
- No change is made to the current browser import, map, progress or correction experience.
- Overseas journeys and overseas road data remain outside the UK catalogue.

## Evidence and provenance

- Great Britain road index: OS Open Roads-derived Roadprints canonical A-road dataset, version `v5`, Open Government Licence v3.0.
- Settlement catalogue: versioned ONS-derived Great Britain built-up-area reference data.
- NI source reviewed but not loaded: OSNI Open Data 50K Transport Lines.

## Next planned work

Proceed to Step 6: the first import vertical slice. A Timeline upload will be validated and represented as an import job, with a simple progress state and durable import summary. Settlement processing, achievements and road-coverage calculation remain outside that first slice.
