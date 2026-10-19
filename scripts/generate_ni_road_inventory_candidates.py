#!/usr/bin/env python3
"""Generate all NI candidates and quantify the effects of official overlaps."""
import argparse
from collections import Counter, defaultdict
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import re

import osmium
from shapely.geometry import LineString
from shapely.ops import unary_union
from shapely.strtree import STRtree

from bulk_settlement_inventory_trial import HIGHWAYS, digest
from ni_settlement_inventory_trial import generate


class InventoryEvidence:
    def __init__(self, accumulator, codes, overlap_rows):
        self.accumulator = accumulator
        self.codes = codes
        self.by_code = {code:i for i,code in enumerate(codes)}
        self.counters = [Counter() for _ in codes]
        self.excluded_samples = [defaultdict(set) for _ in codes]
        self.numeric_names = [set() for _ in codes]
        self.overlaps, self.overlap_geometries = [], []
        for row in overlap_rows:
            a,b = [self.by_code[c] for c in row['codes']]
            g = accumulator.boundaries[a].intersection(accumulator.boundaries[b])
            if g.is_empty or g.area <= 0:
                raise ValueError('Reviewed source overlap is absent')
            self.overlap_geometries.append(g)
            self.overlaps.append(dict(row, eligible_named_ways=[], excluded_named_ways=[]))
        self.overlap_tree = STRtree(self.overlap_geometries)
        cuts = defaultdict(list)
        for row,g in zip(overlap_rows,self.overlap_geometries):
            for code in row['codes']:
                cuts[self.by_code[code]].append(g)
        self.outside_overlap = {i:accumulator.boundaries[i].difference(unary_union(gs)) for i,gs in cuts.items()}
        self.independent_names = {i:set() for i in self.outside_overlap}

    def observe(self, highway, name, coordinates, way_id=None):
        name = (name or '').strip()
        if not highway or len(coordinates)<2:
            return
        line = LineString(coordinates)
        eligible = highway in HIGHWAYS
        for position in self.accumulator.tree.query(line,predicate='intersects'):
            i=int(position)
            category = ('eligible_' if eligible else 'excluded_') + ('named' if name else 'unnamed')
            self.counters[i][category] += 1
            self.counters[i]['class:'+highway] += 1
            if not eligible and name and len(self.excluded_samples[i][highway])<5:
                self.excluded_samples[i][highway].add(name)
            if eligible and name:
                if re.match(r'^\d',name):self.numeric_names[i].add(name)
                if i in self.outside_overlap and self.outside_overlap[i].intersects(line):
                    self.independent_names[i].add(name)
        if name:
            for position in self.overlap_tree.query(line,predicate='intersects'):
                j=int(position)
                # Shared-boundary points alone are not evidence of a road within the overlap.
                if line.intersection(self.overlap_geometries[j]).length <= 0:
                    continue
                key = 'eligible_named_ways' if eligible else 'excluded_named_ways'
                self.overlaps[j][key].append({'way_id':way_id,'name':name,'highway':highway})

    def report(self):
        details = []
        for i,code in enumerate(self.codes):
            c=self.counters[i]
            details.append({'code':code,'eligible_named_ways':c['eligible_named'],
                            'eligible_unnamed_ways':c['eligible_unnamed'],
                            'excluded_named_ways':c['excluded_named'],
                            'excluded_unnamed_ways':c['excluded_unnamed'],
                            'highway_way_counts':{k[6:]:v for k,v in sorted(c.items()) if k.startswith('class:')},
                            'excluded_named_samples':{k:sorted(v) for k,v in sorted(self.excluded_samples[i].items())},
                            'numeric_prefix_names_for_review':sorted(self.numeric_names[i])})
        sensitivity = []
        for i in sorted(self.outside_overlap):
            names=self.accumulator.names[i]
            sensitivity.append({'code':self.codes[i], 'candidate_count':len(names),
                                'count_without_any_source_overlap':len(self.independent_names[i]),
                                'names_relying_on_source_overlap':sorted(names-self.independent_names[i])})
        return {'method':'Report original overlaps and hypothetical removal sensitivity; do not trim or reassign boundaries',
                'overlaps':self.overlaps,'overlap_removal_sensitivity':sensitivity,'settlements':details}


class EvidenceHandler(osmium.SimpleHandler):
    def __init__(self, accumulator, evidence):
        super().__init__()
        self.accumulator,self.evidence=accumulator,evidence

    def way(self, way):
        highway=way.tags.get('highway')
        if not highway:return
        name=way.tags.get('name')
        coordinates=[(node.lon,node.lat) for node in way.nodes]
        # The unchanged GB accumulator remains the source of candidate road names.
        self.accumulator.accept(highway,name,coordinates)
        self.evidence.observe(highway,name,coordinates,way.id)


def validate_candidates(output, catalogue, baselines=None):
    output=Path(output)
    expected={r['code']:r['name'] for r in catalogue}
    paths=sorted(output.glob('N*.json'))
    if len(paths)!=len(expected):raise ValueError('Missing or extra candidate inventory files')
    rows,zeros=[],[]
    seen=set()
    for path in paths:
        data=json.loads(path.read_text());code=data['code'];names=data['roads']
        if (code not in expected or code in seen or path.name!=code+'.json' or data['name']!=expected[code]
            or data['version']!=1 or type(data['count']) is not int or not isinstance(names,list)
            or any(not isinstance(name,str) or not name.strip() or name!=name.strip() for name in names)
            or data['count']!=len(names) or names!=sorted(set(names))):
            raise ValueError('Invalid candidate identity or road-name inventory')
        seen.add(code)
        rows.append({'code':code,'name':data['name'],'count':data['count'],
                     'bytes':path.stat().st_size,'sha256':digest(path)})
        if not names:zeros.append(code)
        if baselines is not None and (Path(baselines)/path.name).exists():
            if path.read_bytes()!=(Path(baselines)/path.name).read_bytes():
                raise ValueError('Saved NI trial inventory changed')
    return rows,zeros


def build_all(boundary_dir,source_dir,output,baselines=None,expected_count=527):
    boundary_dir,source_dir,output=map(Path,(boundary_dir,source_dir,output))
    catalogue=json.loads((boundary_dir/'ni-settlement-catalogue-candidate.json').read_text())['settlements']
    codes=sorted(r['code'] for r in catalogue)
    if len(codes)!=expected_count or len(set(codes))!=len(codes) or any(not re.fullmatch(r'N11\d{6}',c) for c in codes):
        raise ValueError('Incomplete or invalid NI catalogue')
    boundary_audit=json.loads((boundary_dir/'boundary-audit.json').read_text())
    holder={}
    def factory(accumulator):
        evidence=InventoryEvidence(accumulator,codes,boundary_audit.get('overlaps_over_one_square_metre',[]))
        holder['evidence']=evidence
        return EvidenceHandler(accumulator,evidence)
    # The trial generator validates source hashes, extraction coverage and boundaries.
    with redirect_stdout(io.StringIO()):
        report=generate(boundary_dir,source_dir,output,codes,handler_factory=factory)
    rows,zeros=validate_candidates(output,catalogue,baselines)
    evidence=holder['evidence'].report()
    names={r['code']:r['name'] for r in catalogue}
    by_code={r['code']:r for r in evidence['settlements']}
    report['status']='all_ni_candidates_only_not_published'
    report['scope']='all_source_settlements'
    report['settlement_count']=len(codes)
    report['road_name_entries_across_settlements']=sum(r['count'] for r in rows)
    report['zero_count_settlements']=[dict(by_code[c],name=names[c]) for c in zeros]
    report['saved_trial_files_compared']=sum((Path(baselines)/(c+'.json')).exists() for c in codes) if baselines else 0
    report['limitations']=['New NI candidates; no previous live NI inventories for comparison',
                           'Official boundaries retained; overlap sensitivity and name-quality flags need acceptance review',
                           'Road counts cover the existing eligible named local-road classes only',
                           'NI code support, aliases, historic counties and app compatibility require integration review',
                           'No Firebase deployment, Postgres registration or APK build']
    (output/'candidate-manifest.json').write_text(json.dumps({'version':1,'status':'candidate_only','inventories':rows},indent=2)+'\n')
    (output/'overlap-and-road-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
    (output/'generation-audit.json').write_text(json.dumps(report,indent=2)+'\n')
    # Replace the inner trial label with the full-scope report rather than retaining a misleading trial audit.
    (output/'trial-audit.json').unlink()
    summary={key:report[key] for key in ['status','settlement_count','road_name_entries_across_settlements',
             'inventory_file_bytes','osm_processing_seconds','total_seconds_including_hashing','peak_rss_kib','saved_trial_files_compared']}
    summary['zero_counts']=len(zeros)
    summary['overlap_names_solely_dependent_on_overlap']=sum(len(r['names_relying_on_source_overlap']) for r in evidence['overlap_removal_sensitivity'])
    print(json.dumps(summary,indent=2),flush=True)
    return report,evidence


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--boundary-dir',required=True,type=Path)
    parser.add_argument('--source-dir',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--baselines',type=Path)
    args=parser.parse_args()
    build_all(args.boundary_dir,args.source_dir,args.output,args.baselines)
