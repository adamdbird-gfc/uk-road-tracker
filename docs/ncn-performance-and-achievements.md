# Private development 0.25.210 (256)

Based on installed private 209 source a6335f7e and its handover descendant
9713b001. The preceding NCN baseline was 207 source 02ddf96a; the pre-NCN
corrective build was 206 source 452258f6. Include the released NCN adapter and
209 UI; defer unrelated open PRs, catalogue republication and matching changes.

## Responsiveness evidence

The NCN integration introduced `static synchronized install` and
`static synchronized refreshIfDue` on NcnCatalogueFiles. The first holds the
class monitor across HTTP reads (45-second read timeout, ten-minute transfer
budget), decompression, checksums, SQLite validation and filesystem commits.
The second is called on the UI thread by Progress's one-second ticker through
NcnProgressStore.ensure. Installation can therefore block UI polling for the
entire outstanding operation. This monitor dependency is established from the
exact release code, not an observed stack from the owner's phone.

Installation now owns a separate monitor; short UI scheduling never takes it.
Two regressions hold the installer monitor and verify refresh scheduling and the
actual warm ensure path finish without waiting for its release. First Android
check run passed compilation/lint but one new fixture failed because it needed
gzip decompression; corrected before the passing full stabilisation run
39393997991 (0de93208). No failing test was excluded.

NCN route/network totals are summed once when the eligible interval index is
prepared on the worker. UI reads no longer scan the entire index once per route.
The benchmark compares the former scan to cached lookup over the identical
trial ledger, eight routes, three modes and 1,000 repeated screen updates.
Numbers are JVM workload measurements, not Pixel frame rates or archive timings.

NCN workers have background scheduling priority. Overlay work retains only the
latest queued request; generation checks cancel obsolete publication and target
preparation. Epoch changes stop historical work, including disable/re-enable and
catalogue replacement. The existing main-map worker, deadline/recovery, retained
camera/scroll and geometry budgets remain. The footer separately reports selected
NCN preparation/failure instead of declaring that overlay ready prematurely.

An optional atomic, checksum-verified derived checkpoint is keyed by exact archive
revision, assessment algorithm and catalogue SHA. On an unchanged process restart
it restores compact assessment spans/metadata and rebuilds interval unions on the
worker without reading journey archives or rematching. Corrupt/stale checkpoints
fall back to the original resumable per-journey assessment cache. Changed history
still streams the archive to validate fingerprints; only changed assessments are
recomputed. This is a documented remaining I/O cost. Optional checkpoint payloads
over 32 MB are skipped; this never discards journey evidence or in-memory coverage.
No database/raw journey migration or catalogue publication is included.

Local diagnostics retain at most 16,000 characters: map total/render timings,
NCN assessment/restore, overlay and achievement timings; slow frame counts/heap
use; and one main-thread stack after a foreground heartbeat stalls for two seconds.
They are included in the existing Debug report. No journey IDs, locations or route
geometry are added to these diagnostics. A stall sample is diagnostic evidence,
not a declaration that Android issued an ANR. Background/suspended screens are
excluded; timings and records are written on a diagnostics worker.

## Achievement rules and persistence

Awards consume only the completed NCN eligible ledger; no second history scan,
SQLite query or matching pass is added. First discovery needs positive unique
physical coverage. Each measurable route identity has one combined growing
25/50/75/100 family; 100% is its full supported-open-route award, including all
verified disconnected open sections. No extra duplicate 100% badge. The network
has one combined 25/50/75/100 family from canonical physical target union, never
the sum of route distances. Driving/unknown modes cannot contribute; the existing
validated walking/running and cycling matcher rules apply equally to history and
new saves. National, Regional and future verified Named keys remain distinct.
NCN228's held alternative inventory and all 34 source holds receive no awards.

Stable IDs: ncn-first, ncn-network, ncn-route:<exact route identity>. Levels and
recognition dates live in one atomic preferences value. Rows publish only with a
stable archive revision/activation epoch. Existing corrections/deletions recalculate
current levels while retaining prior milestone dates; reinstated coverage does not
change original recognition dates. Existing celebration seen-level persistence is
reused, with one pending event at the highest current family level. System dialog
cancellation leaves it pending; acknowledgement never acknowledges a future level.
Bulk history therefore uses one carousel, not a separate modal per threshold.

Catalogue replacement invalidates the derived checkpoint and per-journey cache
namespace. Recalculate against the new verified-open denominator and eligibility;
stable identity/recognition history survives. Removed/held identities disappear
from active awards while their historical dates remain. A route identity change
must be explicitly reviewed rather than inferred from its name. The network award
always describes the currently supported verified-open catalogue, not every NCN
route ever published. No milestone rounds a displayed 99.999% up to completion;
only numerical arithmetic noise is absorbed (at most 1e-12 of reference length).

The Achievements screen reuses its existing cards, collapsed route-family list,
jump menu and celebration modal. NCN state invalidates the existing achievement
snapshot only when the published assessment revision changes.

## Millennium evidence hold

Preserve the fixed twelve Kent candidates: MP241, MP385, MP437, MP438, MP440,
MP441, MP310, MP315, MP318, MP626, MP633 and MP833. Issue #193 states their current
coordinates/existence and disc associations remain unverified. No candidate is
activated for discovery; no coordinates, poem text, artwork or disc/line mapping
are invented. Only walking/running and cycling may qualify when evidence clears.
Driving/unknown remain excluded. No tribute fallback or original-puzzle claim is
implemented. Geographic recognition, theme/design sets and the fourteen-line
reveal remain explicitly deferred until the agreed evidence/licensing gates pass.

## Owner acceptance and limits

Automated success does not prove Pixel/Samsung performance. No phone/emulator or
original ANR trace is attached. Real cold/warm archive timings, rendering/GC under
the owner's full history, and Android's ANR response remain unverified. Before/after
phone numbers are not fabricated. Compare 209 with this private build via Debug
reports; if stalls recur the new foreground stack identifies the actual blocked
method. Keep #201/#194 open until device acceptance.

Install over the existing app without clearing data. Test cold/warm starts, Map
and retained tabs, filters/Reset/Retry, continuous journey strokes, motorway/A-road
and NCN focused maps, search/mode/group state, offline catalogue/restart, first and
historical milestones, duplicate-safe celebrations and corrected/deleted journeys.
Capture Debug diagnostics after any stall. Wider Firebase promotion is unapproved;
packaging must remain signed-artifact-only with the established application key.
