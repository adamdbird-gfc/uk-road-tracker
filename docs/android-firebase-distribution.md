# Roadprints Firebase APK uploads

The **Package Roadprints test APK** workflow (`build-android-prototype.yml`)
packages a signed APK only when manually requested for a complete delivery slice.
The separate **Android checks** workflow runs unit tests and lint for development
changes and pull requests, without uploading APKs or distributing releases.
Packaging repeats those checks and verifies the stable APK signature before an
optional Firebase App Distribution upload. It does not create GitHub releases.
Failed-check reports expire after three days; signed APK artifacts after seven.

## One-time account setup

1. Open [Firebase App Distribution](https://console.firebase.google.com/project/roadprints-822cc/appdistribution).
   Select **Roadprints Android** (`com.roadprints.capture`) and click **Get started**
   if prompted.
2. Open [Google Cloud service accounts](https://console.cloud.google.com/iam-admin/serviceaccounts?project=roadprints-822cc).
   Create a dedicated service account named `roadprints-app-distribution` in
   `roadprints-822cc`. Grant it **Firebase App Distribution Admin**
   (`roles/firebaseappdistro.admin`), rather than project Owner or Editor.
3. On that service account, open **Keys → Add key → Create new key → JSON**.
4. Open [GitHub repository Actions secrets](https://github.com/adamdbird-gfc/uk-road-tracker/settings/secrets/actions).
   Add a repository secret named **FIREBASE_SERVICE_ACCOUNT_JSON**, containing the
   entire downloaded service-account JSON. Add it directly to GitHub; do not commit
   it or paste it into a chat. The Android `google-services.json` is app configuration
   and cannot authenticate a deployment.
5. The existing `ROADPRINTS_FIREBASE_UPLOAD` variable is no longer used. Uploads
   require the manual checkbox below; no push or pull request distributes a release.

## Deploy from GitHub

Run **Package Roadprints test APK**, select `fix/onboarding-start-tracking`
(or the reviewed delivery branch containing this workflow), and tick
**Upload the verified APK to Firebase App Distribution** when the slice is ready.
Leave the checkbox off to create a signed artifact without uploading it.
The Firebase job prints release links after a successful upload.

Uploads do not automatically invite or notify testers. Open the uploaded release
in Firebase and select the testers or group when you are ready to share it.
Tester email addresses are managed there rather than stored in this repository.
The same stable signing key is used, allowing updates over existing test APKs.

## Deploy from a local terminal

After installing Firebase CLI, authenticate using `firebase login`, then:

```bash
bash scripts/upload_android_firebase.sh /path/to/signed-roadprints.apk /path/to/release-notes.txt
```

For automation, set `GOOGLE_APPLICATION_CREDENTIALS` to the service-account key
file instead of using interactive login. The helper reads the Firebase App ID
from the registered Android app configuration and validates its project/package.
Upload only an APK built from the current checkout; default notes use its version
and commit. Local callers must verify their APK before uploading; CI does so first.

## Verification and failures

- A completed upload appears under Roadprints Android in App Distribution.
- A missing credential produces an explicit setup error rather than silently skipping.
- `403`: check the service account's project and App Distribution Admin role.
- `404` / app not found: complete App Distribution's Get started step for this app.
- A failed upload can be retried by rerunning the Firebase job in GitHub Actions.
- Credentials are written with owner-only permissions in the runner temporary
  directory and removed after the upload, including failure paths.

References: [Firebase CLI APK distribution](https://firebase.google.com/docs/app-distribution/android/distribute-cli),
[App Distribution IAM role](https://docs.cloud.google.com/iam/docs/roles-permissions/firebaseappdistro).
