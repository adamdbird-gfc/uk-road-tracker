# Roadprints 0.25.196 (242)

One combined beta build for #77, the reviewed-catalogue portion of #82, #116 and
#117. No separate Legend/help APK.

- Device Timeline export instructions use Settings → Location → Location services
  → Timeline → Export Timeline data, account confirmation and local Save.
- Floating Legend & map layers button replaces the persistent collection selector
  and notice, freeing map space. Colour/line and pin examples explain overlays;
  persistent switches control their drawing/hit targets without changing progress,
  geometry or camera. Collections and No collection live in the same panel.
- Services and Wetherspoons now use declarative registration, grouping, pins,
  catalogue updates/publication and one configurable achievement engine, alongside
  the shared automatic/manual visit infrastructure delivered in 0.25.195.
- 813 reviewed pub pins include fresh open-status reconciliation for Court of
  Requests and Bottle of Sack. Art Picture House and future Benjamin Franklin
  stay held. Stable identities, original positions and accepted history survive.
- Existing 48 Services and 21 pub achievement definitions/eligibility remain;
  beta access is free. Core recording remains independent of collections.

#77/#116/#117 development is complete and merged in #118. #82
retains certified active/county coverage and deferred Last orders/Pub crawl/full-
county achievement work. Waiting for field results is not development backlog.

Verification on 17034762046e5326d8814e3ed7c9efe6c584c5c1: both Android
workflows passed; 480 tests, zero failures/errors/skips; lint and stable signature
verification passed. Reviewed-map CI and all 28 local catalogue/metadata tests
passed. Merge 2a27267ef3ac2e276cd945e2b366cdb0dbcc4225 has exactly the tested tree.
The release-branch build and Firebase upload also succeeded.

The downloadable APK is 36,030,163 bytes, version 0.25.196 / code 242,
SHA-256 `7092d304157ae1f8fee2a5a2201e15431dfe7b38cda95b86efe5f1ce8ac2fe3e`.
Its V2 signing certificate matches 0.25.195, and packaged checks confirmed the
813-pin reviewed catalogue and new shared/Legend classes. This verifies
implementation and packaging; real-world acceptance remains with testers.
