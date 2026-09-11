(()=>{
  'use strict';

  const DATA_URL='collections/uk-motorway-services-v1.json?v=20260911-norton-canes-v1';
  const ENTITLEMENT_KEY='roadprints:collection-entitlements:v1';
  const LEDGER_KEY='roadprints:service-station-ledger:v1';
  const FUEL_MARK='⛽';
  const LAYER_LABEL='Motorway service stations';
  const CLUSTER_ZOOM_MAX=8;
  const CLUSTER_CELL_PX=56;
  let renderedLayer=null,servicesPromise=null,boundMap=null,lastContext=null,journeyFocusActive=false;

  const isUnlocked=()=>{
    try { return JSON.parse(localStorage.getItem(ENTITLEMENT_KEY)||'{}')['service-stations']===true; }
    catch (_) { return false; }
  };
  const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const visitedServiceIds=()=>{
    const ids=new Set();
    try {
      const ledger=JSON.parse(localStorage.getItem(LEDGER_KEY)||'{}');
      (ledger?.visits||[]).forEach(visit=>{if(visit?.serviceId)ids.add(visit.serviceId);});
    } catch (_) {}
    try {
      JSON.parse(localStorage.getItem('roadprints:service-station-manual-visits:v1')||'[]').forEach(id=>{if(typeof id==='string')ids.add(id);});
    } catch (_) {}
    return ids;
  };
  async function loadServices(){
    if(!servicesPromise) {
      servicesPromise=fetch(DATA_URL).then(response=>{
        if(!response.ok) throw new Error('Service-station catalogue could not be loaded.');
        return response.json();
      }).then(data=>Array.isArray(data?.services)?data.services:[]);
    }
    return servicesPromise;
  }
  function pinIcon(visited=false){
    return L.divIcon({
      className:'roadprints-service-station-icon'+(visited?' is-visited':''),
      html:'<span class="roadprints-service-station-pin" aria-hidden="true"><span>'+FUEL_MARK+'</span></span>',
      iconSize:[31,31],iconAnchor:[15,30],popupAnchor:[0,-28]
    });
  }
  function clusterIcon(count,visitedCount){
    return L.divIcon({
      className:'roadprints-service-station-cluster'+(visitedCount?' has-visited':''),
      html:'<span aria-label="'+count+' motorway service stations">'+count+'</span>',
      iconSize:[42,42],iconAnchor:[21,21]
    });
  }
  function hide(context){
    const layer=context?.serviceStationLayer;
    if(!layer)return;
    layer.clearLayers();
    context.map?.removeLayer(layer);
    context.mapLayerControl?.removeLayer(layer);
    renderedLayer=null;
  }
  function clusterServices(services,map){
    if(map.getZoom()>CLUSTER_ZOOM_MAX) return services.map(service=>[service]);
    const clusters=new Map();
    for(const service of services){
      const point=map.project([service.lat,service.lng],map.getZoom());
      const key=Math.floor(point.x/CLUSTER_CELL_PX)+':'+Math.floor(point.y/CLUSTER_CELL_PX);
      if(!clusters.has(key)) clusters.set(key,[]);
      clusters.get(key).push(service);
    }
    return [...clusters.values()];
  }
  function addSingleMarker(service,visited,layer){
    const name=escapeHtml(service.name||'Motorway service station');
    const operator=service.operator?'<p>'+escapeHtml(service.operator)+'</p>':'';
    L.marker([service.lat,service.lng],{
      icon:pinIcon(visited),
      title:(service.name||'Motorway service station')+(visited?' — visited':' — unvisited')
    }).bindPopup('<div class="service-station-popup"><h3>'+name+'</h3><p>'+(visited?'Confirmed Timeline visit':'Unvisited motorway service station')+'</p>'+operator+'</div>').addTo(layer);
  }
  function addClusterMarker(members,visitedIds,layer,map){
    const visitedCount=members.filter(service=>visitedIds.has(service.id)).length;
    const lat=members.reduce((sum,service)=>sum+service.lat,0)/members.length;
    const lng=members.reduce((sum,service)=>sum+service.lng,0)/members.length;
    const marker=L.marker([lat,lng],{icon:clusterIcon(members.length,visitedCount),title:members.length+' motorway service stations'});
    marker.on('click',()=>map.setView([lat,lng],Math.min(12,map.getZoom()+2)));
    marker.bindPopup('<div class="service-station-popup"><h3>'+members.length+' service stations</h3><p>'+visitedCount+' visited · tap to zoom in</p></div>');
    marker.addTo(layer);
  }
  function bindMap(context){
    if(boundMap===context.map) return;
    if(boundMap) boundMap.off('zoomend moveend',refreshForMap);
    boundMap=context.map;
    boundMap?.on('zoomend moveend',refreshForMap);
  }
  function refreshForMap(){
    if(lastContext && isUnlocked()) void render(lastContext,{force:true});
  }
  async function render(context,{force=false}={}){
    const layer=context?.serviceStationLayer;
    if(!layer||!window.L)return;
    lastContext=context;
    if(!isUnlocked()||journeyFocusActive){hide(context);return;}
    bindMap(context);
    context.mapLayerControl?.removeLayer(layer);
    context.mapLayerControl?.addOverlay(layer,LAYER_LABEL);
    if(!context.map?.hasLayer(layer))layer.addTo(context.map);
    if(renderedLayer===layer&&!force)return;
    try {
      const services=(await loadServices()).filter(service=>Number.isFinite(Number(service?.lat))&&Number.isFinite(Number(service?.lng))).map(service=>({...service,lat:Number(service.lat),lng:Number(service.lng)}));
      if((renderedLayer===layer&&!force)||!isUnlocked())return;
      layer.clearLayers();
      const visitedIds=visitedServiceIds();
      for(const members of clusterServices(services,context.map)){
        if(members.length===1) addSingleMarker(members[0],visitedIds.has(members[0].id),layer);
        else addClusterMarker(members,visitedIds,layer,context.map);
      }
      renderedLayer=layer;
    } catch(error) {
      console.warn('Roadprints service-station layer unavailable:',error);
    }
  }

  window.addEventListener('roadprints:map-ready',event=>render(event.detail));
  window.addEventListener('roadprints:collection-entitlement-change',event=>{
    if(event.detail?.collection==='service-stations') render(window.roadprintsMapContext,{force:true});
  });
  window.addEventListener('roadprints:service-station-ledger-updated',()=>render(window.roadprintsMapContext,{force:true}));
  window.addEventListener('roadprints:service-station-completion-updated',()=>render(window.roadprintsMapContext,{force:true}));
  window.addEventListener('roadprints:journey-focus-change',event=>{journeyFocusActive=event.detail?.active===true;render(window.roadprintsMapContext,{force:true});});
  if(window.roadprintsMapContext) render(window.roadprintsMapContext);
})();