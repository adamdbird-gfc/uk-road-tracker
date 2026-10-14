# Firebase reference-data migration and ready-slice packaging

First implementation step for #123 and #124, 14 October 2026. Neither issue is
complete. Pages/runtime URL cutover, live Hosting, private-repository assessment,
and the migration APK remain later gates. No app version increment is needed yet.

## Audited baseline

The current app is 0.25.199 (245) at release branch `fix/onboarding-start-tracking`,
commit `7c860e87f0d7c997bc6ef6427d2f7eaad1e77814`. `main` contains an older Android
prototype but receives queued settlement inventory publications. Do not merge the
older app over the release branch or deploy its older venue catalogue.

| Consumer | Current source | Migration action |
| --- | --- | --- |
| VenueCatalogueUpdates | Raw GitHub release-branch `collections/published/{collection}/manifest-v1.json` and immutable `catalogue-{revision}.json` | Stage both collections and existing releases; switch URL only after live verification |
| LocalRoadSettlementMatcher.boundary | Render `/settlement-boundaries/{code}`, then Pages `settlement-inventories-v1/{code}-boundary.geojson` fallback | Stage latest main-branch boundaries; switch fallback after live verification |
| Settlement inventory counts/requests/metadata | Render API/database | Preserve API; static manifest/inventories accompany boundaries for publication compatibility |
| Map context and journey matching | Render API | Preserve API |
| Motorways, A-roads, rail stations, historic counties, landmarks and bundled collections | APK assets | Keep bundled; no remote hosting migration needed for current consumers |
| Base map tiles / uncached motorway fallback | OpenStreetMap tiles / Overpass | Outside this static-data cutover |
| Historic county build fallback | Existing versioned GitHub release APK | Build-time privacy dependency; preserve pinned bundled assets and address before #126 |

Reference data from `main` must be combined with current release-branch venue
releases. The staging workflow checks out both sources and records their exact
SHAs. The staging directory is allowlisted; it excludes prototype HTML, source
code, credentials, app configuration, personal data and APKs. It validates the
current catalogue payload hash/size/version and checks inventory manifest counts.
Older immutable catalogue payloads remain for requests with an earlier manifest.
The file hash report stays in the CI artifact, outside the Hosting public root.

## Build audit and change

The Actions history shows both `Android prototype build` and `Build Android
capture prototype` ran on merged 0.25.197, 0.25.198 and 0.25.199 commits. Each
packaged an APK; development branches additionally ran packaging on push plus
PR checks. Both had paths to Firebase upload. This is observed duplication, not
an estimate of billed minutes.

- `android-prototype-build.yml` retains its `build-debug-apk` job ID for existing
  check compatibility, but now runs unit tests/lint, with no APK artifact,
  signing secret or distribution step. Automatic checks cover release/main pushes
  and relevant PRs. Outdated checks can be cancelled by a newer commit.
- `build-android-prototype.yml` is the sole manually dispatched signed packaging
  workflow. It runs existing checks, verifies the stable signature, retains one
  signed artifact for seven days, and optionally uploads that same artifact to
  Firebase. No pushes, PRs or legacy upload variable trigger distribution.
- Check/packaging verification reports and temporary settlement job artifacts
  expire after three days. Existing durable releases/Firebase uploads stay intact.
- Settlement queue schedule remains `17,47 * * * *` (48 nominal invocations/day,
  not guaranteed actual runs). It serves outstanding app requests. Recent runs
  include failures and successes; measure job duration, failures, queue backlog
  and no-op frequency before changing its cadence. Database processing is retained.

Workflow edits on the release branch do not change scheduled jobs on `main`.
After verification, port only the settled workflow changes to `main`, preserving
its newer data and backend code. The old main Android packaging workflow and
branch-specific manual workflow visibility also need reconciliation before #124
can close. Check rulesets/required check names before merging changed job behaviour.

## Hosting setup and preview gate

Firebase Hosting state/access is not available in this local checkout. The
existing App Distribution deployment account has not been verified to have
Hosting permissions. The project ID is known: `roadprints-822cc`.

1. In that project's Firebase Hosting console, verify/create a dedicated data
   site; keep it distinct from the future public website and existing hosted content.
2. Set repository Actions variable `ROADPRINTS_DATA_HOSTING_SITE` to its verified
   site ID. No assumed site hostname is committed to Android.
3. Configure a dedicated Hosting deployment service account with Firebase Hosting
   Admin access in this project. Store its JSON in repository Actions secret
   `FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON`; never in the repository or chat.
   Verify permission requirements against the actual CLI deployment response.
4. Register the new workflow on the default branch with a focused workflow-only
   change after review (GitHub requires this for manual dispatch). Then run
   **Stage Firebase reference data** on this delivery branch with
   `preview_deploy` selected. It validates first, binds only `reference-data` to
   the chosen site, and deploys a seven-day `data-migration` preview channel.
   It cannot deploy to the live channel or change Android URLs.
5. Verify HTTP 200, every staged file hash, manifest/payload consistency, content
   types, caching headers and absence of prototype/code/credential files at the
   preview URL. Review actual project quotas/billing before live publication.

Local staging (use an empty output directory):

```bash
python -m unittest discover -s scripts -p test_firebase_reference_data.py -v
python scripts/stage_firebase_reference_data.py --settlements-root /path/to/main-data-checkout
```

## Remaining delivery gates

After preview verification, configure live publication from the correct source
branches so settlement/venue updates continue to reach Hosting. Verify live bytes
and headers, then centralise/switch the two Android static-data URLs. Keep the old
endpoints during migration. Test one complete migration APK on Firebase: fresh
fetch, cached/offline use, new catalogue revision, boundary API fallback and
preserved visits/progress. Agree the old-APK support window before retiring Pages
(#125). Finally use actual remaining access dependencies/build usage for #126.
Do not disable scheduled publication, Pages or change repository visibility yet.

References:
- https://firebase.google.com/docs/hosting/full-config
- https://firebase.google.com/docs/hosting/test-preview-deploy
- https://firebase.google.com/docs/cli#deploy_targets
