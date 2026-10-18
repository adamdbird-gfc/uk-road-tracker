# Android NCN integration trial

This opt-in development trial connects eight captured source inventories to
Android history assessment, progress and the shared route-detail view. It is
available under Utilities → Debug → NCN integration trial. It is disabled by
default, separate from purchases and the public collection catalogue, and is not
a full NCN release. There is no version bump or Firebase publication.

Activation assesses saved history on the device. Per-journey atomic checkpoints
contain a fingerprint, assessment contract version, pinned catalogue identity,
integrity hash and supported source intervals. They do not duplicate route/GPS
geometry. A restart reuses unchanged assessments. Corrections/mode changes replace
the old assessment; deleted journeys cannot reappear through cached records.
Deactivation hides the trial and stops new assessments while retaining reusable
checkpoints. Only a stable archive revision and unchanged activation epoch may
publish progress. New saves/deletions enqueue coalesced work; they do not wait for
NCN processing. Unsupported matches yield zero credit and a local reason, with
no history/import confirmation prompts.

The source dataset is bundled for this internal trial (about 336 KB compressed),
not downloaded from a production channel. Its packaged manifest pins decoded
SHA256. It contains NCN167, 270, 344, 426, 440, 476, 548 and 755: 206 source-way
associations, all retaining captured-inventory denominators. A failed checksum,
unknown/missing route, invalid binding or published-route readiness claim is
rejected. The compiler consumes the pinned completion plans and validates them
through the Python trial reader. It rejects alternative-branch inventories,
non-unit physical/logical bindings and overlaps between route inventories. Those
broader cases require a further adapter; they cannot silently enter this first
Android consumer. For this guarded subset logical and unique physical coverage
coincide, including the union across modes.

Walking requires the pedestrian matcher and validated stored source references,
or a unique exact full-source geometry identity for supported older records.
Matched travel must follow the identified source, with conservative numerical
clipping; road names and proximity do not establish membership. Cycling requires
the bicycle matcher/mode pair, validated geometry-bound evidence and a uniquely
ordered source-node chain. Quantisation tolerance applies only after graph-node
identity. Malformed evidence clears the whole journey's provisional result.
Road-only corrections that cannot be mapped safely remain held. This is current
snapshot association, not historical access/membership certification.

The Progress trial panel separates unique on-foot and cycling distances and
shows a combined union plus red/white route cards. Route cards open the shared
child map with orange reference and purple supported coverage. Both/on-foot/
cycling controls change the view, not saved evidence. Map taps show the supported
journeys and open the existing editor; Close/Back returns to Progress. Interval
unions are built on the worker before publication, and UI views retain the last
published snapshot while a new revision is assessed.

Twelve Android tests cover the pinned reader, source/geometry hashes, Python
walking/cycling/legacy golden results, ineligible/relabelled modes, changed
geometry/node records, corrections, unlisted source ways, duplicate/mode unions,
deletion, malformed partial evidence, opt-in navigation, checkpoint resume and
removed history. Fixtures contain synthetic matcher outputs over public source
geometry, not real journeys. Python reference validation runs during compilation.

Stacked on PR171 and PR166. The backend provenance work in PR168 remains necessary
for newly saved explicit walking/cycling source evidence; older supported walking
records can use the exact-geometry fallback. Missing historical evidence is not
invented, and may require a normal rematch in a later test release.

Remaining before collection beta: broader physical/logical alternative adapter,
remaining national scope reconciliation, production dataset download/update and
entitlement wiring, main-map collection selection/route badges and initial
achievements. This internal eight-route connection does not satisfy the full
map/progress/achievements beta gate and changes no Postgres rows or live data.
