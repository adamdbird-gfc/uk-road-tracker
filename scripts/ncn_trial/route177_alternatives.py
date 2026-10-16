"""Load explicit, pinned Route177 alternative spans for local saved-path trials."""
import hashlib
import json
from pathlib import Path
from shapely.geometry import LineString
from matched_path_evidence import source_geometry_digest
from .alternatives import AlternativeLedger, AlternativeSpan, LogicalSection
from .coverage import Section, TrialReference
from .journey_replay import projector
from .saved_paths import KnownPath


def load_trial(folder=None):
    folder=Path(folder) if folder else Path(__file__).resolve().parents[2]/'tests/fixtures/ncn-177-discrepancy'
    raw=(folder/'alternative-bindings.json').read_bytes();manifest=json.loads(raw)
    snapshot=(folder/'public-snapshot.json').read_bytes()
    if manifest.get('schema_version')!=1 or hashlib.sha256(snapshot).hexdigest()!=manifest['fixture_sha256']:
        raise ValueError('Alternative snapshot contract changed')
    data=json.loads(snapshot);project=projector(data)
    ways={str(w['way_id']):w for w in data['source_member_ways']+data['nearby_nonmember_ways']}
    version='ncn177-alternatives:'+hashlib.sha256(raw).hexdigest()
    bindings=[];modes={}
    for b in manifest['bindings']:
        if source_geometry_digest(ways[b['physical_key']]['coordinates'])!=b['source_geometry_sha256']:
            raise ValueError('Alternative source geometry changed')
        bindings.append(AlternativeSpan(**{k:v for k,v in b.items() if k!='source_geometry_sha256'}))
        modes.setdefault(b['physical_key'],set()).update(b['modes'])
    reference=TrialReference(version,[Section(key,LineString([project(c) for c in ways[key]['coordinates']]),
        ('NCN:177:reviewed-pilot',)) for key in modes])
    paths=[KnownPath(key,tuple(tuple(c) for c in ways[key]['coordinates']),(key,),version,
        tuple(sorted(allowed)),'exact_source_spans',tuple(ways[key]['node_ids'])) for key,allowed in modes.items()]
    logical=[LogicalSection(s['key'],s['length_m'],tuple(s['routes'])) for s in manifest['logical_sections']]
    return AlternativeLedger(reference,logical,bindings),data,paths,manifest


def activation_trial(folder=None):
    """Assess only on activation; logical policy is pinned into reference.version."""
    from .activation import ActivationTrial
    ledger,data,paths,manifest=load_trial(folder)
    def factory(reference):
        return AlternativeLedger(reference,tuple(ledger.logical.values()),ledger.bindings)
    return ActivationTrial(ledger.reference,ledger_factory=factory,accounting_key="alternative-spans-v1"),data,paths,manifest
