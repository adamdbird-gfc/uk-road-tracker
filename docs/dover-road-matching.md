# Native road matches: reject unsupported intervals without inventing coverage

The Dover terminal report reproduced a 21,575.2 m routed result from 142 submitted
observations spanning 8,733.6 m. Android correctly rejected it against its unchanged
12,227.0 m integrity limit. The actual route overview contained the excess; chunk
distance deduplication was not the cause. Conflicting imprecise provider fixes
among slow/stationary observations caused long circuits through mapped access
roads. Actual checkpoint circulation must still be retained.

The road-path functions were unchanged by the 28 October pedestrian releases.
The supplied saved record contains no prior processing result, and the original
capture was classified as cycling before its reported Bus mode. Neither an earlier
successful match nor the exact state transition reopening it can be established
from that snapshot. Backend matching is transient; historical selection and saved
geometry live on Android. Completed stored matches are skipped by its ordinary
batch policy. No archive refresh, rule invalidation or historical rematch is
introduced here.

For native `road_recovery` requests to `/match`, `road_distance_evidence.py` applies
the existing captured-drive allowance (`max(observed * 1.4, observed + 300 m)`) to
each attributed router leg. Observed distance includes **all intervening submitted
fixes**, including observations the router tidied away, so loops are not assessed
using endpoint separation alone. Unsupported intervals become explicit failures
with their original input bounds and distance rationale. Consecutive retained legs
are assembled from contiguous real step geometry. No connector or shorter route
is substituted. Unsupported steps are excluded from every road-coverage collection
as well as the overview and distance. Input observations remain intact.

Incomplete attribution or disconnected step geometry is left to existing failure
handling; it is never guessed into a corrected route. Fully unsupported requests
still fail rather than return invented coverage. Partial matches must continue to
pass Android's existing full point-accounting and stricter 1.2 / 300 m distance
checks. The change adds no router calls, radius expansion or timeout increase.

The invocation is isolated behind `road_recovery`; `/match-walking`, pedestrian
selection 7/search 2, cycling and legacy road requests retain their policies.
Health exposes road distance version 3; native results expose evidence version 1.

Public tests cover retained observed circulation, explicit disconnected gaps,
malformed attribution, repeatability, coverage exclusion and mode isolation.
Private exact exports/results must remain outside this public repository. Reproduce
existing version-2 Android preparation and check the actual client acceptance with:

```
python scripts/replay_road_diagnostic.py /private/export.json \
  --response /private/corrected-response.json --output-prefix /private/replay
```

The optional `--live` mode uses only an explicitly supplied normal backend URL.
It sends the prepared match request, never the full journey archive, and checks
repeat equality. It does not update the user's device or schedule historical work.
Post-deployment exact Dover and walking fixture acceptance must be recorded before
reporting corrective delivery as verified. A partial match must be described as
partial, with uncertain intervals uncredited.

## Verified deployment — 28 October 2026

PR #229 is deployed as `a0265fe4b2e75187ad1e8b0b39f88180b1c2e211`, Render
`dep-dbh4b8ks728c73c0rk8g`, live at 18:58:39 UTC. Both PR workflows passed;
database-backed run `40749744470` passed all 126 tests without skips.

Two production replays reconstructed from the supplied saved preparation return
identical JSON: 142 input points, 121 matched / 21 unmatched, 11 unsupported
intervals, 6,399.0 m of deduplicated geometry. Both pass the existing Android 220
captured-partial contract. This is a **partial** recovery, not a claim that the
entire port network has a verified match. Twelve of the sixteen original chunks
are preserved exactly. The raw 557 samples and saved 270-point route are untouched.

Post-deployment walking replays preserve exact accepted geometry, distance and
source evidence: Monday/original Charing Cross 1,860.0 m, 12.266 s reference work;
28 October workload fixture 2,103.9 m, 14.033 s. Both are HTTP 200 with zero router
or limited calls, selection 7/search 2, within the existing 20/48-second budgets.
Offline replay also passes all five original visual-defect-region gates.

No APK is required. Retry this failed journey in the existing Android app; the
backend replay does not modify the on-device saved record. Original geometry and
original distance remain preserved by the existing captured-partial save path.
Once saved complete, ordinary batches skip the stored match. Exact provenance of
the earlier reopening and any claimed earlier successful match remains unavailable:
the supplied snapshot contains neither a prior processing result nor transport
change history. No speculative archive repair is performed.
