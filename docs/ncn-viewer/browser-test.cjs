/* Run with Node and Playwright. Optional NCN_CHROMIUM points to an installed browser. */
const assert=require('node:assert/strict');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const fs=require('node:fs');
const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.NCN_CHROMIUM?{executablePath:process.env.NCN_CHROMIUM}:{}),args:JSON.parse(process.env.NCN_CHROMIUM_ARGS_JSON||'["--no-sandbox"]')});
 const context=await browser.newContext({viewport:{width:1440,height:1080},offline:true});
 const page=await context.newPage(),errors=[],requests=[];let checks=0;
 const check=(value,message)=>{assert(value,message);checks++};
 page.on('pageerror',e=>errors.push(e.message));
 page.on('request',r=>{if(!r.url().startsWith('file:'))requests.push(r.url())});
 await context.route(/^https?:/,r=>r.abort());
 await page.goto(pathToFileURL(path.join(__dirname,'demo/index.html')).href);
 await page.waitForFunction(()=>window.ncnViewer);
 check(await page.locator('#case option').count()===8,'Eight cases');
 check(await page.locator('#timeline rect').count()===13,'All thirteen source intervals');
 check((await page.locator('#targetInfo').textContent()).includes('49.590 m'),'Pinned target coverage shown numerically');
 check((await page.locator('#explanation').textContent()).includes('Reviewed decision: ADMIT'),'Plain-English reviewed decision');
 check(await page.locator('.metriccard .result').nth(0).textContent()==='FAIL · corridor geometry only','Native corridor fail');
 check(await page.locator('.metriccard .result').nth(1).textContent()==='PASS · corridor geometry only','Completion hypothetical pass');
 for(const edge of [6,7,8,9,10]){
   await page.locator(`#timeline rect[data-feature="4471747:edge:${edge}"]`).click();
   check(await page.locator('#section').inputValue()===`4471747:edge:${edge}`,'Timeline -> selection '+edge);
   check(await page.locator(`#map path[data-feature="4471747:edge:${edge}"]`).getAttribute('data-selected')==='true','Timeline -> map '+edge);
   check((await page.locator('#operational').textContent()).includes('five projection-dependent'),'All five historical flip labels');
   const details=await page.locator('#resolution').textContent();
   for(const fact of ['Admit','75880','designated','WGS84','10 m / angle 15°','unresolved_official_extent_edges','not integrated or published'])check(details.includes(fact),'Reviewed selected detail '+edge+' / '+fact);
 }
 await page.selectOption('#section','4471747:edge:7');
 await page.click('#locate');
 const camera1=await page.evaluate(()=>window.ncnViewer.state.camera);
 await page.click('#zoomIn');
 check((await page.evaluate(()=>window.ncnViewer.state.camera.scale))>camera1.scale,'Zoom in');
 await page.click('#zoomOut');
 check(Math.abs((await page.evaluate(()=>window.ncnViewer.state.camera.scale))-camera1.scale)<1e-9,'Zoom out');
 const layers=await page.locator('#layers input').count();
 for(let i=0;i<layers;i++){
   const input=page.locator('#layers input').nth(i),layer=await input.getAttribute('data-layer');
   await input.uncheck();check(await page.locator(`#map path.geometry[data-layer="${layer}"]`).count()===0,'Toggle hides '+layer);
   await input.check();check(await page.locator(`#map path.geometry[data-layer="${layer}"]`).count()>0,'Toggle restores '+layer);
 }
 // Click a neighbouring source edge without moving the camera, then return by a real map click.
 await page.selectOption('#section','4471747:edge:8');await page.click('#locate');
 const points=await page.evaluate(()=>{
   const c=window.ncnViewer.state.camera,o=window.ncnViewer.data.cases[0].features[0].geometry.coordinates[0],rad=Math.PI/180,R=6371008.8;
   function point(edge,t){const f=window.ncnViewer.data.cases[0].features.find(f=>f.id==='4471747:edge:'+edge),p=f.geometry.coordinates.map(p=>[(p[0]-o[0])*rad*R*Math.cos(o[1]*rad),-(p[1]-o[1])*rad*R]);return {x:(p[0][0]+(p[1][0]-p[0][0])*t-c.x)*c.scale+320,y:(p[0][1]+(p[1][1]-p[0][1])*t-c.y)*c.scale+280};}
   return [point(7,.95),point(8,.5)];
 });
 const box=await page.locator('#map').boundingBox();
 for(const [index,point] of points.entries()){
   await page.mouse.click(box.x+point.x/640*box.width,box.y+point.y/560*box.height);
   const edge=index===0?7:8;
   check(await page.locator('#section').inputValue()==='4471747:edge:'+edge,'Real map click changes selection to '+edge);
   check(await page.locator(`#timeline rect[data-feature="4471747:edge:${edge}"]`).getAttribute('aria-pressed')==='true','Map -> timeline '+edge);
 }
 const previous=await page.evaluate(()=>window.ncnViewer.state.camera);
 await page.mouse.move(box.x+60,box.y+60);await page.mouse.down();await page.mouse.move(box.x+120,box.y+100);await page.mouse.up();
 check((await page.evaluate(()=>window.ncnViewer.state.camera.x))!==previous.x,'Pan');
 await page.selectOption('#section','77611523');
 check((await page.locator('#resolution').textContent()).includes('Exclude'),'Private reviewed verdict');
 check((await page.locator('#explanation').textContent()).includes('different private residential road'),'Private identity explanation');
 check((await page.locator('#operational').textContent()).includes('EXCLUDED'),'Private remains excluded');
 for(const key of await page.locator('#case option').evaluateAll(os=>os.map(o=>o.value))){
   await page.selectOption('#case',key);
   check(await page.locator('#map path.geometry').count()>0,'Map renders '+key);
   check((await page.locator('#explanation').textContent()).length>20,'Explanation exists '+key);
   check(await page.locator('#timeline rect').count()>0,'Timeline renders '+key);
 }
 await page.selectOption('#case','159311690');
 check((await page.locator('#operational').textContent()).includes('origin unavailable'),'Missing origin labelled');
 await page.selectOption('#case','59306268');
 check((await page.locator('#operational').textContent()).includes('inferred'),'Inferred origin labelled');
 await page.selectOption('#case','1289002662');
 check((await page.locator('#targetInfo').textContent()).includes('target unavailable'),'Unavailable target labelled');
 await page.selectOption('#case','deal');
 // Timeline keyboard navigation also selects and locates.
 await page.locator('#timeline rect[data-feature="4471747:edge:11"]').focus();await page.keyboard.press('Enter');
 check(await page.locator('#section').inputValue()==='4471747:edge:11','Keyboard source selection');
 await page.selectOption('#section','4471747:edge:7');await page.click('#locate');
 const out=process.env.NCN_SCREENSHOTS||path.join(require('node:os').tmpdir(),'roadprints-ncn-viewer-screenshots');fs.mkdirSync(out,{recursive:true});
 await page.screenshot({path:path.join(out,'deal-selected.png'),fullPage:true});
 await page.click('#closeup');
 await page.screenshot({path:path.join(out,'deal-closeup.png'),fullPage:true});
 const beforeWheel=await page.evaluate(()=>window.ncnViewer.state.camera.scale);
 await page.locator('#map').hover();await page.mouse.wheel(0,-100);await page.waitForTimeout(100);
 check((await page.evaluate(()=>window.ncnViewer.state.camera.scale))>beforeWheel,'Wheel zoom');
 await page.setViewportSize({width:390,height:844});await page.click('#overview');
 check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile no horizontal overflow');
 await page.screenshot({path:path.join(out,'deal-mobile.png'),fullPage:true});
 check(errors.length===0,'No browser exceptions: '+errors.join(';'));
 check(requests.length===0,'No external network requests: '+requests.join(';'));
 console.log(JSON.stringify({browser:await browser.version(),checks,offline:true,externalRequests:requests.length,pageErrors:errors,screenshots:out},null,2));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
