# Combined NCN candidate publication readiness — 4 November 2026

## Decision

**Candidate reproduced and packaged; publication blocked by the credentialed delivery entrypoint.** No Firebase write, APK, backend deployment, user journey modification or completion award occurred. Main was independently verified as `06a2ec8586326767c0603b8676404506ed482bcb`; #242, #254 and #264 are merged at their exact recorded heads. This record supersedes the earlier proposed read-only task in the controlled-integration handover: the owner now authorises publication if every gate passes. The blocker is operational, not missing owner permission or unresolved geometry.

The local execution environment has no `FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON`. Current main has the existing `scripts/publish_ncn_reference.py` publisher but no workflow that invokes it. Existing GB/NI publishers use the repository Hosting secret and shared `firebase-reference-publication` concurrency group, but publish different products. Their requests must not be repurposed. Repository secret contents/availability were not inspected. No deployment bypass or new secret was introduced.

## Live base, candidate identity and metrics

At 15:24 UTC the public manifest was read from <https://roadprints-data-822cc.web.app/collections/published/ncn/manifest-v2.json>. Revision 1 exactly matches the retained Android seed manifest. The full 142,796,162-byte compressed asset was downloaded once; compressed SHA-256 `91c8e25f0d58ced6a1e2c91905fe4190b826dc8a469aafc4aab09b83dee1fd91` and expanded 243,724,288-byte DB SHA-256 `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093` match. SQLite quick_check and 219 identities pass. A second manifest read at 15:28:44 UTC is unchanged. This verifies the served NCN artifact, not the Firebase release/version identifier or inventory of every other hosted asset; those still require credentialed preflight.

Fresh seam reproduction from that exact DB passes canonical `cf25e006e79782c9458895bf4b68179b7829fe3b6e7d5ded96be0ccd07929dc0`. Applying the exact reviewed Deal correction produces canonical `d3486a38fc33a78dd9314720a4677906fa7315e28c15feedb1577a1847caac2d`. The resulting SQLite SHA-256 also exactly matches the prior Deal validation: `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f`.

| Measure | Reconciled candidate |
| --- | ---: |
| Additional assessable unique network over pre-seam capacity | 46.396350568 km |
| Assessable unique network | 16,146.369976630 km |
| Overall assessability | 92.608243626372% |
| Remaining unbound unique network | 1,288.762517376 km |
| Deal increment beyond seams | 561.888736534 m |
| Unchanged measurable network denominator | 17,435.132494006 km |

These remain **candidate capacity**, not actual published metrics or individual completion awards. They reconcile the retained national seam receipt and independently audited Deal canonical union delta; no new national geometry assessment was performed. Eligibility, access, source admission, activity modes, direction rules, masks, target lengths and network denominators are unchanged. The exact reviewed cycleway exception remains narrowly pinned. Private 77611523 and the remaining 3.243944 m Deal endpoint stay excluded.

Prepared candidate revision **4** avoids reusing historical seam candidate revision 3 and exceeds served revision 1. It has not been activated or reserved remotely. Its 247,291,904-byte DB packs to **145,856,489 bytes** (139.10 MiB), +3,060,327 bytes (+2.14%) over live; one full successful update transfers about 145.86 MB before protocol/retries. Gzip SHA-256 is `c40117278069777b38c13a099f319855e99878d24b95128f140f8d2f34a6020d`. The existing packer uses deterministic gzip metadata; exact compressed bytes are pinned separately from canonical content. Full local publisher preflight reopens/expands this gzip and validates size, checksum, SQLite and identities.

Proposed immutable asset path, **not published**:

`collections/published/ncn/be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f/catalogue.db.gz`

The [receipt and manifests](data-trials/ncn-publication-readiness-20261104/receipt.json) preserve live, candidate and forward-rollback metadata. No large generated DB is committed; the pinned source, compact delta, review and recipe reproduce it.

## Evidence and consumer compatibility

All 11 preserved Deal deliverable hashes match. Git comparison from validated #254 head to current main shows no changes in `scripts/`, `.github/ncn-reference/`, Deal fixtures, Android NCN runtime/consumer files or NCN seed pins. Prior implementation-to-final-head comparison also leaves these files unchanged. GitHub independently reports successful candidate jobs for seam run 41038552581 and Deal run 41184546990, including reconstruction, all-row audit, every target/network mapping and Android consumer steps.

Reuse the 413 Python checks, 114 offline browser checks, 45 combined-candidate Android checks and earlier 43 seam Android checks. These are historical runs, not newly executed totals. The prior independent audit covers all 1,376,616 rows and isolates exactly one Deal path-row change, preserves all existing bindings and other values, and computes shared canonical coverage by union. Together with identical reproduced content this establishes no added duplicate ownership or shared/parallel double-counting. No geometry/access investigation was reopened. Newly executed: live input integrity/SQLite checks, complete seam and combined reproduction/hash checks, candidate pack/local publication preflight, all 11 deliverable hashes, and five existing publication-contract tests (all pass).

Android 0.25.223 (269), release source `c592f59dc05bfe2a573bc715ff2755b4fa253c70`, has the same NCN consumers as the validated candidate. Owner reports installation; no phone was connected in this session. `NcnCatalogueFiles` discovers the remote manifest at the existing Hosting origin, selects higher revisions, verifies compressed/expanded hashes and SQLite before atomic activation, and retains active/previous generations. Existing devices check when due (normally seven days after a successful check; failure retries remain bounded), so publication would not imply immediate adoption on every phone. A new APK seed pin is unnecessary.

`NcnCatalogue.version` uses the DB digest; `NcnProgressStore` rejects stale derived snapshots and uses version-scoped assessment caches. On catalogue change it invalidates the active snapshot/epoch and re-assesses retained eligible evidence. `NcnAchievements` computes the same route/network thresholds and retains historical unlock timestamps. Existing tests cover repeated/partial evidence, edits/deletes, private negatives, shared union, corrupt cache fallback and historical unlock retention. Original journeys and manual corrections are not rewritten. After future publication, valid historical evidence may yield additional completion/unlocks; no blanket mileage is awarded from assessability alone. Real-device update/rollback acceptance is still unperformed, and credentialed preview/live serving checks remain blocked.

## Smallest viable resolution and rollback

Provide one bounded GitHub Actions NCN publication entrypoint around the **existing publisher**, using the existing Hosting secret/site variable and shared `firebase-reference-publication` serialization, with an immutable request pinning this source, canonical hash, revision and exact manifests. Validate the workflow/request through the normal PR process. Confirm the secret/site configuration in that credentialed job without exposing credentials. This is delivery plumbing; no NCN policy, app change or new APK is needed. It remains follow-up work, not a deployment performed by this documentation PR.

Before any publication, re-read live manifest and compare it to the preserved base; if changed, stop and reconcile. Reproduce from checksum-verified retained input/cache (or verify the served input as done here), require the exact combined canonical and file pins, pack and run `local_check`. Obtain the credentialed live release version/config and full file inventory, retain their hashes and previous immutable asset. Space must allow download, expanded candidate and previous generation; actual device free space has not been measured.

Run `scripts/publish_ncn_reference.py` without `--promote` first. It clones live, preserves all non-NCN files and configuration, checks monotonic revision/immutable path, uploads only the new asset/manifest, verifies complete clone preservation and serves the preview for exact compressed/manifest verification. Preserve its preview receipt. If every gate passes, run the established promotion path with the same pinned inputs, rechecking live for races, and retain final `publication.json` plus source/target Hosting release identifiers and before/after inventories. Preview identity must be recorded from the actual promoting invocation; the current script can create a new clone on a subsequent invocation. Verify the actual promoted artifact rather than assuming it is an earlier preview version.

Rollback is a **higher manifest revision 5 pointing at the old retained DB/asset**, not decreasing back to revision 1. [Prepared rollback manifest](data-trials/ncn-publication-readiness-20261104/rollback-manifest.json) passes `next_manifest`; five existing contract tests verify higher-revision rollback, revision-reuse rejection, preservation, config and integrity rejection. Reconfirm live revision before use; if revision 5 has been used meanwhile, allocate a fresh higher one. Keep the old immutable asset and Hosting source version. Rollback may revert derived coverage to the old catalogue; historical unlock timestamps remain preserved. Do not clear app data, delete original journeys, downgrade the APK or overwrite unrelated Hosting content.

## Reproduction

From the verified main containing #242 and #254, with the exact live DB pin above available as `SHIPPED_DB`:

```sh
python -m pip install -r requirements-ncn-deal.txt
python -m scripts.reproduce_ncn_ordinary_seams --database SHIPPED_DB --output WORK/seam/ncn-national.db
python -m scripts.ncn_deal_correspondence --database WORK/seam/ncn-national.db --output WORK/combined/ncn-national.db
```

Require `scripts.ncn_binding_attainability.content_sha(WORK/combined/ncn-national.db)` to equal the combined canonical hash above. Copy the preserved candidate manifest to `WORK/combined/manifest.json`, then run `scripts.finalize_ncn_android.pack(WORK/combined)` and require every resulting manifest field/checksum/size to match the preserved candidate manifest. Run `scripts.publish_ncn_reference.local_check` on that gzip/manifest. The unchanged seed manifest belongs in the APK; do not modify app assets merely to prepare publication. Use fresh outputs; reproduction deliberately rejects overwrites.

## Separate product requirement and next task

[Issue #265 — Verified parallel-way corridor equivalence](https://github.com/adamdbird-gfc/uk-road-tracker/issues/265) permanently records the owner decision, public-access and corridor proof, private/unrelated negatives, single credit, route/mode/direction controls, no arbitrary tolerance increase and Deal as the bounded first case. It is not implemented by this candidate or session.

Next NCN task: establish the bounded credentialed publication entrypoint, complete preview/promotion/live verification and retain rollback receipt for this exact candidate. After publication, validate device catalogue uptake/history/unlocks before resuming one bounded known-way admitted-span case. No unrelated PR/issue is closed. Backend live commit is unobserved and irrelevant to this data-only artifact identity; no backend change was made.
