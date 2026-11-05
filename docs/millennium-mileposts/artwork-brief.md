# Artwork and animation production brief

Deliverable is a production brief only; no finished artwork is claimed. All six names and seven achievement titles remain exactly as in the authoritative specification. Themes are layers, not extra badges. No poetry, stanzas or historical text embedded in images.

## Asset inventory and rights

| Asset | Required output | Acceptance |
| --- | --- | --- |
| Six personalities | Original head-and-shoulders silhouettes/line illustrations: Mills, Rowe, McColl, Dudgeon, Grimshaw, Harrow | Recognisable from identity-checked permitted references; consistent approximately 2px stroke at 48px design size, scalable. Anonymous silhouettes for locked cards do not reveal names |
| Four post designs plus unknown | Distinctive physical milepost-shaped map outlines, simplified at 24/32/48dp; monochrome vector masters | Derived from verified permitted reference; correct design association. Unknown neutral post outline must not imply a particular artist |
| Six Harrow layers | Separate transparent vector masters/exportable Android-compatible vectors and optional bounded raster textures | Shared 512×512 coordinate box, central identity mask, fixed z-order, each usable alone and in every prefix |
| Harrow final | Separate portrait silhouette, name and biography rendered as accessible native text | No flattening text into art; no identity leakage before seventh discovery |
| Parent/mystery | In Honour of Time symbol, initial `?`, anonymous locked silhouettes | Parent hidden until first individual unlock; six cards initially spoiler-free |

For each asset supply source creator, permitted reference URLs, licence/permission record, version/hash, normal/high-contrast previews, light/dark previews and Android export. Reference photos and official disc images are not automatically reusable. No verified reference set for all six personalities is currently available: final likeness work is a content gate. Do not generate guessed portraits and label them historical likenesses.

## Layer composition

Name assets `harrow-layer-01-seasons` through `harrow-layer-06-final-mystery`; art-version 1. Fixed order back-to-front. Give each a reserved ring/panel so later layers do not hide earlier themes. The central opaque identity veil is separate and remains through stage six. The cipher is native text outside the art, not baked into layers.

| Stage | Theme | Original visual direction |
| --- | --- | --- |
| 1 | Seasons | Four abstract seasonal fields/leaf forms; no copied disc artwork |
| 2 | Continents | Abstract connected land/sea shapes; no geographic collection requirement implied |
| 3 | Ages of Man | Abstract progression of human life/time; inclusive and symbolic, no named historical faces |
| 4 | Zodiac | Abstract constellation ring; no astrology claims or gameplay dependency |
| 5 | Centuries | Twenty abstract time divisions; native accessible labels if needed, no historic puzzle solution |
| 6 | Final Mystery | Original geometric veil/locking motif; Roadprints interpretation, no claim of original series name |
| 7 | Final reveal | Remove all themed coverings and central veil; expose Harrow silhouette; reveal name/biography |

Stage 0 shows no layer or cipher, only `?`. Prefix states 1–6 must be meaningful as static images. The sixth stage must not expose the final portrait. Final removal must work from saved state without replaying earlier animations.

## Motion and accessibility

Default: user-controlled stage changes, 350–600ms gentle opacity/mask transition, at most 2 seconds for final reveal; no automatic looping, flashing, spinning or sound dependency. Resolved glyphs remain fixed. Show progress and Next/Skip/Later throughout. Save completed stages, not animation frame numbers. Pause immediately when recording starts or host loses foreground.

Respect Android animator/reduced-motion settings; offer an in-app static presentation option. Static swaps retain exactly the same information and controls. Announce “Discovery 3 of 7. Ages of Man layer revealed. Six letters resolved” rather than reading scrambled text repeatedly. At stage seven announce the actual name. Ensure 4.5:1 body text and 3:1 non-text/control boundaries against their background; orange/purple pins need contrasting outlines and status labels. Check 200% text scaling, TalkBack order, touch targets, dark map tiles and colour-vision variants.

Production review sheet must include all eight static states (0–7), all six layers separately, each silhouette, a mixed map cluster, large-text summary, static/reduced-motion reveal, paused replay and offline details. Artwork acceptance is visual/content QA, not permission to change earning rules.
