# Achievement celebration delivery — 0.25.194 (240)

Achievement jump labels and section headings now use CollectionAccessStore.name: Motorway Service Stations and UK Wetherspoons Collection, matching Collections and the map selector.

Previously, celebrations depended on Snapshot.newlyUnlocked in the one calculation which recognized a milestone. A background recognition or cached snapshot could persist the earned state, consume first-recognition history, and never deliver the popup. This release separates earned state from durable delivery acknowledgements. Unacknowledged, currently earned achievements are recovered on the next visible, up-to-date Achievements screen, including unlocks from earlier versions.

A family celebrates its highest currently earned level in one card. Acknowledgements store the shown level, so a higher milestone recognized during an open carousel remains pending. Recognition, cache reads, background screens and lifecycle dismissal never mark a celebration delivered. User navigation or closing the currently displayed card acknowledges that card only. Remaining cards survive; closing defers automatic reopening for the current screen session. A Celebrate achievements action in the overview resumes them. The next button advances rather than claiming unseen achievements. Earned badges do not depend on interacting with celebrations.

The existing gold celebration slider retains Previous/Next and swipe navigation, adds welcoming copy, a brief badge animation respecting system animation settings, and an accessibility announcement. Close remains top right. No sound is forced. Removed evidence suppresses pending badges; acknowledging a celebration cannot award or acknowledge a future milestone. Delivery preferences are included by the existing Roadprints backup preference export.

Regression tests cover cached and background unlocks, legacy recovery, family upgrades, withdrawn evidence, lifecycle loss, deferring/resuming batches, per-card acknowledgement and matching collection labels. Pub stop detection remains a separately discussed next slice; this change concerns labels and celebration delivery.
