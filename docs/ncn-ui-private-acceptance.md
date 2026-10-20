# NCN corrective development acceptance — 0.25.209 (255)

Baseline: installed 0.25.207 (253), exact source
`02ddf96aba6c403e2e88eab0d764feb61ef27ec4`.
The national reference manifest/data, route evidence rules, assessment ledger and
journey/matching calculations are unchanged. This build is private review only.

## Follow-up consistency review — 0.25.209 (255)

Based on the installed private 0.25.208 (254), not a new catalogue. Verify:

- Compare M20/A12 Progress rows with NCN and RCN rows: identical 78 × 32 dp rounded
  identifier box footprint, white text, bar/percentage alignment and compact spacing.
  NCN is purple, motorway blue, A-road green. VIEW MAP remains visible and the
  entire NCN row is a touch target of at least 64dp.
- Open representative motorway, A-road and NCN maps: the summary identifier uses
  the same badge; the main heading, percentage, distances, map fit/zoom and journey
  actions remain. Check A627(M), A12, A1(M), M20 and NCN344 where available.
- Compare NCN search and filter controls to Services/Spoons: white search field,
  jade outline/label, gold selected activity and navy outlined unselected buttons.
- Search route number, NCN/RCN identity with/without colon or spaces, and displayed
  name. Matching routes should appear across collapsed groups; a query with no
  matches must show the empty state without stale visible groups.
- Keep a query while switching Combined/Cycling/Walking & running. Check the
  selected state and values; the text field must remain stable. The eligible
  reference catalogue/denominator does not change with activity.
- Expand/collapse groups while browsing, then search and Clear. Clear restores
  browsing expansion preferences and all routes. Search-time expansion must not
  silently overwrite the saved browsing preference.
- Dismiss the keyboard with Search/Done, Clear, activity selection and route-map
  navigation; return from a route map should preserve query/activity/group state.
- Scroll the full list on device, including a narrow display and larger text.
  Automated compact-height checks are at standard text scale, not device proof.

The full Android suite includes combined national-data search/mode/clear/empty/
return-state checks, shared badge colour/dimension checks and existing collection
regressions. Real-device keyboard behaviour, pixel alignment, large-font layout
and scrolling performance remain owner acceptance work. No Firebase tester release
is authorised by this checklist.

## Changes and automated coverage

| Area | Implementation / check |
|---|---|
| Celebration | Full-width centred eyebrow/invitation; symmetrical Close padding and 48dp minimum touch height. Existing queue/carousel/acknowledgement unchanged. Layout and pending-state regression added. |
| Initial NCN card | Shared 40dp jade icon tile and existing bicycle drawable; description-to-action spacing; approximate download/offline explanation. |
| Installed NCN card | Shared card header/actions, Installed · beta access, live download/assessment status, error Retry, secondary removal confirmation. Cancel preserves activation; removal only deactivates NCN and retains catalogue/journeys. |
| Shared summary | Compact identity/percentage header, rounded white track, completed/remaining metrics and explicit reference scope. Existing numerical coverage retained; colour/fraction regression added. |
| NCN Progress | Unique physical-network summary; purple/lavender route identities, purple fill, separate VIEW MAP touch action; existing group/search/mode handling retained. National UI test exercises route/mode navigation. |
| Jump navigation | NCN included in section tracking; explicit jump selects immediately; bottom-of-content tracking; active chip scrolled into view. Regression exercises NCN/another target. |
| Map styling | Solid opaque orange remaining lines, purple completed lines, round caps/joins; three-dp main overlay width. Reference vertices/connectivity and route overlap selection unchanged. Paint and camera-retention regression added. |
| Development distribution | Full build workflow contains no Firebase job/credential. Four Python safeguard tests exercise manual-owner/exact-artifact approval, wrong commit, failed/unrelated run and automatic workflow callers. |

The corrected full CI result and artifact identity are recorded in the development
handover after completion. No automated test proves device visual acceptance.

## Required owner device review

Install over the existing app without clearing data, on Pixel 10 and Samsung if
available. No new build is sent to the wider tester group.

- Celebration: check Close alignment, both headings, long title/description,
  previous/next, position and Let's go; remaining pending items must survive.
- Collection: inspect disabled and installed layouts against Services/Spoons;
  verify bicycle tile, description spacing, consistent action widths and muted
  destructive action. Cancel removal, confirm removal and re-enable; journeys and
  downloaded catalogue must remain available.
- Download: exercise first use, interrupted/resumed transfer, progress, explicit
  failure/Retry, completion and subsequent offline use. Do not delete the existing
  production cache solely to test this without preserving user data.
- Road detail: inspect A627(M), A12, A1(M), M20; confirm white track, blue/green fill,
  values, travelled/remaining/reference labels, complete fit and map space. Keep
  A1/A1(M) distinct. Check B/local recorded-only views and counted journeys.
- NCN Progress: combined/cycling/on-foot, overall and zero/nonzero route cards,
  search and all groups, VIEW MAP and section-specific eligible journeys. Return
  should retain activity, expanded groups, search and scroll.
- Jump between Statistics, Road discovery, Motorways, A-roads, Services, Spoons and
  NCN; selection must follow jumps/manual scrolling. First/last chips must be
  reachable without clipping. Manual scrolling must not cause vertical jumps.
- Maps: inspect UK, region, town and street zoom; orange remaining must be solid
  wherever verified geometry is continuous, purple completed distinct, overlaps
  neither translucent nor excessively thick. No artificial joins across gaps.
- Recheck cold/warm main map, filters/search/Reset/Retry, tabs, journey editing,
  existing collections/achievements, large-font/narrow screens, and realistic
  history performance. Record device/Android version and any timing/memory issue.

Live Firebase tester promotion and production rollback are **not exercised** by
this development release. Only the project owner can approve a specific artifact
for later beta promotion; see `android-development-distribution.md`.
