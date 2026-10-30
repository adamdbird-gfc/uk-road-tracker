# Ordinary seam candidate insertion delta

Public NCN reference evidence only; no personal journeys. Existing catalogue attribution applies: © OpenStreetMap contributors (ODbL 1.0); official open scope from Walk Wheel Cycle Trust (OGL 3.0).

`index.json` pins 96 base64 chunks, the reconstructed gzip bytes, the existing source-offset correction artifact, the implementation commit and the complete expected candidate content. Each full chunk holds 65,536 compressed bytes as 87,384 ASCII bytes. Decode and concatenate in index order; do not infer source admission from proximity.

Decompressed format: `NCNSEAM1\n`, followed by records: unsigned little-endian 32-bit JSON header length, UTF-8 JSON `[route, way, previous_bindings_sha256, insertion_count]`, then that many 41-byte `<IIddddB` structures: final insertion index, target index (sorted target keys within the pinned route), physical start/end, logical start/end, and explicit mode code. Codes: 1 foot, 2 cycling, 3 foot+cycling, 7 cycling+foot (preserves order). Other codes are rejected. Existing bindings fill remaining positions unchanged. All numeric values retain their IEEE binary64 precision.

Use `python -m scripts.reproduce_ncn_ordinary_seams --database SHIPPED_DATABASE --output FRESH_DATABASE` to replay and verify all candidate values. Use `scripts/pack_ncn_ordinary_seam_delta.py` to regenerate the binary delta from the integrated and independently audited candidate databases. SQLite byte layout can differ between the one-pass assessment writer and two-step replay; canonical content must agree for every table.
