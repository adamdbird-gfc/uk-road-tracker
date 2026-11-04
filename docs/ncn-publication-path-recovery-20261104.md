# Existing NCN Firebase publication path recovery — 4 November 2026

## Recovered mechanism

Starting main `2ae2b89276f5541bf5c13d61c6371a5cbc7c2c6e`. The existing NCN workflow was preserved in the historical `infra/ncn-publication` lineage and later `fix/firebase-bandwidth-offline` tip, but absent from reconciled main. The prior readiness review checked current main and incorrectly treated the missing registration as requiring a new delivery entrypoint without tracing the old NCN workflow. The credentials were never demonstrated missing from GitHub; only the local environment lacked them.

Historical run [39330952075](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39330952075), publish job 118328781110, succeeded on 19 October. Its log records `live_verified`, revision 1, source Hosting version `sites/roadprints-data-822cc/versions/ffae619efea48f69` and 18,155 preserved files. The run's overall failure was its subsequent Android build; the NCN publication job passed. Run 39327496695 independently verified the existing Hosting access; 39331303438 subsequently packaged the already-published reference. Historical workflow changes are retained at `00fb8a69`, `3d53d053`, `3d52d380` and `16c82628`.

Recovered configuration is unchanged: project `roadprints-822cc`, Hosting site `roadprints-data-822cc`, GitHub secret `FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON`, variable `ROADPRINTS_DATA_HOSTING_SITE`, and the shared `firebase-reference-publication` lock. No credential, identity, role, API enablement, Hosting site or billing-plan change is made.

## Minimal adaptation

- Restore `.github/workflows/ncn-reference-publication.yml` and its request on main. Remove obsolete Drive/URL handoff and APK packaging paths from this bounded revision-4 execution; reuse the existing verified GitHub reference cache and exact reconstruction/packaging functions. Cache miss fails without a Firebase fallback download.
- Retain `scripts/publish_ncn_reference.py`, clone/preservation checks, atomic immutable paths, monotonic manifest rules, preview verification and promotion. One invocation creates one preview. All preview asset bytes are verified once; live verification then checks the same version's entire stored hash inventory, live release pointer and small exact manifest. This avoids downloading the identical catalogue twice.
- Save source config/inventory, target inventory, quota/access receipt and publication receipt for rollback and audit. Existing revision-5 forward rollback manifest and old immutable asset remain unchanged.
- Before any catalogue transfer or Hosting write, use the existing deployment identity to read live Hosting metadata and project-wide Cloud Monitoring Hosting transfer/storage gauges. Missing permissions/data, stale/future metrics or insufficient headroom block the workflow. No zero-usage assumption or permission grant is made. Gauge data can lag (eight-hour sampling plus reporting delay); a conservative 1 GiB reserve is required in addition to planned verification/new storage. The reserve is a deployment guard, not a quota reservation or guarantee against concurrent client usage.

The official [Hosting usage documentation](https://firebase.google.com/docs/hosting/usage-quotas-pricing) states that quotas are project-wide and include all channels/sites. [Cloud Monitoring metric definitions](https://docs.cloud.google.com/monitoring/api/metrics_gcp_d_h#firebasehosting) document `network/monthly_sent`, `network/monthly_sent_limit`, `storage/total_bytes` and `storage/limit`. All returned domains/sites are conservatively counted; the smallest reported limit is used. Metrics older than ten hours fail. A Hosting Admin credential may not have Monitoring read permission: that is an explicit possible operational blocker, not a reason to invent credentials or enable services.

## Bandwidth discipline and validation

The October investigation at `fix/firebase-bandwidth-offline:docs/firebase-bandwidth-investigation-2026-10-27.md` records the observed 9.77 GB / 10 GB incident and repeated CI catalogue downloads. Main's offline reference-cache controls are preserved. No Firebase call was made to compile or test this integration locally; retained revision-4 files/hashes and historical records were reused.

Twelve focused offline tests pass: seven existing/extended publisher invariants (including manifest-only live verification) and five quota checks (missing/stale/future/insufficient/duplicate/cross-domain cases). Python source/embedded workflow code compiles; YAML parses; `git diff --check` passes. Existing NCN geometry and Android validation remain unchanged and were not rerun. PR CI runs the same offline checks without secrets; only a main request push can invoke authenticated preflight/publication.

Planned successful publication verifier egress is **145,856,489 asset bytes once**, plus bounded manifests/control traffic; previously preview+live required 291,712,978 asset bytes. GitHub cache restoration and reconstruction incur no Firebase CDN bytes. Actual Firebase billable usage may lag; the publication receipt counts verified asset-body bytes separately from project quota measurements and control-plane traffic. New clients would separately download the new version under existing weekly update controls.

Target is exactly proposed revision 4, DB `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f`, gzip `c40117278069777b38c13a099f319855e99878d24b95128f140f8d2f34a6020d`. Quota is checked before offline reconstruction and a small manifest read verifies the live revision-1 base before the publisher runs. If quota fails, no preview/upload/catalogue serving request occurs. Do not rerun a blocked job without resolving its recorded blocker. Re-running the whole publication after success stops at the unchanged-base gate; never repeat a live release merely for evidence.

## Result

Implementation prepared and locally validated. Authenticated Actions preflight/publication result, run/PR/merge identifiers and measured transfer evidence will be recorded in the completion receipt after the authorised main request executes. Issue #265 is untouched. No app/APK/backend/geometry changes are included.
