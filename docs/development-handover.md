# Roadprints development handover

Last reviewed: 2026-10-19. This is the **entrypoint**, not a replacement for issue specifications or code review.

## Start every ChatGPT / Work development session here

1. Read this document and [release integration issue #194](https://github.com/adamdbird-gfc/uk-road-tracker/issues/194) before modifying code or proposing a build.
2. Review current GitHub branch/PR state, CI, merge bases and changes since this document was last updated. Do not assume open PRs are merged, published or deployed.
3. For NCN work, read [epic #79](https://github.com/adamdbird-gfc/uk-road-tracker/issues/79) and child issues [#190](https://github.com/adamdbird-gfc/uk-road-tracker/issues/190), [#191](https://github.com/adamdbird-gfc/uk-road-tracker/issues/191), [#192](https://github.com/adamdbird-gfc/uk-road-tracker/issues/192), [#193](https://github.com/adamdbird-gfc/uk-road-tracker/issues/193) and [#194](https://github.com/adamdbird-gfc/uk-road-tracker/issues/194).
4. Check existing components and stacked PRs before implementing. Reuse established Progress, route-detail, journey-list, collection and navigation behaviours. Record decisions, findings, blockers and tests in the appropriate GitHub issue.
5. Classify outstanding PRs **include / defer / superseded** after reviewing their dependencies and overlaps. Do not blindly merge stacked or divergent branches.
6. Complete coherent feature slices and verify integrated tests before preparing **one coordinated beta APK / Firebase release**. No build or deployment merely to demonstrate a small UI decision. Obtain the user's approval for release when appropriate.

## Release scope and dependencies to assess

- **Service-station visit recognition:** [PR #169](https://github.com/adamdbird-gfc/uk-road-tracker/pull/169), mapped service-site footprints, genuine dwell versus passing traffic, saved Warwick Northbound visit recovery, older hosted catalogue compatibility. Real-device verification remains.
- **Place-aware capture:** [#163](https://github.com/adamdbird-gfc/uk-road-tracker/pull/163), [#164](https://github.com/adamdbird-gfc/uk-road-tracker/pull/164), [#170](https://github.com/adamdbird-gfc/uk-road-tracker/pull/170), [#182](https://github.com/adamdbird-gfc/uk-road-tracker/pull/182): stops at schools/businesses/residences versus continuing running/walking, stationary drift and validated arrival duration.
- **Driving geometry and journey quality:** [#173](https://github.com/adamdbird-gfc/uk-road-tracker/pull/173), [#174](https://github.com/adamdbird-gfc/uk-road-tracker/pull/174): correct routing, continuous map strokes and accurate travel mode.
- **Map/history/provenance:** [#166](https://github.com/adamdbird-gfc/uk-road-tracker/pull/166), [#167](https://github.com/adamdbird-gfc/uk-road-tracker/pull/167), [#168](https://github.com/adamdbird-gfc/uk-road-tracker/pull/168).
- **Shared interactive Progress:** [PR #171](https://github.com/adamdbird-gfc/uk-road-tracker/pull/171) contains focused motorway/A-road maps and contributing journey lists. It is open/unmerged as of this review.
- **NCN Android trial:** [PR #172](https://github.com/adamdbird-gfc/uk-road-tracker/pull/172) is an opt-in eight-route trial stacked on #171, **not** the final collection. It already reuses shared route-detail plumbing.
- **NCN national data:** draft stacked [PRs #175–#187](https://github.com/adamdbird-gfc/uk-road-tracker/pulls) culminate in 219 supported route identities, 34 evidence holds and shared physical ways. A production adapter must handle overlaps and alternatives rather than assuming the trial compiler supports them.
- Triage old open PRs #1, #6–#8 as potentially stale; do not auto-merge.

## Agreed NCN product direction (details in #192)

- Only verified **open** route sections count; preserve route identities; hold unsupported or planned sections.
- Walking/running and cycling contribute; driving does not. Combined completion is a **unique physical union**, not the sum of mode totals.
- Collection map: purple completed sections; muted grey dashed remaining eligible sections; temporarily subdue ordinary Roadprints road overlays, restoring prior state on exit.
- Route taps: verified identity, eligible distance, verified endpoints, mode-filtered completion and road-style contributing journeys. For shared geometry, offer every verified numbered route identity.
- Progress: National, Regional and Named route groups, collapsible like existing lists; numerical sort for numbered routes; search, Jump-to entry, route-focused maps and interactive charts.
- **NCN charts use signpost red**, distinct from **purple completed map strokes**. Motorway and A-road charts retain their existing blue/green conventions.
- Retrospective history assessment should be resumable, correction/deletion aware, and not flood users with review prompts. Purchases/paywall remain deferred for beta.

## Release readiness gates

- Review merge dependency graph and overlapping files; explicitly choose include/defer/superseded for each PR.
- Integrate and validate full NCN source-way/provenance adapter, open-only exclusions, shared physical coverage, route alternatives, and bounded memory/storage.
- Test service-station genuine visits vs drive-through; place-aware trip end/start; walking duration; continuous motorway geometry; saved journey edits/deletions; NCN mode filtering, charts, route maps and contributing journeys.
- Check CI, licences/source provenance, versioned catalogue delivery, migrations, rollback and Firebase staging.
- Perform real-device beta acceptance before release; do not interpret passing branch-level unit tests as proof of an integrated APK.

## Session handover discipline

At the end of substantial work, update issue #194 with: branch/commit or PR, what changed, test evidence, what remains, blockers, dependencies and the next recommended slice. Update the relevant feature issue as well. Prefer links and verifiable state over claims based only on conversation history. Ordinary Chat and Work can both read GitHub, but new sessions should be explicitly directed to this file.

## Reliability continuation — 19 October 2026

Branch: `fix/capture-mode-transition-evidence`, stacked on #182 (`13084738`). See [the reliability audit](tracking-reliability-audit-2026-10-19.md) for reviewed heads, include/defer decisions, file overlaps and outstanding work.

The new slice fixes early activity callbacks splitting at their timestamp instead of the start of GPS-supported mode movement. Stops/gaps cannot pool stale movement votes; the existing handoff preserves every measured edge with one shared endpoint. It does not resolve all of #188 or repair existing saved geometry.

Validation: 34 local JVM tests passed, including 11 new mode-boundary cases; an Android handoff regression is added for PR checks. Full Android tests/lint remain subject to CI. No version bump, APK build, Firebase deployment, merge or live-data publication.

Next recommended slice: reconcile #163 place-visit behaviour onto #170 → #174 → #182 plus this mode-boundary fix, with overnight home drift/morning departure and genuine venue-exercise service tests. Integrate #164/#173 backend changes separately and verify full-route geometry; retain #168 source evidence when NCN joins. #169 visits, #167 late saves and the map/Progress/NCN stacks remain release dependencies, not implemented again here. Keep #188/#194 open pending integrated tests and real-device acceptance.
