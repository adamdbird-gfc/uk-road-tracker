# Final delivery audit validation receipt

5 November 2026. Source base `42ae16d14be18211dc30182f83d0b9673b177d69`.

**412 local Python tests passed, zero failures/errors/skips.**

Local validation:

| Check | Result |
| --- | --- |
| Full existing NCN trial suite | 333 passed; trial-tests.log |
| New Chapel and existing Deal/source-compatibility/binding suites | 54 passed (4 new); focused-tests.log |
| Original bounded policy evidence | 12 passed; policy-tests.log |
| Original discovery register consistency using recovered existing queue | 13 passed; register-tests.log; no discovery rerun |
| Exact original database/gzip hash and size | Match successful revision-4 publication pins |
| SQLite quick_check | ok before/after |
| Independent all-row comparison | One paths row changed; every other row in all seven tables unchanged |
| Route-local/canonical union impact | +8.882678043133552 / +9.155569770460744 metres in the offline candidate |
| Fresh live manifest | Revision 4, 1,079 bytes, original SHA-256 unchanged |

The new candidate is not selected by production. Four Python tests verify exact
source/node/route/mode/prior-binding rejection, a finite connected degree-two
endpoint, the continuous correspondence bound, partial evidence and deduplication.
Existing tests preserve all twelve positive/negative policy outcomes; tests and
policies do not constitute publication or individual completion evidence.

A dedicated CI workflow restores only original GitHub artifact 13357563539,
re-exports exact current rows, creates a disposable candidate, independently audits
every row and runs existing Android NCN consumers plus two new Chapel-specific
consumer tests. Hosted status must be read from the PR; local Android execution
is unavailable because Gradle and the Android SDK are absent here. No phone
acceptance, full Android suite, APK packaging/distribution or backend deployment
is claimed. No candidate publication, historical replay or entitlement award.

Reproduction commands and named blockers are in DELIVERY.md. Before publication,
rollback is simply discarding the candidate/reverting source. Any later publication
requires a separate owner-authorised package, integrity checks and preserved
immutable revision-4 rollback assets.
