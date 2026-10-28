# Roadprints development governance

Status: proposed policy (documentation PR; effective after owner approval and merge).
Owner: Roadprints project owner. Last reviewed: 28 October 2026.

## Principles
- Protect working functionality, user journey evidence, private tester distribution, and production reference data.
- Prefer small, testable, reversible changes; minimise new APKs and deployments.
- Never equate a successful build, PR creation, or backend deployment with user acceptance.
- GitHub issues and PRs must reflect actual delivery state; distinguish implemented, integrated, deployed, tested, accepted and closed.

## Authoritative baseline and integration
- Before changing Android, record the installed APK version/code, exact source commit and branch, parent/base chain, backend version/deployment and reference-data version where known. Mark unknowns explicitly.
- Do not assume main contains the current Android beta. Map stacked PR dependencies before changing bases, merging, or closing.
- Prefer short-lived topic branches from the agreed integration baseline. If stacking is necessary, document parent PR, merge order and consolidation plan.
- Integrate only after compatibility and regression checks; do not bulk-merge old branches or rewrite history to tidy the backlog.
- Do not delete branches or close PRs merely because they are old. First verify whether changes are already integrated, superseded, or still needed.

## Task lifecycle and definition of done
1. Record scope, issue(s), acceptance criteria, regression risks and delivery constraints.
2. Establish the current baseline and reproduce the problem where feasible.
3. Implement the minimum scoped change with automated tests and targeted evidence.
4. Confirm relevant regressions and performance budgets, with honest limitations for unavailable real-device evidence.
5. Document the exact commit/branch, PR/base, CI results, backend deployment, APK version/code and tester distribution status (or explicitly 'none').
6. Update linked issues and PRs with evidence, unresolved items and owner acceptance status.
7. A task is only **completed** when its agreed acceptance criteria are verified and its delivery/integration state is accurately recorded. Otherwise mark **implemented—awaiting integration**, **deployed—awaiting acceptance**, **blocked**, or **deferred**.

## Approval gates
Explicit owner approval is required before:
- Merging into main or changing the agreed integration baseline.
- Building or distributing an Android APK unless specifically authorised in the task.
- Deploying backend, Firebase or production reference datasets unless authorised.
- Modifying or deleting production database records, indexes or schema; changing repository visibility; deleting branches or rewriting Git history.
- Closing issues where acceptance is ambiguous. A documentation-only PR may be opened for review without being merged.

Private development APKs must never be automatically released or distributed to beta testers. No unnecessary intermediate builds.

## Release discipline
- One purposeful build per approved validation milestone where feasible; identify version/code, source SHA, signing and artifact provenance.
- Distinguish CI success from installation, capture/matching, performance and real-device acceptance.
- Record release notes, known limitations, rollback path and distribution approval.
- Preserve a known-good version and avoid changing unrelated infrastructure during targeted fixes.

## Operational safeguards
- Preserve original GPS traces and user corrections; matching failures must not destroy evidence.
- Preserve walking matcher reference coverage and data unless a reversible, reviewed plan is approved.
- Track capacity, Firebase bandwidth, matcher workloads and UI performance using measured baselines; never invent measurements.
- Maintain regression scenarios for Dover terminal/ferry transitions, Charing Cross pedestrian journeys, stationary drift, NCN and core map/navigation performance as applicable.

## Backlog and PR housekeeping
- Link every development PR to an issue or documented scope, record its base and dependent PRs.
- At task end, update issue and PR statuses, document integration and deployment state, and identify follow-up ownership.
- Review stale PRs periodically, but close only with evidence of merge, supersession or explicit cancellation; never confuse draft with abandoned.
- Treat historical capacity issue #189 as a monitored risk; the October 19 measurement is not current. No destructive PostgreSQL work without approval.

## Required end-of-task report
- Objective and outcome; acceptance status.
- Exact source branch/commit, PR and target base; integrated into main? yes/no.
- Tests, CI, regressions and measured before/after evidence; limitations.
- Backend/Firebase changes and deployed version, or 'none'.
- APK version/code, artifact and tester distribution, or 'none'.
- Issues updated/closed and remaining work/risks.
- Any action requiring explicit owner approval.

## One-time reconciliation (separate from this documentation PR)
- Establish the exact source lineage for Android 0.25.220 (266) and the live backend.
- Map the 38 open PRs and stacked chains; classify active, superseded, integrated and deferred with evidence.
- Propose an integration plan with tests and rollback; do not merge, close, delete, build or deploy as part of the audit.
- Present a short owner decision list before any integration changes.
