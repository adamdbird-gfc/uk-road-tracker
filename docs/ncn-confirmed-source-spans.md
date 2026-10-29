# Current source-span catalogues — 16 October 2026

The 48 strongest bidirectional correspondence candidates have now been assessed
against full current OSM identity snapshots. All 53 required root/child relation
requests completed successfully, with consistent overlapping source elements.

47 numbered keys produced bounded, portable catalogues covering 3,778 distinct
source ways. Each association carries full current geometry, aligned OSM node IDs,
source versions and membership evidence, permitted trial modes, an exact-source
geometry contract and a versioned reference checksum. Combined association files
compress to 765,195 bytes; the accompanying references are separate. They are local
trial outputs, not a Firebase release or the complete national progress denominator.

469 ways remain held:

| Reason | Ways |
| --- | ---: |
| Not wholly within the matching published open Main Route corridor | 384 |
| Closed/roundabout or declared branch needing logical policy | 39 |
| Also overlaps a nonqualifying official extent | 34 |
| Mode policy unresolved or restricted | 12 |

NCN228 produces no catalogue under these rules; all 35 source ways remain held.
No whole-route completion is enabled by filling gaps with a shortened denominator.
The scope suffix is `eligible-source-spans`, and both whole-route readiness and app
publication readiness remain false. Equivalent-path grouping remains explicit:
these ungrouped source spans do not automatically implement alternatives or replace
the reviewed Route177 overlay.

Admission requires explicit current relation membership matching network/ref and
the previously assessed member set. Unlisted nearby ways cannot enter. Current
node geometry must agree with the assessed provider representation within 0.03m
numeric tolerance. The complete source way must lie in the matching official open
Main Route's 10m correspondence corridor and must not touch a nonqualifying extent.
The corridor is an association constraint after semantic identity checks, never a
saved-journey matcher or a source of additional route membership. Missing current
geometry, changed membership/classification, provider disagreements, loops and
unreviewed declared alternatives remain held.

Mode policy applies present static mode overrides and recognised highway defaults.
Missing pedestrian permission on a standalone cycleway is not assumed. Footways
remain walking-only unless cycling is explicit; dismount tags do not qualify for
cycling. Conditional/directional restrictions are held for the affected mode, and
motorways/proposed/construction ways are excluded. These observations are not a
certification of historical legal access. One-way traffic direction does not
invalidate independently supported physical passage.

The generated reference uses exact selected source geometry, not a projection of
user travel onto a nearby official line. Saved walking still needs its source-way
contract; cycling still needs its bicycle graph node-chain contract. Driving,
corrections, unsupported matchers and missing identity retain the existing rules.
The associations round-trip through the existing portable bundle validator before
being written. Empty results do not invent fallback paths.

Validation: all 233 local checks pass (228 NCN +5 shared collector). Nine new tests
cover source/node integrity, unlisted paths, changed source classification and
membership, provider drift/missing nodes, loop/alternative holds, official closure/
ferry/category exclusions, mode overrides/conditions, partial saved walking/cycling
credit and a pinned real NCN476 snapshot producing nine identity-backed paths.
Matcher-output tests remain synthetic and do not claim live journeys.

Evidence is retained with the existing research archive under confirmed-sources/
and confirmed-catalogues/. Source code and the compact public NCN476 fixture remain
in Git. No Postgres writes, production deployment, APK or personal journeys were
involved. Next: review held spans/branches, establish complete logical denominators
and equivalent-path groups, then integrate the versioned catalogue into Android.
