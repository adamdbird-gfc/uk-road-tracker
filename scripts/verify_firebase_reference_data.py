#!/usr/bin/env python3
"""Verify the exact staged bytes and serving headers at a Firebase Hosting URL."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def download(url, limit):
    try:
        with build_opener(NoRedirect).open(url, timeout=30) as response:
            return response.status, dict(response.headers), response.read(limit + 1)
    except HTTPError as error:
        return error.code, dict(error.headers), b""


def verify(base_url, report, fetch=download):
    base = urlsplit(base_url)
    if (base.scheme != "https" or not base.hostname or base.username or base.password
            or base.port or base.query or base.fragment or base.path not in ("", "/")
            or not base.hostname.endswith((".web.app", ".firebaseapp.com"))):
        raise ValueError("Expected an HTTPS Firebase Hosting origin")
    if report.get("schema_version") != 1 or not report.get("files"):
        raise ValueError("Unsupported staging report")
    seen = set()
    total = 0
    for row in report["files"]:
        path, size = row["path"], row["bytes"]
        if (not re.fullmatch(r"[a-zA-Z0-9_/-]+\.(json|geojson)", path)
                or path.startswith("/") or ".." in path or path in seen
                or not isinstance(size, int) or not 1 <= size <= 8 * 1024 * 1024
                or not re.fullmatch(r"[a-f0-9]{64}", row["sha256"])):
            raise ValueError("Unsafe or invalid report entry")
        seen.add(path)
        status, headers, payload = fetch(base_url.rstrip("/") + "/" + path, size)
        if status != 200 or len(payload) != size or hashlib.sha256(payload).hexdigest() != row["sha256"]:
            raise ValueError(f"Hosted bytes differ from staged release: {path} (HTTP {status})")
        headers = {key.lower(): value.lower() for key, value in headers.items()}
        expected = "application/geo+json" if path.endswith(".geojson") else "application/json"
        if headers.get("content-type", "").split(";")[0].strip() != expected:
            raise ValueError(f"Unexpected content type: {path}")
        if headers.get("x-content-type-options") != "nosniff":
            raise ValueError(f"Missing nosniff header: {path}")
        immutable = bool(re.fullmatch(r"collections/published/[a-z_]+/catalogue-[0-9]{10}\.json", path))
        cache = headers.get("cache-control", "")
        age = "31536000" if immutable else "300"
        if not re.search(r"(?:^|[, ])max-age=" + age + r"(?:$|[, ])", cache) or (immutable and "immutable" not in cache):
            raise ValueError(f"Unexpected cache policy: {path}")
        total += len(payload)
    # A successful rewrite to index.html must not masquerade as missing data.
    for path in ("index.html", "firebase.json", "README.md", "android-prototype/app/google-services.json"):
        status, _, _ = fetch(base_url.rstrip("/") + "/" + path, 1024)
        if status != 404:
            raise ValueError(f"Non-public path must be absent: {path} (HTTP {status})")
    return {"url": base_url.rstrip("/"), "files_verified": len(seen), "bytes_verified": total}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True)
    parser.add_argument("--report", type=Path, default=Path("hosting-stage-report.json"))
    args = parser.parse_args()
    print(json.dumps(verify(args.url, json.loads(args.report.read_text())), indent=2))
