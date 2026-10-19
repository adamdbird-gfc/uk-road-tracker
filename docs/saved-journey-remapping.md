# Reprocessing saved journeys

Saved native journeys can be rematched after a release introduces improved preparation or backend matching. In Journeys, open a supported journey and choose **Rematch Journey**. This explicitly reassesses completed journeys as well as failed ones; an ordinary Grow Map pass continues to skip completed matches. Installing an update does not automatically rematch the archive or create a bulk repair queue.

Each rematch runs current preparation against the saved route and available evidence, then sends fresh requests to the current matcher. It does not reuse the previous matched geometry as its input. Walking/running preparation reevaluates GPS quality and corroborated stationary drift, even when an older validation version exists. Successful matches replace the derived map and supported distance. Supported native pedestrian arrivals can also repair the end time, preserving its original value. Captured driving uses current preparation and bounded request handling when aligned evidence is available; backend improvements take effect when the updated service is deployed.

Original route geometry and raw capture samples remain intact. “Remove drift” means excluding supported stationary artefacts from the derived walking route, displayed match and distance; it does not destroy the original observations. Unsupported GPS gaps are not bridged. Missing/untimed historical evidence cannot safely acquire invented timing or drift removal. Existing station/vehicle boundaries and user-edited route geometry remain the input constraints: do not blindly replay all raw samples, because the raw recording can contain arrival confirmation and subsequent-leg points outside this journey. New live start/stop/splitting logic does not automatically reconstruct separate historical journeys from an old merged recording.

On failed rematching the attempt/status/error changes, but the prior matched result, distance, end time and original recording are retained. Successful saves use the existing JourneyStore revision/change notifications for dependent screens and accounting. Integration must still verify all combined collection consumers after rematch and respect user corrections; do not automatically transfer coverage from unsupported evidence.

## Regression coverage

WalkingMatchingIntegrationTest verifies repeated rematching, current preparation of an already-completed old-version match with a prolonged stationary tail, replacement of the derived map/distance, preserved original route/raw observations and corrected arrival, and a failed upgrade rematch retaining the previous result. Existing tests cover moving gaps and inflated responses. PR #196 adds these guards alongside the mode-boundary reliability work. Full Android tests/lint run through PR checks; no APK packaging or Firebase deployment is required for these tests.

## Remaining product work

A version-aware list of journeys that may benefit from rematching, a user-triggered bounded bulk repair queue, and historical re-segmentation are separate enhancements. No silent archive-wide migration is introduced here. Device verification and one coordinated release are still required before these improvements are available on installed phones.
