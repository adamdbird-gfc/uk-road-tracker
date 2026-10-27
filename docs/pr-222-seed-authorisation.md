# PR #222 — draft-release staging correction for approval

## Latest status — artifact route proposed, not executed

Run 40555030074 used corrected source 8233533 and valid inputs, but the read-only
runner token did not discover the draft. Do not repeat that release path. The ZIP
remains unchanged/unpublished. See [the artifact bootstrap proposal](pr-222-artifact-bootstrap.md)
for a minimal manual stage using an owner-supplied temporary GitHub asset URL in
an Actions secret, unchanged contents-read permissions and hard-pinned 79bcc97
validation. Supplying that short-lived credential, main registration (including
known managed Pages side effects), staging, and subsequent cache seeding each need
explicit approval. No such actions occurred in this preparation. Once the stage
artifact exists, the registered seed workflow works unchanged with source_sha
79bcc97, numeric run_id and artifact_name, and an empty release_tag.

The draft-discovery correction below is historical context, not the recommended
next run. Runtime token visibility has now failed; do not treat it as pending.

## Current state — 27 October 2026

Keep PR #222 in draft. No merge, Android build, dependency download, Firebase
request, production configuration change or further Actions execution is authorised
by this correction. Do not publish, replace or re-upload the existing bundle.

The owner manually uploaded the verified bundle and authorised the one-file manual
workflow registration on main, commit `69537ebdf59d843a1a56bc3a637857134f546c0e`.
Run [40511948834](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40511948834)
then checked out the approved immutable source
`79bcc970c176c6526224a91c24c89525f10bf152`. Offline preflight passed, but release
staging failed at the published-release-by-tag endpoint with HTTP 404. No asset
was downloaded; artifact publication and cache save/restore were skipped.
The shared cache remains unseeded by this operation.

| Identity | Verified GitHub metadata |
|---|---|
| Draft release | ID `425479238`, tag `reference-pins-2026-10-27-bootstrap`, draft true |
| Uploaded ZIP | Asset ID `681638825`, `roadprints-reference-pins-2026-10-27.zip` |
| ZIP size | 157,393,480 bytes |
| ZIP SHA-256 | `d547847572e574cecd16cdb8e08077bf2e0a1014507c7f876c94c7c88b97853c` |
| NCN member | `catalogue.db`, 243,724,288 bytes, SHA-256 `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093` |
| County member | `boundaries.bin`, 8,992,277 bytes, SHA-256 `b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850` |

The ZIP and member pins are unchanged. Existing independent local hash verification
and GitHub's asset digest agree. No re-download/re-upload is needed for review.

## Corrected discovery and integrity checks

`scripts/stage_github_reference_bundle.py` now uses authenticated paginated
`GET /repos/adamdbird-gfc/uk-road-tracker/releases`, matching the exact input tag
and requiring exactly one result with `draft=true`. Missing, published, ambiguous,
malformed or incompletely paginated results fail before binary download.

It then lists the selected release's assets via its validated numeric ID. Exactly
one asset with the pinned filename must exist; its size, `sha256:` digest, uploaded
state and numeric ID are validated before downloading. Download uses authenticated
`GET /repos/adamdbird-gfc/uk-road-tracker/releases/assets/{asset_id}` with
`Accept: application/octet-stream`. Public browser URLs and API-provided URLs/Link
headers are never followed. Pagination URLs are constructed locally with bounded
pages and response sizes. HTTPS is restricted to this repository's release API
and GitHub release-asset storage; cross-host redirects strip the API token.

The downloaded bytes are independently hashed, then the two fixed root members
are size/hash verified before installation. Existing SQLite/county validation,
artifact publication and cache save/restore ordering are unchanged. No integrity
check or pinned hash was weakened. Errors do not retry or escalate permissions.
HTTP 401/403/404 diagnostics explain possible draft visibility failures without
logging tokens, signed URLs or response bodies.

## Permissions: documented support, runner verification still pending

[GitHub release-list documentation](https://docs.github.com/en/rest/releases/releases#list-releases)
specifies `Contents: read` for installation-token access, but also states that
only callers with repository push access see draft releases.
[Release asset documentation](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)
specifies `Contents: read` and authenticated octet-stream download/redirect support.

The registered workflow retains **contents: read / actions: read**. It does not
request contents write, a PAT or a new secret. The connected GitHub app can read
the draft metadata, but it is a different identity from the ephemeral runner
`GITHUB_TOKEN`. The failed run did not test releases-list visibility or asset
access, so runtime success with that token is **not yet proven**. No runner token
is available locally, and Actions may not be run during this task. Do not claim
that the published-tag 404 establishes a permissions failure or that correcting
the lookup guarantees download access.

The next separately approved run is the controlled verification of that identity.
If listing hides the draft, or asset listing/download returns 401/403/404, stop:
leave release/cache unchanged and review a separately authorised credential or
artifact-transfer approach. Do not publish the release, grant write access,
retry blindly or add Firebase fallback.

## Source availability and exact next operation

**79bcc97 remains immutable, but contains the broken script. Reusing that input
will repeat the failure.** The workflow already registered on main checks out the
explicit `source_sha` before running the script. It is byte-for-byte unchanged by
this correction; no new main registration is needed.

After reviewing the corrective PR commit, separately approve its full 40-character
SHA as `CORRECTED_SOURCE_SHA`. That new source choice is an explicit change from
79bcc97, not a branch-name fallback or rewrite of the old commit. The delivery
receipt identifies the exact corrective SHA. Later documentation-only commits
must not silently replace it.

Proposed invocation, **not executed**:

```sh
gh workflow run reference-pin-cache.yml --ref main \
  --repo adamdbird-gfc/uk-road-tracker \
  -f source_sha="$CORRECTED_SOURCE_SHA" \
  -f release_tag=reference-pins-2026-10-27-bootstrap
```

Leave `run_id` and `artifact_name` blank. Approve one new dispatch with the reviewed
source; do not rerun the old attempt, whose original source is still 79bcc97.
The current connected tools cannot dispatch a new run. An owner can use Actions →
Seed verified offline Android reference pins → Run workflow, with the same inputs.

Success requires all of: exact ZIP/member checks, SQLite integrity, retained
`roadprints-reference-pins-v2` artifact, cache save, deletion of only the two local
installed pin files, exact-key restore into empty paths, and final pin validation.
Cache save alone is insufficient. Missing/evicted/corrupt pins fail closed; there
is no broad restore-key or Firebase recovery path.

## Workflow and external automation review

The workflow is manual-only, owner-only, default-branch-scoped, serialized, and
limited to 15 minutes. It uses pinned checkout/cache/artifact actions and the
runner's Python. Its only external operations are GitHub checkout, release reads,
asset download, artifact transfer and cache operations. No pip, SDK, Gradle,
application startup, Android packaging, Render call, release publication or
Firebase deployment is invoked. No repository ref is written by the seed job.
Publication workflow_run filters name settlement builders, not this workflow.

Cache keys and paths remain identical to Android consumers:
`reference-pins-v2-${runner.os}-${hashFiles(NCN manifest, county catalogue)}`;
`.cache/ncn/catalogue.db` and
`android-prototype/app/src/main/assets/historic-counties/boundaries.bin`.
The key is calculated from the explicitly checked-out source; manifest bytes must
remain unchanged for existing consumers to restore the same exact key.

**Important automation correction:** registration on main unexpectedly triggered
GitHub's managed Pages/Jekyll workflow [40510370047](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40510370047),
which completed its Pages deployment despite `[skip ci]`. Reviewing repository
YAML alone did not reveal this trigger. `[skip ci]` is not a guarantee against
managed Pages runs. No further main change is proposed. Render's API and cron
track main; registration used `[skip render]`, and subsequent read-only deployment
history showed no new Render deployment. Never claim there were no deployments:
the GitHub Pages deployment did occur. This PR correction changes only its feature
branch with skip markers; no main, Render or Firebase change is made.

## Offline verification

`python3 -m unittest discover -s scripts -p test_github_reference_staging.py -v`
passes **14 tests**. Socket connections are rejected; HTTP responses and ZIP
payloads are synthetic. Coverage includes paginated exact draft discovery,
missing/ambiguous/published releases, missing/duplicate/wrong-size/wrong-hash/
incomplete assets, 401/403/404 at discovery and download, no retries or token
leakage, restricted redirects, authenticated asset endpoint usage, corrupt bytes,
full synthetic ZIP/member verification, and unchanged workflow safety controls.

Full Android compilation/lint/Robolectric remain pending pre-provisioned SDK,
Gradle/dependency caches and separate offline verification; no Android task was
attempted here. Do not merge or resume APK packaging on the strength of these
staging tests alone.
