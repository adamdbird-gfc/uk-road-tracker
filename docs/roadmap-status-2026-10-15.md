# Roadprints roadmap checkpoint — 15 October 2026

Current testing release: **0.25.201 (247)**, uploaded to Firebase with all 502
Android tests, lint and signature verification passing. Adam reports that all
beta users now have access. Device acceptance remains observation/testing;
do not reopen delivered features solely because feedback is pending.

| Area | Current position | Remaining work |
|---|---|---|
| UK settlement foundation | Delivered: 8,545 GB + 527 NI settlements, boundaries and ready inventory totals. Five count discrepancies reconciled; existing names/progress protected. Preview/live files and API checks passed. | Observe real-device consumption and progress; future refreshes are reviewed data releases. |
| Settlement processing | Routine timer previously stopped; legacy manual builders now archived on main and the app branch. | Retain explicit maintenance and measured storage/usage checks. |
| Firebase data and APK delivery | Dedicated public data hosting and ready-slice signed APK delivery are operational. Hosting allowance/retained-release headroom was reviewed for publication. | Record wider beta device acceptance and hosting/storage usage as usage develops. [#123](https://github.com/adamdbird-gfc/uk-road-tracker/issues/123) stays open until its remaining acceptance is recorded. |
| Build efficiency | One explicit pinned request produces a signed release; development checks do not distribute APKs. Known Chester retry cause repaired and routine settlement workload removed. | Actual GitHub account/private-plan allowance and cost assessment remains open in [#124](https://github.com/adamdbird-gfc/uk-road-tracker/issues/124). Firebase allowance is a separate question. |
| Android experience | Delivered: consistent larger footer icons, NI historic-county grouping, offline What's new with version/date/comparison and email feedback. | Field-test on Pixel/Samsung and other beta devices. Concrete faults become scoped fixes. |
| Existing collections | Motorway Service Stations and Wetherspoons work is delivered for beta; catalogue/data updates can be separate from APK releases. | Continue behavioural feedback. Verified purchase entitlements remain explicitly deferred; beta collection access stays purchase-free ([#75](https://github.com/adamdbird-gfc/uk-road-tracker/issues/75)). |
| Prototype and Pages retirement | Not complete. Public data has moved; older APK endpoints and remaining dependencies still need an explicit compatibility/retirement check. | [#125](https://github.com/adamdbird-gfc/uk-road-tracker/issues/125), the next infrastructure slice. Access to 0.25.201 is not proof that all older clients are gone. |
| Repository privacy | Repository remains public; no visibility switch has been made. | After Pages/dependency retirement, assess integrations and actual private-repository build/storage allowances, then make the visibility decision ([#126](https://github.com/adamdbird-gfc/uk-road-tracker/issues/126)). |
| National Cycle Network | Agreed next app feature after the infrastructure sequence. No NCN implementation is established by this checkpoint. | Verify dataset reuse terms/coverage and core cycling capability, then scope a representative-route slice ([#79](https://github.com/adamdbird-gfc/uk-road-tracker/issues/79)). |
| Later collections | Rail Explorer and Airport Passport remain refinement proposals. | Evidence rules, data feasibility and bounded delivery scope ([#80](https://github.com/adamdbird-gfc/uk-road-tracker/issues/80), [#81](https://github.com/adamdbird-gfc/uk-road-tracker/issues/81)). Core journey accuracy remains independent of collection purchases. |
| Public website/domain | Backlog; no purchase or published Roadprints site is established. | Website and `roadprints.co.uk` availability, registration, content and hosting ([#122](https://github.com/adamdbird-gfc/uk-road-tracker/issues/122)). |
| Additional achievement | Back to the Future is recorded as an idea. | Agree evidence/wording for entry into a verified settlement with zero eligible named local roads; zero is not proof of physically no roads ([#140](https://github.com/adamdbird-gfc/uk-road-tracker/issues/140)). |

## Agreed sequence

1. Close the settlement processing retirement and record the shipped baseline.
2. Complete the prototype/Pages dependency and compatibility retirement slice.
3. Finish the real account/build-cost and repository-privacy assessment.
4. Start NCN feasibility and a bounded first feature slice.

Beta feedback can interrupt this order for actionable capture, matching or
data-loss defects. No unsupported defect is declared fixed by a data release,
and passing automated checks is distinct from field acceptance. Ireland and
iOS remain longer-term expansion discussions, not replacements for this order.
