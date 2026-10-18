# NCN99: developing network and open-section discrepancies

Reviewed 18 October 2026, following the five-route partial-geometry batch.

NCN99 must remain a collection of confirmed open NCN sections, preserving gaps.
The evidence does not justify treating its entire unmatched extent as planned,
or treating an advertised itinerary as wholly part of the NCN. No new source
ways are admitted by this review. Whole-route activation remains blocked.

## Exact residual assessment

The audit uses the admitted reference's **exact vertex spans**, not the full
source ways retained for provenance. The official open union is 22,604.475 m;
the supported source union is 11,182.224 m. These are different geometry bases,
not the numerator and denominator of a user completion percentage.

| Official SegmentID | Location | Outside admitted source at 10 m, m | Determination |
| --- | --- | ---: | --- |
| 57295 | Bangor–Newtownards–Ballyrickard | 9,958.342 | Geometry and source-membership discrepancy |
| 55854 | Belfast / Middlepath Street | 868.841 | Mapped paths exist; missing from selected source relation |
| 33298 | Ballymacarrett Walkway | 526.902 | Mapped walkway exists; missing from selected source relation |
| 22135 | Belfast / walkway transition | 8.986 | Small alignment residual; preserve existing reviewed spans |
| Remaining 16 features | Belfast–Comber | 0.000 | Preserve existing reviewed spans |

Total union residual: **11,363.071 m**. Per-feature residuals happen to sum to
that union here; this is not a general assumption for overlapping features.
The earlier exploratory full-way comparison understated the largest residual;
the exact admitted-span audit above is authoritative for this review.

## Publisher evidence and decisions

1. [Trust: Bangor to Newtownards Route 99](https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/comber-greenway-bangor-to-newtownards-section-route-99/)
   describes an existing 5.4 km itinerary, explicitly on the NCN, including the
   Whitespots route, an on-road passage through Newtownards, and the Floodgates
   path ending at locked gates at Ballyrickard treatment works. Its onward
   protected connection along the A21 to Comber is described as planned.
   **Retain the open-section evidence; do not invent the missing connection.**
2. [Council: Comber to Newtownards proposal](https://www.ardsandnorthdown.gov.uk/article/1429/Proposed-Greenway-Comber-to-Newtownards)
   describes the wider extension as proposed, with planning approval in January
   2025. [Council: 24 March 2026 milestone](https://www.ardsandnorthdown.gov.uk/article/2801/New-5.4km-Whitespots-Line-Connects-Bangor-and-Newtownards-as-Major-Greenway-Milestone-Completed)
   celebrates completion works but explicitly says public opening is still
   ahead. **A completion event or scheduled opening alone is not opening proof.**
   The current Trust itinerary provides more useful operational evidence, but
   does not resolve every exact source edge or construction-status conflict.
3. Official SegmentID 57295 is a single 46-vertex, 10,072.010 m line labelled
   Open Main Route / TrafficFree. Its length and alignment do not directly
   reconcile to the Trust's 5.4 km itinerary. Nearby mapped cycleways show
   substantial offset from the coarse official line, and the Trust describes
   some on-road travel. **Do not certify this whole feature as one verified
   traffic-free path, discard it as wholly planned, widen the admission buffer,
   or divide its length by the advertised distance.** Keep its official geometry
   in research and the explicit completion-binding hold until exact sections
   can be adjudicated. No planned A21 connection is added.
4. [Trust: Belfast to Comber](https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/comber-greenway-route-99/)
   explicitly warns that the described itinerary includes sections outside the
   NCN. Mapped candidates for the Belfast residuals include Ballymacarrett
   Walkway way 460932694 and Middlepath Street cycleway 635011375, neither a
   member of captured relation 931223. **Keep these as membership review leads,
   not admitted associations.** Road carriageways and parallel footways are not
   interchangeable merely because they fall within a diagnostic buffer.

The 48 supported ways and all 20 captured official open features remain intact.
The national checkpoint stays at 188 supported route keys and 43,457 distinct
ways, with 60 other route review cases. NCN99's within-route discrepancy remains
separate from those 60 cases. Its full official extent is research provenance;
it is not ready to become a public completion target.

## Replay and validation

`scripts/ncn_trial/ncn99_scope_review.py` replays the feature audit offline from
pinned baseline reference/associations/source and six bounded OSM map responses.
The map responses are supplementary discovery evidence, never route-membership
or opening authorization. Input pins SHA-256:
`ce6cd0a0a13eefa2d0fa67825247c2aae2c50661f44a658bd069f24e6558a555`.
Captured publisher pages and their URL/hash receipts accompany the evidence.

The report records zero new admissions, zero removed official open features,
and `completion_binding_ready=false`. Replay checks the 20-feature, 53-source-way,
48-supported-way baseline and exact official union. Source-identity and partial
geometry regressions also pass. No Android activation or APK release is made.

To unblock: establish exact source spans and current membership for the two
Belfast sections, then obtain or reconcile detailed section geometry and current
access status for the Bangor–Newtownards–Ballyrickard feature. Retain physical
breaks throughout; a general developing-network label cannot decide these edges.
