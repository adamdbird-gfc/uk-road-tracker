# Replaying journey diagnostics for the NCN trial

This adds a local read-only adapter for schema-1 Roadprints journey diagnostics.
Keep personal exports and generated reports outside the public repository. CI uses
invented contract examples, never users' journey data. No app/runtime integration,
APK, network request, production database or collection-progress write occurs.

Run from the repository with the development requirements installed:

```sh
python -m scripts.ncn_trial.journey_replay /private/journey.json \
  --mode-variants --output /private/ncn-replay.json
```

The adapter preserves Android's actual sample order: longitude, latitude,
accuracy metres, epoch milliseconds, speed m/s. It prefers raw capture samples
and identifies any fallback to retained samples. Malformed samples become explicit
breaks rather than being removed and accidentally joining unsupported legs.
It never invents point times/accuracy from a journey's duration or densifies a
reconstructed matched route into fake GPS observations.

Three measurements are deliberately distinct:

- The trial reference's existing observation scan reports diagnostic candidate
  coverage from stored timed samples. This is a raw-evidence diagnostic and does
  not apply route edits; it is not an authorised completion contribution.
- Stored matched linework is compared with 3, 8 and 15 metre NCN buffers solely
  to inspect sensitivity. Removed matched edges stay removed, including across
  multipart geometry. Unsupported road-only edits with no edge mapping suppress
  this measurement rather than inventing a corrected path.
- Verified collection credit remains zero. This adapter has no independent
  matched-path membership, competing-path/corridor verification or historical
  reference reconciliation input. That gate is intentionally still open.

Mode variants change only the input label in memory, preserve declared mode and
mark the result as synthetic. They are not genuine cycling/walking field tests,
do not re-time travel or validate speed plausibility, and never alter the source
file. Driving remains ineligible. Missing timed samples remain missing in every
variant. Existing trial limits (500 fixes, local metric projection, bounded
Gravesend reference) remain experimental and are included in result provenance.

Current NCN linework also does not prove the same route membership existed on the
date of a historical Timeline journey. A stored match can support identifying a
candidate for a targeted review; it is not independent ground truth for choosing
between parallel routes. Do not award a whole matched section from an isolated
endpoint near the network, or repeatedly ask about the same unsupported import.

The next product/data gate is a bounded review flow showing supported candidate
sections with their evidence limitations, plus a genuine timed on-foot/cycling
trace and verified competing-path context. Map colours and review placement
remain for discussion with Adam. Marker permissions and verse mapping are a
separate achievement gate. #163, #164 and map filters #166 remain coordinated
release work; no separate trial APK is needed.
