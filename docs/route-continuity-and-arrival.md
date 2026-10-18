# Route continuity and driving arrival

Matched-section outlines are painted before route centrelines in previews, editing and replay. Exact joins no longer acquire white cuts from a later section's rounded cap. Existing geometry gaps and user-removed edges remain distinct; drawing never invents a road connection.

Captured driving requests retain every prepared observation across bounded 500-point requests with one shared endpoint. They no longer uniformly thin an entire long drive to 500 points. Preparation omits a poor, unmeasured interior fix only when it is outside its combined accuracy corridor and bracketed within 30 seconds by precise, plausible measured driving fixes. Original capture samples remain unchanged and omitted indices are recorded.

Driving GPS dwell owns its movement decision: a rejected isolated fix cannot independently restart the arrival timer. Confirmed through-road queues retain their existing protection, while a known residential/local road no longer holds a parked journey open indefinitely. Context checkpoints remain compatible with older saved versions.

The corresponding backend change increases captured-road matching context from eight to 48 observations per router request, preserving one-point overlap and verified distance accounting. Other callers keep their configured chunk size.

## Validation and limits

Synthetic tests cover stroke order, removed edges, request coverage, unsupported-fix filtering, road classification, traffic queues and rejected driving GPS spikes. Private exports were analysed locally without inclusion in the repository. The supplied Warwick export contains real gaps and incorrect parallel-road matches in addition to rendering seams. Exact rematching against the public router has not been verified; existing saved matches require rematching after both changes ship. The new drawing does not repair genuine missing geometry by drawing straight bridges.
