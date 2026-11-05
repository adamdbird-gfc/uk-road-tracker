# Stage 1 investigation and correction report

5 November 2026 · issue #265 · [owner register](DECISIONS.md).
**Investigation delivered; no production correction substantiated to all acceptance gates.**
No global threshold changed. This is not an implementation or publication receipt.

## Reused evidence and bounded method

The merged twelve-map [viewer](../ncn-viewer/parallel/Roadprints_NCN_Corridor_Review.html),
[prior machine summary](../ncn-viewer/parallel/results/national-summary.json),
original checkpoint and complete national candidate register were recovered.
Only fifty existing pieces were selected: Chapel Lane 1, Bradford 2, Derry 12,
Highland 35. No fresh national discovery or catalogue rebuild was performed.

[Export tool](export_evidence.py) verifies checkpoint pins, the original five route
plans/associations and consumed extent inputs. It reconstructs only their original
publisher target graphs to recover exact target identities and endpoint degrees;
it does not run admission, national binding compilation or unique-network union.
The [compact fixture](evidence.json) includes exact source node chains, section
keys, original projection origins, source endpoint neighbours, original bindings,
selected full-precision metric targets, adjacent target edges and all fifty records.
Full-route graph reconstruction verifies every target key against the original plan.
Original route-local interval units stay unchanged. Ellipsoidal local measurements
are diagnostics; [findings](findings.json) and [analysis](analyse.py) reproduce them.

The original checkpoint does **not** contain revision 4 canonical target-to-network
rows or a complete current coverage ledger. Those values cannot be inferred from
local lengths, target degrees or the screening register. No additional catalogue
was downloaded from Firebase. No missing canonical mappings were invented.

## A. Chapel Lane — NCN1 / way 4471919

- Original source identity/hash: `2e3d8e9bf009474905358fa47348ead5414890513a0ace39781ad0f508663f26`.
  The full source is admitted for walking/cycling. Exact target:
  `open-edge:96aac48b8463b443ba916d6dbb020a5118f70a5abc29a814e5e1c00a680a9bf1`.
- Residual target interval **[31.66876172979717, 40.55143977293073] m**;
  8.88267804313356 route-local m. Candidate source edge 24, nodes
  11371661294 → 27412821. Original rejection: direction gate.
- Local angle **17.180366°**, mean sampled separation **0.930476 m**, maximum
  sampled separation **1.943653 m** reproduce the retained screening result.
- Both target endpoints have degree two. Source endpoint node 27412821 connects
  to admitted way **169004647**. This supports a same-road representation lead;
  it does not define the exact source station owning the target junction.
- The target's terminal point projects to **9.036791 ground m** along a candidate
  source edge only **7.794464 ground m** long. Its closest point on the full source
  is the source endpoint, 1.943653 m away. A blanket 17.2° allowance would still
  fail to prove a finite full-residual mapping. These ground values are distinct
  from the route-local interval units above.

**Outcome: candidate improvement, no new binding.** Small mapping offsets are not
being treated as proof of a different physical road. The unresolved item is the
finite ownership of the last source edge and the connected next source at the
publisher junction, against revision 4's existing bindings. Next: obtain those two
path rows and the selected/adjacent target rows (including canonical mappings)
from the retained revision 4 candidate; independently establish one ordered,
non-overlapping source/target anchor partition. A pin-specific correction can then
be tested without widening the national gate. Do not automatically assign the
entire 8.883 m residual or the whole way.

## B. Bradford — NCN66 / way 159311690

- Original admitted hash `cb4f97a8ea3f0bb3676f2eeb28edb6f918f312bb2f0d54e726d368a08f07354c`,
  NCN66 membership supported, walking/cycling modes, three original bindings.
  Recovered original projection origin **[-1.238694665665012, 53.734628280093446]**;
  this resolves the older local-proxy limitation without altering old receipts.
- Two retained pieces total **62.114800383885736 gross route-local m**, rather
  than only the previously displayed leading 16.6° example. Their exact source
  edges, target IDs, coordinates and intervals are in `evidence.json`:
  `PC-fd18f79dd345` (54.08486405222375 m) and `PC-9053d0488bc5` (8.029936331661986 m).
- Both selected source/target edge pairs still fail the original 15° gate in the
  original operational projection. The screen angles are 16.64° / 18.73°.
  Source/reference equality confirms the same OSM feature, not all publisher branches.
- The two targets meet at a **degree-three publisher junction**, not an ordinary
  degree-two seam. Its point [-1.75405494276165, 53.7925165755232] lies about
  1.46 ground m from this source. End source nodes connect to admitted
  160017281 and 674543498. Connectivity of the source chain is supported;
  ownership/direction through the third target arm is not independently resolved.

**Outcome: candidate improvement, no new binding.** Next: inspect the three exact
incident targets, connected source chains and revision 4 current bindings; verify
which ordered target arm is the same physical NCN66 path. Define finite source
anchors, exclude the other arm, and compute the canonical delta before implementing
any pin-specific direction exception. A global 15° increase remains excluded.

## C. East Sussex — RCN90 / way 1289002662

The accepted historical same-feature update is reproducible with the existing
[source-compatibility tool/tests](../../scripts/test_ncn_source_compatibility.py).
Nodes 6925348794 and 6925348793 moved; the ordered chain is stable, maximum
corresponding-vertex displacement about 1.505 m. Original import feature versions
remain unknown. The admitted source section is **1289002662:0:1**, not the full
three-node way; zero original target bindings remain explicit.

The recovered nearest original publisher target is
`open-edge:2cfdbf92bffad636c803823f496b481fd44ce98802fd7d9b3d1d75dba560290d`.
It is **1.218166 ground m** from the source, but forms an angle of **89.858749°**
with the admitted source edge. It fails the actual direction/finite correspondence
check: nearest geometry is not the same target. This is a concrete negative check,
not a claim that no other historical route target can ever correspond.

**Outcome: historical feature compatibility verified; no NCN target correction.**
Exclude the nearest crossing from new attribution. Next: supply an exact RCN90
publisher section demonstrably following this historical path; otherwise retain
ordinary journey travel without RCN90 completion. A note proposing NCN90 membership
is not route identity evidence. Do not remap or recalculate historical journeys.

## Stage 1 acceptance result

No proposed correction satisfies exact finite target ownership plus current
canonical accounting. Therefore no production correction, new entitlement,
assessability gain or route-attainability change is claimed. Existing positive and
negative controls pass; [test receipt](VALIDATION.md). Physical route leads and
historical compatibility evidence are preserved, not silently converted to credit.
