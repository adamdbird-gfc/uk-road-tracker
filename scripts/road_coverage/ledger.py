"""In-memory per-journey coverage and interval selection for the offline prototype."""

from collections import Counter, defaultdict
from dataclasses import dataclass
import math

from .network import (
    MAX_GAP_MS, MAX_SPEED_MPS, MIN_PAIR_M, POLICY, RADIUS_M, Span, continuous_chain, point_at,
)


@dataclass(frozen=True)
class Contribution:
    dataset: str
    policy: str
    covered: tuple[Span, ...]
    unresolved: tuple[Span, ...]
    held_pairs: int


@dataclass(frozen=True)
class Interval:
    start_mm: int
    end_mm: int
    state: str


def union(spans):
    """Merge only touching or overlapping intervals on the same edge."""
    merged = []
    for span in sorted(spans, key=lambda s: (s.edge, s.start_mm, s.end_mm)):
        if merged and span.edge == merged[-1].edge and span.start_mm <= merged[-1].end_mm:
            prev = merged.pop()
            span = Span(span.edge, prev.start_mm, max(span.end_mm, prev.end_mm))
        merged.append(span)
    return tuple(merged)


def assess(network, fixes, *, confirmed_driving, reference_in_period):
    """Read original fixes, preserving breaks and unresolved candidates.

    Fixes use local metres. Callers must establish mode and temporal applicability
    before contributing. Geometry supplied by a matcher belongs in diagnostics.
    """
    covered, unresolved = [], []
    held = 0
    for first, second in zip(fixes, fixes[1:]):
        if not all(math.isfinite(axis) for fix in (first, second) for axis in fix.point):
            held += 1
            continue
        length = math.dist(first.point, second.point)
        if length < MIN_PAIR_M:
            continue
        candidates = network.candidates(first.point, second.point)
        accuracy = (first.accuracy_m, second.accuracy_m)
        elapsed_ms = second.time_ms - first.time_ms
        if (
            confirmed_driving and reference_in_period
            and 0 < elapsed_ms <= MAX_GAP_MS
            and length * 1000 <= MAX_SPEED_MPS * elapsed_ms
            and all(value is not None and 0 < value <= 8 for value in accuracy)
            and continuous_chain(candidates, length)
            and all(network.edges[candidate.span.edge].eligible
                    and candidate.direction_allowed
                    and candidate.separation_m <= min(RADIUS_M, max(accuracy) + 3)
                    for candidate in candidates)
        ):
            covered.extend(candidate.span for candidate in candidates)
        else:
            held += 1
            unresolved.extend(candidate.span for candidate in candidates)
    return Contribution(network.version, POLICY, union(covered), union(unresolved), held)


class Ledger:
    def __init__(self, network):
        self.network = network
        self.journeys = {}
        self.held_pairs = {}
        self.by_edge = {}
        self.span_counts = {}
        self.covered = {}
        self.unresolved = {}

    def replace(self, journey_id, contribution):
        """Replace or delete one journey, rebuilding only the affected edges."""
        rows = defaultdict(lambda: ([], []))
        if contribution is not None:
            if (contribution.dataset, contribution.policy) != (self.network.version, POLICY):
                raise ValueError("Reassess contribution against active dataset and policy")
            for index, spans in enumerate((contribution.covered, contribution.unresolved)):
                for span in spans:
                    edge = self.network.edges.get(span.edge)
                    if (edge is None or (index == 0 and not edge.eligible)
                            or not 0 <= span.start_mm < span.end_mm <= edge.length_mm):
                        raise ValueError(f"Invalid span {span}")
                    rows[span.edge][index].append(span)
        current = {edge: (union(parts[0]), union(parts[1])) for edge, parts in rows.items()}
        previous = self.journeys.get(journey_id, {})
        touched = {edge for edge in current.keys() | previous.keys() if current.get(edge) != previous.get(edge)}
        if contribution is not None and contribution.held_pairs:
            self.held_pairs[journey_id] = contribution.held_pairs
        else:
            self.held_pairs.pop(journey_id, None)
        if current:
            self.journeys[journey_id] = current
        else:
            self.journeys.pop(journey_id, None)
        for edge_id in touched:
            contributors = self.by_edge.setdefault(edge_id, {})
            counts = self.span_counts.setdefault(edge_id, (Counter(), Counter()))
            for counter, old, new in zip(counts, previous.get(edge_id, ((), ())),
                                         current.get(edge_id, ((), ())), strict=True):
                counter.update(new)
                for span in old:
                    counter[span] -= 1
                    if counter[span] == 0:
                        del counter[span]
            if edge_id in current:
                contributors[journey_id] = current[edge_id]
            else:
                del contributors[journey_id]
            if contributors:
                self.covered[edge_id], self.unresolved[edge_id] = (union(counter) for counter in counts)
            else:
                del self.by_edge[edge_id]
                del self.span_counts[edge_id]
                del self.covered[edge_id]
                del self.unresolved[edge_id]
        return len(touched)

    @staticmethod
    def _aggregate(rows):
        return tuple(union(span for row in rows for span in row[index]) for index in (0, 1))

    def states(self, edge_id, journey_ids=None):
        """Filtered states describe only the selected journeys' contributions."""
        if journey_ids is None:
            covered, held = self.covered.get(edge_id, ()), self.unresolved.get(edge_id, ())
        else:
            covered, held = self._aggregate([
                rows for journey, rows in self.by_edge.get(edge_id, {}).items() if journey in journey_ids
            ])
        length = self.network.edges[edge_id].length_mm
        events = defaultdict(lambda: [0, 0])
        for index, spans in enumerate((covered, held)):
            for span in spans:
                events[span.start_mm][index] += 1
                events[span.end_mm][index] -= 1
        events.setdefault(length, [0, 0])
        start = live = unresolved = 0
        intervals = []
        for end, (live_delta, held_delta) in sorted(events.items()):
            state = "covered" if live else "unresolved" if unresolved else "uncovered"
            if end > start:
                if intervals and intervals[-1].state == state:
                    intervals[-1] = Interval(intervals[-1].start_mm, end, state)
                else:
                    intervals.append(Interval(start, end, state))
            start = end
            live += live_delta
            unresolved += held_delta
        return intervals

    def contributors(self, edge_id, interval, journey_ids=None):
        if interval.state == "uncovered":
            return []
        index = 0 if interval.state == "covered" else 1
        return sorted(
            journey for journey, rows in self.by_edge.get(edge_id, {}).items()
            if (journey_ids is None or journey in journey_ids)
            and any(span.start_mm < interval.end_mm and span.end_mm > interval.start_mm
                    for span in rows[index])
        )

    def _sections(self, journey_ids, show_unresolved):
        for edge_id in sorted(self.by_edge):
            for interval in self.states(edge_id, journey_ids):
                if interval.state == "covered" or (interval.state == "unresolved" and show_unresolved):
                    yield self.network.edges[edge_id], interval

    def _coordinates(self, edge, interval):
        return [
            self.network.lonlat(point_at(edge.a, edge.b, offset / edge.length_mm))
            for offset in (interval.start_mm, interval.end_mm)
        ]

    def features(self, journey_ids=None, *, show_unresolved=False):
        """Yield selectable canonical lines for a read-only comparison view."""
        for edge, interval in self._sections(journey_ids, show_unresolved):
            yield {
                "type": "Feature",
                "properties": {
                    "dataset": self.network.version,
                    "edge": edge.id,
                    "name": edge.label,
                    "state": interval.state,
                    "start_mm": interval.start_mm,
                    "end_mm": interval.end_mm,
                    "journeys": self.contributors(edge.id, interval, journey_ids),
                },
                "geometry": {
                    "type": "LineString",
                    "coordinates": self._coordinates(edge, interval),
                },
            }

    def strokes(self, journey_ids=None, *, show_unresolved=False):
        """Join render geometry along one way through degree-two nodes.

        Selection uses edge intervals separately, at tap time.
        """
        by_key = {}
        incoming, outgoing = {}, {}
        for edge, interval in self._sections(journey_ids, show_unresolved):
            key = (edge.id, interval.start_mm)
            by_key[key] = (edge, interval)
            if edge.way is None:
                continue
            if interval.start_mm == 0 and self.network.node_degree[edge.start_node] == 2:
                outgoing[(edge.way, edge.start_node, interval.state)] = key
            if interval.end_mm == edge.length_mm and self.network.node_degree[edge.end_node] == 2:
                incoming[(edge.way, edge.end_node, interval.state)] = key

        next_section = {
            tail: outgoing[node] for node, tail in incoming.items() if node in outgoing
        }
        has_previous = set(next_section.values())
        visited = set()
        for start in sorted(by_key, key=lambda key: (key in has_previous, key)):
            if start in visited:
                continue
            edge, interval = by_key[start]
            coordinates = self._coordinates(edge, interval)
            section_keys = [start]
            visited.add(start)
            successor = next_section.get(start)
            while successor is not None and successor not in visited:
                section_keys.append(successor)
                next_edge, next_interval = by_key[successor]
                coordinates.append(self._coordinates(next_edge, next_interval)[1])
                visited.add(successor)
                successor = next_section.get(successor)
            yield {
                "state": interval.state,
                "coordinates": coordinates,
                "sections": section_keys,
            }
