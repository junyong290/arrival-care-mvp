"""Verify signed APKs on the actual Pleos image, using observed Android UI only.

No service mocking, app state injection, or Car UX restriction overrides are used.
The demo build is explicitly reported as simulation if the SDK build cannot run.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--adb', default='adb')
p.add_argument('--serial', default='emulator-5554')
p.add_argument('--apks', required=True)
p.add_argument('--out', required=True)
p.add_argument('--boot-timeout', type=int, default=900)
a = p.parse_args()
out = pathlib.Path(a.out)
out.mkdir(parents=True, exist_ok=True)
base = [a.adb, '-s', a.serial]
result = {'emulator': 'Pleos Connect 2.1.0', 'variants': [], 'success': False}


def save():
    (out / 'runtime-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')


def adb(*args, timeout=30, binary=False):
    r = subprocess.run(base + list(args), capture_output=True, timeout=timeout)
    if r.returncode:
        raise RuntimeError(r.stderr.decode(errors='replace') + r.stdout.decode(errors='replace'))
    return r.stdout if binary else r.stdout.decode('utf-8', errors='replace').strip()


def collect(folder):
    for name, args in [
        ('app-logcat.txt', ['logcat', '-d', '-s', 'ArrivalCareSDK:V', 'ArrivalCare:I', 'AndroidRuntime:E']),
        ('system-logcat.txt', ['logcat', '-d', '-t', '300']),
        ('activities.txt', ['shell', 'dumpsys', 'activity', 'activities']),
        ('windows.txt', ['shell', 'dumpsys', 'window', 'windows']),
    ]:
        try:
            (folder / name).write_text(adb(*args), encoding='utf-8')
        except Exception as e:
            (folder / (name + '.error')).write_text(str(e), encoding='utf-8')


def screenshot(folder, name):
    data = adb('exec-out', 'screencap', '-p', binary=True, timeout=45)
    assert data.startswith(b'\x89PNG\r\n\x1a\n'), 'Invalid screenshot'
    (folder / (name + '.png')).write_bytes(data)


def park_virtual_vehicle():
    """Use the official emulator console's Park control, never override app UX.

    Endpoints and readback fields come from Pleos 2.1.0's served console HTML:
    its Park handler resets controls, requests Park, then observes actual gear.
    This changes only this test emulator, not a real vehicle or app state.
    """
    record = {'scope': 'Pleos Virtual Console simulated vehicle', 'verified_parked': False}
    result['virtual_console'] = record
    try:
        adb('forward', 'tcp:8088', 'tcp:8088')
        def request(path, method='GET'):
            req = urllib.request.Request('http://127.0.0.1:8088' + path, method=method)
            with urllib.request.urlopen(req, timeout=15) as response:
                payload = response.read()
                return json.loads(payload) if payload else {}
        record['initial_state'] = request('/vehicle/state')
        request('/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0', 'PUT')
        request('/vehicle/gear?gear=Park', 'PUT')
        request('/vehicle/body?door_fl=0&door_fr=0&door_rl=0&door_rr=0', 'PUT')
        for _ in range(10):
            state = request('/vehicle/state')
            record['observed_state'] = state
            if (state.get('gear', {}).get('actual', {}).get('value') == 'Park'
                    and state.get('controls', {}).get('speed_kph') == 0
                    and state.get('controls', {}).get('accelerator_pct') == 0):
                record['verified_parked'] = True
                break
            time.sleep(1)
    except Exception as e:
        record['error'] = str(e)
    # Car UX still independently decides whether app input is permitted.
    (out / 'virtual-console-state.json').write_text(json.dumps(record, indent=2), encoding='utf-8')


def tree(deadline=None):
    last_error = None
    for _ in range(4):
        def remaining_timeout(limit):
            if deadline is None:
                return limit
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError('UI observation deadline expired')
            return min(limit, remaining)
        try:
            response = adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/arrival-pleos-check.xml', timeout=remaining_timeout(35))
            if 'dumped to' not in response:
                raise RuntimeError('UI dump did not confirm fresh output: ' + response)
            xml = adb('shell', 'cat', '/sdcard/arrival-pleos-check.xml', timeout=remaining_timeout(30))
            root = ET.fromstring(xml)
            (current / 'last-ui.xml').write_text(xml, encoding='utf-8')
            return root
        except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError, TimeoutError) as e:
            last_error = e
            if deadline is not None and time.monotonic() >= deadline:
                break
            time.sleep(min(1, max(0, deadline - time.monotonic())) if deadline is not None else 1)
    raise RuntimeError('No fresh UI hierarchy: ' + str(last_error)) from last_error


def rect(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds', ''))))


def lookup(root, label):
    for n in root.iter('node'):
        if label in (n.get('text'), n.get('content-desc')):
            b = rect(n)
            if len(b) == 4 and b[2] > b[0] and b[3] > b[1]:
                return n


def find(label, enabled=None, scroll=True, optional=False):
    for attempt in range(6 if scroll else 1):
        root = tree()
        n = lookup(root, label)
        if n is not None:
            if enabled is not None:
                assert n.get('enabled') == str(enabled).lower(), (label, 'enabled', n.get('enabled'))
            return n
        scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true' and n.get('package') == package]
        if not scrolls or not scroll:
            break
        x1, y1, x2, y2 = rect(scrolls[-1])
        lo, hi = (y2 - 24, y1 + 24) if attempt < 3 else (y1 + 24, y2 - 24)
        adb('shell', 'input', 'swipe', str(x2 - 20), str(lo), str(x2 - 20), str(hi), '450')
        time.sleep(.5)
    if optional:
        return None
    raise AssertionError('UI element not found: ' + label)


def tap(label):
    n = find(label, True)
    x1, y1, x2, y2 = rect(n)
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(.7)


def start():
    adb('shell', 'am', 'force-stop', package)
    launch = adb('shell', 'am', 'start', '-W', '-n', package + '/ai.arrivalcare.app.MainActivity', timeout=90)
    (current / 'launch.txt').write_text(launch, encoding='utf-8')
    # Car UX service readiness is asynchronous; observe it without overriding it.
    deadline = time.monotonic() + 45
    last_observation = 'No unlocked planning UI observed'
    while time.monotonic() < deadline:
        try:
            root = tree(deadline=deadline)
            button = lookup(root, '이 계획으로 안내 시작')
            if button is not None and button.get('enabled') == 'true':
                return
            labels = [n.get('text') for n in root.iter('node') if n.get('text')]
            last_observation = ' | '.join(labels)
        except RuntimeError as e:
            last_observation = str(e)
        time.sleep(min(1, max(0, deadline - time.monotonic())))
    raise AssertionError('Planning input did not become ready within 45 seconds: ' + last_observation)


def happy():
    find('목적지 변경')
    screenshot(current, '01-plan')
    tap('이 계획으로 안내 시작')
    tap('동의하고 시연 경로 요청')
    tap('[시연] 하차 지점 도착')
    find('확인하고 주차장 안내', False)
    screenshot(current, '02-exit-unconfirmed')
    tap('안전하게 정차·주차했어요')
    tap('보호자와 동승자가 모두 내렸어요')
    # The extra confirmation can be below the visible region in a Pleos window.
    # Only absence is optional: dump/ADB errors still propagate as test failures.
    manual = find('모든 문이 닫힌 것을 직접 확인했어요', optional=True)
    if manual is not None:
        tap('모든 문이 닫힌 것을 직접 확인했어요')
    tap('확인하고 주차장 안내')
    find('동의하고 시연 경로 요청')
    screenshot(current, '03-parking-approval')
    tap('동의하고 시연 경로 요청')
    tap('[시연] 주차장 도착')
    find('주차 완료 · 만남 장소 보기', False)
    tap('주차를 마치고 안전하게 내릴 수 있어요')
    tap('주차 완료 · 만남 장소 보기')
    find('여기서 다시 만나요')
    find('1층 안내데스크 옆 대기공간')
    screenshot(current, '04-reunion')
    tap('만남 장소 확인했어요')
    find('도착을 함께 마쳤어요')
    screenshot(current, '05-complete')


def together():
    tap('새로운 도착 계획 만들기')
    tap('동행 조건 변경')
    tap('별도 보호자 없음')
    tap('조건 적용')
    tap('이 계획으로 안내 시작')
    find('모두 함께 주차해요')
    tap('동의하고 시연 경로 요청')
    tap('[시연] 주차장 도착')
    tap('주차를 마치고 안전하게 내릴 수 있어요')
    tap('주차 완료 · 만남 장소 보기')
    find('함께 여기로 이동해요')
    screenshot(current, '06-together')


try:
    deadline = time.monotonic() + a.boot_timeout
    stable = []
    while time.monotonic() < deadline:
        try:
            boot = adb('shell', 'getprop', 'sys.boot_completed', timeout=10)
            pid = adb('shell', 'pidof', 'system_server', timeout=10)
            stable.append(pid if boot == '1' else '')
            print('Pleos boot:', boot, 'system_server:', pid, flush=True)
            if len(stable) >= 3 and pid and all(x == pid for x in stable[-3:]):
                adb('shell', 'pm', 'path', 'android', timeout=20)
                break
        except Exception as e:
            print('Waiting for Pleos:', type(e).__name__, flush=True)
            stable.append('')
        time.sleep(10)
    else:
        raise RuntimeError('Pleos image did not reach a stable boot; no app success claimed')
    props = {k: adb('shell', 'getprop', k) for k in [
        'ro.build.fingerprint', 'ro.build.version.sdk', 'ro.product.cpu.abilist',
        'ro.product.manufacturer', 'ro.product.model', 'sys.boot_completed']}
    assert props['ro.build.version.sdk'] == '34'
    assert 'x86_64' in props['ro.product.cpu.abilist']
    result['device'] = props
    result['display'] = {'size': adb('shell', 'wm', 'size'), 'density': adb('shell', 'wm', 'density')}
    result['stable_system_server_pid'] = stable[-1]
    screenshot(out, '00-pleos-system')
    park_virtual_vehicle()
    for flavor in ['pleos', 'demo']:
        current = out / flavor
        current.mkdir(exist_ok=True)
        package = 'ai.arrivalcare.app.' + flavor
        apk = pathlib.Path(a.apks) / ('arrivalcare-' + flavor + '-offline-0.3.0.apk')
        expected = hashlib.sha256(apk.read_bytes()).hexdigest()
        item = {'flavor': flavor, 'package': package, 'apk_sha256': expected, 'checks': [], 'success': False}
        result['variants'].append(item)
        try:
            install = adb('install', '--no-streaming', '-r', str(apk), timeout=150)
            (current / 'install.txt').write_text(install, encoding='utf-8')
            assert 'Success' in install, install
            path = adb('shell', 'pm', 'path', package).splitlines()[0].removeprefix('package:')
            installed = adb('shell', 'sha256sum', path).split()[0]
            assert installed == expected, 'Installed APK differs from submitted APK'
            item['installed_sha256'] = installed
            (current / 'package.txt').write_text(adb('shell', 'dumpsys', 'package', package), encoding='utf-8')
            start()
            screenshot(current, '00-launch')
            happy()
            item['checks'].append('parents_dropoff_parking_reunion_complete')
            item['checks'].append('no_exit_or_parking_without_explicit_confirmation')
            together()
            item['checks'].append('sole_caregiver_skips_dropoff')
            item['success'] = True
        except Exception as e:
            item['error'] = str(e)
            try:
                screenshot(current, 'failure')
            except Exception:
                pass
        finally:
            collect(current)
            save()
            print(json.dumps(item, ensure_ascii=True), flush=True)
    passed = [v for v in result['variants'] if v['success']]
    assert passed, 'Neither signed offline APK completed the actual Pleos UI journey'
    result['selected_submission_flavor'] = passed[0]['flavor']
    result['selected_apk_sha256'] = passed[0]['apk_sha256']
    result['success'] = True
except Exception as e:
    result['error'] = str(e)
    collect(out)
    try:
        screenshot(out, 'runtime-failure')
    except Exception:
        pass
finally:
    save()
    print(json.dumps(result, ensure_ascii=True, indent=2), flush=True)
if not result['success']:
    raise SystemExit(1)
