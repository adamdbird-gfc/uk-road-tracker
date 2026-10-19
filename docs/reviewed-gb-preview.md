# Reviewed GB Hosting preview

The one-off request in `.github/gb-preview/request.json` reproduces the reviewed
public-reference files from the pinned Geofabrik extract and ONS boundary
snapshot. Supplier MD5 is checked before downloading the national extract;
SHA-256 is checked after downloading. The normalised boundary hash must also
match. The complete staged file/hash report must exactly match the reviewed
report before any deployment is attempted.

Five archived inventory objects were recovered from public Actions build logs:

| Settlement | Run | Build job |
|---|---:|---:|
| Islington | 35975679931 | 107555542154 |
| Ealing | 35975679931 | 107555542089 |
| Hounslow | 35975679931 | 107555542103 |
| Southwark | 36034415027 | 107750935536 |
| Greenwich | 36069988219 | 107868535422 |

These objects retain their original road lists and are serialised using the
original builder's compact JSON format. Their historical boundary bytes were
not recovered; these five use the frozen reviewed ONS candidate boundaries.
The existing 121 published inventory/boundary pairs remain byte-identical.

PR checks run small source-integrity fixtures only. A main push changing the
request runs the national rebuild once, using the existing Hosting credential.
Deployment is limited to the dedicated reference-data site's
`gb-reviewed-20261014` preview channel, expiring after one day. It does not
deploy the live channel, change Postgres, build an APK, or change the billing
plan. Source drift stops the job and requires another review.

The complete preview is checked with the existing exact-byte verifier, in
bounded groups with eight workers. Every JSON/GeoJSON file must have its
expected bytes, MIME type, caching policy and nosniff header; source and
configuration paths must return 404. Verification reads approximately 217 MB
from Hosting once. Small public verification reports are retained for three
days. The national source/index stays on the ephemeral runner.

Live publication and insertion of newly available inventory rows remain
separate steps after successful preview verification. Northern Ireland is
outside this GB release.
