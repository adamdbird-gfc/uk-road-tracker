#!/usr/bin/env python3
"""Fail before preparing an older source ref whose build helpers can download pins."""
import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def verify(root=ROOT):
    ncn = (root / 'scripts/prepare_ncn_reference.py').read_text()
    tree = ast.parse(ncn)
    banned = ('urllib', 'requests', 'http', 'socket', 'subprocess')
    for node in ast.walk(tree):
        modules = [name.name for name in node.names] if isinstance(node, ast.Import) else [node.module or ''] if isinstance(node, ast.ImportFrom) else []
        if any(module.split('.')[0] in banned for module in modules):
            raise ValueError('NCN preparation must have no network transport')
    # Fail closed for historical helpers as well as accidental reintroduction.
    if 'source_database' not in ncn or 'allow_firebase_download' in ncn:
        raise ValueError('Offline-only NCN preparation required')
    county = (root / 'android-prototype/scripts/prepare_county_reference.sh').read_text()
    if any(token in county for token in ('curl ', 'wget ', 'http://', 'https://')):
        raise ValueError('County preparation must be local-only')
    if 'ROADPRINTS_COUNTY_REFERENCE_FILE' not in county:
        raise ValueError('Offline-only county preparation required')
    gradle = (root / 'android-prototype/app/build.gradle.kts').read_text()
    if 'systemProperty("roadprints.referenceNetworkDisabled", "true")' not in gradle:
        raise ValueError('Android unit tests must disable reference networking')
    source = root / 'android-prototype/app/src/main/java/com/roadprints/capture'
    guard = (source / 'ReferenceDataConnection.java').read_text()
    if 'Boolean.getBoolean("roadprints.referenceNetworkDisabled")' not in guard or guard.index('Boolean.getBoolean') > guard.index('url.openConnection()'):
        raise ValueError('Reference network guard must run before URL opening')
    for name in ['NcnCatalogueFiles.java', 'VenueCatalogueUpdates.java', 'LocalRoadSettlementMatcher.java']:
        if 'new ReferenceDataConnection' not in (source / name).read_text():
            raise ValueError('Reference client must use the offline-guarded transport')
    print('Offline build helpers and unit-test transport guards verified; historical download helpers rejected')


if __name__ == '__main__':
    verify()
