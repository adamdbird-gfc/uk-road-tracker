# Roadprints processing model amendment v1

Status: approved product decision  
Date: 20 September 2026  
Applies to: backend rebuild plan, Step 7 onward

## Decision

Roadprints will use a device-resumable, foreground-optimised import and matching queue as the beta default.

The browser/device remains the source of truth for personal Timeline files, Journeys, route geometry, progress, corrections and achievements. Shared UK reference data remains in the backend. The backend must not retain personal Timeline data, Journey history, user identity or import progress.

## User experience requirements

While Roadprints is open, processing should:

- prioritise the fastest safe matching throughput;
- show completed, remaining and retryable work in plain language;
- provide an honest estimated completion time once enough work has completed to calculate one;
- checkpoint completed work locally so a refresh, restart or interruption resumes without repeating finished Journeys;
- make non-essential enrichment wait rather than delay useful Journey, map and core Progress results.

The app may encourage users to remain open during a large import, but must never require them to restart the whole import.

## Deferred capability

A remote durable background queue is deferred. It may be reconsidered only as a later mass-market enhancement after an explicit, approved design for:

- authentication and account isolation;
- clear consent for any temporary remote processing;
- minimum-data handling, retention and deletion;
- independent privacy and security review.

This amendment does not authorise remote storage of personal travel data.

## Plan impact

The original Step 7 direction is split:

1. Now: strengthen the local import coordinator with durable local checkpoints, resumable stages, throughput measurement and estimated completion.
2. Later: evaluate an opt-in cloud queue only if product scale requires it and the privacy safeguards above are approved.

This amendment is part of the approved Journey/import contract baseline and should be read with the existing UK-only, local-first privacy rules.
