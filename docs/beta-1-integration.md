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
| #1, #6 | Superseded as separate merges; already included in ancestry | #1 `a57d8ce3385dac0547a61e3a68ffaf5ca26d658b`, #6 `3df285e6bd8d1b7a062c3bf99cf86e9d1d5d58a8`; both verified ancestors of the tested source |
| #7, #8 | Defer divergent legacy PR disposition | #7 `4b563c2b2c91740d0a449a34037d69aaab311648`, #8 `40652e28985e17286151255060faf32a58ce33aa`; newer station/caching/rate-limit implementations are present; do not merge old whole branches or auto-close |

Current main `f8882d9fbe24498abe60063d82a730f90a232f46` supplies the existing backend dependencies and registered GB/NI data compatibility. Importing its history does not publish data. Original branches are retained.

## Integration changes

- Keep all measured mode boundaries, venue-exercise behaviour and explicit historical rematching from #197. Raw observations and previous matching results survive failed rematches.
- Save up to ten minutes / 120 post-arrival observations in a separate `arrival_dwell_samples` field, bound to the saved arrival. These extend collection evidence, never travel geometry or arrival time. The shared three-minute dwell detector still rejects fast motion, uncertain site footprints, passing traffic and gaps over 90 seconds. Existing low-power sampling intervals are unchanged.
- Persist a save/split intent before replacing the archive. Replay interrupted handoffs with the same next-leg identity and shared measured endpoint. Do not overwrite an already committed journey during replay. Recover armed orphan checkpoints and retain active tracking on a null-intent restart.
- Atomically replace journey files on the same filesystem rather than deleting the previous archive first. Additive JSON evidence needs no destructive migration; application ID, signing identity and existing archive/preference names remain unchanged.

## Validation status

Local backend: 83 tests, 78 passed and five database-dependent tests skipped locally; all passed with PostgreSQL in CI. Complete Android suite and lint passed before the release and again during packaging. Exact verification and release links follow below.

New regressions cover early contextual arrival versus full service dwell, traffic/exercise/gap rejection, bounded arrival evidence, history scans/rematching/deletion, process restoration, sampling gaps, interrupted save/handoff replay and all four supported road-detail entry/close paths. Inherited regressions cover overnight drift, school/work stays, venue laps, both mode transitions, matching geometry and rematch failure retention. The pinned Warwick synthetic fixture tests saved-journey recognition against #169's real catalogue. Private original Warwick replay is recorded separately below; recordings never enter Git history.

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

## Verified checkpoint before packaging

- Android complete unit suite and lint: green at `5c8deea0a6237f484a865994e7148d51af4f1627`, [run 39259997616](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39259997616). The preceding run had one incorrect moving-gap test fixture (111 m displacement below the deliberate 300 m driving threshold); corrected to a genuine >2 km moving gap. All 607 tests pass after that correction.
- Backend/PostgreSQL: green [run 39259997954](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39259997954); separate backend delivery #199 passed [run 39260168551](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39260168551) and merged as `b1926f004bfde1b8d38046016b9aa553b2931cca`. Live `/health` confirms that exact source, API 0.12.0, ten-point captured-road windows, place-feature revision 2, source-evidence version 1 and database ready. No new migration files were introduced by #164/#168/#173.
- Private replay of the original 16 October saved recording: 3,091 raw observations recover **Warwick Northbound only** using the production service detector and bundled mapped grounds. No coordinates, timestamps or private recording fixture were committed. The different 18 October drive has no samples in either Warwick footprint and correctly receives no Warwick credit.
- Local delivery checks: nine immutable-release-request tests, three existing-tester discovery tests, nine service metadata/footprint tests, four venue-registry tests and seven Firebase staging tests pass. Version prepared: **0.25.202 (248)**, upgrade over 0.25.201 (247), with offline release notes. Schema-2 request sharing is explicit and default-off; old requests remain upload-only.

## Released Beta 1 — 0.25.202 (248)

- App integration #198 merged as `6a215c90031392825e2f35e014a8f0b152e37b40`; backend #199 merged as `b1926f004bfde1b8d38046016b9aa553b2931cca` and verified live.
- One immutable request `7738692ef76ba0a3328f6c33b16da29070d6b677` pins the exact tested source `5c8deea0a6237f484a865994e7148d51af4f1627`. Subsequent pre-release commits changed documentation only; no code was swapped after validation.
- [Signed package/distribution run 39261229063](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39261229063) **succeeded**. Packaging repeated **607 tests, zero failures/errors/skips**, lint and stable-key signature verification. Application ID remains `com.roadprints.capture`; existing installations can upgrade without a storage reset.
- Firebase release **3ln17lg2bp3v8** uploaded with offline/versioned release notes and **distributed successfully to existing project testers**, using the explicit schema-2 flag. [Tester installation link](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/3ln17lg2bp3v8). [Firebase console](https://console.firebase.google.com/project/roadprints-822cc/appdistribution/app/android:com.roadprints.capture/releases/3ln17lg2bp3v8).
- APK checked locally: **36,145,010 bytes**, SHA-256 `2b8f1734d403d943e591bc8c1f3fdc77b96498af213cd58b6ab1bd27a165cfe9`; ZIP inspection confirms no NCN assets.
- [APK artifact 12190867034](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39261229063/artifacts/12190867034) retained for seven days; verification reports artifact 12191436657 for three days. No second APK request, NCN collection or reference-data publication.
- Original source branches remain. #164/#166/#167/#169/#170/#173 became accounted-for/merged through their target ancestry; stacked #168/#171/#174/#182/#196/#197 can remain open because their individual base branches were not advanced. #163 remains the superseded standalone design, not a missing integration. Do not delete these branches or blindly merge the open stack again.

## Remaining limits and next slice

Device acceptance is outstanding and #188/#194 stay open. Public-router availability and over-90-second service-dwell gaps remain limits; historical rematching is explicit, not automatic archive re-segmentation. A small follow-up should short-circuit linked-archive reads after the ten-minute arrival-observation window (evidence additions already stop), then collect Pixel/Samsung overnight/screen-off evidence before the Beta 2 national adapter. This optimisation is not included in the released APK. No private live-rematch success is claimed.

Live replay of private driving coordinates was blocked by automatic approval review: checking an export does not explicitly authorise sending its GPS trace to the external matcher. No private live-rematch result is claimed. Offline production preparation of the 18 October drive aligns all 424 route observations and retains 179 non-redundant points in one bounded request; full backend geometry/accounting regressions run with deterministic router responses. Real-device and explicitly authorised private live replay remain separate acceptance evidence.
