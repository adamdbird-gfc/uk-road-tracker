# Five open-section reviews — 18 October 2026

NCN16, 222, 657, 688 and 74 now have reviewed catalogues of verified open source
spans. The five routes were selected together from the remaining UK-anchored
partial-provider queue. Exceptions are retained within each route rather than
blocking the other catalogues. Whole-route completion and Android activation
remain separate work.

| Route | Supported ways | Official open target, km | Supported on foot, km | Supported cycling, km | Unmatched official open extent at 10 m, m |
| --- | ---: | ---: | ---: | ---: | ---: |
| NCN16 | 286 | 68.120 | 62.012 | 66.943 | 950.339 |
| NCN222 | 123 | 29.084 | 20.419 | 26.405 | 2,594.999 |
| NCN657 | 137 | 35.595 | 34.005 | 35.557 | 0.000 |
| NCN688 | 254 | 76.266 | 75.605 | 75.699 | 784.645 |
| NCN74 | 209 | 62.140 | 48.973 | 61.192 | 817.511 |

Official targets and exact source spans use different geometry. These lengths
are not a completion fraction, and walking/cycling totals overlap. A zero
proximity residual does not establish complete source evidence. Every catalogue
retains `completion_binding_ready=false`; all unmatched official Open Main
geometry remains in the research target. Physical breaks are preserved.

## Route determinations

- **NCN16:** exclude proposed child 8031533 and its six ways. These are exactly
  the six provider-missing ways. Also exclude four ways through local link
  children 5676436 and 10582210, tagged `network=lcn`, `ncn=no`, and included
  through explicit `link` roles. None enters the catalogue. Two shared ways,
  276035782 and 626752960, match existing node/coordinate fingerprints and are
  counted once nationally. Eighteen accepted ways retain partly held edges.
  The [publisher](https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/route-16/)
  distinguishes the existing sections from the wider intended connection; no
  proposed Basildon connection is inferred.
- **NCN222:** fresh membership adds 1569257821–24 and 1569483770–71 and removes
  7734686. Reassess the current mapped geometry without claiming a real-world
  reroute or transferring old journey credit. The four-way roundabout group
  1569257821–24 has three gates and remains held. The two new 1569483770–71 ways
  form a reviewed linear member arc with way 4983269. Existing simple two-gate
  ring 5063256 uses the established either-arc policy, which does not invent
  another NCN route or infer cross-route credit. Twenty-one accepted ways have
  partly held edges. Seven official alternative features stay outside the main
  target.
- **NCN657:** remove source member 32772057, absent from the refreshed relation.
  Hold roundabout 4905421 because its provider representation conflicts; do not
  reconstruct a whole ring from inconsistent arcs. The 137 other member ways
  have verified spans. Even though the official line lies within 10 m of those
  spans, the ring remains unresolved. The [publisher](https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/route-657/)
  calls this a route through Thirsk alternative to Route 65. Its own official
  features are Main Route, so it qualifies under its own NCN657 identity and
  does not automatically complete NCN65.
- **NCN688:** reassess added members 1569229154, 1569229156 and 1569229157 against
  the captured source nodes and official open geometry. Retain multi-gate rings
  30691404 and 4900767 as logical branch holds; 4900767 also has conflicting
  provider geometry. Simple two-gate ring 4901302 uses the existing bounded
  either-arc policy. Two accepted ways retain partly held edges. Preserve the
  [published route identity](https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/route-688/)
  rather than treating an approximate parallel route as a new NCN route.
- **NCN74:** hold multi-gate rings 11999654, 22927529 and 8562972; the first two
  also have conflicting provider geometry. None of the 56 ways in disconnected
  source relation 6413919 is admitted: its source note says it is absent from
  Trust mapping, and the captured geometry does not pass this open main extent
  review. Seventeen official alternative features stay outside the target.
  Fourteen accepted ways retain partly held edges. The
  [publisher's Glasgow-area route description](https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/national-cycle-network-routes-in-glasgow-and-the-surrounding-area/)
  is contextual evidence, not a reason to accept every source relation carrying
  the same route number.

These source changes are map membership observations. They do not establish
when physical paths changed or moved. Mode restrictions and official extent
holds remain active. No old full-source inventory is replaced or shortened.

## Capture, checkpoint and validation

The pinned capture contains 13 complete source relation/full responses, six
root provider responses and 459 official features. Of those features, 431 are
explicitly Open Main Route and 28 are Open Alternative Route. The latter are
retained as evidence but excluded from this batch's main-route denominator.
Capture manifest SHA-256:
`845b72088927ef31dd75329125e9edf407abac9bbde0084ab934dcffd8cbf82c`.
Official payload SHA-256:
`f5e7beebb30007fd750d459a378e97921445b815ff8f65a9b4d99e6bf757b526`.

The preceding registry is independently pinned at 189 route keys and 43,615
distinct physical ways, digest
`84471c32436079c6a2e8f964d8b64d9a9683d45d1a97e904d62bdea30bc3175f`.
The batch adds 1,009 route-way associations, of which two already exist in that
registry and agree in physical identity. **1,007 new distinct ways** bring the
checkpoint to **194 supported route keys and 44,622 distinct ways**. No physical
identity conflict was found.

There are **54 other numbered-key source-review cases**: 12 partial geometry,
30 source scope and 12 missing membership. Legacy NCN626 and four regional
zero-association holds, plus within-route gaps and the completion adapter,
remain separately tracked.

Validation uses the unchanged reviewed implementation from the NCN725 slice.
All five catalogues pass its strict hash-pinned reader. Offline replay reproduces
the outputs deterministically; preceding batch and NCN725 outputs remain
unchanged. The section review explicitly asserts zero admissions through the
two local NCN16 links and the disconnected NCN74 relation. The preceding code
checkpoint passed 302 tests and its fixtures; no new code test or APK build is
triggered solely for this data/documentation publication.

The evidence bundle retains full captures, admission reviews, catalogues,
audits, supplementary section determinations and offline replay. The repository
checkpoint is a compact index into those complete, hash-identified outputs.
