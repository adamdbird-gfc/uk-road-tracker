(()=>{'use strict';
const DATA_URL='collections/uk-motorway-services-v1.json?v=20260911-norton-canes-v1',ENTITLEMENT_KEY='roadprints:collection-entitlements:v1';
const card=document.getElementById('serviceStationProgressCard'),list=document.getElementById('serviceStationProgressList'),count=document.getElementById('serviceStationProgressCount'),jump=document.querySelector('[data-service-station-jump]');
if(!card||!list||!count)return;
let loaded=false;
const isUnlocked=()=>{try{return JSON.parse(localStorage.getItem(ENTITLEMENT_KEY)||'{}')['service-stations']===true}catch{return false}};
const roadOrder=(a,b)=>a.localeCompare(b,undefined,{numeric:true,sensitivity:'base'});
const regionFor=station=>station?.region==='NI'?'NI':'GB';
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
    [...byRegion.get(region).entries()]
      .sort(([a],[b])=>roadOrder(a,b))
      .map(([road,items])=>[road,items.sort((a,b)=>a.name.localeCompare(b.name,undefined,{sensitivity:'base'}))])
  ]).filter(([,groups])=>groups.length);
}
function groupNode(road,stations){
  const section=document.createElement('section'),heading=document.createElement('h3'),ref=document.createElement('span'),total=document.createElement('small'),items=document.createElement('ul');
  section.className='service-station-road-group';ref.className='service-station-road-ref';ref.textContent=road;total.textContent=stations.length+' service area'+(stations.length===1?'':'s');heading.append(ref,total);items.className='service-station-checklist';
  stations.forEach(station=>{
    const item=document.createElement('li'),label=document.createElement('label'),checkbox=document.createElement('input'),name=document.createElement('span');
    label.className='service-station-check';checkbox.type='checkbox';checkbox.disabled=true;checkbox.setAttribute('aria-label',station.name+' — not yet visited');name.textContent=station.name;label.append(checkbox,name);item.append(label);items.append(item);
  });
  section.append(heading,items);return section;
}
function regionNode(region,groups){
  const section=document.createElement('section'),heading=document.createElement('h3'),note=document.createElement('span');
  section.className='service-station-region-group';heading.textContent=region==='NI'?'Northern Ireland':'Great Britain';note.textContent=region==='NI'?'Separate motorway network':'England, Scotland and Wales';section.append(heading,note,...groups.map(([road,stations])=>groupNode(road,stations)));return section;
}
function render(){
  if(!isUnlocked()){card.classList.add('hidden');jump?.setAttribute('hidden','');return;}
  card.classList.remove('hidden');jump?.removeAttribute('hidden');if(loaded)return;
  fetch(DATA_URL).then(response=>{if(!response.ok)throw new Error('Service-station catalogue unavailable');return response.json();}).then(data=>{
    const stations=Array.isArray(data?.services)?data.services:[],groups=groupStations(stations);
    count.textContent='0 of '+stations.length;list.replaceChildren(...groups.map(([region,roads])=>regionNode(region,roads)));loaded=true;
  }).catch(error=>{list.textContent='The service-station reference list could not be loaded.';console.warn('Roadprints service-station progress unavailable:',error);});
}
window.addEventListener('roadprints:collection-entitlement-change',event=>{if(event.detail?.collection==='service-stations')render();});render();
})();