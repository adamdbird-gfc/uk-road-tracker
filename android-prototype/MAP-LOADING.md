# Map loading and tab continuity

The overview remains alive beneath the other main tabs, including its camera,
decoded tile cache and displayed route data. Returning to Map uses CLEAR_TOP and
SINGLE_TOP; repeated tab changes do not accumulate map instances. Android may
still recreate the screen after process death or configuration changes. Existing
revision-keyed process/disk route caches and on-disk tiles cover those cases.
Saved journey or service evidence changes trigger a refresh. The visible map
remains available during that refresh. Settlement maps remain separate.

Loading uses a navy Roadprints card, gold indeterminate spinner, teal accent and
offline facts rotating every eight seconds. Timers stop while the screen is
hidden. The empty journey message is suppressed until loading succeeds. Errors
remove the spinner and expose a tap-to-retry message.

Fact references (checked 7 October 2026):
- M1 length, rounded to about 193 miles: https://nationalhighways.citizenspace.com/he/m1-junctions-13-to-16-smart-motorway/
- Preston Bypass, road numbering, M25 opening, sign designers and Roman roads: https://nationalhighways.co.uk/about-us/our-responsibilities/history-of-roads-and-national-highways/

Facts are paraphrased and bundled; loading never waits for a fact network request.
