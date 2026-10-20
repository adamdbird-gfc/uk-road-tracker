# Private development APKs and approved beta promotion

Development packaging is artifact-only. It uses the installed application identity
and stable signing key, runs the full national-data Android suite and lint, and
retains an APK plus verification reports. It has no Firebase job or distribution
credential. Obsolete Firebase flags fail closed. Routine CI, release-request pushes
and NCN asset publication cannot distribute a development build.

## Build for internal review

Use **Private Android development APK**, or the checked-in development request on
`fix/ncn-ui-private-development`. Download `roadprints-capture-prototype-debug`
from its successful run. Record source commit, run/artifact ID, version and APK
SHA-256. Install over the existing app without clearing data. Do not alter the
published catalogue for UI changes.

## Promote only after Adam approves this exact release

The separate **Manually approve an existing signed beta artifact** workflow has
only `workflow_dispatch`, no reusable, push, PR or workflow-run entry point. Only
the repository owner can run its promotion job. Inputs must identify the successful
private development run, its exact source SHA and APK SHA-256, plus the literal
`APPROVE_BETA <source_sha>`. The verifier rejects failed/unrelated runs, expired or
ambiguous artifacts, different source commits and changed APK bytes. It downloads
the existing artifact; it never rebuilds it. Signing certificate and package
identity are checked before the established Firebase credential is used.

The job uses the `approved-beta` GitHub environment. Owner-only manual dispatch,
exact typed approval and checksum are enforced in workflow/script regardless of
whether environment reviewer protection is available. Environment required reviewers
can add a second approval gate through repository Settings → Environments; no
administration permission or reviewer setting is assumed here. Passing CI or a
merge is never distribution approval. The uploader additionally rejects automated
CI events and missing approval checksum before invoking Firebase.

Active legacy packaging/publication entry points must receive the same safeguard
backport. Old immutable workflow commits cannot be rewritten; do not manually
select historical definitions to distribute. Anyone with authority to change
workflows/secrets remains able to change policy; this is not a substitute for
GitHub repository access controls. No new Firebase credentials are introduced.

This corrective 0.25.208 (254) is **development only**; no approval to promote it
has been given. Existing testers must receive neither a release nor notifications.

## Safeguard verification — 20 October

The successful development run 39382924243 contains only `development / build`,
not a Firebase job. Full tests/lint/signing and artifact upload passed; no tester
release was created. Active legacy entry-point safeguard backports:

- main: `647e7087ec62f867b9780d5716c58cf4cdcf7f1a`.
- stabilisation: `e3231bb54d15206238e8005e6d9650a6f737707c`.
- NCN publication: `c51f541f345df6daa0018b4bf4e124efb30b627e`.
- NCN integration: `98ec39a88d621f863f2035657bb4008d59453f30`.
- Owner-only promotion installed on main: `02c87431a2fdb89b107c5a7a1b363c1f65781550`.

Four control tests pass. Approved promotion itself was deliberately not invoked;
0.25.208 remains unapproved for tester distribution. No environment reviewer
configuration or manual Firebase rollback is claimed as tested.
