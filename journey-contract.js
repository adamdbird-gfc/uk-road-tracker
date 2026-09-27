/* Roadprints shared Journey contract adapter.
 * This is intentionally additive: current web code continues to use its legacy
 * fields while the same record also exposes the mode-neutral contract needed
 * by Android and future processing stages.
 */
(function(){
  const ROAD_MODES=new Set(['ROAD','BUS','DRIVING']);
  const FOOT_MODES=new Set(['WALKING','RUNNING','IN_PEDESTRIAN','PEDESTRIAN']);

  function modeFor(value){
    const mode=String(value || 'UNKNOWN').toUpperCase();
    if (mode==='ROAD') return 'driving';
    if (mode==='BUS') return 'bus';
    if (mode==='IN_PEDESTRIAN') return 'pedestrian';
    if (mode==='WALKING') return 'walking';
    if (mode==='RUNNING') return 'running';
    if (mode==='CYCLING') return 'cycling';
    if (mode==='TRAIN') return 'train';
    if (mode==='FERRY') return 'ferry';
    if (mode==='FLIGHT') return 'flight';
    if (mode==='TRANSIT') return 'transit';
    return 'unknown';
  }

  function point(value){
    const lat=Number(value?.lat), lng=Number(value?.lng);
    return Number.isFinite(lat) && Number.isFinite(lng) ? {lat,lng} : null;
  }

  function likelyUK(points){
    return points.length>0 && points.every(({lat,lng}) =>
      lat>=49.8 && lat<=60.9 && lng>=-8.7 && lng<=1.9
    );
  }

  function processingFor(mode, record){
    const road=ROAD_MODES.has(String(record?.travelMode || '').toUpperCase());
    const foot=FOOT_MODES.has(String(record?.travelMode || '').toUpperCase());
    const matched=Boolean(record?.matchedGeoJson);
    const failed=Boolean(record?.matchError || record?.easyImportError);
    const state=matched ? 'complete' : failed ? 'failed_retryable' : 'pending';
    return {
      import:'complete',
      road_matching:road ? state : 'not_required',
      foot_matching:foot ? state : 'not_required',
      last_error:record?.matchError || record?.easyImportError || null,
      attempts:Number(record?.matchAttempts || 0)
    };
  }

  function fromLegacy(record, options={}){
    const points=(record?.points || []).map(point).filter(Boolean);
    const mode=modeFor(record?.travelMode);
    const journeyId=String(record?.journey_id || record?.id || record?.importId || '');
    const ukSupported=likelyUK(points);
    const routeGeometry=points.length>=2
      ? {type:'LineString',coordinates:points.map(({lat,lng})=>[lng,lat])}
      : null;
    return {
      ...record,
      journey_id:journeyId || null,
      revision:Number(record?.revision || 1),
      source:{
        type:options.sourceType || record?.source?.type || 'timeline',
        source_file_fingerprint:options.sourceFileFingerprint || record?.source?.source_file_fingerprint || null,
        source_fingerprint:record?.source?.source_fingerprint || record?.importId || journeyId || null
      },
      started_at:record?.started_at || record?.start || null,
      ended_at:record?.ended_at || record?.end || null,
      timezone:record?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/London',
      mode,
      route_geometry:record?.route_geometry || routeGeometry,
      // Legacy web records use start/end for timestamps; keep those fields
      // untouched and expose contract coordinates under explicit names.
      start_location:record?.startPoint || points[0] || null,
      end_location:record?.endPoint || points[points.length-1] || null,
      distance_meters:Number.isFinite(Number(record?.distance_meters))
        ? Number(record.distance_meters)
        : Number.isFinite(Number(record?.googleDistanceKm))
          ? Number(record.googleDistanceKm)*1000
          : null,
      stops:Array.isArray(record?.stops) ? record.stops : [],
      scope:{
        country:ukSupported ? 'GB' : 'OUTSIDE_GB_OR_UNKNOWN',
        uk_supported:ukSupported,
        visible_in_history:true,
        eligible_for_coverage:ukSupported && (ROAD_MODES.has(String(record?.travelMode || '').toUpperCase()) || FOOT_MODES.has(String(record?.travelMode || '').toUpperCase()))
      },
      processing:record?.processing || processingFor(mode,record),
      derived:{
        road_matches:record?.derived?.road_matches || [],
        coverage_contributions:record?.derived?.coverage_contributions || [],
        service_visits:record?.derived?.service_visits || [],
        achievement_evidence:record?.derived?.achievement_evidence || []
      },
      corrections:Array.isArray(record?.corrections) ? record.corrections : [],
      created_at:record?.created_at || new Date().toISOString(),
      updated_at:new Date().toISOString()
    };
  }

  function archiveFields(record){
    const journey=fromLegacy(record);
    return {
      journey_id:journey.journey_id,
      revision:journey.revision,
      source:journey.source,
      started_at:journey.started_at,
      ended_at:journey.ended_at,
      timezone:journey.timezone,
      mode:journey.mode,
      route_geometry:journey.route_geometry,
      start_location:journey.start_location,
      end_location:journey.end_location,
      distance_meters:journey.distance_meters,
      stops:journey.stops,
      scope:journey.scope,
      processing:journey.processing,
      corrections:journey.corrections,
      updated_at:journey.updated_at
    };
  }

  window.RoadprintsJourneyContract={fromLegacy,modeFor,archiveFields};
})();