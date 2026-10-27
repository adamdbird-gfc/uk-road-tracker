# PR #222 — bootstrap complete

**Do not repeat setup.** Published release `reference-pins-2026-10-27-bootstrap`
and ZIP SHA-256 `d547847572e574cecd16cdb8e08077bf2e0a1014507c7f876c94c7c88b97853c`
are established. The ZIP is 157,393,480 bytes and must not be changed.

Offline test run [40576749837](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40576749837)
and seed run [40579277763](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40579277763)
both succeeded. Seed source was `54c2cca24f150d52161788a82a784d4a1d2a2303`.
Both datasets were saved, restored into empty paths and revalidated under key
`reference-pins-v2-Linux-46a8b52eef4cb8117614b9944797b353027f46e4b30ffa6e3ed582a34a19e024`.

Normal Android builds restore the cache directly; they do not need a secret,
release lookup or repeated ZIP download. Missing/corrupt pins must fail safely.
The original 79bcc97 commit remains unchanged, but its draft downloader is not the
current operational recovery path. No repeat recovery is needed now.

Read [the final integration review](pr-222-final-review.md) for completed checks,
the remaining Android runner validation, exact approval requirements and limits.
The signed URL/browser developer-tools proposal in pr-222-artifact-bootstrap.md
is abandoned historical preparation. Do not create its temporary secret, start
its workflow, publish another release or alter token permissions.
