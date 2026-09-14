# Progressive import onboarding

## Product promise

Roadprints reads a Timeline file on the device, brings the user into their travel story quickly, and continues discovering UK roads without blocking normal use. The file and personal journey history remain on the device.

## Availability states

| Area | Locked | Preparing | Ready |
| --- | --- | --- | --- |
| Journeys | Muted footer icon | Soft pulse while Timeline entries are organised | Lights once the first journey cards are available |
| Map | Muted footer icon | Soft pulse while a lightweight travel map is prepared | Lights once a usable map can be opened |
| Progress | Muted footer icon | Soft pulse while UK road discovery is running | Lights when first road discoveries are available; continues to improve |
| Achievements | Muted footer icon | Soft pulse while enough evidence is assessed | Lights when achievement evaluation has meaningful results |
| Collections | Available independently | — | Does not block core Roadprint import |

A tab wakes once with a restrained animation, then remains stable. It must never flash continuously.

## Import sequence

1. Read and validate the file locally.
2. Organise Timeline entries into journeys and activities.
3. Show the first journey cards and enable Journeys.
4. Prepare a lightweight map and enable Map.
5. Match driving and on-foot journeys in controlled, resumable batches.
6. Persist every completed local result immediately.
7. Update Road discovery and achievements progressively.
8. Reveal the completed Roadprint when all currently retryable work is finished.

## User-facing progress

Use plain language:

- Reading your travel story
- Your journeys are ready to explore
- Your first map is ready
- Road discovery is underway
- A few journeys are still being checked
- Your Roadprint is ready

Do not expose queue, geometry, worker, retry, API or matcher terminology.

## Non-blocking rule

The user can browse every ready area while import work continues. Map rendering is throttled; matching uses small batches; completed work is saved locally. Closing the browser stops active work but does not lose completed results.

## Completion signals

Show compact, non-blocking toasts above the footer only for meaningful milestones. Tapping a toast opens its relevant area; closing it does not affect work.

- Your journeys are ready to explore
- Your first map is ready
- Road discovery is underway — 84 roads found
- Gravesend is ready — 152 of 488 local roads discovered
- Achievement unlocked: Nice to meet you Mary
