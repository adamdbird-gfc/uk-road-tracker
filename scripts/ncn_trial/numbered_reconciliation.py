"""Semantic numbered-route leads; never approve a physical GIS crosswalk."""
from collections import defaultdict
import re


def uk_metadata_anchor(tags):
    return str(tags.get('cycle_network','')).lower().startswith(('uk:','gb:')) or any(
        name in str(tags.get('operator','')).lower() for name in ('sustrans','walk wheel cycle trust'))


def non_uk_namespace(tags):
    prefix=str(tags.get('cycle_network','')).split(':')[0]
    return ':' in str(tags.get('cycle_network','')) and bool(re.fullmatch('[A-Za-z]{2}',prefix)) \
        and prefix.upper() not in ('UK','GB')


def numbered_candidates(inventory, profiles, area_relation_ids):
    keys=[r['id'] for r in inventory['routes']]
    if len(keys)!=len(set(keys)):raise ValueError('Distinct official route keys required')
    known=set(keys);matches=defaultdict(list);held=[];seen=set()
    for profile in profiles:
        identity=profile['relation_id']
        if identity in seen:raise ValueError('Duplicate source profile')
        seen.add(identity)
        tags=profile['tags'];network=tags.get('network');ref=str(tags.get('ref','')).strip()
        key=f'{network.upper()}:{int(ref)}' if network in ('ncn','rcn') and re.fullmatch('[0-9]+',ref) and int(ref)>0 else None
        anchored=uk_metadata_anchor(tags);foreign=non_uk_namespace(tags)
        if key in known and not foreign and (identity in area_relation_ids or anchored):
            matches[key].append(profile)
        else:
            held.append({'relation_id':identity,'same_number_key':key,'source_tags':tags,
                         'reason':'explicit_non_uk_namespace' if foreign else
                                  'global_scope_unverified' if identity not in area_relation_ids and not anchored else
                                  'source_classification_or_number_differs'})
    routes=[{'official_route_key':key,'candidate_relation_ids':[p['relation_id'] for p in rows],
             'uk_metadata_anchor':any(uk_metadata_anchor(p['tags']) for p in rows),
             'source_way_count':len({w['way_id'] for p in rows for w in p['ways']}),
             'scope_or_geometry_approved':False} for key,rows in sorted(matches.items())]
    return {'official_numbered_route_keys':len(known),'candidate_numbered_keys':len(matches),
            'uk_metadata_anchored_keys':sum(r['uk_metadata_anchor'] for r in routes),
            'uk_metadata_anchored_source_way_ids':len({w['way_id'] for rows in matches.values() for p in rows if uk_metadata_anchor(p['tags']) for w in p['ways']}),
            'candidate_source_way_ids':len({w['way_id'] for rows in matches.values() for p in rows for w in p['ways']}),
            'metadata_unanchored_candidate_keys':[r['official_route_key'] for r in routes if not r['uk_metadata_anchor']],
            'unresolved_numbered_keys':sorted(known-set(matches)),
            'same_number_or_classification_leads_held':held,'routes':routes,
            'way_geometry_verified':False,'official_gis_association_approved':False,'completion_ready':False}
