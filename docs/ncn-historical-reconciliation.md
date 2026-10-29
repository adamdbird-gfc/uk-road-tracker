# NCN historical reconciliation — 29 October 2026

This is an **offline tooling and provenance preservation** change based on main
`89ae65dc972d0c7e426e8e87cc74b5c2944ac63b`, after #233 and #234. It does not
change Android 0.25.220 (266), current route identifiers, geometry, eligibility,
completion calculations, assets or Firebase. The restored research modules are
not called by the production API or Android build. No publisher is executed.

## What is already in the active catalogue

Android's `ncn-national/manifest.json` pins database SHA-256
`973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`, compressed
SHA-256 `91c8e25f0d58ced6a1e2c91905fe4190b826dc8a469aafc4aab09b83dee1fd91`,
and #187's completion summary SHA-256
`e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b`.
A read-only retrieval of the live Firebase manifest matched the Android manifest
exactly. The retained publication archive's compressed bytes and extracted database
match both hashes; no large Firebase download or write was required.

That database contains 219 identities, 62,399 distinct physical ways / 62,487
route-way rows, 218 measurable open-main targets and separately preserved but
unmeasurable NCN228. The 34 held identities match the checkpoint. NCN601's reviewed
source spans retain original NCN626 provenance without a blanket alias. Shared
physical ways are deduplicated, not added as separate travel. Existing Android
cross-mode/correction/deletion tests remain the consumer validation.

## Disposition by historical PR

| PR | Historical work accounted for | Disposition |
| --- | --- | --- |
| #165 | Saved-path activation, source evidence, national/scope/access/alternative experiments | Catalogue lineage incorporated; missing research tools, fixtures and tests preserved here. |
| #175 | 187 inventory capture, 175 source catalogues, membership/zero-association holds | Inputs feed the active checkpoint; capture/review tooling and evidence index preserved here. |
| #176 | Six guarded refreshed-membership overlays; 181 supported keys | Incorporated in checkpoint; overlay validators/provenance/tests preserved. |
| #177 | Six zero-association dispositions; regional/NCN626 holds | Incorporated and superseded by later scoped decisions; evidence retained. |
| #178 | Numbered NCN228 alternative; NCN626/601 conflict | Alternative remains unmeasurable; scope guards/review evidence retained. |
| #179 | Exact reviewed NCN601 spans, source NCN626 identities, full denominator | Incorporated; identity/open-target validation preserved. |
| #180 | Five partial-provider catalogue reviews and proposed-member exclusions | Incorporated; strict admission/replay tools and tests preserved. |
| #181 | NCN99 open-extent audit and explicit unresolved source discrepancies | Evidence/audit retained; no unsupported connectors or full-route claim added. |
| #183 | NCN725 verified open spans, proposed branches excluded | Incorporated; preceding-registry guards retained. |
| #184 | NCN16/222/657/688/74 batch and explicit holds | Incorporated; pinned data checkpoint and review retained. |
| #185 | Remaining 54-case review: 25 bounded catalogues / 29 holds | Incorporated; review engine and exact-source guards retained. |
| #186 | Fixed open targets and 25 portable completion plans | Superseded by consolidated #187 plans; reader/activation tests preserved. |
| #187 | Consolidated 219/34 checkpoint and 176 alternative deferrals | Exact active checkpoint hash; compiler/consumer, provenance and tests preserved. |

Every file introduced or modified by the 13 PRs exists at the #187 checkpoint;
later versions intentionally supersede earlier intermediate implementations. The
machine-readable [audit](data-trials/ncn-historical-reconciliation-2026-10-29.json)
records each head/base, unique commit and changed-file inventory. Restore sources
are #187 head `17b90d24` for research, and packaged source `67e74e7d` for the
national SQLite compiler/finalizer, bounded numerical mapping repair, reference
contract/validators and manual publisher. Only the publisher's pure validation
functions are exercised in tests; no publication workflow/credentials are added.

## Data and reproducibility

Large captured evidence remains in its existing durable archives, rather than
being duplicated into Git. Authoritative retained artifacts:

- `roadprints-ncn-completion-checkpoint-2026-10-19.zip` — Library ID
  `libfile_99ba17ccc32c81919dc203e36ac496f5`; includes all 1,147 pinned consumed
  input files, the 658-file completion output and original source provenance.
- `roadprints-ncn-firebase-publication-2026-10-19.zip` — Library ID
  `libfile_8ab97d8102a88191905627b3c064c7cd`; contains the exact active compressed
  SQLite catalogue and manifest. Its historical README describes the then-pending
  publication; that status is superseded by the live-manifest/hash verification.
- GitHub release `reference-pins-2026-10-27-bootstrap`, asset
  `roadprints-reference-pins-2026-10-27.zip`, plus the existing default-branch
  reference cache, provide Android's immutable offline validation input.

For the evidence bundle, the independent `inputs/input-pins.json` digest is
`a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641`.
The preceding research documents are historical records linked from this index; their old Android,
release and queue-status statements do not describe the current application.

After extracting the checkpoint archive and publication database, run:

```sh
python -m scripts.verify_ncn_checkpoint_provenance CHECKPOINT_ROOT catalogue.db
python -m scripts.ncn_trial.completion_checkpoint --inputs CHECKPOINT_ROOT/inputs \
  --expected-pins-sha256 a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641 \
  --output FRESH_OUTPUT_DIRECTORY
```

The first command checks pinned files, registry, all route receipts, exact source
coordinates/node identities/modes and the catalogue identity/hold partition in
read-only mode. The second rebuilds the historical completion stage offline.
Rebuilding SQLite uses the preserved compiler/finalizer/repair stages in a separate
scratch output; **never run repair against the immutable production/reference pin**.
A newly generated SQLite byte hash is not automatically an approved release.

## Validation and genuinely outstanding work

Local checks: 322 NCN tests, five shared cycling-evidence tests, five reference/
preservation tests and one complete shipped-catalogue mapping test passed with
zero failures/errors/skips. Route1 and portable association fixtures build offline.
CI restores the existing reference cache and runs the same checks without publishing.
The real-data receipt is included in the machine-readable audit.

The 34 evidence-held identities and 176 deferred alternative occurrences across
49 route identities remain intentional research gates. Named routes, unverified
milepost/disc associations and whole-published-route readiness are not silently
activated by preserving these tools. Future changes to those admissions,
identifiers, denominator mappings or published data require a focused proposal,
source evidence, tests and owner approval. No new catalogue admission or upload
is necessary to finish historical PR reconciliation.

Historical branches remain intact. This preservation PR is reviewable and **not
merged**; the old stacked PRs can close because their active data is accounted for
and their missing maintainability work is preserved here.
