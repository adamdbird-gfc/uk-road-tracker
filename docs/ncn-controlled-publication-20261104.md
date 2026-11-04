# Controlled NCN publication — 4 November 2026

Scope: deliver the exact reviewed revision 4 from main
`4ec9aeaa2c2e00a2bab5b704151d73c30d30db99`, issue #194. The owner explicitly
authorises implementation, tests, merge and one publication. No geometry, Android,
APK, IAM, billing, credentials or infrastructure changes.

## Evidence and decision

The owner's November 2026 Firebase Usage and Billing screenshot, supplied on
4 November, shows project Roadprints, Spark, 136.2 MB / 10 GB monthly downloads
(1.3%) and 491.6 MB / 10 GB total storage (4.8%). The screenshot SHA-256 and exact
displayed figures are in `.github/ncn-reference/controlled-authorisation.json`.
The backend reporting timestamp is unknown; the owner explicitly accepts this
evidence with a reasonable margin rather than a guaranteed bound on all traffic.

For conservative arithmetic, interpret displayed MB as MiB and round upward to
136.25 MiB = 142,868,480 bytes and 491.65 MiB = 515,532,391 bytes. Use decimal
10,000,000,000-byte limits. This also reconciles the earlier known 142,796,162-byte
catalogue download with the rounded console figure; it is not contrary evidence.

One preview consumes 145,856,489 catalogue bytes. The reported-value projection
is about 282.06 MB in the owner's displayed-unit arithmetic; the deliberately
conservative byte model projects 288,724,969 bytes plus bounded manifests.
Allow a 200,000,000-byte publication verification envelope, a further
2,000,000,000 bytes for reporting lag, normal client uptake/traffic and uncertainty,
and retain the existing 1 GiB storage reserve. Planned transfer assessment is
2,342,868,480 bytes including the whole verifier envelope and traffic reserve,
well below 10 GB. Storage assessment is 1,789,274,215 bytes including the upload
envelope and reserve. These are planning margins, not measured future traffic,
reserved quota or guarantees. No concrete high-traffic/conflicting usage record
was found in the preserved November Actions/publication evidence.

Normal preflight remains the default when the publication request has no
`controlled_authorisation`. An explicit request may instead select the reviewed
policy file. Controlled mode validates fresh owner all-sites evidence (observed
today/yesterday in the same month), candidate revision, both hashes and sizes,
expected live version, transfer cap, minimum reserves and retained forward rollback.
It uses the existing Hosting identity to freshly verify the exact live baseline.
Monitoring's prior 403/PERMISSION_DENIED is recorded with diagnostic run
41871940004; controlled mode need not issue the same denied request again.

## One attempt and transfer controls

The existing `firebase-reference-publication` lock still serialises writers.
The publication job alone receives GitHub contents write permission to create
one durable lightweight tag, `ncn-publication-attempt-<authorisation_id>`, pointing
to the source commit. The tag is an attempt marker, not an application release.
Create-only REST semantics reject duplicates; it is never updated or deleted.
Only first-attempt main pushes may claim. A failed/ambiguous claim stops before
cache restore or Firebase payload operations. A successful claim is consumed even
if later cache, candidate preparation, verification or publication fails. No job
rerun or second publication with this authorisation is permitted. A future owner
authorisation needs a new reviewed ID, candidate identity and fresh usage evidence;
the mechanism does not require new code for every revision.

Reuse verified GitHub cache; a miss has no Firebase fallback. The existing
revision-4 preparation recipe reproduces the already-validated candidate only
because the available verified cache is revision 1, not a stored revision-4 gzip.
Exact canonical/database/gzip pins must match; this does not reopen generation
or geometry investigations. Local gzip expansion, hash and SQLite validation
still precede any Hosting upload.

The publisher clones the existing live version, preserves its complete inventory
and configuration, reuses content hashes, uploads required objects once, verifies
one preview catalogue and promotes that identical immutable version. Live checks
use the version pointer, stored hash inventory and small exact manifest. There
is no second full catalogue download. The controlled downloader streams with
identity encoding, bounds every body (catalogue pin plus at most one rejection
byte; manifests at most 32 KiB plus one), caps request counts and total consumed
bodies, and persists each request/partial outcome. Automatic HTTP/auth upload
retries are disabled. Upload bytes are recorded separately from CDN downloads.
The remaining margin in the 200 MB envelope accounts for protocol/billing
uncertainty; consumed response bodies are not an exact Firebase billing meter.

## Rollback and evidence

Before writes retain source inventory/config and the original immutable NCN
asset's stored transport hash. A missing original asset blocks publication.
Keep source version `sites/roadprints-data-822cc/versions/758e86605707677e`.
The existing revision-5 rollback manifest points to original database
`973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`.
Releasing a retained version needs no catalogue upload/download. For Android
clients that already accepted revision 4, use the established higher-revision
rollback manifest; client re-download may occur if their previous DB is absent.
No rollback is executed by this task unless separately directed after a failure.

Actions retains the selected authorisation, preflight, durable attempt receipt,
baseline-manifest transfer, full source/target inventories, source configuration,
rollback reference, partial transfer journal and publication result. On any
failure stop; do not rerun. Issue #194 receives authoritative PR/main/run/version,
checksums, actual known operations, estimated monthly usage and outcome. Only
successful live verification allows closing the issue.

## Validation

38 focused offline tests initially pass, including existing normal Monitoring
success/failure contracts plus controlled evidence, bounds, revision/hashes,
staleness, identity/live baseline, create-only consumed attempts, locking/order,
rollback preservation, local candidate corruption, a full mocked preview/promotion
flow, failed preview preventing promotion, no duplicate downloads, partial failure
accounting and no large-transfer retries. YAML/embedded Python compile checks pass.
Hosted CI and final publication evidence are recorded on the PR and issue #194.
Existing NCN/Android/candidate validation receipts are reused without rerunning.
