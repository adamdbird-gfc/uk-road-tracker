# Roadprints 0.25.200 (246): Firebase reference-data migration

The app fetches Services/Wetherspoons silent catalogue manifests and payloads,
and public settlement-boundary fallback files, from the permanent Firebase data
site https://roadprints-data-822cc.web.app. Both consumers share one origin constant.
The existing catalogue validation, immutable revisions, atomic local cache,
bundled offline fallback and retry cadence remain. Settlement counts/metadata,
matching and map context still use the established Render API.

Public source-data changes publish automatically through the shared Firebase
publisher, including queue-created settlement commits; unchanged queue attempts
are skipped. Android does not need a release for each catalogue update.

Migration acceptance on a phone: update over the current signed APK; retain
journeys, visits and achievements; confirm installed collection updates and map
pins; exercise settlement boundary fallback where practical, fresh fetch and
cached/offline use. Existing installs continue using raw GitHub/Pages endpoints
until the separate retirement/support-window decision. No old endpoint is removed.

Package this completed slice once using Package Roadprints test APK on main,
selecting Firebase upload. Automatic development/PR checks do not publish APKs.
