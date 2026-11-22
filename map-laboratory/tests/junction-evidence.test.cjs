// Run with: node map-laboratory/tests/junction-evidence.test.cjs
// Synthetic geometry only; no owner locations or private archive.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=html.indexOf('function prepareEvidence(j){');
const end=html.indexOf('function match(j){',start);
assert.ok(start>0&&end>start,'junction confidence helper exists');
const code=html.slice(start,end);
const M=111320,D=Math.PI/180;
const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*.5*D),(a[1]-b[1])*M);
const xyz=(a,o)=>[(a[0]-o[0])*M*Math.cos(o[1]*D),(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1];if(d<.001)return 1e9;const t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const prepare=new Function('metres','xyz','proj',code+';return prepareEvidence')(metres,xyz,proj);
const p=(x,y=0)=>[x/M,y/M],run=(a)=>prepare([null,null,null,[a]]);
// A micro endpoint leg at right angles to the onward road is not credit evidence.
const endpoint=run([p(0),p(0,6),p(-40,6),p(-70,6)]);
assert.equal(endpoint.endpointHooks,1);
assert.equal(endpoint.lines[0].length,3);
// An independent short connector at a right angle is preserved in the middle.
const connector=run([p(0),p(40),p(40,14),p(80,14)]);
assert.equal(connector.endpointHooks,0);
assert.equal(connector.lines[0].length,4);
// An authentic short dead end without parallel earlier evidence remains credited.
const deadEnd=run([p(0),p(40),p(65),p(40),p(0)]);
assert.equal(deadEnd.redundantReturns,0);
assert.equal(deadEnd.lines[0].length,5);
// A short there-and-back on a corridor already traversed elsewhere can be excluded.
const redundant=run([p(0),p(65),p(30),p(45),p(30),p(0)]);
assert.equal(redundant.redundantReturns,1);
assert.equal(redundant.lines[0].length,5);
console.log('PASS: endpoint jitter, short connector, genuine dead end, redundant return');
