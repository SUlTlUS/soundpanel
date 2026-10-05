import subprocess
import time

def adb(*args):
    return subprocess.run(['adb', *args], check=True, capture_output=True, text=True).stdout

adb('shell', 'input', 'keyevent', '224')
adb('shell', 'input', 'keyevent', '4')
adb('shell', 'input', 'keyevent', '25')
time.sleep(.6)
record = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '5', '/data/local/tmp/corner-stable.mp4'])
time.sleep(.6)
adb('shell', 'input', 'tap', '1131', '1229')
record.wait(timeout=12)
print(adb('pull', '/data/local/tmp/corner-stable.mp4', 'evidence/corner-stable.mp4'))
print(adb('shell', 'cmd', 'media_session', 'volume', '--stream', '3', '--get'))
