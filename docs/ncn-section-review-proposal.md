# NCN activation and review behaviour

Agreed with Adam, 15 October 2026. This replaces the earlier proposal to review
sparse historical imports. The review design is supported for new recordings;
it must not create a bulk-import or historical near-miss queue. The wider NCN
map palette and final device layout remain open design/integration work.

## Activation

Assess existing history only when the NCN collection is activated. Start a
resumable background assessment with visible counts of journeys assessed. Core
tracking/navigation remain usable. Assess stored matched paths first; do not
send every historical journey through a fresh GPS matcher unnecessarily.

Credit supported cycling/on-foot NCN matches automatically. Missing raw point
accuracy/timestamps does not, by itself, disqualify an imported path with a
reliable reference association. Proximity alone, a reconstructed line or an
isolated crossing does not establish that association. Driving never qualifies.

Retain per-journey, per-mode, versioned evidence and deduplicate physical coverage.
A corrected journey replaces its prior contribution; deleting one journey keeps
coverage still supported by another. A reference refresh needs reconciliation,
not silent transfer to a new nearby route.

## Existing history and bulk imports

- Existing history is assessed at activation, including earlier native recordings.
- Bulk/Timeline imports added while active are assessed automatically too.
- Supported sections count. Unsupported/ambiguous sections do not count.
- Do not generate a review queue, repeated prompts or hundreds of pending user
  actions from these assessments. An import arriving recently is still an import.
- Repeating activation or importing an unchanged revision must not repeat work or
  inflate unique coverage. Resume unfinished work after interruption.

## Newly recorded journeys

For a native journey recorded with NCN active, use the agreed section review only
when the actual evidence supports plausible alternatives. Show a bounded map
comparison and the original recorded fixes; offer the supported NCN path/road
alternatives and Not sure. A road may itself carry the NCN and qualify.

Save changes only the supported uncertain window, recomputes relevant coverage
and retains the recording. Dismissal/Not sure leaves it uncredited and does not
prompt repeatedly. Keep the explicit decision with source/evidence versions so
reprocessing cannot overwrite it. A changed reference requires reconciliation.
Do not infer that a parallel pavement is ineligible solely from its absence in
one route relation. Source membership and activity access remain necessary.

## Development contract and production integration

`scripts/ncn_trial/activation.py` exercises activation, ingestion origins,
bounded batches, checkpoint restore, pause/reactivation, revision replacement,
deletions, failures and review eligibility. Its assessor is a required input:
this queue does not create verified matched-path evidence from proximity.

The tests use invented journey identities and controlled reference evidence,
including a 700-row historical ambiguity case and an interrupted 101-row batch.
They are development correctness checks, not Android performance measurements or
positive field acceptance. There are no production background jobs, app screens,
Firestore/Render writes or APK changes in this Python contract slice.

Android integration still needs the persisted per-journey ledger, reliable
matched-path/reference association, activation UI, background scheduling and
version migrations. Use bounded work and retries; never report Ready while work
has failed. Reconcile a complete archive snapshot on activation/reactivation so
changes made while disabled are respected. Coordinate #163/#164, map filters
#166 and visibility fixes #167 for the later combined ready-slice beta.
