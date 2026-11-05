# Stage 3 — inactive durable discovery evidence ledger

Date: 5 November 2026. Tracking: #284. Related deferred work: #283.
Starting main: `1ac9938807a53f9bd55ccc302708c52b91baaaca`, verified against
GitHub main and merged #282. Stage 1 #280 and Stage 2 #281 are incorporated.
Status: implemented; validation in progress; unmerged. No activation is included.

## Architecture and reuse

`MilepostDiscoveryLedger` is an additive, Android-owned SQLite derived evidence
store. It reuses `MilepostJourneyEvidence.nativeArchive`, `MilepostProximity` and
the existing capture quality/movement validators. It follows the NCN worker,
source-token, cancellation and atomic-checkpoint pattern. SQLite is used instead
of growing the NCN JSON snapshot or general achievement preferences: indexed
proof fallback and durable per-journey/per-marker paging need no retained
archive-wide map. It introduces no replacement achievement engine.

Existing `JourneyStore` archive IDs/revisions, matching, corrected route evidence,
road/NCN completion, `AchievementStore`, UI and celebration stores are untouched.
The achievements archive reader omits original capture observations; its sampled
route geometry must not become a milepost validator. Existing achievement OOM
protections remain intact. The ledger has no lifecycle caller, registration,
background scheduler, default production database name or entitlement consumer.
Even construction is explicit, and fails on the main looper. No production
migration runs simply by installing this source.

The only Stage 2 contract addition is an optional complete source end date.
The native adapter exposes its existing `ended_at`; missing end dates remain
usable by the pure proximity evaluator but pending in the ledger. Both ends of
the source interval must lie within the supported marker location interval.
Journey-start dates retain `precision=journey`; no exact passing time is invented.

## Data model (schema 1)

| Table | Meaning and invariants |
| --- | --- |
| `runtime` | Evaluator revision; a changed validator/policy invalidates derived assessments and queues living sources on explicit open |
| `catalogue` | One installed monotonic revision, external SHA-256 pin and deterministic normalized row fingerprint; conflicting same-revision content is rejected atomically |
| `identity_registry` | Canonical physical identities and permanent retirement tombstones; retired IDs need a separately reviewed migration to restore |
| `marker` | Current canonical point, eligibility, supported half-open presence interval; only one dated location per physical ID in this slice |
| `alias` | Immutable source alias to canonical physical identity; alias reassignment is refused, including after removal |
| `journey` | User, original journey ID, monotonic source version, immutable evidence token, deletion tombstone, marker cursor and typed assessment state |
| `assessment` | Unique `(user, journey, physical marker)` result, reason, token/source version, catalogue/evaluator/validator, accepted mode/source, journey date/precision, recognition time, distance/method, closest/post coordinates, section and source-index range |
| `recognition` | Unique `(user, physical marker)`, stable ordinal, first recognition, original proof reference/date/catalogue and invalidation reason; survives withdrawal and does not grant an award |

`discoveries` is an indexed query of current qualifying assessments, not a
second mutable achievement table. For each physical marker it selects earliest
supported journey-start instant, then journey ID. The evaluator supplies one
winning section/range per marker/journey. Overlapping journeys and duplicate
imports with different IDs may retain independent compact proofs but yield only
one discovery. Removing the selected proof falls back to the next. Recognition
ordinal and first recognition never move when earlier history arrives.

Aliases resolve before evaluation: one `marker` row is one reviewed physical
identity. Two nearby physical posts remain separate rows. No coordinate, MP
reference, route membership or geographic identity inference is performed.
Current candidate inventory remains **889 candidates, zero physical/eligible
markers**. Stage 1 candidate adapter stays ineligible. `install` accepts only a
trusted, reviewed normalized snapshot; it is not a byte reader, downloader,
publication path or approval mechanism. SHA verification, schema/reader checking,
UK eligibility and independent physical/location verification must happen before
that interface is used by any future production installer. Fingerprinting guards
accidental caller mutation under an unchanged revision/pin.

## Lifecycle and state transitions

1. A future trusted source synchronizer calls `sourceChanged(user,id,version,token,deleted)`
   from a worker. Version is a durable source change-feed generation, never wall
   time. Same-version identical input is a no-op; stale/conflicting input fails.
2. Correction, enrichment, mode change or deletion atomically removes superseded
   assessments immediately and annotates recognition history. Deletions retain
   source tombstones; delayed jobs cannot resurrect them. Explicit higher-version
   re-addition is allowed. Original archive content is never changed here.
3. `processNext` selects one queued source. Its loader must supply one bounded
   row through a trusted validator. ID/token mismatch fails before writing.
   At most 16 canonical markers are evaluated; geometry is not duplicated per
   marker. Iteration rejects more than 10,000 segments per marker. Catalogue is
   capped at 4,096 physical rows, aliases at 32 per row and identities at 512 chars.
4. Results/cursor/recognition commit in one SQLite transaction only if source
   version, token, cursor, deleted state and catalogue revision still match.
   Cancellation/exception/race rolls back the whole page. Retry resumes after the
   last committed marker. A source/catalogue mutation discards prior work.
5. `QUALIFIED` becomes `DISCOVERED`; only a fully validated `OUTSIDE_RADIUS`
   becomes `ASSESSED_NO_DISCOVERY`. Invalid/gap/unsupported mode/source/missing
   evidence stays `PENDING_EVIDENCE`; unapproved or temporally unsupported markers
   stay `PENDING_CATALOGUE` at marker level. A journey with any unresolved marker
   and no discoveries is pending, never a negative cache verdict. Empty inventory
   is pending catalogue. Per-marker state remains available when a journey has
   both supported and pending results.
6. A pending source leaves the queued work set so it cannot starve other work.
   `reassess` explicitly queues it again; new evidence versions and catalogue or
   evaluator changes also queue it. No pending outcome is permanently cached as
   a negative. Future #283 validation attaches new evidence to existing IDs.
7. Discovery reads use keyset paging of at most 16 rows. Proofs from an unfinished
   journey page set are withheld. Already-complete other journeys can still
   support the same marker. No source geometry enters the discovery query.
8. A complete catalogue replacement atomically clears incompatible assessments,
   retires removed identities, preserves aliases/recognition and queues living
   sources. Historical locations not supported by its interval remain pending.
   Multiple historical dated locations, identity merges/splits and retired-ID
   restoration require a reviewed migration; they cannot silently award credit.

There are no awards, reveal state or map unlocks to orphan or retract in this
stage. A future entitlement consumer must use a stable completed evaluation
snapshot and atomic award/presentation publication, rather than treat a partial
backfill or `recognition` history as current credit. This slice deliberately
invalidates stale facts promptly rather than retaining a possibly false count.
The UI integration must expose an updating/pending indication.

## Compatibility and failure behaviour

SQLite schema version 1 is new and additive. There is no older released milepost
schema to migrate. Reopen preserves rows/checkpoints. Unknown upgrade/downgrade
versions throw without dropping/recreating the database; no destructive fallback.
Unknown future schema requires an explicit migration. Catalogue transactions
retain the prior catalogue on validation/constraint errors. There is no automatic
corrupt-file deletion, rollback rewrite or live production database operation.

User identity is an explicit stable partition, not inferred from a device/global
preference. Future authentication integration must supply the correct principal
and define account logout/deletion/backup behaviour. Current source delete removes
proofs while retaining minimal first-recognition provenance as required by the
specification. Account erasure and retention policy are separate rollout work;
this is not a claim of a completed privacy/deletion integration.

## Validation and delivery evidence

Pending CI receipt. Tests cover native and synthetic Stage 2 evidence, all nine
0–200m boundaries, unapproved markers, historical pending evidence, vehicles,
gaps, invalid timing, duplicate/reimport proofs, user isolation, correction,
delete/re-add and stale writers, fallback, recognition stability, earlier history,
restart/page checkpoints, cancellation, catalogue replacement/conflict/removal,
alias/retired-ID protections, future schema preservation, evaluator invalidation,
worker-only access and a 1,605-source SQLite workload. Stage 1's 27 offline tests
also pass locally. No private Timeline data is required or recovered.

Physical-device/ART memory, crash/power-loss durability, capture contention,
account lifecycle, actual archive change-feed integration, backend acceptance,
full historical backfill and accessibility remain unvalidated. Native SQLite
Robolectric is a workstation regression environment, not device acceptance.
Source remains Android 0.25.223 (269); installed device APK/source and live backend
SHA are unknown. Prior live NCN revision 4 is historical evidence only; no new
large reference transfer or live pin verification is needed for this inactive
independent store. No backend/Firebase changes, production schema mutations,
publication, APK packaging, release or tester distribution occurred.

## Recommended next stage

Review/integrate Stage 3 independently. Next build an **inactive integrated
reconciliation worker**: bounded projected archive ingress, durable source change
feed/versions and deletion handling, evaluator token guards, capture-priority
yield/resume, stable summary snapshots and device budgets. Validate it on native
and synthetic data. Keep #283 Timeline reimport a parallel deferred backlog item;
its journeys remain pending. Separately prepare a reviewed physical-marker
identity/dated-location catalogue. Only then implement award derivation, summary,
reveal and map presentation behind separate activation gates. No further product
policy decision is needed to continue this sequence.
