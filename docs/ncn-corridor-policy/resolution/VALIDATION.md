# Validation state

Base: `15ca06d26d045a16583d44038ae6c9fe17778cd7` (merged #275).
Local candidate: `7853f02bd376f95d5e12652971c818e5b4efe948c74f607c75c7afd5b25612a0`.

- 333 existing NCN trial tests passed.
- 60 focused Python checks passed, including six new finite-review tests and unchanged Chapel/Deal/compatibility/accounting controls.
- 12 owner-policy evidence checks passed.
- Complete before/after row audit: only NCN1/4471919, NCN66/159311690 and NCN92/30268009 change, by appended bindings only. No other row changes.
- Exact supplied GPX hashes and 142-segment / 14,261.858200 m overlap reproduced from both original files.
- All new review target coordinates reproduce the current publisher's Open Main vertices at stored precision.
- Immutable review SHA-256: `e76571514408eaa7b20a7e9cd88acce42cdcd188822cde09948161faee18c9ab`.

Remote final-head candidate reconstruction and Android consumer results are pending at initial PR creation. No successful Android run is claimed until its final-head result is verified. The dedicated workflow has no publish/deploy/release step.
