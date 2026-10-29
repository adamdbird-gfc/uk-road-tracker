"""Explicit alternative-path accounting after source-identity matching.

Bindings map reviewed physical spans onto a common logical section. This module
never selects paths from proximity, changes matcher evidence or inflates actual
walking/cycling distance. Route completion and physical distance remain separate.
"""
from dataclasses import dataclass
import math
from .coverage import TrialLedger, merge_intervals


@dataclass(frozen=True)
class LogicalSection:
    key: str
    length_m: float
    routes: tuple


@dataclass(frozen=True)
class AlternativeSpan:
    physical_key: str
    physical_start: float
    physical_end: float
    logical_key: str
    logical_start: float
    logical_end: float
    modes: tuple = ('on_foot', 'cycling')


class AlternativeLedger(TrialLedger):
    def __init__(self, reference, logical_sections, bindings):
        super().__init__(reference)
        self.logical = {s.key:s for s in logical_sections}
        if len(self.logical) != len(logical_sections) or not self.logical:
            raise ValueError('Distinct logical sections required')
        if any(not s.key or not math.isfinite(s.length_m) or s.length_m <= 0 or not s.routes
               or len(set(s.routes)) != len(s.routes) or any(not isinstance(r,str) or not r for r in s.routes)
               for s in logical_sections):
            raise ValueError('Positive logical lengths and route identities required')
        self.bindings = tuple(bindings)
        occupied = {}
        for b in self.bindings:
            if b.physical_key not in reference.sections or b.logical_key not in self.logical:
                raise ValueError('Unknown physical or logical section')
            if not reference.sections[b.physical_key].eligible:
                raise ValueError('Ineligible physical section')
            values=(b.physical_start,b.physical_end,b.logical_start,b.logical_end)
            if not all(math.isfinite(v) for v in values):raise ValueError('Finite span anchors required')
            if not 0 <= b.physical_start < b.physical_end <= reference.sections[b.physical_key].line.length:
                raise ValueError('Physical span outside reference')
            if not 0 <= min(b.logical_start,b.logical_end) < max(b.logical_start,b.logical_end) <= self.logical[b.logical_key].length_m:
                raise ValueError('Logical span outside section')
            if not b.modes or not set(b.modes) <= {'on_foot','cycling'} or len(set(b.modes)) != len(b.modes):
                raise ValueError('Explicit eligible modes required')
            # Each physical interval has one logical meaning in this reference;
            # routes sharing it belong on the same LogicalSection.routes tuple.
            for mode in b.modes:
                previous=occupied.setdefault((b.physical_key,mode),[])
                if any(min(b.physical_end,z)>max(b.physical_start,a)+1e-7 for a,z in previous):
                    raise ValueError('Overlapping alternative bindings')
                previous.append((b.physical_start,b.physical_end))
        if set(self.logical) != {b.logical_key for b in self.bindings}:
            raise ValueError('Every logical section needs an explicit binding')

    def summary(self):
        physical=super().summary()  # Keeps existing evidence/range validation.
        covered={mode:{} for mode in ('on_foot','cycling')}
        for evidence in self.activities.values():
            if evidence.mode not in covered:continue
            for key,start,end in evidence.intervals:
                for b in self.bindings:
                    if key!=b.physical_key or evidence.mode not in b.modes:continue
                    a,z=max(start,b.physical_start),min(end,b.physical_end)
                    if z <= a:continue
                    def mapped(position):
                        return b.logical_start+(position-b.physical_start)/(b.physical_end-b.physical_start)*(b.logical_end-b.logical_start)
                    covered[evidence.mode].setdefault(b.logical_key,[]).append((mapped(a),mapped(z)))
        sections={};routes={}
        for key,section in self.logical.items():
            foot=merge_intervals(covered['on_foot'].get(key,[]))
            cycle=merge_intervals(covered['cycling'].get(key,[]))
            values={'on_foot_m':sum(z-a for a,z in foot), 'cycling_m':sum(z-a for a,z in cycle),
                'combined_m':sum(z-a for a,z in merge_intervals(foot+cycle)), 'length_m':section.length_m}
            values['completed_fraction']=min(1,values['combined_m']/section.length_m)
            sections[key]=values
            for route in section.routes:
                record=routes.setdefault(route,{'on_foot_m':0,'cycling_m':0,'combined_m':0,'length_m':0})
                for field in record:record[field]+=values[field]
        for record in routes.values():record['completed_fraction']=min(1,record['combined_m']/record['length_m'])
        return {'physical_distance':physical, 'logical_sections':sections, 'logical_routes':routes}
