# Settlement discovery builders retired — 15 October 2026

The verified GB and NI datasets now provide all 9,072 settlement inventories
and boundaries. Live ready/count and geometry lookups passed for NI; GB live
verification and API checks passed earlier. Roadprints 0.25.201 (247), including
the offline NI county index, is uploaded to Firebase. Adam reports sharing it
with all beta users. Access to a release does not establish that every tester
has installed it or completed device acceptance.

The default-branch half-hourly queue schedule was already removed in PR #148.
This retirement removes both legacy discovery-builder entry points from active
Actions on main and the current app branch:

- `build-settlement-inventories.yml`: older name-based artifact-only builder.
- `build-settlement-batch.yml`: discovery queue, per-settlement Overpass build
  and publication pipeline. This also removes the old schedule still present
  in the app branch's copy, preventing its accidental reintroduction later.

Reviewed main copies are retained as non-executable `.yml.txt` documents under
`docs/retired-workflows/2026-10-15/`. Original branch-specific versions remain in
Git history. Generation scripts and historical batch lists are retained for
investigation and deliberately reviewed future work. No data files, saved road
names, ready totals, user progress or live services are removed by retirement.

No queued or running Actions jobs were found at the audit. ChatGPT task lookup
found two old, already-disabled one-off planning reminders and no enabled
settlement task; their settings are unchanged.

## Retained maintenance

Firebase publishing, hosted-file validation, inventory reconciliation, county
metadata loading and explicit shared local-road catalogue ingestion remain.
The latter can populate detailed road-class/feature rows; the national
name/count inventory release does not make that distinct maintenance tool
redundant. Its pilot request is not a recurring discovery schedule.

Future national refreshes or expansions should use pinned source snapshots,
name/progress compatibility review, preview verification, immutable publication
and explicit database registration. Do not restore the old discovery queue as
routine servicing. Public files continue to serve the app normally.

Validation: active workflow references checked; no scheduled discovery builder
remains on either branch; existing publication/reconciliation checks remain.
No APK build, database write or deployment is required for this retirement.
