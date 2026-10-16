"""Experimental, local NCN coverage trial. Not imported by application/runtime code.

Inputs use local metric coordinates. Synthetic observations exercise the evidence
and accounting rules; this is not a replacement for cycling/pedestrian routing.
"""
from dataclasses import dataclass
import math

from shapely.geometry import LineString, Point
from shapely.ops import unary_union
from shapely.strtree import STRtree

MODES = {'cycling': 'cycling', 'bicycle': 'cycling', 'walking': 'on_foot',
         'running': 'on_foot', 'pedestrian': 'on_foot', 'on_foot': 'on_foot'}


@dataclass(frozen=True)
class Section:
    key: str
    line: LineString
    routes: tuple
    eligible: bool = True


@dataclass(frozen=True)
class Fix:
    x: float
    y: float
    seconds: float
    accuracy: float = 3.0


@dataclass(frozen=True)
class Evidence:
    version: str
    mode: str
    intervals: tuple
    decisions: tuple


def merge_intervals(intervals):
    merged = []
    for start, end in sorted((min(a, b), max(a, b)) for a, b in intervals):
        if end <= start:
            continue
        if merged and start <= merged[-1][1] + 1e-7:
            merged[-1] = (merged[-1][0], max(end, merged[-1][1]))
        else:
            merged.append((start, end))
    return merged


class TrialReference:
    MAX_FIXES = 500
    MAX_SNAP_M = 8.0
    MAX_ACCURACY_M = 10.0
    MAX_LEG_M = 50.0
    MAX_GAP_S = 30.0

    def __init__(self, version, sections, context=()):
        if not version or len({s.key for s in sections}) != len(sections):
            raise ValueError('Version and distinct reference keys required')
        self.version = version
        self.sections = {s.key: s for s in sections}
        self.context = tuple(context)
        for line in [s.line for s in sections] + list(self.context):
            if line.is_empty or line.length <= 0 or not all(math.isfinite(v) for xy in line.coords for v in xy):
                raise ValueError('Finite non-empty reference lines required')
        # Measure against one physical edge basis. Partial geometric unions can
        # double-count lines differing by floating-point interpolation noise.
        linework = unary_union([s.line for s in sections])
        parts = [linework] if linework.geom_type == 'LineString' else list(linework.geoms)
        self.edges, self.mapping = {}, {s.key: [] for s in sections}
        for part in parts:
            for first, second in zip(part.coords, list(part.coords)[1:]):
                first, second = sorted((tuple(first), tuple(second)))
                line = LineString([first, second])
                if line.length <= 1e-7:
                    continue
                identity = tuple((round(x, 6), round(y, 6)) for x, y in (first, second))
                self.edges.setdefault(identity, {'line': line, 'routes': set()})
        section_buffers = [s.line.buffer(1e-6) for s in sections]
        section_tree = STRtree(section_buffers)
        for identity, edge in self.edges.items():
            line = edge['line']
            for index in sorted(int(i) for i in section_tree.query(line)):
                section = sections[index]
                if not section_buffers[index].covers(line):
                    continue
                first = section.line.project(Point(line.coords[0]))
                last = section.line.project(Point(line.coords[-1]))
                if abs(abs(last - first) - line.length) > 1e-4:
                    continue
                self.mapping[section.key].append((identity, first, last))
                if section.eligible:
                    edge['routes'].update(section.routes)
        if any(not self.mapping[s.key] for s in sections):
            raise ValueError('Reference cannot be expressed on physical edge basis')

    @staticmethod
    def same_local_path(first, second, point):
        shared = first.intersection(second)
        return shared.length >= 5.0 and shared.distance(point) < 0.05

    def candidates(self, fix):
        if not all(math.isfinite(v) for v in (fix.x, fix.y, fix.seconds, fix.accuracy)):
            return (), 'invalid_fix'
        if not 0 < fix.accuracy <= self.MAX_ACCURACY_M:
            return (), 'uncertain_accuracy'
        point = Point(fix.x, fix.y)
        entries = [(s.line.distance(point), s.key, s.line) for s in self.sections.values()]
        entries.extend((line.distance(point), None, line) for line in self.context)
        if not entries:
            return (), 'no_reference'
        entries.sort(key=lambda item: item[0])
        best_gap, _, best = entries[0]
        if best_gap > self.MAX_SNAP_M:
            return (), 'off_network'
        nearby = [r for r in entries if r[0] <= best_gap + max(3.0, fix.accuracy)]
        projection = best.interpolate(best.project(point))
        # A crossing point is not shared linework; parallel alternatives are held.
        if any(not self.same_local_path(best, line, projection) for _, _, line in nearby[1:]):
            return (), 'ambiguous_path'
        keys = tuple(key for _, key, line in nearby if key is not None
                     and self.sections[key].eligible
                     and (line is best or self.same_local_path(best, line, projection)))
        return keys, 'candidate' if keys else 'nonqualifying_path'

    def observe(self, mode, fixes):
        canonical = MODES.get(mode)
        if canonical is None:
            return Evidence(self.version, mode, (), ('ineligible_mode',))
        if not 3 <= len(fixes) <= self.MAX_FIXES:
            return Evidence(self.version, canonical, (), ('insufficient_or_excessive_fixes',))
        layers = [self.candidates(f) for f in fixes]
        decisions = [reason for _, reason in layers]
        legs = {}
        for index, (first, second) in enumerate(zip(fixes, fixes[1:])):
            dt = second.seconds - first.seconds
            distance = math.hypot(second.x - first.x, second.y - first.y)
            if not 0 < dt <= self.MAX_GAP_S or not 1 <= distance <= self.MAX_LEG_M:
                decisions.append('unsupported_gap_or_movement')
                continue
            for key in set(layers[index][0]) & set(layers[index + 1][0]):
                line = self.sections[key].line
                a = line.project(Point(first.x, first.y))
                b = line.project(Point(second.x, second.y))
                if abs(b - a) <= 1.5 * distance + 3.0:
                    legs.setdefault(key, []).append((index, a, b))
                else:
                    decisions.append('unsupported_reference_detour')
        intervals = []
        for key, rows in legs.items():
            run = []
            for row in rows + [(len(fixes) + 1, 0, 0)]:
                if run and row[0] != run[-1][0] + 1:
                    # At least three consecutive supported fixes on this section.
                    if len(run) >= 2:
                        intervals.extend((key, min(a, b), max(a, b)) for _, a, b in run)
                    run = []
                run.append(row)
        return Evidence(self.version, canonical, tuple(intervals), tuple(decisions))


class TrialLedger:
    """Rebuildable per-activity evidence; repeats never increase unique coverage."""
    def __init__(self, reference):
        self.reference = reference
        self.activities = {}

    def put(self, activity_id, evidence):
        if evidence.version != self.reference.version:
            raise ValueError('Reference version migration requires reconciliation')
        if not activity_id:
            raise ValueError('Activity identity required')
        # Re-import/mode correction replaces the old activity contribution.
        self.activities[activity_id] = evidence

    def remove(self, activity_id):
        self.activities.pop(activity_id, None)

    def summary(self):
        by_mode = {'cycling': {}, 'on_foot': {}}
        for evidence in self.activities.values():
            if evidence.mode not in by_mode:
                continue
            for key, start, end in evidence.intervals:
                if key not in self.reference.sections:
                    raise ValueError('Unknown reference section')
                section = self.reference.sections[key]
                if not 0 <= start <= end <= section.line.length + 1e-6:
                    raise ValueError('Coverage outside reference')
                for identity, first, last in self.reference.mapping[key]:
                    low, high = max(start, min(first, last)), min(end, max(first, last))
                    if high <= low:
                        continue
                    a, b = ((low - first, high - first) if first < last else
                            (first - high, first - low))
                    by_mode[evidence.mode].setdefault(identity, []).append((a, b))
        totals = {'cycling_m': 0.0, 'on_foot_m': 0.0, 'combined_m': 0.0, 'routes': {}}
        for identity, edge in self.reference.edges.items():
            cycling = merge_intervals(by_mode['cycling'].get(identity, []))
            foot = merge_intervals(by_mode['on_foot'].get(identity, []))
            values = {'cycling_m': sum(b - a for a, b in cycling),
                      'on_foot_m': sum(b - a for a, b in foot),
                      'combined_m': sum(b - a for a, b in merge_intervals(cycling + foot))}
            for field, value in values.items():
                totals[field] += value
            if values['combined_m'] > 0:
                for route in edge['routes']:
                    record = totals['routes'].setdefault(route, {'cycling_m': 0.0, 'on_foot_m': 0.0, 'combined_m': 0.0})
                    for field, value in values.items():
                        record[field] += value
        return totals
