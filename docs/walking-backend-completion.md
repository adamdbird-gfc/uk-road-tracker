# Walking backend completion — 24 October 2026

Production workspace selected by the owner: My Workspace. Service
`uk-road-tracker-api` (`srv-da82330u01pc73ccg6n0`), main, auto-deploy on commit.
Original live source: `aabb681810fe5d5cb047ecb9aa6774cdb8b22334`, deploy
`dep-dbbutv0u01pc73ae3pf0`.

Runtime request logs on 22–23 October confirm repeated 504s at 55.2–55.5 seconds.
They establish the application deadline being reached, not the exact upstream
cause. Logs lack journey IDs, input-point counts and per-stage router work.
No personal GPS or IP addresses are reproduced here.

This backend-only slice ports 214's sixteen-extra-subdivision cap to main.
Successful root chunks do not consume that retry budget. Native walking also
gets a request-local 48-second fallback budget, 10-second upstream-call bound
and 96-call ceiling. Existing outer 55-second safety limit is unchanged.
Verified siblings survive omitted timed-out/budget-limited sections. With no
verified geometry, return recoverable 503 with Retry-After rather than inventing
an off-path result. Old clients and driving/cycling retain their policies.
Coordinate-free work counters are returned and logged; health reports
`walking_work_version=2`. Context revision 3 adds public complex destination
site tags/names and refreshes only derived context tiles, not user history.

Local suite: 91 tests, five DB-dependent skips; database-backed CI 40127264927
and road-recovery CI 40127264932 passed. PR #217 merged as
7dd2c58c628ec819ace2b645d18aa296ddb238b4; Render deploy
dep-dbegppdg1s2s739dhv30 is live at that exact source. Live health confirms
walking_work_version=2 and database ready. Public Kent/Scottish walking references
matched 6/6 and 8/8 points with genuine geometry. Overseas synthetic fallback
matched 3/4 points with real geometry and one upstream call, retaining unmatched
evidence. Runtime work counters independently corroborate these checks.
Simulated deadline/network/partial/long-route regressions passed; no claim is
made that every future external-router request will succeed.
No Android release, Firebase distribution, catalogue publication or historic
journey reassessment belongs to this slice. Issue #216 tracks coordination.
