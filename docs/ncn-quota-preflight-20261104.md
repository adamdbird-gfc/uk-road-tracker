# NCN revision 4 quota preflight — 4 November 2026

## Scope and preserved failure

Owner authorises the minimum correction, validation, integration and conditional
publication. Starting main: `13aa63c6e7f1110241f09714d6b2fddbdc6cc2ea`.
Run [41864030136](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/41864030136),
publish job `126693506381`, failed on its first Monitoring request. The downloaded
337-byte artifact `13341447609` contains only `quota.json`, matching the job log:
Hosting access verified; live version
`sites/roadprints-data-822cc/versions/758e86605707677e`; quota HTTP 403;
catalogue bytes downloaded 0; Hosting writes false. The old implementation
discarded the Google error body. Therefore the original artifact alone cannot
establish API-disabled versus IAM-denied; it is not evidence of exhausted bandwidth.

Exact request: `GET https://monitoring.googleapis.com/v3/projects/roadprints-822cc/timeSeries`,
filter `metric.type="firebasehosting.googleapis.com/network/monthly_sent"`,
24-hour interval, FULL view, pageSize 1000, no redirects. Existing service-account
credential uses OAuth scope `https://www.googleapis.com/auth/cloud-platform`.
Subsequent metrics are `network/monthly_sent_limit`, `storage/total_bytes`,
`storage/limit` under the same Hosting prefix.

The [official method contract](https://docs.cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.timeSeries/list)
accepts that endpoint, project-ID form and scope. The
[Monitoring access-control contract](https://docs.cloud.google.com/monitoring/access-control)
requires `monitoring.timeSeries.list`; Hosting access does not establish that
permission. The [Hosting metric definitions](https://docs.cloud.google.com/monitoring/api/metrics_gcp_d_h#firebasehosting)
document these metrics. No established alternative current-usage mechanism was
found in the recovered publisher or October bandwidth incident record. Historical
console usage and elapsed calendar time are not a current quota measurement.

## Minimum safe correction and execution

The preflight now records the fixed endpoint, metric, scope, required permission,
HTTP status, and allowlisted Google status/ErrorInfo reason/cause. It never saves
raw error messages, credential material, principal identities or arbitrary metadata.
Known API-disabled, IAM-denied and insufficient-scope errors are distinguishable;
unknown errors remain blocked. Malformed responses and exception messages are
not echoed. Wrong-site live versions are explicitly rejected.

`ncn-quota-preflight.yml` permits one read-only main push diagnostic using the
existing secret, site variable and shared `firebase-reference-publication` lock.
Its PR job uses no secrets. Its authenticated main job only runs the preflight;
it has no catalogue cache, reconstruction, CDN download or Hosting write step.
This isolates diagnosis from publication. Source integration does not modify the
publication request, so it does not launch a publication attempt.

Existing safety thresholds, project-wide aggregation, freshness checks, 1 GiB
reserve, publication workflow, cache-only candidate preparation, immutable
preview/promotion, preservation inventory and rollback rules remain intact.
No APIs, IAM grants, credentials, billing plans, projects or sites are changed.
If the diagnostic identifies unavailable quota/API/access, stop and preserve its
receipt; do not retry publication. Only verified sufficient current quota permits
one new publication request, with a fresh preflight inside that locked workflow.

## Validation and unchanged release evidence

22 focused offline tests passed: seven publisher invariants, five quota assessment
tests and ten authenticated-preflight/diagnostic/workflow tests using mocked
sessions. Covers sufficient/insufficient headroom, 401/403/404/429/500/503,
missing/mismatched identity, unexpected site, malformed API/error bodies, secret
redaction, repeated pagination, preflight-before-cache, shared locking, unchanged
live baseline, promotion and manifest-only live verification. Python compilation
and `git diff --check` pass. Hosted PR checks must pass before merge.

Candidate manifest and original geometry/packaging/Android/browser receipts are
unchanged. No candidate reconstruction or 145 MB download is needed for diagnosis.
Expected revision 4 gzip: 145,856,489 bytes,
`c40117278069777b38c13a099f319855e99878d24b95128f140f8d2f34a6020d`;
database SHA-256 `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f`.
These are retained validation pins, not a new byte-level verification of a local
candidate. Expected +46.396 km / 92.6082% assessability, 1,288.763 km unbound,
Deal +561.889 m remain applicability/reconciliation evidence, not user credit.
Publication changes reference assessability only; no completion is fabricated or
automatically awarded by this work. Android 0.25.223 (269) and backend are untouched;
actual installed app and live backend commit were not reverified in this scope.

No rollback is needed unless publication occurs. Existing live version above and
the retained revision-1 immutable asset remain the baseline; the validated
`docs/data-trials/ncn-publication-readiness-20261104/rollback-manifest.json`
is a forward revision-5 rollback to the revision-1 database. Publication must
retain actual source inventory/configuration and verify the baseline before writes.

## Authoritative outcome

The implementing PR discussion and linked issue #194 carry the final hosted CI,
merge/main, authenticated diagnostic run, bounded quota receipt, precise access
blocker or conditional publication receipt. Read those results before another
attempt. No runner queue or diagnostic completion is treated as publication.
