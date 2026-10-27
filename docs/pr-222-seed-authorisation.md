# PR #222 — ready-to-authorise GitHub cache seed

## Recommendation and current state

**GO for approval of the staged operation described here. NO-GO for execution
until approval, authenticated upload capability and workflow registration exist.**
Full Android verification remains a separate NO-GO until tooling/dependency caches
are pre-provisioned. Do not merge PR #222 or use an APK build workflow to bootstrap.

The existing bundle was verified locally again, without regeneration or downloads:

| File | Bytes | SHA-256 |
|---|---:|---|
| ZIP `roadprints-reference-pins-2026-10-27.zip` | 157,393,480 | `d547847572e574cecd16cdb8e08077bf2e0a1014507c7f876c94c7c88b97853c` |
| Member `catalogue.db` | 243,724,288 | `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093` |
| Member `boundaries.bin` | 8,992,277 | `b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850` |

The current staging workspace can read the verified ZIP at
`/workspace/scratch/0707e03317a5/roadprints/.cache/reference-bootstrap/roadprints-reference-pins-2026-10-27.zip`.
A durable copy is [available here](https://chatgpt.com/library?file_id=libfile_84308ddbbf488191add27b1e27dd0e5e&account_id=personal).
A GitHub runner cannot directly read this workspace/Library file; the approved
external draft-release upload below bridges that boundary. No such upload occurred.

## Exact proposed operation — none of these commands has been executed

1. Resolve the approved, immutable PR #222 head into `REVIEWED_SHA`. Confirm the
   reviewed workflow file's SHA-256 is
   `ab164307bdf83d30e77134e081fc42418affeeba61481a0a77afce2f20624c41`.
   Reject changed workflow/source content until reviewed again.
2. With an existing owner-authorised GitHub upload client, create the private draft
   release `reference-pins-2026-10-27-bootstrap` and upload the exact ZIP. This
   requires repository contents-write authority for the operator, not the seed job.
   Do not publish the draft, upload a replacement or use `--clobber`.

   Example for an already authenticated, already installed GitHub CLI:

   ```sh
   gh release create reference-pins-2026-10-27-bootstrap \
     --repo adamdbird-gfc/uk-road-tracker --draft --target "$REVIEWED_SHA" \
     --title 'Verified reference pins — private bootstrap' \
     --notes 'Exact verified PR 222 reference bundle. Keep draft; no app release.'
   gh release upload reference-pins-2026-10-27-bootstrap \
     /absolute/path/roadprints-reference-pins-2026-10-27.zip \
     --repo adamdbird-gfc/uk-road-tracker
   ```

   The current connected GitHub tools expose no local binary asset-upload method
   and `gh` is absent here. Do not export credentials or install a client silently;
   an approved existing uploader/operator is a prerequisite.
3. Separately authorise registration of **only** the reviewed
   `.github/workflows/reference-pin-cache.yml` on the default branch (`main`).
   Do not merge the PR or copy app code. The workflow checks out `source_sha`, so
   its validation/helpers come from the approved PR commit, not main's older app.
   The file was confirmed absent from main by a read-only GitHub contents check.
   Recheck the default branch and trigger filters immediately before this write.
   The registration commit must include `[skip ci] [skip render]`; confirm any
   external auto-deployment integration honours the skip before making that write.
   If this cannot be established, registration is NO-GO pending separate review.
   This single dispatch-only workflow path does not match the currently inspected
   automatic build/publication path filters. Registration is a repository write,
   and remains unapproved/unperformed in this task.
4. Explicitly authorise **one** manual run:
   - Workflow: **Seed verified offline Android reference pins**
   - File: `reference-pin-cache.yml`; event: `workflow_dispatch`; ref: `main`.
   - `source_sha`: the approved complete 40-character `REVIEWED_SHA`.
   - `release_tag`: `reference-pins-2026-10-27-bootstrap`.
   - Leave `run_id` and `artifact_name` blank for this first bootstrap.

   ```sh
   gh workflow run reference-pin-cache.yml --ref main \
     --repo adamdbird-gfc/uk-road-tracker \
     -f source_sha="$REVIEWED_SHA" \
     -f release_tag=reference-pins-2026-10-27-bootstrap
   ```

5. After that approved run only, inspect its results: ZIP/member pins, SQLite check,
   root-level artifact `roadprints-reference-pins-v2`, cache save, deletion of the
   two local installed pins, exact-key restore and revalidation. A saved-cache
   message without successful restore/revalidation is insufficient. Record the
   run/artifact/cache identities before enabling future development builds.

The alternative existing-artifact mode remains supported: supply a run ID and
artifact name instead of `release_tag`. Never pass the prior APK/report/receipt
artifact IDs as reference bundles. This preparation authorises neither mode.

## Network/dependency and trigger review

The seed workflow uses `ubuntu-24.04`'s existing Python 3; no Python setup/download,
pip, Java, SDK, Gradle, app startup, test suite or APK task is invoked. The workflow
is owner-only, dispatch-only, serialized and restricted to default-branch scope.
Its token has **contents: read / actions: read** and no Firebase/production secrets.

| Operation | Anticipated network |
|---|---|
| External draft creation/upload, after approval | GitHub repository/release APIs and GitHub asset storage; no application deployment. |
| Pinned checkout | GitHub git/source retrieval for `source_sha`; credentials are not persisted. |
| New release downloader | Fixed same-repository `api.github.com/.../releases/` paths and redirects only to `release-assets.githubusercontent.com`; HTTPS only. API tokens are stripped from cross-host redirects. Other hosts, including Firebase, fail before redirect connection. |
| Optional artifact restore | Official GitHub Actions artifact API/storage. |
| Local validation/extraction | No network: archive size/hash, fixed member sizes/hashes, SQLite integrity/route count and county pin checks. Imported files are never executed. |
| Artifact retention / cache save and restore | Official GitHub Actions artifact/cache services and their signed GitHub-managed storage. |

Official Node20 action definitions were inspected at the exact commits below;
all action references in this workflow are immutable. They are standard trusted
GitHub transfer actions, not a general network sandbox for arbitrary future code.
The explicit release downloader is host-restricted; no Firebase client, deployment
script or Android task is on the workflow's executed path. No end-to-end network
trace is claimed: the first run is still pending authorisation.

- checkout: `11d5960a326750d5838078e36cf38b85af677262`.
- download-artifact: `d3f86a106a0bac45b974a628896c90dbdf5c8093`.
- upload-artifact: `ea165f8d65b6e75b540449e92b4886f43607fa02`.
- cache save/restore: `0057852bfaa89a56745cba8c7296529d2fc39830`.

No pushes, PRs, schedules or workflow_run events can start this seed workflow.
Its name is not among the settlement workflows watched by the repository's
publication workflow. It does not write a repository ref, publish a release,
request a beta, call a reusable packaging workflow or deploy. The proposed draft
release is a new metadata record; its publication/deployment is expressly excluded.

Cache key and paths exactly match both Android consumers:
`reference-pins-v2-${runner.os}-${hashFiles(NCN manifest, county catalogue)}`;
`.cache/ncn/catalogue.db` and
`android-prototype/app/src/main/assets/historic-counties/boundaries.bin`.
The key is computed from the checked-out reviewed source. Default-branch scope is
visible to later PR/feature builds; the workflow rejects PR/feature seed refs.
Pin changes create a new key and require a new approved bundle/pin review. Eviction
causes consumers to fail closed. A corrupt existing immutable cache must be
explicitly removed and reseeded; there is no broad-key or Firebase fallback.
Retain the private draft bundle for recovery; artifact retention is 90 days and
cache retention is separately controlled by GitHub.

## Controlled offline Android verification — prepared, not executed

Minimum: JDK 17; Gradle 8.10; Android SDK platform 35 and build tools selected by
AGP 8.6.1; cached AGP 8.6.1 / Google Services 4.4.4 / Crashlytics 3.0.6 plugins;
all existing app Maven dependencies; Robolectric 4.14.1 plus instrumented API
28/33 jars; JUnit 4.13.2; org.json 20240303; both verified reference pins.

Only JDK 17 and the real reference pins are available here. Historical successful
Ubuntu build jobs establish a compatible runner class, not a currently reusable
persistent SDK/Gradle/Robolectric workspace. No suitable fully provisioned runner
or dependency cache has been confirmed, and no downloads/compilation were started.

One later controlled procedure, after separate verification authorisation:

1. Restore the exact reference cache and pre-provisioned tool/dependency caches
   through GitHub-only steps. Check every prerequisite locally; stop if absent.
2. Run `verify_offline_build_controls.py`, `prepare_ncn_reference.py` and county
   preparation. They must validate the restored pins with no network transport.
3. Run the compile/lint/test phase inside an egress-denied network namespace on
   the prepared Linux runner. Enable loopback only for Gradle's local IPC; no
   external interface/route. If isolation is unavailable, stop rather than fall
   back to an online invocation. Preserve explicit SDK/cache paths for the process.
4. Within that isolation, run only:

   ```sh
   NCN_TEST_DATABASE="$PWD/.cache/ncn/catalogue.db" \
   gradle --offline --no-daemon -p android-prototype \
     :app:compileDebugJavaWithJavac :app:lintDebug :app:testDebugUnitTest \
     -Drobolectric.offline=true \
     -Drobolectric.dependency.dir=/absolute/preprovisioned-robolectric-jars
   ```

   The reviewed Test configuration forwards these flags and disables reference
   connections. Do not use offline test exclusions, dependency refresh, SDK
   installation, assemble, signing, upload or application launch tasks.
5. Retain results; report failures honestly. A green seed run alone does not make
   the Android application verified or authorise merging the PR.

## Evidence from this preparation

Existing real ZIP and both member hashes/sizes checked; no asset regeneration.
Five new local safety tests passed with socket connections blocked (host rules,
redirect token handling, corrupt input, input validation and workflow restrictions).
The adjusted existing manual-seed contract test passed. The new extractor also
processed the existing real bundle locally and its NCN output passed SQLite
validation. Python syntax, YAML parsing and diff checks pass. These are local
staging checks, not an Actions run or Android build.

**Approval requested next:** the private GitHub draft/asset upload, the single-file
manual-workflow registration on main, and one explicitly scoped seed dispatch
using the reviewed immutable source SHA. None has been performed. SDK/dependency
installation, full Android verification, merge, APK creation and distribution are
outside that approval.
