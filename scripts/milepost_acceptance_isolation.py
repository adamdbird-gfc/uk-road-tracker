"""Fail-closed source/compiled release isolation checks. Does not claim to audit a packaged APK."""
import argparse
import pathlib
import unittest
import sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

ROOT = pathlib.Path(__file__).resolve().parents[1]
APP = ROOT / "android-prototype/app"


def verify(compiled=False):
    main = APP / "src/main"
    gate = (main / "java/com/roadprints/capture/MilepostIntegration.java").read_text()
    assert "PRODUCTION=new MilepostIntegration(MilepostCatalogue.READY," in gate
    assert "private MilepostIntegration(" in gate
    for source in main.rglob("*"):
        if source.is_file() and source.suffix in (".java", ".xml"):
            text = source.read_text()
            assert "MilepostAcceptance" not in text, source
            assert "synthetic-acceptance" not in text, source
    for folder in ("src/release", "src/debug"):
        assert not any((APP / folder).rglob("MilepostAcceptance*"))
    build = (APP / "build.gradle.kts").read_text()
    assert 'applicationIdSuffix = ".milepostacceptance"' in build
    assert 'buildTypes.create("acceptance")' in build
    manifest = (APP / "src/acceptance/AndroidManifest.xml").read_text()
    assert 'android:exported="false"' in manifest and 'android:allowBackup="false"' in manifest
    assert 'FirebaseInitProvider" tools:node="remove"' in manifest
    # Real candidate inventory is not packaged by main/acceptance source sets.
    assert not list((main / "assets").rglob("catalogue.jsonl"))
    from scripts.milepost_approval import verify as verify_approval
    verify_approval()
    if compiled:
        release = APP / "build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"
        acceptance = APP / "build/intermediates/javac/acceptance/compileAcceptanceJavaWithJavac/classes"
        assert (release / "com/roadprints/capture/MilepostIntegration.class").is_file(), release
        assert not list(release.rglob("MilepostAcceptance*"))
        assert not list(release.rglob("MilepostRuntimeProbe*"))
        assert (acceptance / "com/roadprints/capture/MilepostAcceptanceActivity.class").is_file()
        manifests = list((APP / "build/intermediates/merged_manifests/release").rglob("AndroidManifest.xml"))
        assert manifests, "Missing merged release manifest"
        for path in manifests:
            assert "MilepostAcceptance" not in path.read_text()
    print("PASS: source isolation" + (" and compiled release classes/merged manifest" if compiled else ""))


class IsolationTest(unittest.TestCase):
    def test_source_isolation(self):
        verify()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiled", action="store_true")
    verify(parser.parse_args().compiled)
