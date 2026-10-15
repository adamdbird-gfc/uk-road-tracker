# NCN section review: interaction proposal

15 October 2026. Proposed interaction for discussion with Adam, not an agreed
map palette or a shipped screen. Builds on the agreed section-level confirmation
rules in #79 and the evidence experiments in #165. No APK or runtime wiring.

## Entry and scope

Show a review action on the relevant journey and within NCN progress. Selecting
it opens the bounded uncertain section, not a request to redraw an entire trip.
Keep the original recording and already supported sections. Preserve the user's
camera when they leave the review. No arrival-time modal or interruption during
an active journey is proposed. Core accurate mapping remains available without
the collection; NCN-specific review belongs to the enabled collection.

## Two evidence cases

### Plausible alternatives with adequate recorded evidence

Show the recorded fixes, the provisional match and only verified, supported
alternatives. Ask which path was used, using source-backed names where possible.
The options may be the NCN path, the road alongside and Not sure; they are not
fixed choices to offer in every location. If the road itself is an eligible NCN
route, it can qualify too. Never classify a pavement as ineligible solely because
it is absent from one route relation.

Show a preview of the selected section before Save. Save corrects only that
window and recomputes supported core/collection coverage. Keep the mode and unique
physical overlap rules. Reprocessing retains the explicit decision. Changes to
source identity, geometry, evidence or eligibility require reconciliation.

### Sparse historical import without supported alternative paths

The attached in-conversation design explores this case first. Show the stored
locations separately from the reconstructed route. Explain the limited evidence
in plain language. Highlight only the candidate overlap, not the whole route.
Do not attach an evidence percentage or a confidence claim to the inferred path.

Proposed choices:

- I used the highlighted section: retain this as the user's recollection, linked
  to the candidate/reference/evidence snapshot. It does not independently verify
  membership, access, historic route validity or a different matched path. Keep
  the contribution pending until those checks support it.
- I took a different route: exclude this candidate from NCN credit. Keep the
  original journey and do not snap it to an unspecified alternative road.
- Not sure: keep the candidate pending/uncredited; suppress repeat prompts. The
  user can revisit it from the journey. Dismissal has the same credit result.

A recollection is distinct from a verified path correction. Do not pass it into
the existing controlled confirmation model as though candidate validation had
already succeeded. Review records need an explicit evidence/decision kind.

## Result and editing

Before Save, changing a selection is a preview; closing discards the draft.
After Save, show a brief result and Change my response. Update only the candidate
window. Leave the rest of the trip and its existing achievements/coverage intact,
except where a supported correction actually changes their qualifying evidence.
An explicit exclusion removes this candidate's prior contribution if one exists.
Reference refreshes must not silently promote old recollections to completion.

The screen should show Pending, Confirmed or Excluded in words, alongside line
styles and labels. Colour alone must not convey status. Proposed highlights in
the design are for discussion and do not settle the wider NCN map palette.

## Before runtime integration

- Agree the two cases and wording with Adam; especially whether retaining a
  recollection while pending is clear enough for a historical import.
- Establish supported historical source/path associations and activity access.
- Validate a genuine timed cycling/on-foot trace with verified alternative context.
- Add the explicit review record kind and section-level persistence/correction
  wiring; keep sparse recollections out of the verified-confirmation path.
- Check accessible labels, large text, map line overlap and return behaviour on
  the combined beta. Coordinate #163, #164 and map filters #166. No separate APK.

Personal traces used for the in-conversation preview remain outside this public
repository. The preview is local-only interaction design and cannot alter app
journeys or collection progress.
