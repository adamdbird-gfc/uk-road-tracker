#!/usr/bin/env bash
# Upload only. Tester invitations/distribution are managed in the Firebase console.
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
apk=${1:?Usage: bash scripts/upload_android_firebase.sh path/to/signed.apk [release-notes.txt]}
test -f "$apk" || { echo "APK not found: $apk" >&2; exit 1; }
command -v firebase >/dev/null || { echo "Install Firebase CLI: npm install --global firebase-tools@15.33.0" >&2; exit 1; }

metadata=$(python3 - "$repo_dir" <<'PY'
import json, pathlib, re, sys
root = pathlib.Path(sys.argv[1])
config = json.loads((root / 'android-prototype/app/google-services.json').read_text())
if config['project_info']['project_id'] != 'roadprints-822cc':
    raise SystemExit('Unexpected Firebase project.')
clients = [client for client in config['client'] if client['client_info']['android_client_info']['package_name'] == 'com.roadprints.capture']
if len(clients) != 1:
    raise SystemExit('Expected exactly one com.roadprints.capture Firebase app.')
print(clients[0]['client_info']['mobilesdk_app_id'])
gradle = (root / 'android-prototype/app/build.gradle.kts').read_text()
print(re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1))
PY
)
app_id=$(head -n 1 <<< "$metadata")
version=$(tail -n 1 <<< "$metadata")
notes=${2:-}
if [ -z "$notes" ]; then
  notes=$(mktemp)
  trap 'rm -f "$notes"' EXIT
  {
    printf 'Roadprints %s\n\n' "$version"
    printf 'Signed Android beta build. Unit tests, lint and signature verification passed in CI.\n'
    git -C "$repo_dir" log -1 --format='Change: %s%nCommit: %H'
  } > "$notes"
fi
test -f "$notes" || { echo "Release notes not found: $notes" >&2; exit 1; }

firebase appdistribution:distribute "$apk" \
  --app "$app_id" \
  --project roadprints-822cc \
  --release-notes-file "$notes" \
  --non-interactive
