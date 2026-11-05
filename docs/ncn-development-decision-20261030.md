# NCN development decision — 30 October 2026

**Recommend B: prepare the already validated NCN seam candidate for a separate
publication-readiness review.** Stop the diagnostic increment here. No new
publication or national assessment is authorised by this recommendation.

We have explained the apparent contradiction. The two stages measure a flattened
map using different scales, so a path near the 10 m boundary can look just inside
in one measurement and just outside in another. They also ask different questions:
whether an entire edge qualifies, or whether a supported part aligns with one
finite target. The new report labels those measurements and preserves the actual
decision separately. It does not turn a nearer-looking road into a valid route.

**This increment makes zero additional NCN kilometres assessable.** The earlier
seam correction already demonstrated **45.834 km of unique-network assessable
capacity across 217 routes**. That is candidate capacity, not new travelled miles
or a claim that it is published in the app. The retained assessment leaves
approximately **1,289.324 km** unbound; this task has not recalculated that figure.

1. **Are the diagnostic inconsistencies sufficiently explained?** Yes for these
   bounded cases and the reporting contract. Deal's five corridor changes are
   reproduced, as is the private-road geometric flip without eligibility. Missing
   and inferred origins remain limitations. This is not a national proof of
   positional accuracy or every historical admission decision.
2. **Is more projection research needed before ordinary attribution work?** No
   demonstrated general blocker remains. Keep existing measurement conventions,
   offsets and gates. Any future operational reprojection would require its own
   design and migration evidence; it is unnecessary for the existing correction.
3. **Which remaining gap category is strongest?** Among uncorrected categories,
   known-way admitted-span correspondence offers the most bounded route to an
   evidence-backed correction because the physical feature can be identified.
   However, the Deal review found zero additional eligible spans. The historical
   101.320 km unique-network review envelope is not recoverable mileage. No new
   unbound category currently has an implementation-ready, quantified correction
   stronger than the already validated ordinary seams. Bradford direction,
   Cumbria identity and hairpin ambiguities remain justified restrictions.
4. **What would a new correction require?** Select an exact remaining target
   interval and immutable source version, prove route membership/access/modes and
   admitted span, recover the necessary origin/offset/target metadata, exclude
   competing branches/private paths and masks, then independently bound every
   additional interval. Only then measure additive candidate capacity and test
   existing ledger/Android consumers. Proximity alone is insufficient. There is
   no need to start that investigation before preparing the proven candidate.
5. **Can the 45.834 km correction proceed to readiness review?** No technical
   blocker has been demonstrated by this work. PR #242's retained evidence records
   159,711 independently checked added bindings, preserved prior bindings and
   catalogue tables, and successful candidate-specific CI with 43 Android
   NCN/celebration tests. These are historical candidate results, not fresh live
   catalogue or device verification in this task. The report's historical pending
   Android paragraph is superseded by the completed CI evidence on PR #242.

The next bounded task should verify the **actual active catalogue version and
hash**, match it to the candidate's exact input pins, reproduce the preserved
candidate with its canonical content digest, confirm consumer compatibility on
current validated source, and prepare a versioned publication plan and tested
rollback to the existing asset. Check version selection, cache invalidation,
download size/bandwidth and retention of existing user evidence. Stop with a
reviewable readiness result; obtain separate approval before publication.
Do not extend scope to new gap corrections or operational projection changes.

Evidence: [diagnostic implementation and validation](ncn-consistent-geometry-diagnostics-20261030.md),
[projection audit](ncn-projection-audit-20261030.md),
[residual reconciliation](ncn-post-seam-reconciliation-20261030.md),
[seam candidate validation](ncn-ordinary-seam-validation-20261030.md), and
[PR #242 with final candidate CI evidence](https://github.com/adamdbird-gfc/uk-road-tracker/pull/242).
