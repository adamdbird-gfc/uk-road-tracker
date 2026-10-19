"""Local confirmation experiment; no app prompts, storage or GPS matcher wiring."""
from dataclasses import dataclass
import hashlib
import json
import math

from shapely.geometry import LineString, Point
from shapely.ops import substring

from .coverage import Evidence, MODES, TrialReference


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


@dataclass(frozen=True)
class Alternative:
    key: str
    line: object
    target_keys: tuple = ()
    membership: str = 'unknown'

    @classmethod
    def from_section(cls, key, section):
        return cls(key, section.line, (section.key,), 'eligible' if section.eligible else 'ineligible')


@dataclass(frozen=True)
class Window:
    activity: str
    key: str
    mode: str
    fixes: tuple
    base_path: str | None = None

    def fingerprint(self):
        if not all(math.isfinite(v) for f in self.fixes for v in (f.x, f.y, f.seconds, f.accuracy)):
            return None
        return digest({'mode': self.mode, 'fixes': [[float(f.x), float(f.y), float(f.seconds), float(f.accuracy)]
                                                  for f in self.fixes]})


@dataclass(frozen=True)
class Review:
    reference: object
    window: Window
    alternatives: tuple
    options: tuple
    eligible_mode: bool
    context_hash: str

    def has_ncn_option(self):
        return any(a.key in self.options and a.membership == 'eligible' and a.target_keys for a in self.alternatives)

    def evidence(self, choice):
        if choice not in self.options:
            raise ValueError('Selection is not an evidenced plausible alternative')
        candidate = next(a for a in self.alternatives if a.key == choice)
        spans = []
        if candidate.membership == 'eligible' and self.eligible_mode:
            for key in candidate.target_keys:
                section = self.reference.sections[key]
                positions = [section.line.project(Point(f.x, f.y)) for f in self.window.fixes]
                spans.extend((key, min(a, b), max(a, b)) for a, b in zip(positions, positions[1:]))
        return Evidence(self.reference.version, MODES.get(self.window.mode, self.window.mode), tuple(spans),
                        ('user_confirmed:' + choice,))

    def selected_geometry(self, choice):
        candidate = next(a for a in self.alternatives if a.key == choice)
        positions = [candidate.line.project(Point(f.x, f.y)) for f in self.window.fixes]
        # Preserve travel direction and out-and-back traversals in the match;
        # unique completion is calculated separately by the ledger.
        coordinates = []
        for first, last in zip(positions, positions[1:]):
            part = substring(candidate.line, first, last)
            if part.geom_type == 'LineString':
                points = list(part.coords)
                coordinates.extend(points if not coordinates else points[1:])
        return LineString(coordinates)


def prepare_review(reference, window, alternatives):
    """Only sustained alternatives supported by every fix can be offered here.

    This is a controlled candidate-validation experiment, not a path planner.
    Real eligibility/conflation must precede Alternative construction.
    """
    if not window.activity or not window.key or len({a.key for a in alternatives}) != len(alternatives):
        raise ValueError('Distinct activity, window and alternative identities required')
    for candidate in alternatives:
        if candidate.membership not in {'eligible', 'ineligible', 'unknown'}:
            raise ValueError('Unsupported membership state')
        if candidate.line.is_empty or candidate.line.length <= 0:
            raise ValueError('Alternative requires a usable path')
        for key in candidate.target_keys:
            if key not in reference.sections:
                raise ValueError('Unknown reference identity')
            section = reference.sections[key]
            # Trial target links are exact public-reference geometry, not a
            # fabricated association between a nearby road and an NCN segment.
            if candidate.line.hausdorff_distance(section.line) > 1e-6:
                raise ValueError('Unverified path-to-reference association')
            if candidate.membership == 'eligible' and not section.eligible:
                raise ValueError('Nonqualifying reference cannot become eligible')
        if candidate.membership == 'eligible' and not candidate.target_keys:
            raise ValueError('Eligible path requires verified reference identity')
    mode_ok = window.mode in MODES
    fixes = window.fixes
    supported = mode_ok and 3 <= len(fixes) <= reference.MAX_FIXES
    supported = supported and all(all(math.isfinite(v) for v in (f.x, f.y, f.seconds, f.accuracy))
                                  and 0 < f.accuracy <= reference.MAX_ACCURACY_M for f in fixes)
    if supported:
        supported = all(0 < b.seconds - a.seconds <= reference.MAX_GAP_S
                        and 1 <= math.hypot(b.x - a.x, b.y - a.y) <= reference.MAX_LEG_M
                        for a, b in zip(fixes, fixes[1:]))
    plausible = []
    if supported:
        for candidate in alternatives:
            positions = [candidate.line.project(Point(f.x, f.y)) for f in fixes]
            distances = [candidate.line.distance(Point(f.x, f.y)) for f in fixes]
            follows = all(d <= min(reference.MAX_SNAP_M, max(3.0, f.accuracy)) for f, d in zip(fixes, distances))
            follows = follows and sum(abs(b - a) for a, b in zip(positions, positions[1:])) >= 10
            follows = follows and all(abs(pb - pa) <= 1.5 * math.hypot(b.x - a.x, b.y - a.y) + 3
                                      for a, b, pa, pb in zip(fixes, fixes[1:], positions, positions[1:]))
            if follows:
                plausible.append(candidate)
    # Two source identities on identical physical linework are not an ambiguity.
    options, retained = [], []
    for candidate in sorted(plausible, key=lambda a: ({'eligible': 0, 'ineligible': 1, 'unknown': 2}[a.membership], a.key)):
        equivalent = False
        for other in retained:
            shared = candidate.line.intersection(other.line)
            if shared.length >= 10 and all(shared.distance(candidate.line.interpolate(candidate.line.project(Point(f.x, f.y)))) < .05
                                           and shared.distance(other.line.interpolate(other.line.project(Point(f.x, f.y)))) < .05
                                           for f in fixes):
                equivalent = True
                break
        if not equivalent:
            options.append(candidate.key)
            retained.append(candidate)
    context_hash = digest({'version': reference.version,
                           'paths': sorted((a.key, a.line.wkb_hex, a.membership, a.target_keys) for a in alternatives)})
    return Review(reference, window, tuple(alternatives), tuple(options), mode_ok, context_hash)


class ConfirmationTrial:
    """Small JSON-roundtrippable decision log; never replaces captured fixes."""
    def __init__(self, rows=None):
        self.rows = dict(rows or {})

    @staticmethod
    def key(review):
        return json.dumps([review.window.activity, review.window.key], separators=(',', ':'))

    def state(self, review, collection_enabled=True):
        row = self.rows.get(self.key(review))
        same = row and row['reference_version'] == review.reference.version \
            and row['evidence_hash'] == review.window.fingerprint() and row['context_hash'] == review.context_hash
        status = 'unresolved'
        if row:
            status = ('pending' if row['choice'] is None else 'confirmed') if same else 'needs_reconciliation'
        choice = row['choice'] if same else None
        prompt = bool(collection_enabled and review.eligible_mode and len(review.options) >= 2 and review.has_ncn_option() and row is None)
        selected = next((a for a in review.alternatives if a.key == choice), None)
        ncn_status = ('pending_reconciliation' if status == 'needs_reconciliation' else
                      'pending' if selected is None else
                      'credited' if selected.membership == 'eligible' else
                      'not_eligible' if selected.membership == 'ineligible' else 'pending_membership')
        return {'status': status, 'ncn_status': ncn_status, 'prompt': prompt, 'choice': choice,
                'display_path': choice or review.window.base_path,
                'display_path_certain': bool(choice),
                'original_fixes': review.window.fixes,
                'evidence': review.evidence(choice) if choice is not None else
                    Evidence(review.reference.version, MODES.get(review.window.mode, review.window.mode), (), ('pending',))}

    def choose(self, review, choice, collection_enabled=True):
        if not collection_enabled or not review.eligible_mode or len(review.options) < 2 or not review.has_ncn_option():
            raise ValueError('No eligible collection ambiguity to confirm')
        if choice is not None and choice not in review.options:
            raise ValueError('Selection is not an evidenced plausible alternative')
        self.rows[self.key(review)] = {'reference_version': review.reference.version,
                                      'evidence_hash': review.window.fingerprint(),
                                      'context_hash': review.context_hash, 'choice': choice,
                                      'provenance': 'explicit_user_selection',
                                      'mode': review.window.mode}
        return self.state(review, collection_enabled)

    def export(self):
        return json.dumps({'schema_version': 1, 'decisions': self.rows}, sort_keys=True)

    @classmethod
    def restore(cls, raw):
        value = json.loads(raw)
        if value.get('schema_version') != 1 or not isinstance(value.get('decisions'), dict):
            raise ValueError('Unsupported decision store')
        return cls(value['decisions'])
