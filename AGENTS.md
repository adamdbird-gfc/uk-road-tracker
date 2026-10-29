# Roadprints coding-agent instructions

Read [Development Governance](docs/DEVELOPMENT_GOVERNANCE.md) before making code or infrastructure changes. Its approval gates, baseline verification, definition of done and required delivery report remain mandatory. Use the [development handover](docs/development-handover.md) for the current entrypoint; older delivery sections are historical evidence.

The current validated `main` branch is the authoritative development and source integration baseline. Android **0.25.220 (266)** has been integrated into main, and the historical stacked Android and NCN development branches have been reconciled. Do not restart new work from obsolete historical branches or repeat the completed reconciliation.

Normally create short-lived feature/fix branches directly from the latest validated main and target main with PRs. A different baseline requires explicit owner approval. Avoid stacked PRs; where genuinely necessary, document dependencies, integration order and the plan to return to main.

For relevant Android, backend or reference-data work, verify the actual installed APK version/code and source provenance, live backend commit/deployment, and active Firebase reference-data versions/pins. Do not assume they match the latest source on main; record unknowns explicitly. Source integration, APK packaging, backend deployment, Firebase publication and real-device acceptance are separate delivery states.

Complete appropriate regression validation and obtain owner approval before merging. Never deploy, build/distribute APKs, delete branches, change repository visibility or modify production data without the owner's explicit approval. Preserve original journey data, reference datasets, regression coverage and private tester distribution safeguards. Prevent unapproved automatic deployments when merging source (for example, Render's `[skip render]` merge-commit phrase).

At the end of every task, report source commit/branch, PR/base, tests, integration, deployment, APK/distribution, issue status and outstanding acceptance. A task is not complete solely because a PR or build exists.
