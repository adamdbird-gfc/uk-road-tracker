(() => {
  'use strict';

  const PROGRESS_KEY='uk-road-tracker-progress-v1';
  const ENTITLEMENT_KEY='roadprints:collection-entitlements:v1';
  const LEDGER_KEY='roadprints:service-station-ledger:v1';
  const SERVICES_URL='collections/uk-motorway-services-v1.json';
  const MATCH_RADIUS_M=350;
  const DUPLICATE_WINDOW_MS=45*60*1000;
  let cataloguePromise=null;

  const isUnlocked=()=>{
    try {
      return Boolean(JSON.parse(localStorage.getItem(ENTITLEMENT_KEY)||'{}')['service-stations']);
    } catch (_) {
      return false;
    }
  };

  const haversineMeters=(a,b)=>{
    const radians=Math.PI/180;
    const dLat=(b.lat-a.lat)*radians,dLng=(b.lng-a.lng)*radians;
    const x=Math.sin(dLat/2)**2+
      Math.cos(a.lat*radians)*Math.cos(b.lat*radians)*Math.sin(dLng/2)**2;
    return 6371000*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));
  };

  async function loadCatalogue(){
    if(!cataloguePromise){
      cataloguePromise=fetch(SERVICES_URL,{cache:'force-cache'})
        .then(response=>{
          if(!response.ok) throw new Error('Service catalogue unavailable');
          return response.json();
        })
        .then(data=>Array.isArray(data?.services)?data.services:[]);
    }
    return cataloguePromise;
  }

  function sourceVisits(){
    try {
      const saved=JSON.parse(localStorage.getItem(PROGRESS_KEY)||'{}');
      return Array.isArray(saved?.confirmedTimelineVisits)?saved.confirmedTimelineVisits:[];
    } catch (_) {
      return [];
    }
  }

  function normaliseVisit(visit){
    const lat=Number(visit?.lat),lng=Number(visit?.lng);
    const startMs=Date.parse(visit?.start||'');
    if(!Number.isFinite(lat)||!Number.isFinite(lng)||!Number.isFinite(startMs)) return null;
    return {
      id:String(visit.id||[visit.start||'',visit.end||'',lat.toFixed(5),lng.toFixed(5)].join('|')),
      start:visit.start,
      end:visit.end||null,
      startMs,
      lat,lng
    };
  }

  async function rebuild(){
    if(!isUnlocked()){
      localStorage.removeItem(LEDGER_KEY);
      window.dispatchEvent(new Event('roadprints:service-station-ledger-updated'));
      return;
    }
    const visits=sourceVisits().map(normaliseVisit).filter(Boolean).sort((a,b)=>a.startMs-b.startMs);
    let services=[];
    try { services=await loadCatalogue(); } catch (_) { return; }

    const candidates=[];
    for(const visit of visits){
      let match=null;
      for(const service of services){
        const distanceM=haversineMeters(visit,service);
        if(distanceM<=MATCH_RADIUS_M && (!match || distanceM<match.distanceM)){
          match={service,distanceM};
        }
      }
      if(match){
        candidates.push({
          id:`${match.service.id}|${visit.id}`,
          serviceId:match.service.id,
          serviceName:match.service.name,
          road:match.service.road||null,
          start:visit.start,
          end:visit.end,
          lat:visit.lat,lng:visit.lng,
          sourceVisitIds:[visit.id],
          matchDistanceM:Math.round(match.distanceM),
          isFirstVisit:false
        });
      }
    }

    const retained=[];
    const lastByService=new Map();
    for(const candidate of candidates){
      const candidateMs=Date.parse(candidate.start||'');
      const previous=lastByService.get(candidate.serviceId);
      const previousMs=previous ? Date.parse(previous.start||'') : NaN;
      if(previous && Number.isFinite(candidateMs) && Number.isFinite(previousMs) &&
        candidateMs-previousMs<=DUPLICATE_WINDOW_MS){
        previous.end=candidate.end||previous.end;
        previous.sourceVisitIds.push(...candidate.sourceVisitIds);
        previous.matchDistanceM=Math.min(previous.matchDistanceM,candidate.matchDistanceM);
        continue;
      }
      retained.push(candidate);
      lastByService.set(candidate.serviceId,candidate);
    }

    const seenServices=new Set();
    for(const entry of retained){
      entry.isFirstVisit=!seenServices.has(entry.serviceId);
      seenServices.add(entry.serviceId);
    }
    localStorage.setItem(LEDGER_KEY,JSON.stringify({
      version:1,
      generatedAt:new Date().toISOString(),
      sourceVisitCount:visits.length,
      visits:retained
    }));
    window.dispatchEvent(new Event('roadprints:service-station-ledger-updated'));
  }

  window.addEventListener('roadprints:collection-entitlement-change',event=>{
    if(event?.detail?.collection==='service-stations') void rebuild();
  });
  window.addEventListener('roadprints:confirmed-visits-updated',()=>void rebuild());
  window.addEventListener('storage',event=>{
    if(event.key===PROGRESS_KEY || event.key===ENTITLEMENT_KEY) void rebuild();
  });
  void rebuild();
})();
