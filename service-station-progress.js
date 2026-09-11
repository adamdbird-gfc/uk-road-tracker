(()=>{
  'use strict';
  const DATA_URL='collections/uk-motorway-services-v1.json?v=20260911-norton-canes-v1';
  const ENTITLEMENT_KEY='roadprints:collection-entitlements:v1';
  const LEDGER_KEY='roadprints:service-station-ledger:v1';
  const MANUAL_KEY='roadprints:service-station-manual-visits:v1';
  const card=document.getElementById('serviceStationProgressCard');
  const list=document.getElementById('serviceStationProgressList');
  const count=document.getElementById('serviceStationProgressCount');
  const jump=document.querySelector('[data-service-station-jump]');
  const achievements=document.getElementById('serviceStationAchievements');
  if(!card||!list||!count)return;
  let stationsCache=null;

  const isUnlocked=()=>{try{return JSON.parse(localStorage.getItem(ENTITLEMENT_KEY)||'{}')['service-stations']===true}catch{return false}};
  const roadOrder=(a,b)=>a.localeCompare(b,undefined,{numeric:true,sensitivity:'base'});
  const regionFor=station=>station?.region==='NI'?'NI':'GB';
  const automaticIds=()=>{try{return new Set((JSON.parse(localStorage.getItem(LEDGER_KEY)||'{}')?.visits||[]).map(visit=>visit?.serviceId).filter(Boolean));}catch{return new Set;}};
  const manualIds=()=>{try{return new Set(JSON.parse(localStorage.getItem(MANUAL_KEY)||'[]').filter(id=>typeof id==='string'));}catch{return new Set;}};
  const completionIds=()=>new Set([...automaticIds(),...manualIds()]);
  function saveManualIds(ids){
    localStorage.setItem(MANUAL_KEY,JSON.stringify([...ids].sort()));
    window.dispatchEvent(new Event('roadprints:service-station-completion-updated'));
  }
  function groupStations(stations){
    const byRegion=new Map([['GB',new Map()],['NI',new Map()]]);
    stations.forEach(station=>{
      if(!station?.road||!station?.name)return;
      const roads=byRegion.get(regionFor(station));
      if(!roads.has(station.road))roads.set(station.road,[]);
      roads.get(station.road).push(station);
    });
    return ['GB','NI'].map(region=>[
      region,
      [...byRegion.get(region).entries()].sort(([a],[b])=>roadOrder(a,b))
        .map(([road,items])=>[road,items.sort((a,b)=>a.name.localeCompare(b.name,undefined,{sensitivity:'base'}))])
    ]).filter(([,groups])=>groups.length);
  }
  function groupNode(road,stations,automatic,manual){
    const section=document.createElement('section'),heading=document.createElement('h3'),ref=document.createElement('span'),total=document.createElement('small'),items=document.createElement('ul');
    section.className='service-station-road-group';ref.className='service-station-road-ref';ref.textContent=road;total.textContent=stations.length+' service area'+(stations.length===1?'':'s');heading.append(ref,total);items.className='service-station-checklist';
    stations.forEach(station=>{
      const item=document.createElement('li'),label=document.createElement('label'),checkbox=document.createElement('input'),name=document.createElement('span');
      const confirmed=automatic.has(station.id);
      label.className='service-station-check'+(confirmed?' timeline-confirmed':'');
      checkbox.type='checkbox';checkbox.checked=confirmed||manual.has(station.id);
      checkbox.disabled=confirmed;
      checkbox.setAttribute('aria-label',station.name+(confirmed?' — confirmed from Timeline':checkbox.checked?' — manually marked visited':' — mark visited manually'));
      checkbox.addEventListener('change',()=>{
        const next=manualIds();
        if(checkbox.checked)next.add(station.id);else next.delete(station.id);
        saveManualIds(next);render();
      });
      name.textContent=station.name;
      label.append(checkbox,name);
      if(confirmed){const tag=document.createElement('small');tag.textContent='Timeline';label.append(tag);}
      item.append(label);items.append(item);
    });
    section.append(heading,items);return section;
  }
  function regionNode(region,groups,automatic,manual){
    const section=document.createElement('section'),heading=document.createElement('h3'),note=document.createElement('span');
    section.className='service-station-region-group';heading.textContent=region==='NI'?'Northern Ireland':'Great Britain';note.textContent=region==='NI'?'Separate motorway network':'England, Scotland and Wales';
    section.append(heading,note,...groups.map(([road,stations])=>groupNode(road,stations,automatic,manual)));return section;
  }
  function renderAchievements(stations,complete){
    if(!achievements)return;
    const unlocked=isUnlocked();
    achievements.classList.toggle('hidden',!unlocked);
    if(!unlocked)return;
    const countNode=achievements.querySelector('.service-station-achievements-heading > strong');
    const rules={
      'first-stop':complete.size>=1,
      'ten-stops':complete.size>=10,
      'moneybags':complete.has('msa:norton-canes:52.6643:-1.9688'),
      'being-posh':complete.has('msa:peterborough:52.5314:-0.3215')
    };
    const unlockedCount=Object.values(rules).filter(Boolean).length;
    if(countNode)countNode.textContent=unlockedCount+' of '+Object.keys(rules).length;
    achievements.querySelectorAll('[data-service-achievement]').forEach(item=>{
      const id=item.dataset.serviceAchievement,earned=rules[id]===true;
      item.classList.toggle('is-unlocked',earned);
      const icon=item.querySelector('.service-station-achievement-icon');
      const status=item.querySelector('[data-achievement-status]');
      if(icon)icon.textContent=earned?'★':'🔒';
      if(status)status.textContent=earned?'Unlocked':'Locked';
    });
  }
  function render(){
    if(!isUnlocked()){card.classList.add('hidden');jump?.setAttribute('hidden','');renderAchievements([],new Set());return;}
    card.classList.remove('hidden');jump?.removeAttribute('hidden');
    if(!stationsCache){
      fetch(DATA_URL).then(response=>{if(!response.ok)throw new Error('Service-station catalogue unavailable');return response.json();}).then(data=>{
        stationsCache=Array.isArray(data?.services)?data.services:[];render();
      }).catch(error=>{list.textContent='The service-station reference list could not be loaded.';console.warn('Roadprints service-station progress unavailable:',error);});
      return;
    }
    const automatic=automaticIds(),manual=manualIds(),complete=completionIds(),groups=groupStations(stationsCache);
    count.textContent=complete.size+' of '+stationsCache.length;
    list.replaceChildren(...groups.map(([region,roads])=>regionNode(region,roads,automatic,manual)));
    renderAchievements(stationsCache,complete);
  }
  window.addEventListener('roadprints:collection-entitlement-change',event=>{if(event.detail?.collection==='service-stations')render();});
  window.addEventListener('roadprints:service-station-ledger-updated',render);
  window.addEventListener('roadprints:service-station-completion-updated',render);
  render();
})();