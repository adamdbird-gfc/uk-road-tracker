#!/usr/bin/env bash
set -euo pipefail
trap 'adb logcat -d -s RoadprintsRailwayRuntime:I RoadprintsRailway:I RoadprintsTiming:I AndroidRuntime:E | tail -180' EXIT
package=com.roadprints.capture.milepostacceptance
test_class=com.roadprints.capture.RailwayMileageRuntimeTest
apk_root=android-prototype/app/build/outputs/apk
adb install "$apk_root/acceptance/app-acceptance.apk"
adb install "$apk_root/androidTest/acceptance/app-acceptance-androidTest.apk"
for phase in prepare verify; do
  adb shell am force-stop "$package"
  output=$(adb shell am instrument -w -e class "$test_class#upgradeDistanceAndReopenArchiveOnAndroid" -e phase "$phase" "$package.test/androidx.test.runner.AndroidJUnitRunner")
  printf '%s\n' "$output"
  grep -F 'OK (1 test)' <<< "$output"
done
if [[ "${1:-}" == national ]]; then
  output=$(adb shell am instrument -w -e class "$test_class#reconstructsCardiffInterchangeInBothDirections" "$package.test/androidx.test.runner.AndroidJUnitRunner")
  printf '%s\n' "$output"
  grep -F 'OK (1 test)' <<< "$output"
fi
if [[ "${1:-}" == navigation || "${1:-}" == national ]]; then
  adb shell pm clear "$package"
  output=$(adb shell am instrument -w -e class "$test_class#repeatedNavigationRemainsResponsiveDuringHistoricalChecks" "$package.test/androidx.test.runner.AndroidJUnitRunner")
  printf '%s\n' "$output"
  grep -F 'OK (1 test)' <<< "$output"
fi
