# Wetherspoons completion and future Last orders — 0.25.197

## Dated catalogue and targets

The full 12 October 2026 website-name reconciliation accounts for 814 current UK
members. Each URL resolves to exactly one reviewed stable identity: 813 active
mapped pubs and temporarily closed Art Picture House. NEC's generic operator URL
is the documented alternate for NEC Piazza, not another pub. The public finder
still reports 827 listings on 13 October; its nine overseas, three future and one
former-member entries are excluded explicitly. The catalogue is a dated reviewed
reference, not a real-time operating-status guarantee or a claim to have exported
the official mobile app list.

`collections/wetherspoons-coverage-review-v1.json` records the complete URL-to-ID
crosswalk, active/temporary states, source date/counts and county totals. All
active IDs match the asset once; their county code/name/nation matches the pinned
92-county HCT reference. There are 67 eligible historic counties and 813 active
pub targets. The remaining 25 counties have no active target and do not award or
appear as new completion cards. Temporary closures are excluded from active
completion until an actual reopening review. Future opening dates never activate
pubs automatically. The original 29 former exclusions are not Last orders credit.

Published revision 2026101302 has `reviewed_uk_completion_v1` approval, a verified
county-count certificate, `coverage_complete=true` and `completion_target=813`.
The previous partial approval contract remains supported; a partial release
cannot award new completion. Both publishers and the app check approval counts,
review dates, county denominators, integrity and monotonic revisions. CI verifies
the full membership crosswalk and county-reference match. Individual premises
qualification is still separate: the data release does not enable visits itself.

Sources: https://www.jdwetherspoon.com/pub-search/ and the individual evidence
URLs in the existing review ledgers. Current-member names/links were reconciled
in the saved 12 October name report and #107. App positions, names and historic
county attribution remain the reviewed OSM/supplement data; operator directory
coordinates, descriptions, photographs and complete addresses are not imported.

## Achievements

- **Pub crawl:** one supported visit in every eligible historic county. No time
  limit or drinks requirement. Repeats do not add progress. Counties with no
  qualifying pub are excluded. Supported retained visits to a subsequently
  closed pub still count in its historic county if that county remains eligible.
- **County completion:** every active pub in an individual eligible county.
  Cards are grouped behind a lazy County completion section. Registry entries
  cover all 92 stable county identities, so later pub openings can make another
  county eligible without bespoke code or a new county definition.
- **Completed it, mate: Wetherspoons:** every active pub in the reviewed UK target.
- **Last orders:** a supported, dated visit before a verified permanent closure
  occurring after the feature lifecycle baseline. Existing accepted visits can
  qualify when a pub closes later. Temporary closures, scheduled future closures,
  a change of operator while the business stays open, and pubs already closed
  before the baseline do not qualify. Unknown legacy visit dates cannot prove
  Last orders eligibility. There are no production closure rows at launch, so
  this badge correctly begins with no qualifying closures.

Earned county/national/Pub crawl completion retains its original supporting IDs
when targets grow or ordinary lifecycle changes occur. Removing an incorrect
supporting visit revokes retained credit; recognised/seen milestone history
still prevents duplicate celebrations. County attribution corrections are
re-evaluated. Access removal preserves the saved evidence and completion proofs;
deleting collection history clears them. Existing 21 pub and 48 Services rules
and identities are retained.

## Future closure publication

Keep the lifecycle baseline unchanged. After verifying an actual permanent
closure, remove the pub from active targets and add its stable ID, last-known
reviewed name/position/county, `known_active_from`, `permanently_closed_at`, dated
review and primary `evidence_url` to the catalogue's `lifecycle` array. Archive
rows never become live check-in pins. Neither a passed advertised date nor
missing directory membership is sufficient closure evidence. Update the dated
membership reconciliation and county target counts before publishing.

The app rejects duplicate/conflicting active/closed identities, future closure
or review dates, dates before the baseline and missing last-known positions.
Silent payloads cannot change the compiled lifecycle epoch. Existing dated
Timeline/native proofs are rechecked against archived premises when their
sources change; source removal/rejection still revokes credit. No retroactive
former-pub trading-date research is needed for this future-only rule.

## Combined release

Includes #119's Legend polish: hidden during initial/refresh loading, restored
when the card clears, white padded layer panel, narrower line references and
contrasting On/Off controls. One 0.25.197 (243) signed APK; no separate UI build.
Final Android test/lint/signature results are recorded in the PR/release report.
