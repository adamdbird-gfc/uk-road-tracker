# Frozen source compatibility controls

Derived from OSM contributors' public data (ODbL; https://www.openstreetmap.org/copyright), existing Roadprints NCN fixtures and PRs #239–#244. Publisher target excerpts retain the provenance/licence recorded in those research receipts; no new target or source admission is made here. No personal journeys are included.

`controls.json` is deterministically reconstructed by `python -m scripts.prepare_ncn_compatibility_controls --output OUTPUT.json` from four exact SHA-256-pinned inputs already committed in the repository and the small private-road supplement. The adapter checks source hashes before reconstructing geometry; a matching shape hash never recreates missing feature versions or node identity.

`deal-private-control.json` selects only OSM way77611523 and its ten ordered nodes from the locally retained PR #243 `NCN1_unadmitted_Deal.osm` capture. Its complete raw capture checksum was checked against PR #243 before extraction. The supplement retains exact way version, ordered nodes, geometry, tags and raw capture checksum; usernames are omitted. Extraction performs no access or route inference.

The eight primary comparisons use pinned NCN source geometry versus captured walking geometry. Cumbria is explicitly a physical-feature control with **no admitted NCN source row**, rather than a fabricated NCN association. East Sussex and London recover node chains directly from the existing source-section fixture. Other original chains remain unknown; observed October chains are labelled separately. Current tags/versions are observations, not original import metadata.

The ninth comparison tests Deal's admitted cycleway source against the distinct private parallel road. Target-alignment metrics always compare the source with the contextual publisher target, not the reference feature. Full-way target extent checks are deliberately conservative; they cannot decide a specific admitted source subspan.

All captures are historical frozen evidence. No live database, OSM API, national extract, router or catalogue build is used by fixture reconstruction or diagnostic execution.
