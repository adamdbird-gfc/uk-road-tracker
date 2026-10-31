# NCN visual evidence viewer

Open [demo/index.html](demo/index.html) locally in a browser. It is one complete offline HTML file: no tiles, authentication or server. All eight original bounded cases from PR #249 remain. Source baseline is main `40f3c0d00e171800a157af4b3db5d4125134203b`; #249 source was copied from exact head `e274261bf4b60219ba473f4d8f0c78ef4ffb4154`, without merging that PR.

Select a Deal source edge on the map, timeline or selector. The new **31 October resolution** card gives its Admit/Exclude verdict, route and source identity, access/cycling evidence, local metric separation, alignment, old exclusion and applicable thresholds. Baseline statuses and corridor results remain visible as historical evidence. Red means baseline held; it is not the new reviewed verdict. Choose private 77611523 to inspect its retained exclusion. Locate/zoom/layer controls and keyboard selection are supported; mobile controls have 44 px targets.

[The Deal resolution](../ncn-deal-resolution-20261031.md) establishes that the five held source edges represent the same NCN1 route as the publisher geometry. A narrowly pinned candidate adds **561.593 m route-local / 561.889 m network assessability** across two adjoining targets. No national tolerance changes, source identity transfer, physical reference expansion or real journey credit. The original 495.388 m focus target becomes fully assessable. This HTML does not apply the candidate or calculate eligibility.

The seven additional cases retain their original results and limitations: Newry NCN9, Wales NCN8, West Midlands NCN81, London NCN1, Bradford NCN66, Cumbria NCN6/7 and East Sussex RCN90. Unknown origins/statuses remain unknown; inferred origins remain labelled. These cases receive no new admission.

Generate and validate from the repository root:

```sh
python -m pip install -r requirements-ncn-deal.txt
PYTHONPATH=. python docs/ncn-viewer/generate.py
PYTHONPATH=.:tests python docs/ncn-viewer/test_viewer.py -q
node docs/ncn-viewer/browser-test.cjs
```

The browser checks require Playwright and Chromium. `NCN_CHROMIUM`, `NCN_CHROMIUM_ARGS_JSON` and `NCN_SCREENSHOTS` select a local binary, launch arguments and screenshot output. The contract retains schema v1 with additive `resolution_review` and `resolution_delta` fields; source coordinates, baseline statuses and original receipt fields are unchanged. Generator and browser renderer are separate. Full measurement records are embedded in the HTML and can be inspected beside the selected feature.

`validation.json` is the original #249 historical validation receipt, not a fresh claim for this increment. Current validation is in `resolution-validation.json` and the dedicated PR. The public hosted viewer remains unchanged; this task does not deploy it. Attribution: OpenStreetMap contributors (ODbL 1.0); published extent Walk Wheel Cycle Trust (OGL 3.0), with original attribution in pinned evidence. This is not surveyed navigation geometry.
