# Permanent public reference-data publication

The validated Firebase preview contains 249 public files / 13,241,691 bytes.
Permanent publication uses the dedicated roadprints-data-822cc Hosting site.
The permanent origin is https://roadprints-data-822cc.web.app; Android should only
switch after its first verified live publication has succeeded.

The shared Hosting workflow accepts preview_deploy or live_deploy, never both.
PR checks stage/validate only. Deployment jobs use the existing Hosting secret,
only the reference-data target, and verify every staged public byte/hash and
serving header after publication. Render APIs and bundled app assets are untouched.

Publish Firebase reference data combines the current release branch's collection
releases with current main settlement files. It runs when the relevant public
files change or is manually requested. Queue commits made with GITHUB_TOKEN do
not trigger another push workflow; workflow_run therefore observes completed
settlement builders. A preflight diff skips queue runs with no new public data,
including empty/failed attempts; partially successful publication is retained.
A single publication concurrency group serialises writers across source branches.

Register this publisher on both release and main branches. The first merge adding
it to the release branch intentionally starts live publication. Main keeps its
current data/backend/app files; only the publication entry point is added there.
Subsequent data updates require no Android build. Existing raw GitHub/Pages data
continues to support old installed APKs during migration; retain those endpoints
until the old-version support window and retirement step are agreed.

Live deployment acceptance: successful deploy and exact hosted-file verification,
then Android URL migration, automated checks and one signed migration APK uploaded
to Firebase. Preview success alone is not live acceptance or device validation.
