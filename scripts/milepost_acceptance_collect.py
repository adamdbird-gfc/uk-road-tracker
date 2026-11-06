"""Collect one acceptance-only device run. Never targets the production package.

Requires already installed private acceptance + test APKs. Does not install, clear,
restore, publish, or distribute APKs. All outputs may contain private GPS diagnostics.
"""
import argparse
import datetime
import json
import pathlib
import shutil
import subprocess
import threading
import time

PACKAGE = "com.roadprints.capture.milepostacceptance"
TEST = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
CLASS = "com.roadprints.capture.MilepostDeviceAcceptanceTest"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", required=True, help="Explicit adb serial; prevents selecting another device")
    p.add_argument("--runtime-kind", choices=("emulator", "physical"), required=True)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--output", type=pathlib.Path, required=True)
    p.add_argument("--method", choices=("onePhysicalPostAndDesigner", "sixPostsRemainAnonymous", "allAwardsAndDuplicateSummary",
                   "correctionFallbackWithdrawalAndReopen", "interruptedWorkerAndSimulatedCaptureState",
                   "repeatedEntryReplayRecreationAndRuntimeProfile", "replayCapturePauseAccessibilityStructureAndFocus",
                   "processRestartReceipt", "manualAcceptanceWindow"), default="repeatedEntryReplayRecreationAndRuntimeProfile")
    p.add_argument("--restart-phase", choices=("prepare", "verify"), default="prepare")
    p.add_argument("--manual-seconds", type=int, default=60)
    p.add_argument("--keep-fixtures", action="store_true", help="Manual run preserves existing synthetic archive")
    args = p.parse_args()
    if not 1 <= args.manual_seconds <= 600:
        p.error("--manual-seconds must be between 1 and 600")
    if not shutil.which("adb"):
        p.error("adb unavailable; Android device evidence remains UNVERIFIED")
    if len(args.source_sha) != 40 or any(c not in "0123456789abcdef" for c in args.source_sha):
        p.error("--source-sha must be a full 40-character Git SHA")
    args.output.mkdir(parents=True, exist_ok=False)
    base = ["adb", "-s", args.serial]

    def adb(*cmd, timeout=20):
        result = subprocess.run(base + list(cmd), capture_output=True, text=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError(f"adb command failed: {cmd}: {result.stderr}")
        return result.stdout

    adb("get-state")
    assert "package:" in adb("shell", "pm", "path", PACKAGE), "Acceptance app not installed"
    receipt = {"recorded_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
               "runtime_kind": args.runtime_kind, "classification": "operator supplied; verify serial/model",
               "source_sha": args.source_sha, "source_provenance": "operator supplied; match private build receipt",
               "package": PACKAGE, "serial": args.serial, "method": args.method,
               "restart_phase": args.restart_phase, "actual_gps_continuity": "unverified by synthetic instrumentation"}
    for key, cmd in {"model": ("shell", "getprop", "ro.product.model"),
                     "android": ("shell", "getprop", "ro.build.version.release"),
                     "ram": ("shell", "cat", "/proc/meminfo"),
                     "installed_package": ("shell", "dumpsys", "package", PACKAGE),
                     "initial_exit_info": ("shell", "dumpsys", "activity", "exit-info", PACKAGE)}.items():
        (args.output / (key + ".txt")).write_text(adb(*cmd))
    stop = threading.Event()
    def sample():
        with (args.output / "meminfo.txt").open("w") as out:
            while not stop.is_set():
                out.write(f"\nMONOTONIC_SECONDS={time.monotonic()}\n")
                try:
                    out.write(adb("shell", "dumpsys", "meminfo", PACKAGE))
                except Exception as error:
                    out.write(str(error))
                out.flush()
                stop.wait(1)
    sampler = threading.Thread(target=sample)
    sampler.start()
    try:
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", CLASS + "#" + args.method,
                     "-e", "restartPhase", args.restart_phase, "-e", "manualSeconds", str(args.manual_seconds),
                     "-e", "manualSeed", "false" if args.keep_fixtures else "true", TEST, timeout=max(300,args.manual_seconds+90))
        (args.output / "instrumentation.txt").write_text(result)
        receipt["instrumentation_pass"] = "OK (1 test)" in result and "FAILURES" not in result and "INSTRUMENTATION_FAILED" not in result
    except Exception as error:
        receipt["instrumentation_pass"] = False
        receipt["instrumentation_error"] = str(error)
    finally:
        stop.set()
        sampler.join(timeout=25)
    for name, cmd in {
        "runtime.jsonl": ("exec-out", "run-as", PACKAGE, "cat", "files/milepost-acceptance-runtime.jsonl"),
        "gfxinfo.txt": ("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats"),
        "final_exit_info.txt": ("shell", "dumpsys", "activity", "exit-info", PACKAGE),
        "logcat.txt": ("logcat", "-d", "-v", "threadtime", "MilepostAcceptance:V", "RoadprintsTiming:V", "AndroidRuntime:E", "*:S"),
        "movement.jsonl": ("exec-out", "run-as", PACKAGE, "cat", "files/roadprints_movement_diagnostics.jsonl"),
    }.items():
        try:
            (args.output / name).write_text(adb(*cmd))
        except Exception as error:
            receipt[name] = "unavailable: " + str(error)
    (args.output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    if not receipt["instrumentation_pass"]:
        raise SystemExit("Instrumentation did not pass; inspect receipt and logs")


if __name__ == "__main__":
    main()
