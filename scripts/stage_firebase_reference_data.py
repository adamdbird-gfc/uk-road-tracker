#!/usr/bin/env python3
"""Stage only the public data needed for the first Android Hosting migration."""
import argparse
import hashlib
import json
import re
from pathlib import Path

from publish_venue_catalogue import validate

ROOT = Path(__file__).resolve().parents[1]


def stage(root, settlements_root, output):
    root, settlements_root, output = map(Path, (root, settlements_root, output))
    # Require an empty dedicated output. Never recursively delete an arbitrary path.
    if output.exists() and any(output.iterdir()):
        raise ValueError("Staging directory must be empty")
    files = {}

    def add(base, relative):
        source = base / relative
        if source.is_symlink() or not source.is_file() or not source.resolve().is_relative_to(base.resolve()):
            raise ValueError(f"Missing or unsafe public asset: {relative}")
        payload = source.read_bytes()
        json.loads(payload)
        files[relative] = payload
        return payload

    registry = json.loads((root / "collections/venue-collections-v1.json").read_text())
    for definition in registry["collections"]:
        collection = definition["id"]
        if not re.fullmatch(r"[a-z][a-z0-9_]*", collection):
            raise ValueError("Invalid collection path")
        directory = f"collections/published/{collection}"
        manifest = json.loads(add(root, f"{directory}/manifest-v1.json"))
        revision = manifest["revision"]
        filename = manifest["filename"]
        if (manifest["schema_version"] != 1 or manifest["collection_id"] != collection
                # Hosting can retain a release older than a newly bundled app.
                # Clients choose their newer bundle; staging must preserve the
                # existing immutable release for older installed clients.
                or type(revision) is not int or not 1 <= revision <= 9999999999
                or filename != f"catalogue-{revision}.json"
                or not 2 <= manifest["bytes"] <= 2 * 1024 * 1024):
            raise ValueError("Unsupported catalogue manifest")
        # Keep previous immutable releases so an in-flight older manifest remains usable.
        for path in sorted((root / directory).glob("catalogue-*.json")):
            if not re.fullmatch(r"catalogue-[0-9]{10}\.json", path.name):
                raise ValueError("Invalid catalogue release filename")
            validate(json.loads(add(root, f"{directory}/{path.name}")), definition)
        payload = files.get(f"{directory}/{filename}")
        if (payload is None or len(payload) != manifest["bytes"]
                or hashlib.sha256(payload).hexdigest() != manifest["sha256"]
                or json.loads(payload)["catalogue_version"] != manifest["catalogue_version"]):
            raise ValueError("Catalogue manifest integrity failure")

    manifest = json.loads(add(settlements_root, "settlement-inventory-manifest-v1.json"))
    codes = set()
    for row in manifest["inventories"]:
        code = row["code"]
        if not re.fullmatch(r"[A-Z][0-9]{8}", code) or code in codes:
            raise ValueError("Invalid or duplicate settlement identity")
        codes.add(code)
        if row["status"] == "available":
            data = json.loads(add(settlements_root, f"settlement-inventories-v1/{code}.json"))
            if data["code"] != code or data["count"] != row["count"]:
                raise ValueError("Inventory does not match manifest")
    # Boundary fallback may be requested independently of the inventory manifest.
    for path in sorted((settlements_root / "settlement-inventories-v1").glob("*-boundary.geojson")):
        if not re.fullmatch(r"[A-Z][0-9]{8}-boundary\.geojson", path.name):
            raise ValueError("Invalid boundary filename")
        add(settlements_root, f"settlement-inventories-v1/{path.name}")

    output.mkdir(parents=True, exist_ok=True)
    report = {"schema_version": 1, "files": []}
    for name, payload in sorted(files.items()):
        destination = output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(payload)
        report["files"].append({"path": name, "bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()})
    report["total_bytes"] = sum(row["bytes"] for row in report["files"])
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--settlements-root", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "hosting-public")
    parser.add_argument("--report", type=Path, default=ROOT / "hosting-stage-report.json")
    args = parser.parse_args()
    report = stage(args.root, args.settlements_root or args.root, args.output)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Staged {len(report['files'])} public files ({report['total_bytes']} bytes)")
