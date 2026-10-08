# Roadprints Firebase APK uploads

The existing **Build Android capture prototype** workflow can upload its signed,
verified APK to Firebase App Distribution via Firebase CLI 15.33.0.
The Firebase job depends on the build job, so unit tests, lint and APK signature
verification must succeed first. GitHub release publication continues separately.
No Android version change is needed for this deployment setup.

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
5. To enable automatic uploads, add a repository **Actions variable** named
   **ROADPRINTS_FIREBASE_UPLOAD** with value **true**. This enables uploads for builds
   on `feat/firebase-beta-measurement` and `main`. Other branches can use the manual
   option below. Leave the variable unset to use manual uploads only.

## Deploy from GitHub

Run **Build Android capture prototype**, select `feat/firebase-beta-measurement`
(or another branch containing this workflow), and tick
**Upload the verified APK to Firebase App Distribution**.
The Firebase job will print the release links after a successful upload.
When the automatic-upload variable is enabled, supported branch builds upload
without selecting that checkbox.

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
