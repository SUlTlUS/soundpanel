"""Bounded device evidence capture and temporary logging for the volume hook."""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("serial")
parser.add_argument("action", choices=["debug", "restore", "capture"])
parser.add_argument("--output", default="evidence/multi-app-volume-toggle/runtime-20261017")
args = parser.parse_args()
output = Path(args.output)
output.mkdir(parents=True, exist_ok=True)


def adb(*command):
    return subprocess.run(["adb", "-s", args.serial, *command], check=True, capture_output=True).stdout


def shell(command, root=False):
    return adb("shell", "su -c " + shlex.quote(command) if root else command)


prefs = "/data/user/0/dev.glass.soundbar/shared_prefs/panel.xml"
state_file = output / "temporary-log-level.json"
if args.action in ("debug", "restore"):
    original = shell("cat " + prefs, root=True)
    pattern = rb'(<int name="hook_log_level" value=")([0-9]+)("\s*/>)'
    match = re.search(pattern, original)
    if not match:
        raise RuntimeError("Current logging preference not found")
    if args.action == "debug":
        if state_file.exists():
            raise RuntimeError("Restore the previous diagnostic logging session first")
        state_file.write_text(json.dumps({"original": int(match[2])}), encoding="utf-8")
        level = 3
    else:
        level = json.loads(state_file.read_text(encoding="utf-8"))["original"]
    # Stop only this module's settings app, preserving all other preference values.
    shell("am force-stop dev.glass.soundbar")
    original = shell("cat " + prefs, root=True)
    updated, count = re.subn(pattern, lambda m: m[1] + str(level).encode() + m[3], original)
    if count != 1:
        raise RuntimeError("Expected exactly one logging preference")
    scratch = "/data/local/tmp/soundbar-diagnostic-" + uuid.uuid4().hex + ".xml"
    local = output / ("temporary-" + uuid.uuid4().hex + ".xml")
    try:
        local.write_bytes(updated)
        adb("push", str(local), scratch)
        shell("cat " + scratch + " > " + prefs, root=True)
    finally:
        shell("rm -f " + scratch)
        local.unlink(missing_ok=True)
    shell("am start -W -n dev.glass.soundbar/.Launcher")
    time.sleep(1)
    if args.action == "restore":
        state_file.replace(output / "restored-log-level.json")
    print("Logging level:", level)
else:
    stamp = str(time.time_ns())
    (output / (stamp + "-screen.png")).write_bytes(adb("exec-out", "screencap", "-p"))
    for name, command, root in [
        ("hook", "tail -n 200 /data/user/0/dev.glass.soundbar/files/soundbar-debug.log", True),
        ("sysui", "dumpsys activity service com.android.systemui/.SystemUIService", False),
        ("window", "dumpsys window windows", False),
        ("activity", "dumpsys activity activities", False),
        ("audio", "dumpsys audio", False),
    ]:
        (output / (stamp + "-" + name + ".txt")).write_bytes(shell(command, root))
    (output / (stamp + "-logcat.txt")).write_bytes(adb("logcat", "-d", "-t", "10000"))
    print("Captured:", output / stamp)
