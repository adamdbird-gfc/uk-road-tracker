# Regression test receipt — 5 November 2026

**134 local tests passed, zero failures/errors/skips.** Python 3.12, Shapely 2.2.0,
pyproj 3.8.0. No APK, device, full Android suite or production acceptance claimed.
Exact local output is preserved in [receipts](receipts/).

| Suite | Passed | Evidence |
| --- | ---: | --- |
| New bounded geometry/finite-admission/shared-accounting regressions | 12 | bounded-evidence.log |
| Existing Deal, compatibility, open completion, binding and seam controls | 72 | existing-controls.log |
| Coverage, inventory completion and extent composition | 37 | accounting.log |
| Original discovery register/source/viewer data consistency | 13 | discovery.log |

The focused CI workflow runs the first 121 tests without catalogue, archive,
Firebase, Android or deployment access. CI result and final head are recorded on
[PR #274](https://github.com/adamdbird-gfc/uk-road-tracker/pull/274); this file makes
no claim of a remote result before it completes. The thirteen original discovery
tests passed locally after restoring the existing register; CI intentionally does
not download that archive or repeat national discovery.

## Controls and interpretation

- Newry NCN9 and Mid-Wales NCN8 retain exact compatible source identity and bounded
  membership in the existing compatibility tests.
- The existing Deal compiler preserves its reviewed five-edge correction, exact
  geometry/node/mode/previous-binding pins and finite non-overlapping source ownership.
  Wrong route, changed source, private different identity and overlap controls pass.
- Wolverhampton retains hairpin ambiguity; London finite 1:2 admission cannot extend
  by whole-way ID; Cumbria NCN7 does not alias NCN6.
- East Sussex documented shared-node movement remains compatible; the new nearest
  perpendicular target check fails correspondence despite only about 1.2 m separation.
- Chapel Lane's current last-edge correspondence remains rejected, and the test
  demonstrates why a whole-residual projection needs an explicit junction anchor.
  The connected source neighbour is retained as a lead, not falsely called absent.
- Bradford's exact original origin is restored, both candidate pairs still fail the
  actual 15° gate, and both incident targets expose a degree-three junction.
- Derry/Highland source occurrence and finite section ranges are checked against
  exact retained geometry; full source context cannot silently extend admission.
- Existing repeated/reversed travel, mixed modes, edits/deletion and alternative
  accounting controls pass. A new synthetic two-route canonical union test counts
  a shared 10 m interval once despite both route totals and overlapping alternatives.
  This accounting test is not evidence for Cumbria's unproved membership.
- The national 10 m / 15° policy remains unchanged; no endpoint extrapolation or
  source-membership grant is introduced. No existing runtime file changes.

The old compatibility/discovery tests include private-access rejection as a legacy
*diagnostic* classification. They are preserved as historical behaviour, not endorsed
as a journey rejection or final equivalence decision. The owner register explicitly
supersedes that rationale. No private-journey runtime change or acceptance is claimed.

## Reproducibility and limits

All fifty selected candidate edges match exact original source occurrences and
hashes. Every recovered target key hashes its full-precision original metric
coordinates. Independent geodesic checks agree with local source lengths within
0.1 mm for these bounded lines. Sampled offsets remain labelled samples, not a
continuous national maximum bound.

Repeated compact export and findings generation compare byte-for-byte. Consumed
input pins and original plan/source checksums are in evidence.json; file hashes
are in receipts/input-and-output-hashes.json. Original source versions remain
unknown where absent. Current revision 4 canonical mappings and complete binding
coverage were not restored, so no speculative canonical delta is reported.

Journey retention remains a governing policy and existing runtime is untouched;
these tests do not assert a newly implemented private-access journey path or
physical phone acceptance. Reference publication and historical entitlements remain
unchanged. See the impact report for unchanged capacity versus unknown potential.
