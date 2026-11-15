#!/usr/bin/env bash
set -euo pipefail

baseline="$RUNNER_TEMP/build285.apk"
curl --fail --silent --show-error --location --retry 3 --max-time 120 \
  https://github.com/adamdbird-gfc/uk-road-tracker/releases/download/roadprints-0.25.239-285-journey-stage1/Roadprints-0.25.239-285.apk -o "$baseline"
echo '959a5b8a1e4e529129ceb1a26794b801fdff34f91d6ba636a4ca5f40b8db29dc  '"$baseline" | sha256sum --check

package=com.roadprints.capture
archive=files/journey_stage1-upgrade-fixture.json
fixture="$RUNNER_TEMP/archive-fixture.json"
cat > "$fixture" <<'JSON'
{"journey_id":"stage1-upgrade-fixture","mode":"driving","transport_confirmation":"confirmed","title":"User retained title","stops":[{"dwell":120}],"journey_corrections":{"removed_matched_segments":[1]},"distance_meters":522,"raw_capture_samples":[[0.37,51.44,6,1790000000000],[0.371,51.441,7,1790000060000]],"processing_status":"complete"}
JSON
adb install "$baseline"
adb shell am start -W -n "$package/.OnboardingActivity"
adb shell am force-stop "$package"
adb exec-out run-as "$package" sh -c 'mkdir -p files'
adb shell "run-as $package sh -c 'cat > $archive'" < "$fixture"
adb shell "run-as $package sh -c 'mkdir -p shared_prefs'"
ncn="$RUNNER_TEMP/ncn-fixture.xml"
awards="$RUNNER_TEMP/awards-fixture.xml"
printf '%s\n' '<map><boolean name="enabled" value="false"/><long name="revision" value="17"/></map>' > "$ncn"
printf '%s\n' '<map><string name="unlocked">{"test-award":{"level":1,"unlocked_at":1790000000000}}</string></map>' > "$awards"
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_ncn_national_v2.xml'" < "$ncn"
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_achievements_v1.xml'" < "$awards"
# Seed the format already written by build 285 and verify it survives the package update.
snapshot="files/journey-interpretation-v1/$(printf '%s' stage1-upgrade-fixture | sha256sum | cut -d' ' -f1)-$(sha256sum "$fixture" | cut -d' ' -f1).json"
adb shell "run-as $package sh -c 'mkdir -p files/journey-interpretation-v1'"
adb shell "run-as $package sh -c 'cat > $snapshot'" < "$fixture"
adb exec-out run-as "$package" cat "$archive" | cmp - "$fixture"
adb install -r "$RUNNER_TEMP/$APK"
adb shell pm path "$package" | grep -F package:
adb exec-out run-as "$package" cat "$archive" | cmp - "$fixture"
adb shell am start -W -n "$package/.OnboardingActivity"
adb exec-out run-as "$package" cat "$archive" | cmp - "$fixture"
adb exec-out run-as "$package" cat shared_prefs/roadprints_ncn_national_v2.xml | cmp - "$ncn"
adb exec-out run-as "$package" cat shared_prefs/roadprints_achievements_v1.xml | cmp - "$awards"
adb exec-out run-as "$package" cat "$snapshot" | cmp - "$fixture"
