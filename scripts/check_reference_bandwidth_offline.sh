#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
# JDK compiler module also works on runtimes whose javac launcher is absent.
source=android-prototype/app/src/main/java/com/roadprints/capture
java com.sun.tools.javac.Main -d "$classes" \
  "$source/ReferenceDataCache.java" "$source/ReferenceDataDiagnostics.java" \
  "$source/ReferenceDataConnection.java" tests/reference-data/ReferenceDataOfflineTest.java
java -cp "$classes" com.roadprints.capture.ReferenceDataOfflineTest
python3 -m unittest discover -s scripts -p test_reference_bandwidth_offline.py -v
