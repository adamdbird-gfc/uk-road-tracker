# Reproduce the bounded read-only audit

Use the exact baseline in receipt.json. Python standard library only; no matcher/compiler imports and no national source graph required.

Restore these existing inputs once if absent; their archive and inner-file SHA-256 values are recorded in receipt.json/results.json:

- Original revision-4 archive: GitHub Actions artifact **13357563539** (artifact ID is authoritative). Extract `candidate/catalogue.db.gz`, verify compressed SHA `c40117278069777b38c13a099f319855e99878d24b95128f140f8d2f34a6020d`, decompress to `revision4.db` and verify SHA `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f`. Do not fetch the catalogue from Firebase or regenerate it.
- Historical classified intervals: existing `roadprints-ncn-remaining-gap-classification.zip`, Library `libfile_5dc7eef825d081918e5e49fe2ee47e36`. Extract `classified-intervals.jsonl.gz`.
- Existing national screen: `Roadprints_NCN_Corridor_Evidence.zip`, Library `libfile_8fa6e75900dc8191962d677a9e4def61`. Extract `national-candidates.json`. Do not run discovery again.
- Pinned #276 and Chapel review JSON files are already in the checkout; audit.py verifies their exact hashes and original path rows.

From the repository root:

```sh
python docs/ncn-national-return-audit/audit.py \
  --database INPUTS/revision4.db \
  --inventory INPUTS/classified-intervals.jsonl.gz \
  --screen INPUTS/national-candidates.json \
  --output OUTPUTS
python docs/ncn-national-return-audit/validate.py \
  --database INPUTS/revision4.db --results OUTPUTS
```

Compare JSON/CSV outputs to the committed results; compare decompressed interval CSV content to the separately preserved register. audit.py uses deterministic ordering and gzip mtime=0. The script rejects incorrect database, classification, screen or review hashes. The database is opened only through SQLite `mode=ro`. The #276 comparison exists only in in-memory interval unions. Nothing invokes production matching, source admission, candidate compilation, network publication, deployment or journey/ledger writes.

Validation completed: exact input pins and SQLite quick_check; published national deficit against retained receipt; #276 added canonical distance against prior every-row audit; exhaustive historical-region partition; additive route allocation; independent full-register arithmetic; reversed projection/shared-union controls; 7/8/9-decimal component sensitivity; Python syntax. The initial local runs exposed zero-length source-only target rows, tiny unmapped canonical edges, and one assertion that incorrectly assumed every target had a canonical mapping. The final diagnostic handles those existing reference representations without altering them. No runtime test suite or Android test rerun is claimed for documentation-only additions.

The retained register excludes only fragments <=1e-7 m for size analysis (8.1982 micrometres total); exact JSON accounting includes them. Historical labels are not semantic cause findings, and screening thresholds are not production or recovery thresholds. The independently saved gzip register is linked in REPORT.md. Restore it to `results/canonical-intervals.csv.gz` only if running validate.py against the committed results rather than freshly generated OUTPUTS.

The report stops at the evidence boundary: no current full-network source absence/eligibility/edition classification, no safe national recovery forecast. No further Work session or implementation is authorised by these commands or this report.
