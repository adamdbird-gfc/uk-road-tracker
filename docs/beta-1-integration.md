# Beta 1 integration and device acceptance

Reviewed 19 October 2026. Integration branch: `integration/beta-1-core`.
Beta 1 contains core tracking reliability, existing venue collections and interactive road Progress.
Beta 2 contains the NCN collection. No NCN catalogue, Android adapter or data publication is included here.

## Reviewed disposition

| PR | Disposition | Exact reviewed head / handling |
|---|---|---|
| #163 | Superseded by reconciliation in #197 | `26bd7d6fe8ecc7f8da73ffd6b4bb33549e04be9d`; do not merge its old service/model files again |
| #170 | Include through #197 ancestry | `707fa749c10128f18e875b7dbefb1ee915ad93f8` |
| #174 | Include through #197 ancestry | `d475425ffddaed64586e5a007fe3fb07ae9f9427` |
| #182 | Include through #197 ancestry | `13084738b802a1e5c5815daaaced43e8d370232e` |
| #196 | Include through #197 ancestry | `e0817029ed5f4fcc7f3f30603bb40256e1624cf2` |
| #197 | Include as reliability base | `3dcf99b55925800c4e1417b90f932d3d56c8da11` |
| #164 | Include through #168 ancestry | `f36c456b2b289efa3f55796f9499192049fbda83` |
| #168 | Include source provenance only | `2cde46de75aa4f78a244c94e65edab147b83e433` |
| #173 | Include backend road matching | `f1e752cd5c6ff39831000a38ed0cf4095f222a9f` |
| #169 | Include mapped service grounds and catalogue compatibility | `34a96fbf7434f3a76c04cddc3dcdc42017b0392d` |
| #166 | Include through #171 ancestry | `7c413b18b8352d47d073f62e74e9e9802b42bbad` |
| #171 | Include interactive road Progress | `b0ee7a6d67b3e0f25da4fd7804fb9e7a44deac42` |
| #167 | Include, reconciled with #166 map/history | `57fdb2e7df2751411e13bf57a3f360aa68d73052` |
| #165, #172, #175–#187 | Defer to Beta 2 | NCN research, eight-route Android trial and national data stack; no collection shipped |
| #1, #6–#8 | Defer legacy PR disposition | Existing matching coordination, start gates, caching and retry handling have evolved; do not merge stale implementations or auto-close |

Current main `f8882d9fbe24498abe60063d82a730f90a232f46` supplies the existing backend dependencies and registered GB/NI data compatibility. Importing its history does not publish data. Original branches are retained.

## Integration changes

- Keep all measured mode boundaries, venue-exercise behaviour and explicit historical rematching from #197. Raw observations and previous matching results survive failed rematches.
- Save up to ten minutes / 120 post-arrival observations in a separate `arrival_dwell_samples` field, bound to the saved arrival. These extend collection evidence, never travel geometry or arrival time. The shared three-minute dwell detector still rejects fast motion, uncertain site footprints, passing traffic and gaps over 90 seconds. Existing low-power sampling intervals are unchanged.
- Persist a save/split intent before replacing the archive. Replay interrupted handoffs with the same next-leg identity and shared measured endpoint. Do not overwrite an already committed journey during replay. Recover armed orphan checkpoints and retain active tracking on a null-intent restart.
- Atomically replace journey files on the same filesystem rather than deleting the previous archive first. Additive JSON evidence needs no destructive migration; application ID, signing identity and existing archive/preference names remain unchanged.

## Validation status

Local backend: 83 tests, 78 passed and five database-dependent tests skipped pending PostgreSQL CI. Android complete unit tests/lint and combined backend/PostgreSQL CI are required before any release request. This document will record the exact validated source and release run after completion.

New regressions cover early contextual arrival versus full service dwell, traffic/exercise/gap rejection, bounded arrival evidence, history scans/rematching/deletion, process restoration, sampling gaps, interrupted save/handoff replay and all four supported road-detail entry/close paths. Inherited regressions cover overnight drift, school/work stays, venue laps, both mode transitions, matching geometry and rematch failure retention. The pinned Warwick synthetic fixture tests saved-journey recognition against #169's real catalogue; private recordings must never enter Git history.

## Pixel / Samsung targeted beta checklist

Record device model, Android version, app version, battery setting and approximate times. Export diagnostics only through the existing private route.

1. Upgrade over the existing app without clearing storage. Check old journeys, collection visits, place reviews and filters; explicitly rematch one old walk and one drive. A failed/network-off rematch must retain its previous result.
2. Leave armed overnight at home, then depart on foot. Repeat a long school/work stay. No stationary journey; retain the genuine departure's first supported movement and location.
3. Run laps in a park or large campus with the screen off. Movement within the venue must remain a journey. Stop at a shop/home and compare the displayed arrival time with the actual stop.
4. Walk to a parked car, drive a motorway/local-road route, stop and walk away. Check modes, shared endpoints, continuous geometry and complete M-road matching. Check gaps are disclosed rather than bridged speculatively.
5. With tracking active, remove the app process using the OS/test tooling, allow a supported restart and reopen. Check one copy of each leg, retained identity and recovery status. Repeat with screen off, battery saver and a deliberately interrupted GPS interval. Force-stop prevents Android automatic restart until the user opens the app; do not treat that OS behaviour as device acceptance.
6. Visit a motorway service station for at least three minutes, then depart. Drive past another without stopping. Only the genuine supported visit should count. Reassess saved Warwick Northbound history and verify the intended side/site rather than granting a pin-proximity visit.
7. Open motorway, A-road, B-road and local-road Progress where supported; focus a road, inspect contributing journeys and return with Close/Back. Preserve the previous Progress section/scroll. B/local remaining maps explicitly depend on available reference coverage.

Passing automated checks does not establish real-device acceptance. Samsung battery restrictions, sparse GPS beyond 90 seconds, indoor accuracy and process scheduling still need field evidence. No automatic historical re-segmentation or archive-wide rematch is added.

## Beta 2 next slice

Keep #172 separate from Beta 1. Reconcile its shared Progress adapter with this integration, then adapt the #165 → #175–#187 national source stack: open sections only, source-way overlaps/alternatives, unique physical coverage, mode filtering, correction/deletion-aware history and bounded storage. Validate before any NCN catalogue publication or APK.
