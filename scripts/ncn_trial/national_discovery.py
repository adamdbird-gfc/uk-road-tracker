"""Bounded, adaptive discovery of top-level source routes, not membership credit.

A capped map response is subdivided rather than silently treated as complete.
Jurisdiction, nested members and official GIS correspondence require separate
verification. This module consumes a supplied read-only catalogue fetcher.
"""
from collections import deque
from concurrent.futures import ThreadPoolExecutor, wait, FIRST_COMPLETED
import math


def discover_routes(fetch, bounds, limit=100, max_depth=5, max_requests=128, workers=3):
    if len(bounds)!=4 or not all(type(v) in (int,float) and math.isfinite(v) for v in bounds) \
            or bounds[0]>=bounds[2] or bounds[1]>=bounds[3]:
        raise ValueError('Finite, ordered query bounds required')
    if not all(type(v) is int and v>0 for v in (limit,max_requests,workers)) \
            or type(max_depth) is not int or max_depth<0:
        raise ValueError('Positive bounded request settings required')
    pending=deque([(tuple(bounds),0)]);active={};routes={};cells=[];conflicts=[];unsupported=[]
    attempted=0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        while pending or active:
            while pending and len(active)<workers and attempted<max_requests:
                cell,depth=pending.popleft();active[pool.submit(fetch,cell,limit)]=(cell,depth);attempted+=1
            if not active:break
            completed,_=wait(active,return_when=FIRST_COMPLETED)
            for future in completed:
                cell,depth=active.pop(future)
                record={'bounds':list(cell),'depth':depth}
                try:
                    value=future.result();rows=value['results']
                    if not isinstance(rows,list) or len(rows)>limit:raise ValueError('Invalid capped response')
                    for row in rows:
                        if not isinstance(row,dict) or type(row.get('id')) is not int or row['id']<=0:
                            raise ValueError('Invalid source identity')
                        if row.get('type')!='relation':unsupported.append(row);continue
                        key=row['id']
                        if key in routes and routes[key]!=row:conflicts.append({'id':key,'first':routes[key],'later':row})
                        routes.setdefault(key,row)
                    record['returned']=len(rows)
                    if len(rows)==limit:
                        if depth>=max_depth:record['unresolved']='response_cap_at_max_depth'
                        else:
                            x0,y0,x1,y1=cell;xm,ym=(x0+x1)/2,(y0+y1)/2
                            pending.extend((b,depth+1) for b in [(x0,y0,xm,ym),(xm,y0,x1,ym),(x0,ym,xm,y1),(xm,ym,x1,y1)])
                            record['subdivided']=True
                except Exception as error:
                    record['error']=str(error)
                cells.append(record)
    unresolved=[c for c in cells if c.get('unresolved') or c.get('error')]
    return {'schema_version':1,'scope':'top_level_route_discovery_in_query_extent_only',
            'bounds':list(bounds),'limit':limit,'requests':attempted,
            'routes':[routes[k] for k in sorted(routes)],
            'cells':sorted(cells,key=lambda c:(c['depth'],c['bounds'])),
            'unvisited_cells':[{'bounds':list(b),'depth':d} for b,d in pending],
            'conflicts':conflicts,'unsupported_objects':unsupported,
            'query_extent_discovery_complete':not unresolved and not pending and not conflicts,
            'country_membership_verified':False,'nested_members_complete':False,
            'association_approved':False,'completion_ready':False}
