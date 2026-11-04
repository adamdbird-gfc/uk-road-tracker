# Draft NCN corridor equivalence policy — review only

This policy grants no completion and changes no production thresholds. Approval belongs to a finite, versioned alternative-to-target mapping, never every way within a radius.

## Evidence gates

| Gate | Acceptable evidence | Hold or reject |
| --- | --- | --- |
| Designated target identity | Exact route, target span and reference version | Nearby route or similar number alone |
| Alternative identity | Pinned provider/way ID, full geometry/hash, reviewed finite source sections and original snapshot where available | Whole-way identity extending excluded sections |
| Public/modal access | Explicit public permission or authoritative highway evidence appropriate to walking/cycling, with conditional restrictions recorded | Private/no, unknown permission, `bicycle=no` for cycling, `foot=no` for walking |
| Same corridor | Independent route/council/highway evidence plus a physical corridor explanation | Proximity, matching names or a matching bearing alone |
| Connectivity | Ordered finite entry and exit anchors; demonstrable lawful connectors; topology/barriers checked | One crossing/shared node, inaccessible barrier, unconnected opposite carriageway |
| Direction | Route-specific legal direction, monotone progression and junction continuity | Reverse one-way cycling, hairpin return arms, crossing-only paths |
| Measurement | Appropriate local metric CRS, finite spans, continuous bounds or labelled sample bounds, lateral offset profile and endpoint uncertainty | Minimum distance alone, global distorted projection, extrapolation past endpoints |
| Accounting | Map every approved alternative to the same canonical target/network intervals; union across routes, modes and alternatives | Sum of alternative lengths, route-local totals presented as network gain |
| Version change | Same-feature history and geometry provenance reviewed; mappings invalidated/reviewed on unsupported identity changes | Silently transferring admission after moved nodes, changed access or new way identity |

Unknown tags are missing evidence, not permission. Absence of relation membership need not prove rejection for a legitimate alternative, but requires independent corridor proof. Current OSM observations do not recover discarded original import versions. Walking-reference presence does not prove cycling rights. Driving remains ineligible under issue #265.

## Corridor types

| Type | Required distinction | Separation approach |
| --- | --- | --- |
| Adjacent carriageway/cycleway | Verify same transport corridor, both connectors, access and eligible activity; do not count unrelated driving | A finite reviewed envelope tied to actual road/path layout and barriers |
| Footpath/shared-use path beside road | Mode-specific access and genuine through alternative; avoid private frontage/crossing paths | Survey/map-supported corridor boundaries; no universal maximum from this sample |
| Opposite-side infrastructure | Verify safe/legal access to the same end sections, road crossings, direction and discontinuities | Width alone is insufficient; divided roads/rail/water barriers require explicit review |
| Same identified route, offset publisher line | Treat as representation correspondence, with exact identity and bounded target anchors | Reuse the exact-review pattern of Deal; do not call it a new parallel-way policy |
| Short connectors/transitions | Confirm actual link and finite target termination; retain missing endpoint residues | No blanket endpoint extrapolation or automatic short-gap filling |
| Hairpins/nearby neighbouring roads | Distinguish target arm/order and neighbourhood purpose | Reject proximity-only mapping regardless of small separation |

**No universal lateral threshold is defensible yet.** The 40 m /20° discovery screen only produces a bounded review queue. Existing 10 m/15° production rules remain unchanged. A later type-specific numerical rule requires evidence distributions with approved positives, access/topology negatives, boundary cases and explicit error bounds. Do not choose the threshold by maximising recovered distance.

## Classifications and review record

High confidence requires all finite identity/access/corridor/topology gates to be established. Requires review means a plausible admitted finite candidate with incomplete equivalence evidence. Unresolved covers missing/contradictory admission, original access or source provenance. Rejected means evidenced private, illegal, unrelated or topologically wrong alignment. Store the reason, evidence references, observations versus original metadata, reviewer/date, geometry/reference hash, route identity, permitted modes/direction, finite anchors and canonical interval mapping. A separate record should explain rejected controls.

Even high-confidence diagnostic geometry does not award completion. A later implementation must also validate the actual eligible journey and immutable source evidence. Historical valid traversal may be assessed after an approved reference update; preserve original journeys, manual corrections and unlock history. Test partial traversal, repeat/import, switching alternatives, overlapping route identities, edits/deletion/recalculation and retrospective evidence. Union canonical coverage before calculating thresholds.

## Evaluation against this review pack

| Examples | Policy result | Remaining failure or evidence gap |
| --- | --- | --- |
| Deal designated cycleway | Existing exact-reviewed identity passes; already published | Does not establish separate public road/path alternatives |
| Newry / Mid-Wales | Existing positive same-feature controls retained | No distinct alternative established; no new recovery |
| Wellington Parade private road | Reject despite 0–4.567 m sampled separation and one shared node | Independent public permission absent |
| Wolverhampton hairpin | Reject automatic arm equivalence | No unique ordered entry/exit mapping |
| London prefix | Hold excluded finite section | Whole-way ID cannot supply missing span approval |
| Bradford | Review local finite direction correspondence | Existing direction gate still applies |
| Cumbria | Unresolved route identity | NCN7 membership does not alias NCN6 |
| East Sussex | Separate source-version investigation | Geometry update does not create target entitlement |
| NAT-01 NCN92 designated cycleway | Prioritise exact finite correspondence review | Unadmitted sections, endpoint connectivity, independent corridor evidence and canonical mappings incomplete |
| NAT-02 NCN7 road | Review admitted finite spans/topology | General road classification alone does not approve every neighbouring line |
| NAT-03 Chapel Lane | Review connector/direction evidence | Short distance is not a reason to extrapolate |

The framework prevents the two known false positives and retains the established positives, but has insufficient newly verified **distinct** parallel alternatives to calibrate a national acceptance rule. The appropriate next decision is further bounded evidence gathering, not automatic recovery.
