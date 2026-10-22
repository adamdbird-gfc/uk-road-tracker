"""Explicit two-gate topology policy for current NCN member roundabouts.

Only shared OSM node IDs form groups. Two node-connected arcs in a simple tagged
roundabout cycle are alternatives. General circuits/branching graphs never get
this policy merely because their endpoints or coordinates are close.
"""
from collections import defaultdict


def review_roundabouts(ways):
    remaining={w for w,e in ways.items() if e.get('tags',{}).get('junction') in ('roundabout','circular')}
    reviewed=[];held=[]
    while remaining:
        group={remaining.pop()};nodes=set(ways[next(iter(group))]['nodes'])
        while True:
            neighbours={w for w in remaining if nodes.intersection(ways[w]['nodes'])}
            if not neighbours:break
            group.update(neighbours);remaining.difference_update(neighbours)
            nodes.update(n for w in neighbours for n in ways[w]['nodes'])
        graph=defaultdict(list);edge_sources={};invalid=False
        for wid in sorted(group):
            ns=ways[wid]['nodes']
            for i,(a,b) in enumerate(zip(ns,ns[1:])):
                if a==b:continue
                pair=tuple(sorted((a,b)))
                if pair in edge_sources:invalid=True
                edge_sources[pair]=(wid,i)
                graph[a].append(b);graph[b].append(a)
        outside={n for w,e in ways.items() if w not in group for n in e['nodes']}
        gates=nodes&outside;ends={n for n,v in graph.items() if len(v)==1}
        record={'way_ids':sorted(group),'gate_node_ids':sorted(gates)}
        if not invalid and len(gates)==2 and ends==gates and all(len(v)<=2 for v in graph.values()):
            reviewed.append({**record,'policy':'linear_member_arc','branches':[]});continue
        if invalid or len(gates)!=2 or not graph or any(len(v)!=2 for v in graph.values()):
            held.append({**record,'reason':'not_a_simple_two_gate_roundabout'});continue
        start,finish=sorted(gates);branches=[]
        for neighbour in sorted(graph[start]):
            previous=start;current=neighbour;steps=[];visited={start}
            while True:
                wid,i=edge_sources[tuple(sorted((previous,current)))];ns=ways[wid]['nodes']
                steps.append({'way_id':wid,'edge_index':i,'forward':ns[i]==previous})
                if current==finish:break
                if current in visited:raise ValueError('Unexpected repeated node in reviewed simple cycle')
                visited.add(current)
                following=next(n for n in graph[current] if n!=previous)
                previous,current=current,following
            branches.append(steps)
        reviewed.append({**record,'policy':'either_two_gate_arc','branches':branches})
    return {'reviewed':reviewed,'held':held}
