# Consolidated NCN completion checkpoint — 19 October 2026

All **219 supported route identities** now have a versioned completion record.
The earlier 194 catalogues have been adapted alongside the latest 25. There
are **218 captured-open-main plans** and one separately preserved NCN228
numbered-alternative inventory. These describe reviewed data capacity, not user
travel, full promoted itineraries or Android activation.

No source geometry or route membership is admitted by this change. The physical
registry remains **62,399 distinct ways**, with 62,487 route-way associations.
All 34 held identities stay listed: the latest 29 and five earlier holds.
The original 253-key inventory is therefore still fully accounted for.

## Earlier formats and explicit scopes

| Input contract | Records | Treatment |
| --- | ---: | --- |
| Earlier exact-source catalogues | 175 | Validate references, bundles and original inventory receipts; use their pinned national open-main target. |
| Reviewed membership inventories | 6 | Read through the existing overlay consumer against the exact predecessor; retain only matching physical sections in the new reference. |
| Reviewed open-span catalogues | 37 | Read through the existing strict catalogue consumer with their own pinned open extent. |
| NCN228 numbered alternative | 1 | Preserve its original decoded plan and association bytes and its source-inventory scope. |

The 182 existing source-inventory contracts remain unchanged in the replay
inputs. Unsupported source mileage is not removed from those inventories to
construct a new percentage. New open-main targets instead use the complete
union of their corresponding explicitly Open Main features. All unbound open
target geometry remains in the denominator. Regional prefixes remain regional;
N/A features are not promoted. In particular RCN90 uses only its two explicitly
Open Main features, retaining the 41 N/A features in the pinned snapshot.

The consolidated reader, `read_checkpoint_route`, dispatches these two output
scopes explicitly. An inventory plan cannot be relabelled as captured-open-main
or silently become another numbered route. A separately pinned manifest and
plan/association hashes are required. The plan reference and route-folder
identities must also agree.

## Reviewed alternatives remain separate

An existing either-arc source-inventory decision does not by itself define a
publisher-edge correspondence. Applying ordinary straight-edge bindings to that
ring could otherwise turn a valid either-arc decision into a requirement to
travel both arcs, or credit an unreviewed shortcut.

The consolidated compiler explicitly defers those accepted ring ways and nearby
publisher target edges until their open-target alternative mapping is reviewed.
There are **176 route-way occurrences across 49 route identities** with deferred
alternative correspondence. These counts overlap physical identities and are
not distances. Their original inventory contracts and source associations are
preserved. This is a conservative open-target hold, not a route closure or a
reversal of the earlier inventory either-arc decision. Other source sections
continue to bind under the existing unique monotone correspondence policy.

This also applies to the latest 25 catalogues in the consolidated checkpoint.
Their earlier standalone plans are not overwritten. The consolidated plans have
new versions, pinned adapter inputs and explicit deferral records; earlier trial
checkpoints require reconciliation rather than silent transfer.

The separately reviewed Route177 alternative pilot is not replaced by this
checkpoint. NCN228 remains its own numbered alternative; it does not receive or
grant another route's completion credit.

## Physical identity and Android handoff

The existing Android trial is draft
[PR172](https://github.com/adamdbird-gfc/uk-road-tracker/pull/172), branch
`feat/ncn-android-integration`, reviewed at commit
`c0485d0d7f86dbeb7897d583cede582a62fc49a3`. This pass inspected its compiler,
catalogue, matcher, ledger and integration documentation without modifying them.

It has eight fixed keys, assumes physical/logical distance equivalence, rejects
overlapping or alternative inventories in its compiler and limits decoded input
to 12 MB. Its existing safeguards should not be relaxed to force in this batch.
It remains an opt-in development trial, with no wider catalogue activation.

The broader registry includes **88 shared physical ways**. Route-specific local
projections can also give the identical source way different metre offsets.
For example, way 23384774 is 645.886 m in NCN10's metric projection and 669.266 m
in NCN6's. Its source nodes and coordinates are identical. These are projection
differences, not two travelled paths; adding route totals would overcount.

The next Android adapter needs:

- One canonical physical edge/distance basis, indexed by verified source identity,
  with piecewise conversion to each route's logical offsets. Deduplicate shared
  physical travel once while preserving each route's permitted modes and bindings.
- Separate admitted physical source intervals from logical completion intervals.
  Paint only actual supported source travel, including source spans whose logical
  correspondence is deferred; never paint the offset publisher line as travel.
- Preserve zero-contribution target gaps and the distinction between captured
  open targets, source inventories and reviewed alternatives.
- Index source hashes and node-chain candidates instead of scanning every path
  per matched leg. Use bounded catalogue loading and background processing.
- Version the portable schema and accounting policy, reconcile per-journey
  checkpoints and keep corrections/deletion/resume behaviour. Retain the rule
  against bulk-import review prompts and invented historical evidence.
- Add cross-language golden fixtures for shared ways, projection conversion,
  non-unit bindings, unbound targets and either-arc policies before a device trial.

These are source-reviewed integration findings, not Android performance
measurements. No Android asset, Java file, entitlement, Firebase release or APK
is changed by this checkpoint.

## Validation and replay

322 NCN tests and five shared cycling-evidence tests pass (327 total). New
regressions cover changed input pins, path escape, exact identity/open-main
filtering, fixed prior inventory receipts, the 219/34 partition, deferred
alternative credit and portable scope dispatch. Existing saved-path activation,
correction, deletion and restore tests remain in the suite.

The compiler validates each new plan through the corresponding portable reader
and compares its admitted path rows with the exact input bundle. The final
checkpoint readback also verifies every manifest and every pinned input file.
NCN228's decoded plan and bundle remain identical to its reviewed predecessor.
Full validation receipts, route metrics, deferrals and the source-reviewed
Android handoff are included in the evidence bundle.

The independently pinned input manifest SHA256 is
`a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641`.
The 658-file output summary SHA256 is
`e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b`.
The earlier national snapshot remains pinned at
`331023ba1d8ca175471700b1bdb5d7828984d535792c0c54e7a4b465d48cf3f2`;
later reviewed catalogues retain their own separately pinned extents. This is
offline preparation from those observation dates, not a fresh route-status check.

From the repository root, with the evidence bundle beside the checkout:

```sh
python -m scripts.ncn_trial.completion_checkpoint \
  --inputs ../ncn-completion-checkpoint-2026-10-19/inputs \
  --expected-pins-sha256 a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641 \
  --output ../ncn-completion-checkpoint-replay
```

Use a fresh output directory. All 219 records must succeed before the batch
summary is written. The evidence contains every consumed input required for
this completion stage. Original source-admission research remains in its
preceding evidence bundles. Data attribution remains OpenStreetMap contributors
(ODbL 1.0) and Walk Wheel Cycle Trust official scope (OGL 3.0).
