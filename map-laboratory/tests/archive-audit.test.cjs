const assert = require('node:assert/strict');
const {test} = require('node:test');
const {assess, SegmentIndex, reviewQueue} = require('../tools/archive-audit.cjs');

const point = (x, y) => [x / 111320, y / 111320];
const edge = (x, y, u, v) => ({a: point(x, y), b: point(u, v)});
const coverage = (novel, repeat = [], accessLinks = []) => ({novel, repeat, accessLinks});
const route = {onFoot: true, sourceType: 'timeline_import'};

test('counts tiny detached claims, and recognises a preserved access link', () => {
 const road = edge(0, 0, 30, 0), nib = edge(35, 6, 36, 6);
 const history = new SegmentIndex();
 assert.equal(assess(route, coverage([nib], [road]), history).detached, 1);
 const linked = assess(route, coverage([nib], [road], [[road.b, nib.a]]), history);
 assert.equal(linked.components, 1);
 assert.equal(linked.detached, 0);
});

test('parallel geometry is a review candidate but an actual branch is not', () => {
 const history = new SegmentIndex();
 history.add(edge(0, 0, 100, 0));
 const offset = edge(10, 12, 60, 12), branch = edge(0, 0, 0, 35);
 assert.equal(assess(route, coverage([offset, branch]), history).parallel, 50);
});

test('review flags potential divided road crossings and recognises mapped ones', () => {
 const pinned = [0, 10].map((y, id) =>
  ({id, name: 'Example Road', kind: 'primary', coords: [point(-30, y), point(30, y)]}));
 const shortcut = edge(0, -5, 0, 15), history = new SegmentIndex();
 const testRoute = {...route, independentRoadReferences: pinned};
 assert.equal(assess(testRoute, coverage([shortcut]), history).crossingCandidates, 1);
 const crossed = {...testRoute, independentRoadReferences: [...pinned,
  {kind: 'footway', footway: 'crossing', coords: [shortcut.a, shortcut.b]}]};
 assert.equal(assess(crossed, coverage([shortcut]), history).crossingCandidates, 0);
});

test('keeps held-back groups separate from named regression cases', () => {
 const row = (zip, group, detached = 0) =>
  ({zip, group, mode: 'walking', pinnedWays: 3, crossingCandidates: 0, detached, parallel: 0});
 const result = reviewQueue([row(1, 1), row(2, 1, 10),
  row(3, 2, 5), row(4, 2, 4), row(5, 3)], [1]);
 assert.deepEqual(result.flagged.map(item => item.zip), [3]);
 assert.deepEqual(result.controls.map(item => item.zip), [5]);
 assert.equal(result.excludedGroups, 1);
});
