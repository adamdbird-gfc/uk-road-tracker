const assert=require('node:assert/strict'),{test}=require('node:test');
const fs=require('node:fs'),path=require('node:path');
const Coverage=require('../canonical-coverage.js');
const p=(x,y=0)=>[x/111320,y/111320];
const ref=(kind,coords)=>({kind,coords});
const journey=(parts,refs)=>({parts,refs});
test('audit distinguishes missing pedestrian references from absence of movement',()=>{
 const result=Coverage.audit(journey([[p(0),p(20)]],[ref('residential',[p(0),p(20)])]));
 assert.equal(result.references.paths,0);
 assert.equal(result.outcomes['no-pedestrian-references'],1);
 assert.equal(result.referenceSlices>0,true);
});
test('audited road/path transfer retains eligible pedestrian reference slices',()=>{
 const r=journey([[p(0),p(20),p(20,16),p(40,16)]],[
  ref('residential',[p(0),p(20)]),ref('footway',[p(20),p(20,16)]),
  ref('residential',[p(20,16),p(40,16)])]);
 const original=JSON.stringify(r);
 const result=Coverage.audit(r);
 assert(result.intervals.some(x=>x.type==='path'&&x.outcome==='eligible'));
 assert.equal(result.intervals.length,result.referenceSlices);
 assert.equal(JSON.stringify(r),original,'private source is never mutated');
});
test('audit explains absent support rather than fabricating network credit',()=>{
 const result=Coverage.audit(journey([[p(0),p(20)]],[ref('footway',[p(100),p(100,20)])]));
 assert(result.intervals.every(x=>x.reason==='insufficient-source-overlap'));
 assert.equal(result.outcomes['insufficient-source-overlap'],result.referenceSlices);
});
test('held parallel roadside footway retains exact decision reason',()=>{
 const result=Coverage.audit(journey([[p(0,2),p(40,2)]],[
  ref('residential',[p(0),p(40)]),ref('footway',[p(0,4),p(40,4)])]));
 assert(result.intervals.some(x=>x.reason==='parallel-roadside-path'));
});
test('current journey only: no public-upload or persistent detailed report',()=>{
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(page.includes("CanonicalCoverage.audit(diagnosticRoute)"));
 assert(page.includes("currentCoverageAudit=null;$('coverageAuditSummary').textContent=''"));
 assert(page.includes("URL.createObjectURL(blob)")&&page.includes("link.download='roadprints-private-coverage-audit-journey-'"));
 assert(page.includes('id="coverageAuditPanel"')&&page.includes('id="runCoverageAudit"'));
 assert(!page.includes('fetch(url, {method:'));
 assert(page.includes('LAB r480')&&page.includes('>PR #480</a>'));
});
