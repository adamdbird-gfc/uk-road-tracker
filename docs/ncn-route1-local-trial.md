# NCN Route1 local coverage trial

15 October 2026. Linked feature: #79. Experimental development code only.

## Outcome and limits

20 automated checks pass against a checksum-verified, 47-feature Route1 subset
around Gravesend and clearly labelled synthetic observations. The source fixture
is 22,998 bytes, or 6,081 bytes compressed. It supplies 320 unique physical edges
for this trial. It is not an app feature, raw-GPS matching acceptance, a final
palette, a device benchmark or proof of nationwide route completeness.

The important negative control deliberately omits a parallel alternative: a
nearby synthetic path then wrongly gains 160 m of credit. Supplying the known
alternative withholds that credit and leaves an equally plausible midline
ambiguous. NCN proximity alone must not become production completion evidence.
Integrate validated cycling/on-foot path evidence and local alternatives before
updating saved progress. The current experimental thresholds are not approved
capture settings.

## Reproduction

Install the isolated `requirements-ncn-trial.txt` into a development environment:

```sh
python -m unittest scripts.ncn_trial.test_coverage -v
python -m scripts.ncn_trial.run --output /tmp/roadprints-ncn-route1-trial
```

The runner uses local fixture data and writes only to the requested output. It
makes no network calls. Neither the backend nor Android imports this experiment.
The CI job runs these checks on relevant PR changes only; it creates no APK and
uploads no build artifact.

## Representative results

| Controlled observation | Unique combined coverage |
|---|---:|
| Cycle 70% of the selected on-road reference | 290.195 m |
| Walk 70% of the selected traffic-free reference | 193.879 m |
| Repeat/reverse the same cycling section | 290.195 m total |
| Cycle 10–70%, walk 40–90% of the same section | 331.652 m combined |
| Drive along the NCN road | 0 m |
| On-reference observations with poor accuracy | 0 m |
| Known parallel path or ambiguous midpoint | 0 m |

In the mixed-mode case, cycling is 248.739 m and on-foot is 207.282 m. Their
physical union is 331.652 m, not their sum. Source interval evidence is expressed
on one physical edge basis before measurement; floating-point differences in
partial line interpolation must not inflate shared coverage.

Tests also exercise interrupted/missing observations, a perpendicular crossing,
walking/running aliases, shared synthetic route labels, partial overlaps with
different vertices, activity replacement, mode correction, deletion and refusal
to silently reuse evidence against a changed reference version. TEST-prefixed
route labels and parallel paths are synthetic, not invented public route claims.

## Independent road/path positional check

A bounded OSM query returned 877 road/path ways. At 41 sample positions per
selected NCN section, nearest-way distances were:

| NCN source | Median | 95th percentile | Maximum |
|---|---:|---:|---:|
| FID954, on-road, Royal Pier Road/The Terrace vicinity | 0.918 m | 2.393 m | 2.878 m |
| FID16404, traffic-free, Gordon Promenade vicinity | 0.637 m | 1.681 m | 2.596 m |

This compares positions only: route membership was not independently verified,
and the nearest OSM way is not automatically the correct or legally eligible
path. The source calls one segment traffic-free while nearby OSM ways use the
`service` highway class with bicycle access; those classifications serve
different purposes and must not be treated as contradictory road permissions.
OSM-derived evidence and its ODbL provenance are retained separately from this
OGL Route1 fixture. No photographs or official restricted milepost records are
included in this trial.

## Next sequence

1. Exercise validated matched geometry and competing real road/path candidates,
   retaining ambiguous evidence without granting credit.
2. Extend representative cases to NI Route9 and Wales/Scotland, and settle
   source-update identities before persistent coverage is written.
3. Review map layering, controls and mode presentation with Adam.
4. Integrate the agreed NCN slice with tracking #163 and backend boundaries #164;
   run assembled checks, then one intended APK/Firebase release.

No APK, production deployment, Firebase publication, Render database write or
existing app progress change is part of this experiment. Mystery achievements
retain the separate waypoint/disc mapping and permission gates in #79.
