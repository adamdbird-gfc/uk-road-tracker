# Silent reviewed pub-map updates — 0.25.190 (236)

Installed Spoons collections check the small published manifest when the map
opens, at most once per day after a successful check. Failed checks retry after
an hour on a later map opening. No polling service, GPS-triggered requests,
journey upload or location query is added. Checks run on a background executor.
The map remains available immediately using bundled or previously validated data.

The first publication is revision 2026101201 and contains the existing 811 pins.
The bundled revision is also 2026101201, so an unchanged manifest does not trigger
a full download. Newer revisions are checked for schema, collection, map-only
approval, UK coordinates, complete reviewed rows, unique IDs/aliases, count,
version, byte length and SHA-256. Remote data cannot enable visits, achievements
or a completion target. All existing identities and per-pub approximation/source
labels remain available to the map.

Manifest downloads are limited to 32 KiB; catalogue downloads to 2 MiB.
HTTPS requests use a fixed repository directory, allowlisted filenames,
no redirects and finite connect/read timeouts. A validated update is committed
with Android AtomicFile as one manifest/payload envelope before replacing the
in-memory map. Corrupt, interrupted or unsupported releases keep the last good
map. The cache is revalidated on process restart. Cache corruption or an older
cache falls back to the bundle. A newer bundled revision supersedes an older
downloaded cache. The selected collection and camera are preserved when pins
refresh; collection removal does not erase the cached reference or any history.

## Publishing the next reviewed data release

1. Reconcile operating-status and location changes in the review ledgers.
   Rebuild with scripts/build_wetherspoons_map.py using the pinned source.
   Unknown candidates and future openings do not activate automatically.
2. Run scripts/publish_wetherspoons_map.py with the approved map file, a strictly
   increasing ten-digit revision, and collections/published/wetherspoons as the
   output directory. Published catalogue filenames are immutable. A correction
   or deliberate rollback requires a new higher revision.
3. Commit the new catalogue file and manifest together in a reviewed pull request
   targeting fix/onboarding-start-tracking. The publication workflow validates the
   checksum/contract and runs both builder and publication regression suites.
   Merge after checks pass. Do not alter the bundled Android asset or app version
   for a data-only release. Keep the publication branch/path available because
   installed apps use its raw.githubusercontent.com URL.

Example:

    python3 scripts/publish_wetherspoons_map.py --catalogue /path/to/approved-map.json --revision 2026101301 --directory collections/published/wetherspoons
    python3 -m unittest discover -s scripts -p 'test_*wetherspoons_map.py' -v
    python3 scripts/publish_wetherspoons_map.py --verify --directory collections/published/wetherspoons

App behaviour changes, new approval schemas and check-in/achievement support
still require an app release. Data updates are not evidence of a user's visit.
The client uses SHA-256 to verify transfer integrity; release approval comes
from the controlled HTTPS repository publication and review process.

Regression coverage includes the actual 811-row asset, cached offline restart,
unchanged-manifest download avoidance, failed downloads, checksum/size mismatch,
unsupported/audit/visit-enabled data, duplicate identities, older revisions,
tampered cache fallback, fixed publication paths, reader limits and scheduling.
Android unit tests, lint and signed APK checks run in CI.
