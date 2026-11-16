#!/usr/bin/env bash
set -euo pipefail
package=com.roadprints.capture.milepostacceptance
test_class=com.roadprints.capture.RailwayMileageRuntimeTest
apk_root=android-prototype/app/build/outputs/apk
adb install "$apk_root/acceptance/app-acceptance.apk"
adb install "$apk_root/androidTest/acceptance/app-acceptance-androidTest.apk"
for phase in prepare verify; do
  adb shell am force-stop "$package"
  output=$(adb shell am instrument -w -e class "$test_class" -e phase "$phase" "$package.test/androidx.test.runner.AndroidJUnitRunner")
  printf '%s\n' "$output"
  grep -F 'OK (1 test)' <<< "$output"
done
adb logcat -d -s RoadprintsRailwayRuntime:I
