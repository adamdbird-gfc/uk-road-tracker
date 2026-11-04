"""Extend the existing offline viewer's pinned examples with the national queue."""
import gzip,hashlib,json,math
from pathlib import Path
from collections import defaultdict
from urllib.request import urlopen
from shapely.geometry import LineString
from shapely.ops import transform
from pyproj import Transformer
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
def feature(coords,layer,label,**props):return dict(type='Feature',geometry=dict(type='LineString',coordinates=coords),properties=dict(layer=layer,label=label,**props))
def build():
 old=json.loads((HERE.parent/'demo/evidence.json').read_text());examples=[]
 stories={
 'deal':('Deal · designated cycleway','High confidence','The designated public NCN1 cycleway and the 561.889 m revision 4 correspondence improvement.','The old five-edge discrepancy has already been corrected; it is not additional national recovery.','The cycleway is the same identified route, not proof that each nearby way counts.','Accept the already reviewed correspondence; keep separate alternative approvals bounded.','Verify each separate road/path, public access, connectors and finite target span before approval.'),
 '23385711':('Cumbria · route identity control','Unresolved','An existing physical way whose bounded relation evidence says NCN7.','The historical investigation concerned NCN6; source identity does not establish NCN6 entitlement.','The same physical feature could carry several identities, but that requires explicit evidence.','Do not alias NCN6 and NCN7 from proximity.','Route-specific relation/extent evidence and target correspondence.'),
 '1289002662':('East Sussex · changed OSM nodes','Unresolved','A retained source whose node coordinates changed after the walking import.','No bound target interval is supplied for this source; no missing metres are quantified here.','The old and new shapes represent the same node chain; this is a version issue.','Handle as source compatibility, not a parallel-corridor grant.','A finite eligible target mapping and original import metadata.'),
 '1050757473':('London · admitted prefix boundary','Requires review','Only the pinned finite source section of Mudlarks Boulevard.','The excluded prefix cannot inherit admission from whole-way identity.','An adjoining piece of the same way may look continuous on a map.','Finite spans, access and connectivity must be verified; do not extend by ID alone.','Exact excluded-prefix identity/access and target anchors.'),
 '59306268':('Newry · towpath positive control','High confidence','The existing admitted Newry Canal Towpath geometry and bounded NCN9 membership.','Historical ordinary-seam evidence is already accounted for in revision 4.','This is an unchanged same-feature positive control, not a newly approved parallel path.','Preserve existing admitted geometry; no further recovery inferred.','A distinct alternative and mapped connectors would be needed for corridor equivalence.'),
 '31252441':('Mid-Wales · on-road positive control','High confidence','Existing admitted rural NCN8 road geometry.','Historical seam capacity is already included; source and reference shapes agree.','No separate alternative has been established by this control.','Keep this as a positive geometry/provenance control.','Identify a distinct public parallel way before claiming new equivalence.'),
 '44807950':('Wolverhampton · hairpin negative control','Rejected','A source next to target return arms with ambiguous finite correspondence.','A nearby arm is not proof of the same section.','Close parallel geometry can refer to the opposite side of a hairpin.','Reject automatic whole-way equivalence; preserve the existing hold.','Unambiguous ordered entry/exit anchors and route-specific direction.'),
 '159311690':('Bradford · direction discrepancy','Requires review','An admitted NCN66 source with exact retained geometry.','Leading alignment angles were about 16.6°, outside the existing 15° gate.','A representation or finite-alignment issue is plausible, but separate infrastructure is unproved.','Review finite spans; do not widen the national gate to recover it.','Local geometry, endpoints, access and direction evidence.')}
 for c in old['cases']:
  name,cls,recognises,missing,alternative,decision,needed=stories[c['key']];fs=[]
  for f in c['features']:
   if f['geometry']['type']!='LineString':continue
   p=f['properties'];layer=p['layer'];newlayer={'target':'designated','focus_target':'designated','recognised':'assessable','reference':'assessable','admitted':'assessable','excluded':'rejected','negative':'rejected'}.get(layer)
   if c['key']=='deal' and 'edge_index' in p:newlayer='assessable' # revision 4 exact approved edges; historical excluded layer is stale.
   if newlayer:
    fs.append(feature(f['geometry']['coordinates'],newlayer,f['id'],source_record=p['source_reference'],historical_status=p['status'],evidence=p.get('evidence',p.get('resolution_review',{})),source_geometry_sha256=p['geometry_sha256']))
  if c['key']!='deal':
   # Whole source is NOT asserted as current assessable where no per-edge replay exists.
   for f in fs:
    if f['properties']['layer']=='assessable':f['properties']['layer']='candidate';f['properties']['status_note']='Pinned source/reference context; exact current assessable subspans unavailable.'
  loc=fs[0]['geometry']['coordinates'][0];examples.append(dict(id='REF-'+c['key'],name=name,route=c['route'],classification=cls,location=loc,features=fs,recognises=recognises,missing=missing,alternative=alternative,decision=decision,needed=needed,provenance='Existing pinned offline viewer evidence; no new whole-way eligibility asserted. '+c['key']))
 # A separate private-road control, with current bounded OSM observations.
 context=json.loads((HERE/'deal-osm-context.json').read_text());nodes={n['id']:n for n in context['elements'] if n['type']=='node'};ways={str(w['id']):w for w in context['elements'] if w['type']=='way'}
 def coords(w):return [[nodes[n]['lon'],nodes[n]['lat']] for n in w['nodes']]
 private=ways['77611523'];cycle=ways['4471747'];fw=Transformer.from_crs(4326,32631,always_xy=True);a=transform(fw.transform,LineString(coords(cycle)));b=transform(fw.transform,LineString(coords(private)));dist=[b.interpolate(i/100,normalized=True).distance(a) for i in range(101)]
 private_measurements=dict(crs='EPSG:32631',length_m=b.length,min_separation_m=b.distance(a),sampled_max_separation_m=max(dist),shared_node_count=len(set(private['nodes'])&set(cycle['nodes'])),tags=private['tags'],version=private['version'],source='deal-osm-context.json',new_credit_m=0)
 examples.append(dict(id='NEG-DEAL-PRIVATE',name='Deal · Wellington Parade private road',route='NCN:1',classification='Rejected',location=coords(private)[len(coords(private))//2],features=[feature(coords(cycle),'designated','Designated cycleway',tags=cycle['tags']),feature(coords(cycle),'assessable','Reviewed cycleway'),feature(coords(private),'rejected','Private residential road',**private_measurements)],recognises='The designated cycleway; revision 4 includes the reviewed finite correction.',missing='This road is not an unassessable NCN section; no deficit is assigned to it.',alternative=f'Wellington Parade is {b.length:.1f} m long, close and parallel, but explicitly access=private.',decision='Reject. One shared node and close alignment do not override private access or prove the same NCN route.',needed='Independent public permission, eligible activity access and a separately approved corridor mapping; none is established.',provenance='Bounded OSM context observed 4 November 2026; original import versions remain separate.'))
 rows=json.loads(gzip.decompress((HERE/'results/national-candidates.json.gz').read_bytes()));group=defaultdict(list)
 for r in rows:group[r['route'],r['source_way']].append(r)
 groups=sorted(group.values(),key=lambda g:(-sum(r['unassessable_m'] for r in g),g[0]['id']));chosen=[]
 # One substantial queue group, one Scottish, and one geographically distant remaining group.
 chosen.append(groups[0]);scot=[g for g in groups if g[0]['location'][1]>55.8 and g is not chosen[0]]
 if scot:chosen.append(scot[0])
 while len(chosen)<3:
  def score(g):
   x,y=g[0]['location'];return min(math.hypot((x-h[0]['location'][0])*math.cos(math.radians(y)),y-h[0]['location'][1]) for h in chosen)
  chosen.append(max((g for g in groups if g not in chosen),key=score))
 frozen=HERE/'selected-osm-ways.json';observations=json.loads(frozen.read_text()) if frozen.exists() else {}
 for g in chosen:
  r=g[0];wid=r['source_way']
  if wid not in observations:
   try:observations[wid]=json.loads(urlopen(f'https://api.openstreetmap.org/api/0.6/way/{wid}/full.json',timeout=15).read())
   except Exception as e:observations[wid]={'error':type(e).__name__,'missing_evidence':True}
 frozen.write_text(json.dumps(observations,indent=2)+'\n')
 selected=[]
 for i,g in enumerate(chosen):
  r=max(g,key=lambda r:r['unassessable_m']);wid=r['source_way'];obs=observations[wid];w=next((w for w in obs.get('elements',[]) if w['type']=='way'),{});tags=w.get('tags',{});total=sum(x['unassessable_m'] for x in g);fs=[]
  from collections import Counter
  groupcounts=dict(Counter(x['classification'] for x in g))
  for x in g:
   fs.extend([feature(x['target_coordinates'],'designated','Published target',target_key=x['target_key']),feature(x['gap_coordinates'],'gap','Unassessable span',candidate_id=x['id'],gap_m=x['unassessable_m']),feature(x['alternative_coordinates'],'candidate','Retained source alignment',**{k:v for k,v in x.items() if not k.endswith('coordinates')},observed_tags=tags)])
  # Finite admitted SOURCE geometry is distinct from assessable TARGET geometry.
  for x in g:
   if x.get('candidate_edge_within_admitted_section'):
    fs.append(feature(x['source_coordinates'],'assessable','Admitted finite source edge',source_way=x['source_way'],source_edge=x['source_edge'],status_note='Pinned source admission only; red target correspondence remains unassessable. This does not assert new completion.'))
  # No unsupported coverage is painted on the rest of these targets.
  from discover import subtract
  bytarget=defaultdict(list)
  for x in g:bytarget[x['target_key']].append(x)
  # Coverage not fully reconstructed here. Do not paint other gaps as admitted.
  reason='Live access restriction conflicts with automatic equivalence.' if tags.get('access') in ('private','no') else 'Retained route membership makes this a useful finite-span review, but does not prove a separate parallel infrastructure alternative.'
  name=tags.get('name',tags.get('highway','source alignment'))
  ex=dict(id=f'NAT-{i+1:02d}',name=f'{r["route"]} · {name}',route=r['route'],classification='Requires review',location=r['location'],features=fs,recognises='The pinned admitted source sections; the red target spans remain unassessable after exact revision 4 additions.',missing=f'{len(g)} residual pieces, gross route-local {total:.1f} m in this source-way review group ({groupcounts}); not a unique-network recovery estimate.',alternative=f'Way {wid}, observed highway={tags.get("highway","unknown")}; mean separation for the selected piece {r["separation_mean_m"]:.1f} m, five-point sampled maximum {r["separation_sampled_max_m"]:.1f} m.',decision=reason,needed='Confirm same corridor versus target generalisation, original access/source edition, both endpoint connectors, finite ordered correspondence and canonical network union.',provenance=f'National queue source way {wid}; frozen source geometry plus separately observed live OSM tags. Live geometry is context and does not replace the frozen source.');examples.append(ex);selected.append(dict(id=ex['id'],candidate_ids=[x['id'] for x in g],source_way=wid,observed_tags=tags,gross_route_local_m=total))
 data=dict(schema_version=1,baseline=BASE if False else '8c04b7eb1ac1bc8a6e8700ed19a14ba7ec34149c',revision=4,diagnostic_only=True,examples=examples,selected_national_groups=selected,private_measurements=private_measurements)
 (HERE/'review-data.json').write_text(json.dumps(data,indent=2)+'\n');template=(HERE/'viewer.template.html').read_text();js=(HERE/'vendor/leaflet.js').read_text().replace('//# sourceMappingURL=leaflet.js.map','');css=(HERE/'vendor/leaflet.css').read_text();html=template.replace('__LEAFLET_CSS__',css).replace('__LEAFLET_JS__',js).replace('__DATA__',json.dumps(data,separators=(',',':')).replace('<','\\u003c'));(HERE/'Roadprints_NCN_Corridor_Review.html').write_text(html)
 print(json.dumps({'examples':len(examples),'selected_national_groups':selected,'private':private_measurements},indent=2))
if __name__=='__main__':build()
