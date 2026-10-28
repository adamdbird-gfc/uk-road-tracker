# Roadprints coding-agent instructions

Read [Development Governance](docs/DEVELOPMENT_GOVERNANCE.md) before making code or infrastructure changes. Treat its approval gates, baseline verification, definition of done and required delivery report as mandatory.

Do not assume `main` is the current Android beta baseline. Inspect stacked PR dependencies and record exact commits before proposing integration. Never merge, deploy, build/distribute APKs, delete branches, change repository visibility or modify production data without the owner's explicit approval.

At the end of every task, report source commit/branch, PR/base, tests, integration, deployment, APK/distribution, issue status and outstanding acceptance. A task is not complete solely because a PR or build exists.
