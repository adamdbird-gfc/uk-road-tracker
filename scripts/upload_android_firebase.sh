#!/usr/bin/env bash
# Upload only by default. Explicit opt-in shares with existing project testers.
set -euo pipefail
# No CI caller may create even an undistributed Firebase release without manual owner approval.
if [ "${GITHUB_ACTIONS:-false}" = true ]; then
  test "${GITHUB_EVENT_NAME:-}" = workflow_dispatch
  test "${GITHUB_ACTOR:-}" = "${GITHUB_REPOSITORY_OWNER:-}"
  test "${ROADPRINTS_RELEASE_CHANNEL:-}" = approved_beta
  test -n "${ROADPRINTS_APPROVED_APK_SHA256:-}"
  test "$(sha256sum "${1:?APK required}" | cut -d ' ' -f 1)" = "$ROADPRINTS_APPROVED_APK_SHA256"
fi

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
  generated_notes=true
  trap 'rm -f "$notes"' EXIT
  python3 - "$repo_dir" "$version" > "$notes" <<'PY'
import datetime, json, pathlib, sys
root, version = pathlib.Path(sys.argv[1]), sys.argv[2]
document = json.loads((root / 'android-prototype/app/src/main/assets/release-notes.json').read_text())
matches = [release for release in document['releases'] if release['version_name'] == version]
if len(matches) != 1:
    raise SystemExit('Expected release notes for the version being uploaded.')
release = matches[0]
date = datetime.date.fromisoformat(release['release_date'])
print(f"Roadprints {version} — {date.day} {date:%B %Y}\n")
if release.get('since'):
    print(f"Changes since {release['since']}\n")
for change in release['changes']:
    print(f"{change['title']}\n{change['description']}\n")
print('Spotted an issue, or would like to suggest a new feature? Contact Roadprints: adamdbird@gmail.com')
PY
fi
test -f "$notes" || { echo "Release notes not found: $notes" >&2; exit 1; }

tester_file=""
cleanup() { if [ -n "$tester_file" ]; then rm -f "$tester_file"; fi; if [ "${generated_notes:-false}" = true ]; then rm -f "$notes"; fi; }
trap cleanup EXIT
args=()
if [ "${ROADPRINTS_DISTRIBUTE_EXISTING_TESTERS:-false}" = true ]; then
  tester_file=$(mktemp)
  chmod 600 "$tester_file"
  python3 "$repo_dir/scripts/firebase_existing_testers.py" "$repo_dir/android-prototype/app/google-services.json" > "$tester_file"
  args+=(--testers-file "$tester_file")
fi
firebase appdistribution:distribute "$apk" "${args[@]}" \
  --app "$app_id" \
  --project roadprints-822cc \
  --release-notes-file "$notes" \
  --non-interactive
