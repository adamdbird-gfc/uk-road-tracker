# Cumulative Map archive validation

The archive audit runs the Lab's existing replay logic in chronological order
against a private diagnostics ZIP and the pinned reference files in the
checkout. It reports measurements and candidate review groups. It has no
network dependency and does not edit the ZIP. Keep the JSON report private
because ZIP ordinals link back to the owner's archive.

```sh
node map-laboratory/tools/archive-audit.cjs /path/to/archive.zip map-laboratory TRUSTED_ORDINALS
```

Replace `TRUSTED_ORDINALS` with space-separated ZIP ordinals already used to
develop or check rules. Omit it to get an unfiltered review queue. To compare
revisions, use the same audit script and archive, passing each checkout's own
`map-laboratory` directory so the replay uses its pinned reference data.

## Review signals

| Signal | What is measured |
| --- | --- |
| Detached coverage | New geometry in components separate from the largest selected-journey component, using a 2 m join tolerance. Supported dashed links count towards connectivity. Intentional breaks and connections in earlier history can still trigger this signal. |
| Parallel novelty | New geometry running 3–22 m from approximately parallel earlier geometry in the same travel mode. This can be a different path or carriageway. |
| Potential divided-road crossing | A leg going across distinct parallel ways of the same named main road without a nearby mapped crossing in the available surface references. This cannot establish legal access or detect every wrong crossing. |
| Capture agreement | Retained non-Timeline fixes more than 25 m from selected-journey geometry, for captures with at least ten fixes. GPS drift and missing network data can trigger the signal. |
| Reference support | The presence of nearby pinned references, and separately whether a lookup failed. Having references for part of a journey does not establish coverage along the whole route. |

These are triage signals. Counts measure how many lines require investigation,
not an error rate. A false merge can also hide genuine coverage, so inspect
representative unflagged controls. The review queue excludes geographic groups
containing named regressions, then retains one walking or running candidate per
group per severity and a set of unflagged controls. Groups use the saved start
position rounded to a grid. Check their geographic separation manually before
treating them as a holdout, especially where routes cross a grid boundary.

## Android acceptance

Use the latest verified owner release lineage. Compare the same archive on
the baseline and candidate, and demonstrate that the native Map correctly
renders supported street and pedestrian connections, suppresses duplicate
coverage, and preserves genuinely different paths. Check retained raw and
matched journey bytes, manual corrections and all unchanged map modes. Use the
same inputs for cold and steady-state warm map timing and peak heap comparison.

The Android cumulative Map uses the native road-reference asset and disposable
contributions. The Lab uses additional public road and pedestrian geometry with
tags such as separately mapped pedestrian crossings. Validate equivalent
evidence before transferring rules that rely on those tags. Track confirmed
unresolved gaps separately from missing evidence. Test the exact candidate
source before signing an owner APK, then verify the package ID, increasing
version code and established signer before in-place installation.
