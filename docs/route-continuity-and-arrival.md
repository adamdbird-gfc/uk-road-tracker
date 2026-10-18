# Route continuity and driving arrival

Matched-section outlines are painted before route centrelines in previews, editing and replay. Exact joins no longer acquire white cuts from a later section's rounded cap. Existing geometry gaps and user-removed edges remain distinct; drawing never invents a road connection.

Captured driving requests retain every prepared observation across bounded 500-point requests with one shared endpoint. They no longer uniformly thin an entire long drive to 500 points. Preparation omits a poor, unmeasured interior fix only when it is outside its combined accuracy corridor and bracketed within 30 seconds by precise, plausible measured driving fixes. Original capture samples remain unchanged and omitted indices are recorded.

Driving GPS dwell owns its movement decision: a rejected isolated fix cannot independently restart the arrival timer. Confirmed through-road queues retain their existing protection, while a known residential/local road no longer holds a parked journey open indefinitely. Context checkpoints remain compatible with older saved versions.

The corresponding backend change increases captured-road matching context from eight to ten observations per router request, matching the live public router capacity, preserving one-point overlap and verified distance accounting. Other callers keep their configured chunk size.

## Validation and limits

Synthetic tests cover stroke order, removed edges, request coverage, unsupported-fix filtering, road classification, traffic queues and rejected driving GPS spikes. Private exports were analysed locally without inclusion in the repository. The supplied Warwick export contains real gaps and incorrect parallel-road matches in addition to rendering seams. With explicit authorisation, 48 selected GPS observations from the affected M40 stretch were replayed through the production road-matching assembly against public OSRM. All 48 matched, all 94 road-step features were attributed to M40, and the six route sections and road steps joined exactly with zero gaps. Verified distance was 22,865.8 metres with complete deduplicated accounting and no failed sections. Only this excerpt was verified; existing saved matches require rematching after both changes ship. The new drawing does not repair genuine missing geometry by drawing straight bridges.

## Public router capacity regression

The live replay rejected 48-, 25-, 24-, 13- and 12-point windows with `TooBig: Too many trace coordinates`. Ten-point windows succeeded. The proposed 48-point backend window was corrected to ten before release; the final excerpt replay used six requests, all successful without recursive splits. A synthetic regression also verifies that an upstream ten-point cap can recursively recover a larger input without dropping observations or inflating distance. Private coordinates and router responses remain outside the repository.
