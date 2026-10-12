#!/usr/bin/env python3
"""Read-only UI smoke of the exact signed APK; never log in or create production data."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.aistudio.blink.appvtwo'
ACTIVITY = PACKAGE + '/com.example.MainActivity'


def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True,
                          text=True, timeout=90).stdout.strip()


def healthy(seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if not adb('shell', 'pidof', PACKAGE, check=False):
            raise RuntimeError('BLINK process exited during the release smoke test')
        logs = adb('logcat', '-d', '-b', 'crash')
        if PACKAGE in logs:
            raise RuntimeError('BLINK Java/native crash detected; see private CI diagnostics')
        time.sleep(2)


def nodes():
    # Retry while Compose transitions settle. Do not capture authenticated UI.
    for _ in range(5):
        adb('shell', 'rm', '-f', '/sdcard/blink-smoke-ui.xml')
        adb('shell', 'uiautomator', 'dump', '/sdcard/blink-smoke-ui.xml', check=False)
        raw = adb('shell', 'cat', '/sdcard/blink-smoke-ui.xml', check=False)
        try:
            return list(ET.fromstring(raw).iter('node'))
        except ET.ParseError:
            time.sleep(2)
    raise RuntimeError('Could not inspect the rendered release UI')


def require_text(text, exact=False):
    for _ in range(8):
        for node in nodes():
            values = [node.get('text', '').casefold(), node.get('content-desc', '').casefold()]
            if (text.casefold() in values if exact else any(text.casefold() in value for value in values)):
                return node
        time.sleep(2)
    raise RuntimeError('Expected signed-out UI was not rendered: ' + text)


def tap_text(text):
    node = require_text(text, exact=True)
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if not bounds:
        raise RuntimeError('Missing bounds for ' + text)
    x1, y1, x2, y2 = map(int, bounds.groups())
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


def start():
    result = adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
    if 'Error:' in result or 'Exception' in result:
        raise RuntimeError('MainActivity failed to start')
    healthy()


def set_network(online):
    api = int(adb('shell', 'getprop', 'ro.build.version.sdk'))
    if api < 25:
        # API 24 emulator images have no simulated Wi-Fi. Use their modem
        # console instead of spawning the unsupported Wi-Fi service command.
        result = adb('emu', 'gsm', 'data', 'home' if online else 'unregistered')
        if 'OK' not in result or 'KO:' in result:
            raise RuntimeError('Emulator modem did not accept the connectivity change')
    else:
        state = 'enable' if online else 'disable'
        adb('shell', 'svc', 'wifi', state)
        adb('shell', 'svc', 'data', state)
    for _ in range(30):
        state = adb('shell', 'dumpsys', 'connectivity')
        default = re.search(r'^\s*Active default network:\s*(\S+)', state, re.MULTILINE)
        if default and ((default.group(1) != 'none') == online):
            return
        time.sleep(2)
    raise RuntimeError('Emulator did not reach the required online/offline state')


def exercise():
    start()
    require_text('Connect. Share. Discover.')
    tap_text('Log in')
    require_text('Welcome back')
    healthy(10)
    tap_text('Back')
    require_text('Connect. Share. Discover.')
    tap_text('Create account')
    require_text('Start your BLINK profile')
    healthy(10)
    tap_text('Back')
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    start()
    adb('shell', 'am', 'force-stop', PACKAGE)
    try:
        set_network(False)
        start()
        require_text('Connect. Share. Discover.')
    finally:
        set_network(True)
    adb('shell', 'am', 'force-stop', PACKAGE)
    start()
    require_text('Connect. Share. Discover.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--previous', required=True, type=Path)
    parser.add_argument('--version-code', required=True)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    actual = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    if actual != args.sha256:
        raise RuntimeError('APK bytes differ from signed release metadata')
    adb('shell', 'settings', 'put', 'global', 'window_animation_scale', '1')
    adb('shell', 'settings', 'put', 'global', 'transition_animation_scale', '1')
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1')
    adb('uninstall', PACKAGE, check=False)
    adb('logcat', '-c')
    adb('install', str(args.apk))
    exercise()
    # Install the real previous production build, then update in place. No -d,
    # no uninstall between old/new, no disposable signer masking an update error.
    adb('uninstall', PACKAGE)
    adb('install', str(args.previous))
    before = adb('shell', 'dumpsys', 'package', PACKAGE)
    first_install = re.search(r'firstInstallTime=(.+)', before)
    if not first_install:
        raise RuntimeError('Cannot establish previous installation identity')
    adb('logcat', '-c')
    adb('install', '-r', str(args.apk))
    after = adb('shell', 'dumpsys', 'package', PACKAGE)
    if f'versionCode={args.version_code} ' not in after:
        raise RuntimeError('Installed version differs from production metadata')
    if first_install.group(0) not in after:
        raise RuntimeError('Upgrade replaced the installation instead of updating it')
    exercise()
    if hashlib.sha256(args.apk.read_bytes()).hexdigest() != actual:
        raise RuntimeError('APK changed during validation')
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps({
        'apkSha256': actual, 'versionCode': args.version_code,
        'androidApi': adb('shell', 'getprop', 'ro.build.version.sdk'),
        'freshInstall': True, 'upgrade': True, 'signedOutNavigation': True,
        'backgroundResume': True, 'offlineRestart': True,
        'authenticatedFlowsTested': False,
    }, indent=2) + '\n')
    print('PASS: exact production APK fresh install, upgrade, signed-out navigation and restart')


if __name__ == '__main__':
    main()
