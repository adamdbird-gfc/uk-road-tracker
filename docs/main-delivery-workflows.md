# Manual delivery entry points on main

Main holds the older Android prototype and the current settlement publication.
Current Android development lives on fix/onboarding-start-tracking. These focused
workflow changes preserve main's app, backend and public reference data files.

- Package Roadprints test APK is manual only. It delegates to the reviewed
  release-branch workflow and explicitly checks out the current app source, runs
  tests/lint, verifies stable signing and optionally uploads to Firebase. It cannot
  automatically package main's obsolete app on push.
- Stage Firebase reference data is a registered manual entry point. It delegates
  to the current release branch for collection data/scripts and combines latest
  main settlement files. Preview deployment is opt-in; no live deploy is available.
- The queue retains its existing schedule and database behaviour. Intermediate
  settlement artifacts expire after three days. Its ignored unsupported
  download-artifact input is removed; the existing pattern download remains.

The reusable workflow definitions must be merged through PR #128 before these
entry points can execute. Firebase Hosting still requires the verified
ROADPRINTS_DATA_HOSTING_SITE variable and FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON
secret. Those are distinct from the established App Distribution credentials.
A connected Firebase account is needed to inspect and configure actual Hosting.

Neither Pages retirement, Android URL cutover nor repository privacy occurs here.
These remain part of the later delivery gates for issues #123 to #126.
