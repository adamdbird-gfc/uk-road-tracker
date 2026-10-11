# Scotland catalogue review — 11 October 2026

All 56 Scottish candidates in the pinned 831-row OSM snapshot have a review
outcome: 55 approved for map display and one excluded from the current collection.
Each approval records its individual operator listing, stable OSM identity,
aliases, source name and label position. Repeated names such as Counting House
and Cross Keys are checked separately by locality.

Cross Keys, Peebles is excluded as a former Wetherspoon, not described as a
closed pub. CAMRA's March 2026 update identifies a change of operator since
May 2024; the old operator URL redirects to the homepage. Preserve its identity.

The staged national catalogue now has 172 map-approved venues, three exclusions,
two holds and 654 candidates without a decision. The builder's 656 unapproved
candidates includes the holds. All 831 snapshot candidates are accounted for.
Installed 0.25.186 still has 37 pins. No app code, version or asset changes.

## Reconciliation findings

The current operator listing confirms Red Gown in St Andrews as operating, but
it is absent from this OSM snapshot. Record a missing-venue lead pending an OSM
pub identity and premises position; do not substitute a university centroid.

William Chambers, Edinburgh remains a planned venue, absent from the snapshot.
Operator sources disagree about its future opening date: its own page says
3 November 2026 while the new-openings page says 26 January. Neither supports
an operating pub today. Record the disagreement and recheck actual opening;
never activate based merely on the passage of an advertised date.

The independent researcher list corroborates names/localities and identifies
Red Gown and former Peebles Cross Keys. It also lists a former Sir Walter Scott
site. Current Wetherspoon and Edinburgh Airport listings independently confirm
the operating Sir Walter Scott near gate 10 after security. A former-site entry
must not be applied to the current pub. All map positions remain OSM-derived;
precise indoor premises must be checked before future visit recognition.

Postcode differences are recorded per review: Great Glen PH33 6DG/PH33 6AD,
Crossed Peels FK8 1DU/FK8 1AT and Robert the Bruce DG1 2AB/DG1 1DJ. Source and
operator street/site names match. Do not overwrite OSM fields with directory
fields. Prestwick Pioneer has missing town/postcode retained; it is reviewed
as the town-centre pub, not an airport pub. Dundee/Broughty Ferry and
Glasgow/Uddingston locality variants are explicit. Operating pubs at hotels
are included as pubs, without introducing hotel collection features.

Sir Walter Scott and Booking Office are transport-interchange examples:
airport/station presence alone cannot earn pub visit credit. All premises and
visit recognition remain unapproved. Black Bear's franchise status does not
exclude an operating Wetherspoon pub from this collection.

Missing-venue reconciliation remains incomplete; this batch is not a claim
of complete Scottish or UK coverage. Red Gown needs resolution before release.
Completion targets and automatic visit awards remain disabled.

## Delivery and validation

Keep the agreed order: establish/reconcile the UK catalogue, publish a bundled
app version, then implement silent validated catalogue updates with offline
fallback. Recorder, matcher and importer remain unchanged.

Seven catalogue regression tests pass. A preview built against the pinned
snapshot contains exactly 172 map-only venues and is 81,754 bytes. This already
exceeds the current 64 KiB app loader limit; the future app-release slice must
increase its bounded limit and validate the complete asset's loading/clustering.
Do not package this preview with the existing loader.
