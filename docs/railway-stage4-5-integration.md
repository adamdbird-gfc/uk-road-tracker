# Roadprints Stage 4.5 regional railway comparison

Build 0.25.236 (282) adds network-first inference to the existing owner comparison and historical worker. The Build 281 regional matcher remains the fallback outside the reviewed network, for incomplete results, and when network evidence is insufficient. The physical routes are computed offline from the exact Stage 4.4 topology and packaged with a pinned SHA-256. The app scores up to 72 platform variants of three material corridor options, using the existing raw GPS selection and geometry projection. The test release keeps the existing signed owner build configuration.

## Catalogue qualification

The reference asset `stage1-osm-20261112-r1` is unchanged. The separate `north-kent-passenger-r1` comparison overlay is 64,537 bytes compressed and pins 3,165 directed edges and 72 connected paths. Eight Sidcup variants qualify westbound. Other paths remain available as competing hypotheses. Runtime candidate generation selects from this finite graph product. No unrestricted runtime search is needed.

- NCS Courthill Loop ways 163742792, 382531263 and 382531270, LCH Mid-Kent ways 623073078, 382531266 and 622907369, and LLL Ladywell Loop way 4346192 carry approximately 986 m of missing connected passenger geometry. The OSM line names, ELRs and node joins agree with the [Network Rail sectional appendix](https://filestore.limitofshunt.org.uk.s3.amazonaws.com/sectional-appendices/nr/ksw/module-2-2006.pdf) and [junction chainages](https://www.railwaycodes.org.uk/elrs/_mileages/l/lch.shtm).
- Way 256367760 is a 24.09 m South Eastern Main Line viaduct continuation, tagged main rail and linked by existing nodes between XTD segments at the terminal. It qualifies on the enumerated paths.
- The reviewed HDR/XTD tunnel ways are tagged operational main passenger rail. About 405 m lay on the Stage 4.4 leading path. Tunnel links can contribute physical geometry to the comparison. The scorer still requires non-tunnel movement witnesses.
- Gravesend platform 0 uses OSM stop 501754791 and 307.83 m of the North Kent line on way 41117629. Its siding tag excluded it in Build 281. [Southeastern](https://www.southeasternrailway.co.uk/travel-information/station-information/stations/gravesend) lists it for westbound Charing Cross services via Sidcup. The overlay also carries Gravesend platforms 1 and 2 and two Charing Cross stops on the pinned XTD tracks. These are route hypotheses. The implementation does not write station visits.
- The eastern direction, the other branches' held links, HS1 and unqualified tracks remain excluded from inferred mileage. Endpoints currently require credible near-boundary fixes within a two-minute window and a 550 m station catchment. Arbitrary intermediate stops, interchanges and other station pairs remain with the existing fallback. Additional archived context and station suggestions are outside this first comparison slice.

Regenerate with the preserved Stage 4.4 module and NumPy using `scripts/build_stage45_railway_paths.py regional.sqlite network-first-r1.bin.gzip --stage44-prototype /path/to/extracted/stage44`. The generator pins the source and checks every candidate join by OSM node ID.

## Replay results

| Control | Build 281 | Stage 4.5 |
| --- | --- | --- |
| 6 November Gravesend to Charing Cross | Partial, 12,612.330 m | Complete inferred route via Sidcup, 39,125.162 m, 84 motion witnesses |
| Same train, middle observations withheld | Partial, 791.872 m | Ambiguous across three materially different corridors. No inference. |
| Confirmed 7 October drive | Ineligible | Ineligible |
| Same drive forced Train | Unresolved, 0 m | Unresolved, 0 m |
| 12 November St Pancras to Gravesend-area recording | Partial, 1,553.801 m | Partial, same support. HS1 remains unqualified. |
| Sheffield to St Pancras | Unresolved, outside regional rail coverage | Unresolved, 0 m |

The inferred length is 2.2 m above Stage 4.4's leading geometry because the Android replay selects the qualified platform 0 variant and excludes tunnel fixes as direct rail witnesses. The independent engineering reference is approximately 39,228 m, a difference of 103 m or 0.26%. Platform choice remains uncertain. The archive retains its original 43,898.931 m. [Engineering chainage](https://www.railwaycodes.org.uk/elrs/_mileages/x/xtd.shtm) · [Stage 4.4 evidence](https://chatgpt.com/library?file_id=libfile_8aa11fe6353c8191a1d544f2fd89fb91&account_id=personal)

## Owner validation

Install 0.25.236 (282) over the existing signed build 281. In **Journeys**, open **View Journey** for the 6 November Gravesend–Charing Cross recording. **Railway comparison** should show **Complete inferred route**, about **24.3 mi**, and **via Sidcup**. The recorded distance should still show **27.3 mi**. Open the St Pancras journey and another saved Train journey. Allow any in-progress capture to finish, then return to details to see the retrospective assessment. Compare the displayed recorded distances and totals, and use Roadprints normally. If responsiveness changes, copy the app's diagnostics soon after the slowdown. Device installation, UI responsiveness, battery and memory measurements remain part of phone acceptance.

The results are derived comparison receipts only. The original evidence and authoritative mileage stay in the archive. The new catalogue hash participates in receipt invalidation. The existing worker checkpoints atomically, skips unchanged receipts, yields to capture and releases the path data when the worker closes the catalogue. On-device measurements are required before considering authoritative mileage.
