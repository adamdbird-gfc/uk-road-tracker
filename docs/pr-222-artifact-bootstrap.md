# Historical signed-URL proposal — abandoned

Do not follow these setup instructions. The owner instead published the verified
release and successful run 40579277763 already seeded/restored/revalidated the
shared cache. No browser developer tools or temporary URL secret is needed.
Use [the final integration review](pr-222-final-review.md) as the current handover.
The remainder is retained only as historical preparation, not authorisation.

# PR #222 — GitHub-only artifact bootstrap proposal

## Owner clarification and data review

The owner accepts wider access to the national reference datasets provided no
personal information is included and redistribution terms are respected. The
repository is public: signed-in readers can download its Actions artifacts, and
shared caches are not a privacy boundary. Do not describe this artifact route as
owner-only storage. The temporary signed asset URL and account credentials must
remain secret. One Pages rebuild is now approved if existing website content is
unchanged; main registration, secret creation and workflow execution still each
require a clearly described approval before acting.

Offline inspection rehashed the unchanged ZIP and NCN database, listed all five
ZIP members and all SQLite tables, and parsed all 219 route records and 62,487
path records. These contain route labels, public map geometry, OSM node/way IDs,
network mappings and provenance, not user journeys/accounts/timestamps/credentials.
County metadata describes 92 public historic county boundaries. The source build
code generates this reference database independently of personal capture data.

Source terms checked on 27 October 2026:
- OpenStreetMap: ODbL 1.0 permits redistribution with attribution and share-alike
  conditions. https://www.openstreetmap.org/copyright and
  https://opendatacommons.org/licenses/odbl/1-0/ .
- Official NCN scope: the publisher's ArcGIS NCN metadata identifies OGL 3.0 and
  Ordnance Survey attribution. OGL permits redistribution with source credit:
  https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/ .
- Historic County Borders Project permits personal, educational, non-commercial
  and commercial reuse without specific permission, and requests acknowledgement:
  https://county-borders.co.uk/ .

The proposed artifact now includes both pinned manifests and a licence/source
notice alongside the two unchanged datasets. This does not edit/repackage the
existing ZIP or alter either dataset hash. Keep those notices with subsequent
redistributions. The existing seed accepts these additional files unchanged;
its own retained artifact currently contains the two data files, while notices
remain in the original staging artifact and this reviewed workflow/source record.

## Decision — preparation only

Use a small, manually dispatched **Stage existing verified reference artifact**
workflow, proposed as `.github/workflows/reference-artifact-stage.yml`. It reads
GitHub's temporary signed URL for the already uploaded asset from a temporary
Actions secret. It validates the existing ZIP and both datasets, then uploads
`catalogue.db`, `boundaries.bin`, their pinned manifests and `REFERENCE-LICENCES.txt`
as `roadprints-reference-pins-v2`.

**Conditional GO for review, not execution.** No Actions, secret/credential creation,
permission changes, main writes, cache changes, release publication/re-upload,
Firebase/Render operation, merge or APK build occurred during this preparation.

There is no credential-free upload field for workflow_dispatch; its inputs are
small text fields, not a binary ingress channel. A GitHub-hosted runner cannot
read the owner's downloaded local ZIP directly. The read-only runner token has
now failed to discover the existing draft (run 40555030074). Retrying that route
or merely adding another upload-artifact step cannot bridge the missing ingress.

## Why this route

An owner browser session can access the existing draft asset. GitHub's asset
service provides temporary signed download URLs on its own release-asset host.
The signed URL is a **bearer credential scoped to that asset**, not a PAT or a
repository write token. It must be kept in an Actions secret, never chat, a PR,
workflow input, committed file, screenshot or log. Creating that temporary secret
is a separate approval step, not an already-performed operation.

This uses GitHub hosting already holding the approved ZIP; it does not introduce
external hosting, create a personal access token, expand GITHUB_TOKEN permissions,
re-upload the ZIP or publish the draft. The URL's expiry is controlled by GitHub;
no fixed lifetime or successful ingress has been live-tested/claimed here. A run
queued beyond expiry must fail, not retry, publish or broaden permissions.

Rejected alternatives:

- Workflow text inputs cannot hold the 157,393,480-byte ZIP, and are not secrets.
- A public issue attachment, published release, public branch chunks or external
  file host would expose/redistribute the bundle or create unnecessary copies.
- A self-hosted runner on the owner's everyday PC is disproportionate for this
  public repository. It adds registration credentials and execution exposure;
  GitHub specifically cautions against self-hosted runners for public repositories.
- Uploading an Actions artifact directly from an ordinary browser is not provided
  by the Actions UI. Upload-artifact runs inside an authorised job and uses its
  existing ephemeral runtime authentication; no actions:write grant is requested.

## Exact bytes and pinned source

Existing release: `425479238`, draft true, tag
`reference-pins-2026-10-27-bootstrap`; asset `681638825`.
Do not publish, replace, rename or upload it again.

| Item | Bytes | SHA-256 |
|---|---:|---|
| `roadprints-reference-pins-2026-10-27.zip` | 157,393,480 | `d547847572e574cecd16cdb8e08077bf2e0a1014507c7f876c94c7c88b97853c` |
| `catalogue.db` | 243,724,288 | `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093` |
| `boundaries.bin` | 8,992,277 | `b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850` |

The staging workflow hard-pins checkout to original immutable source
`79bcc970c176c6526224a91c24c89525f10bf152` with credentials not persisted. It imports
only the existing local `extract_verified` function from that source; it never
calls the broken release-tag downloader. This preserves the original validation
code, dataset hashes and cache-key manifests. It additionally runs NCN SQLite
quick_check/route-count validation and county catalogue length/hash validation.
No application code, shell files or ZIP-supplied scripts execute.

## Proposed workflow and network behaviour

The proposal is owner-only, manual-only, default-branch-scoped, serialized and
limited to ten minutes on ubuntu-24.04. Token permissions are **contents: read**;
all unlisted permissions are none. No repository token/cookies are passed to the
asset download. Official checkout and upload-artifact actions are pinned.

| Step | Network / effect |
|---|---|
| Pinned checkout | GitHub source only; no LFS/submodules or dependency setup |
| Offline helper preflight | Source inspection only; no Android execution |
| ZIP download | HTTPS GET to `release-assets.githubusercontent.com`, restricted to `/github-production-release-asset/1347394235/` and a signed query; same restriction on redirects; proxies disabled |
| Local checks | ZIP hash/size, fixed member pins, SQLite and county catalogue checks; no network |
| Artifact upload | GitHub Actions artifact service/storage, using job runtime authentication; two verified data files, their manifests and licence notice; no URL/secret/ZIP included; retention 90 days |

The binary download is bounded to the exact ZIP size and made once, without retry.
Exceptions are replaced with a generic diagnostic so signed URLs cannot appear
in Python tracebacks. An expired URL, unexpected host/path, redirect, corrupt ZIP,
wrong member or failed SQLite/county check prevents the artifact step. Nothing in
this workflow saves, restores, evicts or modifies a shared reference cache.

No Firebase client, Render command, SDK, Gradle, pip, Android task, release
publication, repository ref write, deployment secret or auto-follow-up dispatch
is present. The stage workflow's name does not match the settlement builders
watched by Firebase publication's workflow_run trigger.

## Registration review receipt

Read-only review at main `69537ebdf59d843a1a56bc3a637857134f546c0e` covered all
24 configured workflow files. No push/PR filter matches this new workflow path,
and no workflow_run filter names the staging workflow. This does not suppress
the separately observed managed Pages trigger. Read-only Render service checks
still show API `srv-da82330u01pc73ccg6n0` and cron `crn-dapdcsg473hc7393bk3g`
tracking main with commit-triggered autoDeploy enabled. No configuration changed.
Keep both skip markers, and recheck these facts before any approved registration.

Proposed workflow SHA-256: `2669437e234418bc69bf71b9ed27b475ad2f3544f3edee403cfea0f2c55ccd52`.

## Approvals and owner steps — do not perform yet

1. **Approve the reviewed one-file main registration** of
   `.github/workflows/reference-artifact-stage.yml` from the corrective PR commit.
   This does not merge PR #222 or change the existing seed workflow. The commit
   must contain `[skip ci] [skip render]`; first recheck the current main head,
   all workflow filters and Render configuration.

   **The owner has now approved one unchanged-site Pages rebuild:** the
   previous main registration triggered managed Pages/Jekyll deployment despite
   `[skip ci]`. A new main write can do so again. The owner accepts that known GitHub Pages side effect provided existing site
   content is unchanged. Only the workflow file may be registered; do not copy
   these PR documentation changes to main. Do not change Pages settings or assume
   the skip marker prevents the managed run. Main's configured push filters do not match
   the new staging path; Render's API and cron follow main and rely on the
   documented `[skip render]` suppression. No claim of universally inert main
   writes is made.

2. **Approve supplying the temporary, asset-scoped bearer URL as an Actions secret.**
   After registration, while signed into GitHub in the owner's browser:
   - Open the existing draft release's asset. In browser developer tools → Network,
     enable Preserve log and initiate that existing ZIP download. Inspect the
     redirect/final asset request and copy its full Request URL only if it is
     HTTPS on `release-assets.githubusercontent.com` and its path starts with
     `/github-production-release-asset/1347394235/`.
   - This is a GitHub asset access, not Firebase. The transfer may be cancelled
     once the redirect URL is available; do not upload or publish anything.
   - Do not copy the browser's GitHub cookie, request headers or session token.
     Do not copy the public browser_download_url: that still requires draft auth.
   - In repository Settings → Secrets and variables → Actions → New repository
     secret, set `ROADPRINTS_REFERENCE_ZIP_URL` to that signed URL. Do not paste it
     into this conversation. If GitHub supplies a different host/path, or no signed
     URL, stop; that case has not been authorised or demonstrated safe here.

3. **Approve one staging dispatch**, after the secret is ready:

   ```sh
   gh workflow run reference-artifact-stage.yml --ref main \
     --repo adamdbird-gfc/uk-road-tracker
   ```

   There are no inputs and no source substitution. An owner can instead use
   Actions → Stage existing verified reference artifact → Run workflow → main.
   Review the resulting hash/SQLite/county checks and artifact identity. Remove
   the temporary secret promptly after the attempt, including failure. Do not
   blindly rerun an expired URL. No workflow attempts to delete/manage secrets.

4. **Separately approve one cache-seeding dispatch** only after successful artifact
   verification. The existing workflow can be used **without modification**:

   ```sh
   gh workflow run reference-pin-cache.yml --ref main \
     --repo adamdbird-gfc/uk-road-tracker \
     -f source_sha=79bcc970c176c6526224a91c24c89525f10bf152 \
     -f run_id=VERIFIED_STAGING_RUN_ID \
     -f artifact_name=roadprints-reference-pins-v2
   ```

   **Leave release_tag empty.** Use the new staging run's numeric ID, not its
   artifact ID and not any failed run. Existing seed code already downloads a
   same-repository Actions artifact with `actions: read`, validates the two pins,
   retains a reference artifact, saves the exact default-branch cache key, removes
   only the installed local pins, restores that key and revalidates. Source 79bcc97
   bypasses its broken release path when run_id/artifact_name are supplied.

   Cache success requires final restore/revalidation, not merely a saved message.
   Keep full Android verification/merge/APK packaging outside these approvals.

## Verification and limitations

Seven socket-blocked offline tests execute the proposed workflow's Python block
with mocked transfer and synthetic data: URL/redirect scope, bounded download,
expiry/no retry, bad ZIP, validation-before-output and absence of token/cookie
headers, plus manual/source/permissions/artifact contract. Existing 14 draft
staging tests remain unchanged. No live signed URL or staging job was tested.

Main remains `69537ebdf59d843a1a56bc3a637857134f546c0e` at preparation. Draft release,
asset and shared cache are unchanged. Full Android compile/lint/Robolectric still
need separately provisioned offline tooling. The missing practical prerequisite
is owner capture of a fresh GitHub signed URL and approval to store/use that
short-lived credential; without an approved ingress mechanism there is no fully
private, credential-free GitHub-hosted bootstrap under the current restrictions.

Primary references:
- [Release asset API / octet-stream and redirect behaviour](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)
- [Manual workflow inputs and size limit](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onworkflow_dispatchinputs)
- [Actions secrets and masking](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [Self-hosted runner security](https://docs.github.com/en/actions/reference/security/secure-use#hardening-for-self-hosted-runners)
