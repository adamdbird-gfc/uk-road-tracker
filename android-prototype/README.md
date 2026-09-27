# Roadprints Android capture prototype

This is an isolated, deliberately small first slice for validating the native capture path.

It provides:

- foreground location permission
- temporary manual Start/Stop controls
- driving or walking mode selection
- local GPS breadcrumbs
- Journey-shaped JSON saved in device-only SharedPreferences
- no background tracking
- no network upload
- no route matching yet

The Start/Stop controls are a testing harness. The final Roadprints experience is intended to detect journeys automatically after capture quality, storage and processing have been validated.

## Build

Open this directory in Android Studio and run the app configuration on a physical Android device. Location capture should be tested outdoors with the device location setting enabled.

The prototype intentionally uses platform Android APIs and no external runtime services. It is not yet the production Roadprints app.
