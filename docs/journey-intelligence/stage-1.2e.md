# Stage 1.2E · Integrated Gravesend road coverage

Stage 1.2E extends the normal overview Map from PR #319 at `7d54df3`. That branch stacks on #318, #317, #316 and the railway predecessors. Build 288 is preserved under its release tag `roadprints-0.25.242-288-journey-stage1.1` at `31e6bdd`. Integrate the stacked changes in dependency order when approved.

## What the Map shows

Dark pink geometry marks supported local-road intervals in central Gravesend. The Map unions overlapping millimetre ranges per OSM section. Tapping the line opens the existing road summary with the section's covered distance and the selected journeys that contributed at the tapped point. Date, status and transport filters apply before aggregation, and the existing Recorded travel layer controls this display. Coverage is a Map projection. Journey files, mileage, edits and progression use their existing data paths.

The APK bundles the tagged OSM snapshot with decoded SHA-256 `9c3ce6b58789df13f2019b69be50cb95aa56eacdf612b1606973cfaf08fe4aba`. Its bounds are 51.4370 to 51.4445 N and 0.3715 to 0.3845 E. Of 1,540 indexed edges, 896 local-road edges (9.937 km) can contribute. Primary roads and primary links stay with major-road rendering and remain alignment competitors. Named and unnamed eligible roads use the same tests. Map attribution remains © OpenStreetMap contributors.

The matcher reads original GPS or fused fixes for completed native Driving journeys within their saved start and end time. Automatically classified Driving can contribute after normal processing. The Stage 1.2D accuracy, spacing, continuity, access and direction checks still apply. Routes with removed road segments keep their edited legacy representation. Timeline imports, sparse or ambiguous fixes, and recordings outside the reference retain the legacy path where present. The projection clips only the portions of each legacy line that can be aligned with the same journey's accepted intervals. Unresolved overlap may therefore remain visible as recorded travel.

The cache stores per-journey spans keyed by the source file token and reference/policy version. Changes, new journeys and deletions are reflected on the next Map refresh. Matching and geometry preparation use the existing Map worker. Original fixes are streamed, and unchanged files reuse their compact interval rows. The existing compressed Map snapshot and retained view remain in use. Missing reference or unavailable derived rows fall back to existing travel geometry. The physical Pixel 10 and real local Driving accuracy require owner observation.

## Owner acceptance and recovery

Install the GitHub Release APK over build 288. Open Map and use Roadprints normally. After a local drive finishes processing, check covered stretches, tap for the journey, and check that a later repeat adds history without duplicating the covered line. Check that Journeys and the usual map filters still work.

For a serious regression, branch from the released Stage 1.2E source, revert its Map, asset inclusion and projection changes, and set the next unused version code above the affected APK. Keep the baseline app ID and signing certificate. Run the owner upgrade check from the affected APK and confirm the original Map presentation and archive integrity before publishing a signed corrective GitHub Release. The earlier Map snapshot revision rejects the Stage 1.2E snapshot. Compact local-road rows are unused and can be removed through Android's Clear cache action. Install the corrective APK over the existing app to retain the archive and preferences.
