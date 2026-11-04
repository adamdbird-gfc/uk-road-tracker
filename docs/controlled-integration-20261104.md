# Roadprints controlled integration — 4 November 2026

## Authoritative source and delivery states

All new development starts from latest validated main. The source integration baseline after #262, #263 and #254 is `fb061f83c06ff920229409949513f5eb00248d9c`; the subsequent record-only commit containing this document is its successor. The final main SHA is also recorded in the integration PR completion comment, avoiding a self-referential commit hash. Historical handover and research delivery statuses remain dated evidence.

| Increment | Exact head | Merge into main | State |
| --- | --- | --- | --- |
| #262 continuous on-foot capture | 78ef06f36e41e36226ff415f878aa99449d8d75a | efa9d397b621603c8d911a638b24e0ded3b49319 | Integrated; physical acceptance pending |
| #263 Android 0.25.223 (269) release preparation | 503ea252b62e970f59f01ca39437563df5632e5a | c592f59dc05bfe2a573bc715ff2755b4fa253c70 | Packaging source; final receipt below |
| #254 exact reviewed Deal NCN1 correspondence | 319b2b05e0b548e7db2040d4502077037c449774 | fb061f83c06ff920229409949513f5eb00248d9c | Offline candidate implementation integrated; not published |

#262 was clean/mergeable against unchanged main 3a89e6074eed420609116f20e2ad64fc123484dd. CI 41836944570 tested f883adbc0ae13ef5273126cc1a77bc1a9e1a737f: 848 passing runnable Android tests / 852 cases / 120 suites; zero failures/errors; four established opt-in NCN skips; five recovery checks. Final-head changes are only report/receipt files. No repeated root-cause or full-volume investigation was performed. The preserved JSON receipt remains authoritative historical test evidence.

#254 was clean/mergeable. Its 24 added paths do not overlap any of the 26 changed paths between its base 40f3c0d00e171800a157af4b3db5d4125134203b and release main c592f59d. NCN compilers, reference pins and Android NCN consumers are unchanged since its original validation. All 11 deliverable hashes in docs/ncn-viewer/resolution-validation.json were independently verified from the final git objects. The final head changes only its report, screenshot and validation JSON relative to tested d2c9d4cf01102f1fe1abf5b0b1e0791a7d042d36. GitHub confirms CI 41184546990 passed; job logs independently show 45 candidate Android tests with zero failures/errors/skips, exact candidate reproduction and all-row/mapping checks. Reuse the recorded 413 Python and 114 offline browser checks; no fresh national geometry assessment was needed.

## Android release and outstanding acceptance

Release 0.25.223 (269), source c592f59dc05bfe2a573bc715ff2755b4fa253c70, packaging run 41848943967. Independent downloaded report verification: 119 suites / 851 cases / 847 passed / four established candidate skips / zero failures or errors; five recovery checks passed. ZIP SHA-256 e817618904332821d7ea9f5bc58394d3459a41e5339613f190dbd1438163a35c matches GitHub artifact 13331734649; ZIP CRCs and every XML total verified. The earlier 1,605-archive volume test is reused from #262.

Signed APK verified: **Roadprints-0.25.223-269.apk**, 36,624,253 bytes; SHA-256 `f49eb51a1ebeb2a683b0b0b343d64e3d443c957d789089595090253bd5e40c2e`. Packaging run 41848943967 completed successfully. GitHub artifact 13333530491 (roadprints-capture-prototype-debug), ZIP SHA-256 bdd518462010c44d16c83acfe9b250aaa43e22f12e153c03225dbd1d7e32d1a1; retained in Actions until 11 November 2026. Downloaded ZIP/CRC, extracted APK digest/size, application/version/certificate receipt and APK ZIP integrity all independently verified. CI apksigner verifies the signature against the established certificate; this task does not claim physical installation. Owner download saved as [Roadprints-0.25.223-269.apk](https://chatgpt.com/library?file_id=libfile_28c934eeddc48191acc87949e6292913&account_id=personal). No Firebase distribution.

Application ID remains com.roadprints.capture; the established certificate SHA-256 is 5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031. APK source is c592f59d; later #254 adds offline tools, documents, viewer and an opt-in Android test, with no application runtime/reference asset changes. Final main's packaged runtime therefore matches this release. The compiled source remains traceable to its exact packaging SHA rather than being relabelled as a later documentation commit.

Physical Pixel 10 / Android 17 acceptance remains outstanding: install the signed update over 268 while capture is idle; confirm version/archive count/representative historical journeys, manual corrections and achievements; continuous walk/run with brief STILL noise; sustained genuine arrival; walk/train/walk station handoff; driving and traffic stops. Do not uninstall or clear data. Preserve diagnostics if any scenario fails; use a higher-code forward fix rather than a destructive downgrade.

Known limits: no automatic historical reconstruction/join (#253); map context must be enabled/available for supported automatic driving evidence (#255); device driving recall, latency and mode-edit stall remain unverified. Achievement memory reduction is not a universal heap bound; large-history CPU/attribution and physical acceptance remain (#256). GPS-gap and excess-matching protections remain; prior precise crash is not reproduced. Installed 268 is owner-reported, not queried from a connected phone. Backend/live Firebase versions are unknown; no backend, Firebase, reference publication, journey mutation or tester notification was performed. Existing issues stay open pending their actual acceptance.

## Settled NCN findings and integrated corrections

- #239 attainability tools and #242 ordinary seam implementation/compact reproduction are integrated. The seam candidate is assessed, not published: 45,834.46183164045 m added unique-network capacity across 217 routes, all existing bindings/denominators preserved; 218 measurable routes and none fully attainable.
- #243–#247 provenance, admitted-span and metric diagnostic research is integrated. Native projection/predicate differences explain threshold flips; no general operational reprojection or tolerance relaxation is justified. Private 77611523, masks, branches and unsupported endpoints remain excluded. Older zero-recovery Deal findings are superseded only for the exact five reviewed edges by #254's independent identity/access evidence.
- #254 now admits cycleway 4471747 edges 6–10 by pinned reviewed correspondence. Six finite bindings change one row out of 1,376,616, adding 561.593211566739 m route-local and 561.888736534494 m unique-network capacity beyond the seam candidate. The 495.388143301922 m focus target becomes fully assessable. The separate 3.243944230930 m adjoining endpoint remains uncredited.
- Seam + Deal candidate arithmetic: 16,146,369.976629720 m assessable / 17,435,132.494005967 m published target; 1,288,762.517376248 m unbound; 92.608243626372%. Total improvement over the pre-seam capacity is 46,396.35056817494 m. These are candidate capacity figures, not deployed catalogue measurements or personal completion awards. No route/network 100% claim.
- #254 carries forward exact #249 offline viewer with the Deal decision. #249 remains open, substantially incorporated through #254; do not blindly merge it again. #248 additive diagnostic research, #251 hosted-access record, #238 register and #240/#241 historical scoping remain preserved/unmerged; none is required for this correction. The hosted viewer has not been updated. No PR/branch was deleted or unrelated issue closed.

## Remaining work ranked by readiness and impact

| Rank | Task | Impact and readiness |
| --- | --- | --- |
| 1 | Publication-readiness review of combined ordinary-seam + Deal candidate | 46.396 km already demonstrated against pre-seam capacity; implementation/consumer evidence ready. Verify live pins, current consumer transition and rollback before a separate publication decision. |
| 2 | One bounded known-way admitted-span correction beyond Deal | Historical 101.320084 km unique-network review envelope / 175 routes, not guaranteed recovery. Select one immutable target/source case; membership/access/finite-span proof required. |
| 3 | Missing admitted source correspondence | Historical 618.865767 km / 202-route review envelope; largest potential reach, lower readiness due identity/source gaps. No quantified new correction ready. |
| 4 | Direction/alignment and residual branch/endpoint ambiguity | Historical direction envelope 426.410665 km / 217 routes, not recoverable mileage. Topology-specific evidence required; do not relax 15-degree or private-path controls. |
| 5 | Remaining Deal adjoining endpoint | 3.243944 m; separate junction correspondence research needed, low impact. Do not use it to delay the resolved five-edge correction. |

Historical review envelopes overlap and cannot be added or treated as current residual partitions. Deferred masks remain intentional exclusions. Mileposts/biographies and theme work remain separate from core completion.

## Next precise task — combined candidate publication readiness

Start from latest validated main containing this record. Perform a bounded, read-only readiness review; do not begin another projection/source-admission investigation or publish data in that task.

1. Verify actual active NCN manifest/version/hash against the retained shipped DB SHA-256 973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093 and retained checkpoint/input pins. If production has changed, document the exact mismatch before proposing a rebuild; do not overwrite it.
2. Restore retained immutable inputs/cache, reproduce scripts.reproduce_ncn_ordinary_seams then scripts.ncn_deal_correspondence into fresh outputs. Expected canonical content: seam cf25e006e79782c9458895bf4b68179b7829fe3b6e7d5ded96be0ccd07929dc0; combined d3486a38fc33a78dd9314720a4677906fa7315e28c15feedb1577a1847caac2d. SQLite file layout may vary; canonical content and independent row audits are authoritative. Reuse unchanged audit evidence rather than repeat broad assessment.
3. Check current consumer version selection/cache invalidation/achievement re-evaluation with preserved original evidence. Exercise existing national mapping/seam/Deal consumer gates only where final candidate/pins or changed consumers require them. Confirm unchanged denominators, modes, masks, unsupported/private negatives, no duplicate source ownership and preserved manual corrections/unlocks.
4. Prepare a monotonic candidate manifest/pin transition, measured compressed size/bandwidth, exact artifact checksums, retained previous manifest/asset and tested rollback/forward-recovery plan. Separate catalogue activation from APK delivery; establish whether an APK pin update is actually necessary.
5. Deliver a ready/blocked receipt and a concrete publication plan. Stop before publication or historical reassessment. No new national dataset, matcher redesign, tolerance relaxation or production writes.
