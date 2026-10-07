# Automatic service station collection — 0.25.165

Builds on 0.25.164, preserving landmarks, Sightseer, Groundhog Day, county alignment and capture fixes.

## Goals

First Stop; 25%, 50%, 75% and 100% of the 112-site catalogue; every eligible motorway; Watford Gap, Gloucester, Gretna Green and Pont Abraham; existing Norton Canes and Peterborough badges; Moto, Welcome Break, Roadchef and Extra completion; Tebay/Gloucester/Cairn Lodge; England/Scotland/Wales.

Ten Stops is removed. Each achievement has a visited/remaining checklist. Each named site counts once regardless of carriageway. Operator aliases and country metadata are shared with the catalogue generator.

## Evidence and unlocking

Manual ticks and journey confirmation prompts are removed. Confirmed Timeline place visits match the nearest mapped site point within 350 metres. Captured stops use original timed GPS samples, not drawn or matched routes: at least four distinct reliable samples across three minutes, sample gaps at most 90 seconds, accuracy at most 50 metres, speeds at most 2 m/s when supplied, within 125 metres of a site point and within an 80-metre stop cluster. Missing accuracy, untimed routes and old user-confirmed candidates are insufficient evidence.

Evidence is retained before entitlement. Unlock runs an asynchronous archive backfill, rematches saved Timeline evidence against every site point and recognises earned service achievements immediately, without a revisit. New captures/imports also update unlocked achievements. The existing prototype checkout remains simulated; this change does not introduce Play Billing.

One streaming archive record is projected at a time, skipping matched/map geometry. A generation and archive-revision check prevent a stale backfill from restoring deleted or replaced journeys. Deleted journeys lose their derived visit links; other supporting journeys or Timeline visits remain valid. Backup restore triggers a fresh evidence scan. Delete All preserves collection entitlement.

Earned milestones retain a cohort of supporting visited sites: adding stations or changing operators does not revoke an earned badge, while deleting the underlying evidence still recalculates it.

Tests cover accurate stops, pass-throughs, movement, missing/poor accuracy, gaps, duplicate/reversed times, both carriageways, pre-purchase capture, old archives, Timeline deduplication, stale manual confirmations, deletion, milestone boundaries and catalogue completeness.
