# Roadprints 0.25.201 (247) — 15 October 2026

Utilities now includes **What's new**, an offline release-notes screen showing
the release number, date and plain-language changes since the previous version.
It introduces the consistent larger footer icons and completed GB/NI settlement
coverage, including the offline historic county memberships added in PR #151.

The contact section says “Spotted an issue, or would like to suggest a new
feature?” and provides a **Contact Roadprints** button. It opens a mailto draft
to `adamdbird@gmail.com`, with the installed version in the subject. It attaches
no diagnostics or journey data. If no email app is available, a dialog offers
an explicit copy-address action.

Release entries live in `android-prototype/app/src/main/assets/release-notes.json`.
Keep previous entries when adding future releases; record the actual release
date, comparison version and user-facing changes. Notes are shown newest first
and future versions are excluded. Android tests require an entry matching the
installed build's version name and code, so a version bump without matching
notes fails packaging. The screen uses the existing scrolling utility layout,
system-bar insets, accessible headings and a Back to Utilities footer.

Phone acceptance: open Utilities → What's new; check version/date and read the
notes with larger system text; tap Contact Roadprints and verify the recipient;
cancel the draft; return to Utilities. Check larger footer icons and NI county
grouping while confirming existing journeys and progress remain available.
