# PR #222 — Firebase bandwidth protection verified

**Technical integration and verification complete. Ready for merge approval.**
Do not repeat bootstrap, asset discovery, release upload, secret setup or Android
verification. The PR has not been merged. This report supersedes earlier pending
verification/setup instructions.

## Final evidence

[Successful Android verification 40585966577](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40585966577):

- Reviewed application/integration source: `2f70f1145b211cc46f646d84b9a319bd8bb7cfd3`.
- Registered verification workflow: main commit `a94996f7f10b3b3c05e5fa2fcfde3098feec0116`.
- Exact GitHub reference cache restored; both local datasets validated before build.
- Android compilation, unit-source compilation and lint passed.
- Three required Robolectric instrumented Android test libraries resolved before
  isolation; no Android SDK installation occurred.
- Unit tests executed inside a network namespace with only loopback available,
  using Gradle/Robolectric offline modes and the reference-transport guard.
- **801 tests / 107 suites; zero failures, errors or skips.** Independently summed
  from the downloaded XML reports, not inferred from the green workflow icon.
- Verification reports: artifact `12772066575`, 360,284 bytes, expires 3 November
  2026. Artifact digest `890b08c8df4bc8eb15a3fc59989309ffdd422f7aacf1a5da1be016c208a71f9d`.
- Runner also passed 25 pure Java offline cache/transport/diagnostics checks,
  16 Python build/reference contracts, 4 distribution controls and 6 county
  recovery checks. Published-release staging's 14 offline tests previously passed
  in successful run 40576749837 and locally.

The final verification addresses the earlier failures without weakening existing
Android assertions: diagnostics now clear both persisted bandwidth counters and
report text, with serialized flush snapshots preventing stale writes; the isolated
launcher retains the already-exported NCN_TEST_DATABASE path. National NCN tests
use the verified local database and pass without contacting Firebase.

## Established cache and dataset identities

Successful seed run [40579277763](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40579277763)
previously saved/restored/revalidated the shared default-branch cache. Do not seed
again while that valid cache exists. Published release
`reference-pins-2026-10-27-bootstrap` and its ZIP are unchanged.

ZIP: `roadprints-reference-pins-2026-10-27.zip`, 157,393,480 bytes, SHA-256
`d547847572e574cecd16cdb8e08077bf2e0a1014507c7f876c94c7c88b97853c`.

Cache key:
`reference-pins-v2-Linux-46a8b52eef4cb8117614b9944797b353027f46e4b30ffa6e3ed582a34a19e024`.

| Restored build location | Bytes | SHA-256 |
|---|---:|---|
| `.cache/ncn/catalogue.db` | 243,724,288 | `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093` |
| `android-prototype/app/src/main/assets/historic-counties/boundaries.bin` | 8,992,277 | `b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850` |

The successful Android job restored this exact key itself, beyond the earlier
seed-only proof. Source contract tests require the normal Android checks, reusable
packager, seed and manual verification workflows to use identical key/path
expressions, exact matches and explicit cache-miss failure. The manifests did not
change. Original source commit 79bcc97 remains immutable; no runtime source is
silently rewritten by the later documentation receipt.

## Routine build behaviour

Android checks and private development packaging restore the shared GitHub cache,
then verify the NCN hash/SQLite integrity and county hash/length before build-tool
setup. Release/development/NCN callers use the protected local reusable packager.

`prepare_ncn_reference.py` has no network transport; county preparation is local
only. Historical network-capable helpers are rejected by source preflight. No
build path falls back to Firebase for these two national datasets. A missing or
evicted cache fails at restore with the requested key; corrupt contents fail pin
validation. No broad restore key or normal-build cache save bypass is present.
The county recovery helper's legacy 'Firebase ... checksum verified' log wording
labels the dataset provenance; that function only reads/hashes local files.

When a reference version changes or GitHub evicts the cache, deliberate recovery
can reuse the already-published verified GitHub bundle. That is exceptional
maintenance, not a download on every build. No repeat asset transfer is required
for normal development while the current cache is available.

The verified workflow compiles/lints/tests without assembling an APK. Ordinary
future APK packaging remains the existing separately authorised development
process; this task did not generate, install, distribute or publish an APK.

## Safety, scope and remaining approval

Reference data is national public map data, not personal journeys. Licence/source
notices and metadata are documented in the PR. Account credentials, signed URLs
and production secrets were not added to app/source/report outputs. Unit execution
had no external network. No Firebase request was made during investigation,
correction or verification; reference bytes came from GitHub cache storage.

The source retains Android baseline 0.25.218 (264) and recent journey, matching,
map, Progress and NCN performance work. Corrections stay within reference-data
caching/diagnostics, build infrastructure and their tests. Website files and
reference contents are unchanged. Authorised workflow-registration commits used
`[skip ci] [skip render]`; known static GitHub Pages rebuilds were approved. No
Render/Firebase production deployment, billing/configuration change or new
credential was performed by this work.

**Only integration/merge approval remains.** PR #222 currently targets
`fix/navigation-progress-responsiveness`, not main. Do not silently retarget or
merge the stacked development baseline into main. Before any approved merge,
recheck the current target/heads and event behaviour: no release/development/data
request JSON is changed by this PR; automatic Android checks, if triggered by a
parent PR update, inherit the verified cache-only protection. Main and its
production integrations must remain outside an unapproved merge action.

Full automated validation is complete; no further verification run is required
for later documentation-only receipts with identical runtime code. No device
installation/startup was performed or claimed. Merge and APK distribution remain
separate explicit owner decisions. Stop bandwidth-protection work here.
