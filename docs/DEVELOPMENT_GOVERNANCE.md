# Roadprints development governance

Status: approved and adopted by the owner on 29 October 2026.
Owner: Roadprints project owner. Last reviewed: 29 October 2026.

## Principles
- Protect working functionality, user journey evidence, private tester distribution, and production reference data.
- Prefer small, testable, reversible changes; minimise new APKs and deployments.
- Never equate a successful build, PR creation, or backend deployment with user acceptance.
- GitHub issues and PRs must reflect actual delivery state; distinguish implemented, integrated, deployed, tested, accepted and closed.

## Authoritative baseline and integration
- The latest validated `main` is the authoritative source integration baseline. New development begins there unless the owner explicitly approves an exception.
- Android 0.25.220 (266) is integrated into main. Historical stacked Android and NCN branches have been reconciled; do not resume development from obsolete historical branches.
- Normally create short-lived feature and fix branches directly from main, and target main with their pull requests.
- Avoid stacked PRs. Where genuinely necessary and approved as a baseline exception, record dependencies, integration order and the plan to consolidate and return to main.
- Before integration, complete appropriate compatibility and regression validation and obtain owner approval. Do not bulk-merge old branches or rewrite history to tidy the backlog.
- Verify the actual installed APK version/code and source provenance, live backend commit/deployment, and active Firebase reference-data version/pins for relevant work. Record exact source commits/branches and any approved dependencies; mark unknowns explicitly. Installed, deployed and published versions do not automatically match main.
- Keep source integration, APK packaging, backend deployment, Firebase publication and real-device acceptance as distinct delivery states. A source merge authorises none of the other states by itself.
- Where deployment is not approved, prevent a source merge from triggering automatic deployment (for example, use Render's `[skip render]` merge-commit phrase).
- Preserve working journey data, reference datasets, regression coverage and private tester distribution safeguards. Do not delete branches or close PRs merely because they are old; first verify incorporation, supersession or remaining work.

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
- Merging into main or using a development baseline other than validated main.
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

## Completed reconciliation record
The one-time repository reconciliation is complete. Android 0.25.220 (266) was
integrated through #233; router regression preservation through #234; NCN
maintenance resources through #235; and the permanent integration record through
#236. The final reconciliation baseline is
`d62b9e40c6095f93c82efe95e1686d0a0f73d01e`. Future approved development advances
from the latest validated main, rather than remaining pinned to that historical commit.

The 39 historical PRs were closed with explanatory comments, and all 40 reviewed
historical branches were retained. These records are evidence, not instructions
to restart the old audit or merge historical stacks. The superseded one-time audit
instructions remain available in this file's Git history.

See the [permanent integration and validation record](android-025220-integrated-baseline.md),
[NCN provenance and research dispositions](ncn-historical-reconciliation.md), and
[current development handover](development-handover.md).
