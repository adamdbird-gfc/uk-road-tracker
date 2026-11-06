# Stage 6 — inactive mystery presentation and accessible replay

6 November 2026. Related #290. Starting main `05f94bbcc824462d018767a0e03e0668ea373427`; PR #289 independently confirmed merged at that commit. Stage 6 targets main in one unmerged PR, #291. The PR records final head, ending main and exact final-head validation evidence.

## Delivered boundary

A functional native `MilepostPresentationView` can be embedded in an Activity and exercised in tests. It is deliberately **not called from AchievementsActivity, registered in the manifest, added to AchievementStore, or scheduled by a production caller**. There is no live activation, migration, catalogue installation/publication, backend change, APK packaging or distribution. Source version remains 0.25.223 (269); installed APK/source provenance, device-active reference pins and live backend SHA are unknown. All 889 actual candidates remain unapproved. No issue is closed.

The component reuses existing native ScrollView/LinearLayout/TextView/Button styling and AndroidX accessibility support, existing SharedPreferences persistence, ScreenDataLoader for explicit details requests, and the existing capture-state preference contract. It does not create a second eligibility or celebration engine. Existing general celebration queues cannot resurrect these batches because no milepost definition or event is registered with them.

## Compact input and identity protection

`MilepostPresentationReader.read` is an explicit worker-only read of Stage 5's seven-state receipt. It uses the existing archive/NCN freshness check before and after reading a consistent SQLite transaction. The previous complete achievement state remains readable with an updating label when stale, but cannot start a new summary or reveal. It performs no evaluation, reference download, archive decoding or achievement write.

The only Stage 5 change extracts its unchanged 16-fact SQL page into a shared method. An optional correlated recognition-ordinal column supports presentation without a separate query per marker; the derivation path retains the original projection and qualifications. Physical identity, current source token/version, evaluator/catalogue, approved location, supported mode and qualifying proof checks remain unchanged. Pages and detail queries retain no geometry.

Before any individual recognition, six `?` cards have no names, biographies, conditions, identity-bearing tags or hidden navigation targets. Unknown-designer-only discoveries show generic discovery summaries and details; the reveal action remains disabled until the collection is introduced. Any one individual, including Grimshaw, can introduce anonymous silhouettes and collective six-part progress. Only currently earned identities and their draft biographies are visible. Once introduced, corrections do not erase that introduction; withdrawn identities become anonymous with an explicit previous-recognition/withdrawal label. The collective count reflects current qualifications.

Biography copy comes from the repository's existing conservative drafts in `verification-register.md`, including the qualified provenance for Harrow. No new historical claims or likenesses are introduced. Final content/rights review remains a release dependency.

## Harrow contract

| Distinct currently eligible posts | Stage | Newly resolved zero-based positions |
| --- | --- | --- |
| 0 | Hidden | none |
| 1 | Seasons | 0, 7 |
| 2 | Continents | 1, 8 |
| 3 | Ages of Man | 2, 9 |
| 4 | Zodiac | 3, 10 |
| 5 | Centuries | 4, 11 |
| 6 | Final Mystery | 5, 12 |
| 7 or more | Charlie Harrow revealed | 6 |

Cipher version 1 renders `CHARLIE HARROW`, preserving the space after letter position 6. Unresolved letters use fixed +7 modulo-26 substitution; resolved positions use the target. There is no random/device/time/locale seed. Duplicate visits cannot advance the Stage 5 distinct physical count. Regions, routes and disc types have no presentation role.

Six separate layer identifiers reconstruct each prefix in fixed order; a separate generic silhouette and opaque central veil reconstruct from the current static stage alone. Stage 0 has no theme or cipher; stage 7 removes all theme panels and the veil. Placeholders are programmatic neutral panels and a generic silhouette, not guessed historical portraits. Native text labels identify the themes independently of colour. Only the current composition is retained; no bitmap is decoded. Next updates the existing reveal text, controls and composition in place; the six cards are retained until their input snapshot changes. Approved artwork can replace the renderer against the same layer/stage contract.

All transitions are intentionally **static and user-controlled** in this delivery. There is no unfinished animation frame to persist, timing task, motion setting dependency, automatic reveal queue, sound or haptic dependency. Decorative animated artwork is not implemented or necessary to use the complete experience.

## Summaries, detail pages and dates

One inline, non-modal summary contains newly recognised currently qualifying unique physical posts, newly recognised current individual/collective awards, supported journey-date range, unknown award dates, and counts of queued/incomplete journeys. It offers View discoveries, optional Replay reveal, Later and Skip celebrations. There is no automatic card-by-card celebration.

Counts use durable first-recognition ordinals, never journey/proof counts. Reimports/alternative proofs/reinstatement do not count as new physical discoveries. An acknowledgement stores only that complete batch's post/award high-water marks and receipt, so a later batch remains pending. A later historical date correction may change the supported date without reordering recognition or replay.

Dates are supported UTC calendar journey dates, explicitly labelled journey precision; no exact passing time is inferred. Grimshaw/parent historical dates may remain unknown. Recognition time is never substituted. The summary's date range concerns new physical posts; a separate sentence identifies unknown new achievement dates.

View discoveries opens actual bounded native detail pages in recognition order, showing physical ID and available journey date. Each page retains at most 17 compact candidates and displays 16. Pagination scans only compact fact pages; it does not load all posts or journey geometry. A completed details delivery acknowledges the inspected batch. Failed/stale/capture-interrupted requests do not acknowledge it. Request tokens reject callbacks after host pause, replacement input or capture start. Details remain available after summary acknowledgement, including for unknown-designer-only discoveries. Maps, geographical names and map pins remain separate workstreams.

## Durable state and lifecycle

`MilepostPresentationStore` writes one atomic, schema-versioned SharedPreferences JSON record per user in a separate namespace: summary acknowledgement, deferred receipt, last completed reveal stage, replay cursor, paused/dismissed/replaying flags, static preference and artwork version. It never opens the eligibility database or writes existing award/celebration preferences. Unknown/corrupt presentation schemas fail closed without overwriting the original record; a future production host must surface that error safely rather than reset it silently.

Replay can restart at zero or resume its completed cursor. Construction/reconstruction starts paused, preserving the preceding completed static stage. Later saves the cursor without acknowledgement. Skip moves to the current final stage and acknowledges just the supplied batch. Subsequent history can increase the target without resetting the cursor or opening another modal. Withdrawals clamp the visible stage to current qualification while preserving the stored cursor/completed-stage history; reinstatement restores the available historical cursor. Artwork-version replacement preserves the stage and pauses for explicit continuation.

The component registers/unregisters capture preference and host Activity lifecycle listeners with attachment. The host can also explicitly call `onHostResume/onHostPause`; attachment after an already resumed Activity should call `onHostResume` when installing the view. Opening, every Next/Skip, and host resume check the live capture preference. Capture start immediately pauses and persists replay, hides reveal/summary controls, and invalidates pending detail callbacks. Recording completion never automatically resumes a reveal. Host pause/back/detachment and saved-instance-state callbacks preserve the completed cursor.

## Accessibility

- Locked identity text, biographies, tags, descriptions and navigation are spoiler-safe; the six cards use anonymous positional tags only.
- Cipher and placeholder art are decorative accessibility exclusions. Completed-stage progress announces theme and resolved-letter count, never scrambled characters. The seventh announcement may name Harrow.
- Always-static transitions provide the complete reduced-motion experience even when Android animators are disabled.
- Native Next, Skip, Replay, Resume and summary controls have 48dp minimum heights, normal keyboard behaviour, high-contrast explicit text/background colours and vertical focus order. Next retains keyboard focus across reconstructed stages, moving to Skip at the final stage.
- Native SP text has wrap-content heights and a vertical scroll container; tests exercise 200% text scaling at a narrow viewport. Headings use AndroidX compatibility for minSdk 26.
- No colour, sound, animation, fine motor timing or portrait recognition is necessary to understand progress.

Automated tree/layout/label checks are not physical TalkBack, switch access, font/device-layout or contrast/visual acceptance. Those remain device acceptance before activation.

## Validation and memory interpretation

Focused tests cover all six first-individual states, unknown-only and Grimshaw-only introduction, all eight static states and all 13 cipher positions, independent layer prefixes, collective/bulk history, supported/unknown dates, acknowledgement and per-user persistence, restart/pause/resume/skip, capture start/resume/transition checks, stale input, withdrawal/reinstatement, details pagination, 200% scaling, spoiler-safe labels and unchanged existing entitlements.

The no-APK workflow retains compilation/lint and relevant milepost, archive-memory, general celebration, geometry-memory, NCN, walking, Timeline, capture-transition, train and ferry regressions. Test fixtures use isolated synthetic approvals. Reference bytes are cache-only and verified against source pins; Android tests run without external networking under a 256MiB heap. Final-head CI/test totals and artifact hashes belong in the PR.

Two Stage 6 probes measure incremental workstation heap separately: (1) three warm compact reads over 4,096 discoveries / 1,605 source identities / 8,192 proofs, retaining seven awards; (2) three warm native static replay cycles. Setup and source reconciliation are excluded. Both report before/sampled peak/after-GC retained heap and elapsed time. They measure the whole JVM/framework and GC noise, not allocation ownership, ART/PSS or cold-device performance. Zero decoded artwork bitmap bytes is a structural property, not a claim of zero graphics/view memory. Workstation guards are 16MiB incremental peak / 4MiB retained for compact reads, 32MiB / 8MiB for the native view; these are regression guards, not device promises. The existing integrated Stage 4/5 test suite remains unchanged in its benchmark coverage; no additional archive-scale geometry benchmark was introduced.

Stage 4's approximately 218.7MiB workstation peak remains unresolved physical-device headroom risk. Stage 6 does not claim to fix it or establish capture latency, main-thread frame budgets, native graphics cost, power-loss durability or Pixel/Samsung acceptance. No broad physical profiling is performed.

## Activation dependencies

Keep the PR unmerged pending owner review. A separate authorised integration/activation task must wire the worker-produced receipt and view into the existing screen, handle error/freshness refresh lifecycle, schedule approved derivation, obtain approved catalogue/content/artwork, verify device performance/accessibility and recording continuity, and independently authorise any live entitlements or APK. #283 historical Timeline recovery and #286 portability remain deferred.
