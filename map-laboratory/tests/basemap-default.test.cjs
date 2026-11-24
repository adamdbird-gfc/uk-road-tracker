const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
assert(page.includes('backgroundTiles.addTo(map);L.control.zoom('),'street basemap added automatically on Lab startup');
assert(!page.includes('id="zipBasemap"')&&!page.includes("$('zipBasemap')"),'unnecessary toggle fully removed');
assert(!page.includes('backgroundTiles.remove()'),'importing ZIP must not hide basemap');
assert(page.includes("for(const id of ['routeFirst','canonicalMode','dateForm','coverageAuditPanel'])$(id).hidden=!zip;"),'ZIP mode controls remain available');
assert(page.includes('LAB r449')&&page.includes('>PR #449</a>'),'release marker accurate');
console.log('PASS: street basemap automatically visible in legacy and ZIP modes without redundant toggle');
