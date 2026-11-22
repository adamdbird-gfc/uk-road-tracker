const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
assert(html.includes('id="shadowRoads" aria-pressed="false">Pedestrian → road preview OFF'),'initial preview state is explicit');
assert(html.includes('#shadowRoads[aria-pressed="true"]'),'active preview styling visible');
assert(html.includes("button.textContent='Pedestrian → road preview '+(shadowRoads?'ON':'OFF')"),'toggle updates visible button text');
assert(html.includes("const m=cache.get(step-1),count=m?.pedestrianRoadPreview?.additions?.size??0"),'proposal count obtained from current journey');
assert(html.includes("' additional road intervals proposed (not credited)'"),'status clarifies read-only output');
assert(html.includes("shadowRoads=!shadowRoads"),'button toggles preview state');
assert(html.includes("stroke(proposals,'#00bcd4',3.8)"),'preview remains wired to drawing');
console.log('PASS: pedestrian road preview toggle gives visible ON/OFF feedback and proposal count');
