# NCN revision 4: exact artifact recovery

Starting main: `b103af36fb165dac1820915138f619d522042b2a`; issue #194; PR #271.
Android 0.25.223 (269), geometry, backend and APK distribution are unchanged.

## Cause and identities

Run 41890999302 consumed `ncn-r4-20261104-owner-console` and failed before preview/promotion. Its retained manifest describes the **final** hosted-runtime output: SQLite `2b3bfe8636e156568b5d5f352321ea2bf5734585d63e756b75d95ddd1456e808`, gzip `3d4e9b6c19a148f1ecc1e6d3285ab72506abcb130d08eb46df731c1b96d52463`, 145,796,148 compressed bytes. The logged `557457cfedfcf40f7c7308580d3aad9920d6772938676821c62d4f1ffb868118` belongs to the **intermediate Stage 1** SQLite database, not the final database or gzip.

The preparation recipe used the host SQLite writer without pinning its version/build. Original SQLite 3.53.1 defaults `secure_delete=OFF`; Ubuntu SQLite 3.45.1 has `SECURE_DELETE` compiled in and defaults ON. Their original/rebuilt databases have identical SQL schema, every stored row value and every compressed BLOB. However, 70,089 bytes across 2,427 of 60,374 pages differ, including unused cell/free-space bytes and the writer header. Changing only the four-byte writer stamp does not restore identity. A synthetic regression separately demonstrates secure-delete settings changing physical bytes without changing reference values. See the retained [full comparison](data-trials/ncn-publication-readiness-20261104/writer-comparison.json).

Logical equality, SQLite bytes and outer gzip bytes are three independent checks. The host output was independently validated but **never approved as a replacement**. Original manifests remain untouched. No geometry was regenerated or policy reconsidered; only the existing checksummed correction recipe was replayed from the existing verified reference cache.

## Original candidate recovered

The original writer and packaging process reproduces all original pins locally and in GitHub candidate job 126819626610, run 41900022422:

| Property | Original approved identity |
| --- | --- |
| Revision | 4 |
| SQLite bytes | 247,291,904 |
| SQLite SHA-256 | `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f` |
| Gzip bytes | 145,856,489 |
| Gzip SHA-256 | `c40117278069777b38c13a099f319855e99878d24b95128f140f8d2f34a6020d` |
| Canonical content | `d3486a38fc33a78dd9314720a4677906fa7315e28c15feedb1577a1847caac2d` |
| All-row total | 1,376,616 |

[Provenance](data-trials/ncn-publication-readiness-20261104/recovered-artifact.json) pins source commit, successful candidate job, artifact ID, ZIP digest, database/gzip identities, schema and table counts. No writer header was changed in the successful recovery. The complete original checksums matched before any such operation. The candidate artifact is retained for 90 days; missing/expired artifacts fail closed. The source-pinned SQLite 3.53.1/zlib 1.3.2 setup script remains available for an explicitly reviewed exact reconstruction, with the original full checksum gate. Publication has no automatic reconstruction or retry fallback.

## Publication correction and bounded authorisation

Publication and final-head candidate CI download **artifact 13357563539 from run 41900022422**, restore the existing verified seed cache and independently check manifest equality, compressed size/hash, bounded expansion, database size/hash, SQLite integrity, all canonical values, schema and reference counts. They do not regenerate or repack the archive. Original candidate/rollback manifests and the old consumed attempt tag remain unchanged.

The new create-only authorisation is `ncn-r4-20261104-artifact-recovery-01`. It retains the same candidate, expected Hosting baseline `758e86605707677e`, accepted November evidence, 200 MB verification envelope, 2 GB reporting/traffic reserve, rollback revision 5 and max_attempts=1. It explicitly references the consumed authorisation and failed run. Main merge changes the request once and therefore triggers one new publication. Never rerun that publication; preserve diagnostics on failure.

Existing Hosting identity checks, shared writer lock, rollback inventory/config preservation, one catalogue preview verification, promotion of the identical immutable Hosting version and manifest-only live verification remain. No Cloud Monitoring, IAM, billing or Android changes.

## Validation and delivery

49 focused publication/integrity tests pass locally; final-head hosted CI must also pass before the authorised merge. Earlier candidate-only diagnostic runs cannot publish; two initial fixture assertions used Ubuntu's existing writer stamp as a purported mutation and were corrected to use a distinct stamp. The successful original reproduction is independently verified again on the final head; an overall earlier diagnostic workflow result is not substituted for required final-head checks.

The final publication run, live version, actual transfer accounting and issue outcome belong in the immutable Actions publication artifact and issue #194 completion receipt. Keep #194 open until live publication and integrity verification succeed. No APK build, backend deployment or physical-device acceptance is claimed.
