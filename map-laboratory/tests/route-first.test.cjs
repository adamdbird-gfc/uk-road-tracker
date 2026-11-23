const assert = require('node:assert/strict');
const {test} = require('node:test');
const {reconstruct} = require('../route-first.js');

const at = (x,y=0) => [x/111320,y/111320];
const line = coordinates => ({type:'LineString',coordinates});
const feature = (coordinates,kind) => ({geometry:line(coordinates),properties:{reference_kind:kind}});
const fc = (...features) => ({type:'FeatureCollection',features});
const journey = (mode, recorded, matches, refs=[]) => ({
  mode,route_geometry:line(recorded),
  processing_result:{geojson:fc(...matches.map(coords=>feature(coords))),road_geojson:fc(...refs)}
});

test('sparse walking evidence uses the ordered saved match, retaining a square crossing and losing unrelated branches', () => {
  const walked = [at(0),at(10,3),at(20,3),at(30),at(35,8),at(40,15),at(50,15),at(60,15)];
  const input = journey('walking', [walked[0],walked.at(-1)], [walked], [
    feature([at(0),at(10),at(20),at(30)],'residential'),
    feature([at(30),at(35,8),at(40,15)],'footway'),
    feature([at(40,15),at(40,4)],'footway'),
    feature([at(50,15),at(50,23)],'service')
  ]);
  const frozen = JSON.stringify(input);
  const result = reconstruct(input);
  assert.equal(result.source,'saved match');
  assert.equal(result.projected,1);
  assert.deepEqual(result.parts,[[at(0),at(10),at(20),...walked.slice(3)]]);
  assert.equal(JSON.stringify(input),frozen);
});

test('a gap needs an ordered trace and a reference-supported link', () => {
  const a=at(0), b=at(13), c=at(30), d=at(44);
  const input=journey('driving',[a,b,c,d],[[a,b],[c,d]],[feature([b,at(22),c],'residential')]);
  assert.equal(reconstruct(input).joins,1);
  assert.deepEqual(reconstruct(input).parts,[[a,b,at(22),c,d]]);
  input.route_geometry=line([a,b,at(20,20),c,d]);
  assert.deepEqual(reconstruct(input).parts,[[a,b],[c,d]]);
  input.route_geometry=line([a,b,c,d]);
  input.processing_result.road_geojson=fc(feature([b,c],'footway'));
  assert.equal(reconstruct(input).parts.length,2);
});

test('an explicit removal stays a break and a fully removed saved route stays empty', () => {
  const a=at(0), b=at(13), c=at(30), d=at(44);
  const input=journey('walking',[a,b,c,d],[[a,b,c,d]],[feature([b,c],'residential')]);
  input.journey_corrections={removed_matched_segments:[1]};
  assert.deepEqual(reconstruct(input).parts,[[a,b],[c,d]]);
  input.journey_corrections.removed_matched_segments=[0,1,2];
  assert.deepEqual(reconstruct(input).parts,[]);
});

test('driving, cycling and railway keep real turns, reversals and source component boundaries', () => {
  const a=at(10), b=at(22), c=at(22,12), d=at(44);
  const loop=[a,b,c,b,d];
  for (const mode of ['driving','cycling','train']) {
    const input=journey(mode,[a,d],[loop],[feature([a,b,d],'residential'),feature([b,c],'cycleway')]);
    assert.deepEqual(reconstruct(input).parts,[loop],mode);
  }
  const input=journey('train',[a,d],[[a,b],[c,d]],[feature([b,c],'footway')]);
  assert.deepEqual(reconstruct(input).parts,[[a,b],[c,d]]);
  delete input.processing_result;
  assert.deepEqual(reconstruct(input).parts,[[a,d]]);
  assert.equal(reconstruct(input).source,'recorded');
});

test('a branch that was independently traversed remains an ordered out-and-back', () => {
  const a=at(0), b=at(20), c=at(20,20), d=at(50);
  const walked=[a,b,c,b,d];
  const input=journey('walking',walked,[walked],[
    feature([a,b,d],'residential'),feature([b,c],'steps')
  ]);
  assert.deepEqual(reconstruct(input).parts,[walked]);
});
