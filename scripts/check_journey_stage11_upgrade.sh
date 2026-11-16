#!/usr/bin/env bash
set -euo pipefail

baseline="$RUNNER_TEMP/build287.apk"
curl --fail --silent --show-error --location --retry 3 --max-time 120 \
  https://github.com/adamdbird-gfc/uk-road-tracker/releases/download/roadprints-0.25.241-287-journey-stage1.1/Roadprints-0.25.241-287.apk -o "$baseline"
echo '1b9c26fa261688a536cf9d35ad86e5bba1a012d9418b7ca36e4744b22288a088  '"$baseline" | sha256sum --check

package=com.roadprints.capture
signer=$(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -n 1)
"$signer" verify --print-certs "$baseline" > "$RUNNER_TEMP/build287-certificate.txt"
grep -E "certificate SHA-256 digest: $CERT_SHA256$" "$RUNNER_TEMP/build287-certificate.txt"
"${signer%/*}/aapt" dump badging "$baseline" > "$RUNNER_TEMP/build287-package.txt"
grep -F "package: name='$package' versionCode='287' versionName='0.25.241'" "$RUNNER_TEMP/build287-package.txt"
archive=files/journey_stage1-upgrade-fixture.json
fixture="$RUNNER_TEMP/archive-fixture.json"
cat > "$fixture" <<'JSON'
{"journey_id":"stage1-upgrade-fixture","mode":"driving","transport_confirmation":"confirmed","title":"User retained title","stops":[{"dwell":120}],"journey_corrections":{"removed_matched_segments":[1]},"distance_meters":522,"raw_capture_samples":[[0.37,51.44,6,1790000000000],[0.371,51.441,7,1790000060000]],"processing_status":"complete"}
JSON
adb install "$baseline"
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F "Status: ok"
adb shell am force-stop "$package"
adb exec-out run-as "$package" sh -c 'mkdir -p files'
adb shell "run-as $package sh -c 'cat > $archive'" < "$fixture"
adb shell "run-as $package sh -c 'mkdir -p shared_prefs'"
ncn="$RUNNER_TEMP/ncn-fixture.xml"
awards="$RUNNER_TEMP/awards-fixture.xml"
printf '%s\n' '<map><boolean name="enabled" value="false"/><long name="revision" value="17"/><string name="activity">cycling</string></map>' > "$ncn"
printf '%s\n' '<map><string name="unlocked">{"test-award":{"level":1,"unlocked_at":1790000000000}}</string></map>' > "$awards"
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_ncn_national_v2.xml'" < "$ncn"
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_achievements_v1.xml'" < "$awards"
# Seed the format already written by build 287 and verify it survives the package update.
snapshot="files/journey-interpretation-v1/$(printf '%s' stage1-upgrade-fixture | sha256sum | cut -d' ' -f1)-$(sha256sum "$fixture" | cut -d' ' -f1).json"
adb shell "run-as $package sh -c 'mkdir -p files/journey-interpretation-v1'"
adb shell "run-as $package sh -c 'cat > $snapshot'" < "$fixture"
adb exec-out run-as "$package" cat "$archive" | cmp - "$fixture"
adb install -r "$RUNNER_TEMP/$APK"
adb shell pm path "$package" | grep -F package:
adb shell dumpsys package "$package" > "$RUNNER_TEMP/installed-roadprints.txt"
grep -F "versionCode=$VERSION_CODE " "$RUNNER_TEMP/installed-roadprints.txt"
grep -F "versionName=$VERSION_NAME" "$RUNNER_TEMP/installed-roadprints.txt"
adb exec-out run-as "$package" cat "$archive" | cmp - "$fixture"
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F "Status: ok"
adb exec-out run-as "$package" cat "$archive" | cmp - "$fixture"
adb exec-out run-as "$package" cat shared_prefs/roadprints_ncn_national_v2.xml | cmp - "$ncn"
adb exec-out run-as "$package" cat shared_prefs/roadprints_achievements_v1.xml | cmp - "$awards"
adb exec-out run-as "$package" cat "$snapshot" | cmp - "$fixture"
printf '%s\n' "Verified in-place 287 → $VERSION_CODE update; journey, source snapshot, NCN and achievement fixtures match byte for byte." | tee -a "$GITHUB_STEP_SUMMARY"
