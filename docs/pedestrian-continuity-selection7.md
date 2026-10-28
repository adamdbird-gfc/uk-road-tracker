# Pedestrian continuity selection 7

Selection 6's global dynamic programme still rewarded approaching the centre
of every uncertain observation. Correlated drift could accumulate enough reward
to buy side-street visits. Its travel prior also discounted distance separately
at each transition using that transition's worst accuracy.

Selection 7 retains the existing global optimiser and full connected candidate
lattice. Observation costs start outside the recorded accuracy region; network
travel has one sequence-wide scale, the median bounded positive accuracy with
a 20 m minimum. Cumulative travel therefore prices returns and longer detours
consistently. Observation chronology, outside-accuracy costs, transition shape
and connectivity still determine the route. There is no endpoint-only routing,
geometry deletion, geographic special case or fabricated crossing. Missing
accuracy retains the existing legacy objective.

The private original Charing Cross diagnostic was replayed against 11,734
production network rows. Selection 6 offline geometry and evidence equal the
live endpoint exactly. Proposed selection 7: 279 to 262 coordinates, 1,986.7 to
1,860.0 m, six to zero immediate reversals, no revisited vertices or repeated
exact edges. Exact-edge metrics alone are insufficient: fixed-bound regional
plots must also be inspected for non-retracing detours and actual crossings.
The user's continuous-walk confirmation excludes side visits; it does not
identify an exact pavement. Real main-corridor network connectors remain.

Median three-run local matching time: 2.780 to 2.839 seconds (+2.1%). Search work
is unchanged: 1,176 searches / 171,942 settled nodes, maximum 33 candidates per
layer. These local times are not production CPU measurements. Existing 20 s
reference and 48 s request budgets are unchanged.

Seven movement controls cover correlated drift, sustained real side visits,
uncertain out-and-back walks and loops, frequently connected parallel pavements,
real crossings and endpoint drift. The drift, parallel-switching and endpoint
tests fail under selection 6. A backend evidence test separately verifies that
an NCN-tagged false spur is absent from coverage while a genuine visit retains
it, with distance, geometry hash and contiguous evidence spans aligned.

Private acceptance specifications can be passed to the existing replay helper
with `--acceptance`. This checks reviewed forbidden excursion vertices and
optionally all revisited vertices for this confirmed continuous fixture. Never
apply the no-revisit assertion to arbitrary walks: genuine returns are valid.
Raw diagnostic positions, accuracy and network snapshots remain private.

No Android change is needed: installed 0.25.220 sends retained accuracy and
explicit Rematch replaces processing_result. Details and editor consume that
GeoJSON and its existing evidence schema. The matcher family remains
preloaded_pedestrian_network_v4; pedestrian_selection_version is 7. No APK,
migration, reference rebuild or historical journey rewrite is required.

Production deployment and live confirmation are recorded in the development
handover after verification. Phone-side visual acceptance cannot be inferred
from a backend replay.
