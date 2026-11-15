"""Prepare the unmodified official Pleos 2.1.0 image on an Ubuntu KVM runner.

Only logs and small environment metadata are placed in the artifact directory.
The proprietary image, its archive, and AVD userdata stay in RUNNER_TEMP.
The caller verifies boot and actual APK behavior; process launch is not success.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

PLEOS_XML = 'https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml'
PLEOS_URL = 'https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp_car_emulator.zip'
PLEOS_SIZE = 4087751964
PLEOS_SHA1 = 'b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa'
# Google's official emulator archive lists this retired version; the current
# repository2 XML only lists current release channels.
EMULATOR_URL = 'https://edgedl.me.gvt1.com/edgedl/android/repository/emulator-linux_x64-13610412.zip'
EMULATOR_SIZE = 319086252
EMULATOR_SHA256 = '2fe2b56fe93ce75e1d478a40162131381d911c355efeaedb54dd1e0d0897a5cf'
EMULATOR_ARCHIVE_SOURCE = 'https://developer.android.com/studio/emulator_archive'


def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'ArrivalCare-Pleos-Verification'})
    return urllib.request.urlopen(request, timeout=60)


def download(url, path, size, algorithm, expected):
    for attempt in range(3):
        digest = hashlib.new(algorithm)
        total = 0
        reported = time.monotonic()
        try:
            with fetch(url) as response, path.open('wb') as target:
                while block := response.read(4 * 1024 * 1024):
                    target.write(block)
                    digest.update(block)
                    total += len(block)
                    if time.monotonic() - reported > 20:
                        print(f'{path.name}: {total / 1e9:.2f}/{size / 1e9:.2f} GB', flush=True)
                        reported = time.monotonic()
            if total != size or digest.hexdigest() != expected:
                raise RuntimeError(f'{path.name}: size/checksum differs from pinned official artifact')
            print(f'{path.name}: {algorithm} verified', flush=True)
            return
        except Exception:
            path.unlink(missing_ok=True)
            if attempt == 2:
                raise
            time.sleep(3)


def extract(archive, destination):
    with zipfile.ZipFile(archive) as z:
        unpacked = sum(entry.file_size for entry in z.infolist())
        for entry in z.infolist():
            resolved = (destination / entry.filename).resolve()
            if not resolved.is_relative_to(destination.resolve()):
                raise RuntimeError('Archive path outside intended directory')
    # Leave space for writable AVD data; do not delete unrelated runner tools.
    if shutil.disk_usage(destination.parent).free < unpacked + 4 * 1024 ** 3:
        raise RuntimeError(f'Insufficient runner disk for {archive.name}: requires extraction plus 4 GiB reserve')
    destination.mkdir(parents=True, exist_ok=True)
    # Preserve executable modes from the official Linux emulator ZIP.
    subprocess.run(['unzip', '-q', str(archive), '-d', str(destination)], check=True)
    archive.unlink()


def diagnose(command, out, name):
    """Always retain loader errors instead of hiding CalledProcessError.output."""
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired) as error:
        stdout = getattr(error, 'stdout', '') or ''
        stderr = getattr(error, 'stderr', '') or ''
        if isinstance(stdout, bytes):
            stdout = stdout.decode(errors='replace')
        if isinstance(stderr, bytes):
            stderr = stderr.decode(errors='replace')
        result = subprocess.CompletedProcess(command, -1, stdout, stderr + '\n' + str(error))
    (out / f'{name}-stdout.txt').write_text(result.stdout, encoding='utf-8')
    (out / f'{name}-stderr.txt').write_text(result.stderr, encoding='utf-8')
    (out / f'{name}.txt').write_text(
        f'exit_code={result.returncode}\ncommand={command!r}\n'
        f'--- stdout ---\n{result.stdout}\n--- stderr ---\n{result.stderr}', encoding='utf-8')
    print(f'{name}: exit {result.returncode}', flush=True)
    if result.returncode:
        print(result.stdout + result.stderr, flush=True)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    scratch = Path(os.environ['RUNNER_TEMP']) / 'arrivalcare-pleos-runtime'
    scratch.mkdir(parents=True, exist_ok=False)
    sdk = Path(os.environ['ANDROID_HOME']).resolve()
    adb = sdk / 'platform-tools/adb'
    metadata = {
        'pleos_version': '2.1.0', 'android_api': 34, 'abi': 'x86_64',
        'image_url': PLEOS_URL, 'image_sha1': PLEOS_SHA1, 'image_bytes': PLEOS_SIZE,
        'emulator_version': '35.6.11', 'emulator_sha256': EMULATOR_SHA256,
        'emulator_checksum_source': EMULATOR_ARCHIVE_SOURCE,
        'configured_display': '2560x1440', 'density_dpi': 160, 'ram_mib': 4096,
        'cold_boot': True, 'gpu': 'swiftshader_indirect', 'virtualization': 'KVM',
        'scope': 'Actual Pleos system image; CI software GPU differs from recommended physical GPU',
        'free_disk_before_bytes': shutil.disk_usage(scratch).free,
        'boot_verified': False,
    }
    metadata_path = out / 'environment.json'
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding='utf-8')

    # Fail fast on Linux loader/runtime or KVM problems before the 4 GB image.
    emulator_zip = scratch / 'emulator-35.6.11.zip'
    download(EMULATOR_URL, emulator_zip, EMULATOR_SIZE, 'sha256', EMULATOR_SHA256)
    emulator_parent = scratch / 'emulator-host'
    extract(emulator_zip, emulator_parent)
    emulator = emulator_parent / 'emulator/emulator'
    diagnose(['ldd', str(emulator)], out, 'ldd-emulator-launcher')
    for filename in ['qemu-system-x86_64', 'qemu-system-x86_64-headless']:
        qemu = emulator.parent / 'qemu/linux-x86_64' / filename
        if qemu.is_file():
            diagnose(['ldd', str(qemu)], out, 'ldd-' + filename)
    # Direct ldd may report bundled libraries that the launcher adds to its
    # search path. The real executable result below is the pass/fail criterion.
    version = diagnose([str(emulator), '-version'], out, 'emulator-version')
    if version.returncode:
        raise RuntimeError(
            f'Emulator -version exited {version.returncode}; see emulator-version-stderr.txt and ldd evidence')
    if '35.6.11' not in version.stdout + version.stderr:
        raise RuntimeError('Unexpected emulator executable version')
    accel = diagnose([str(emulator), '-accel-check'], out, 'acceleration')
    if accel.returncode:
        raise RuntimeError('KVM acceleration is not available; see acceleration-stderr.txt')
    metadata['emulator_version_verified'] = True
    metadata['acceleration_verified'] = True
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding='utf-8')

    with fetch(PLEOS_XML) as response:
        document = ET.fromstring(response.read())
    package = next(node for node in document.iter()
                   if node.tag.rsplit('}', 1)[-1] == 'remotePackage'
                   and node.get('path') == 'system-images;android-34;pleos_car_emulator;x86_64')
    official = {node.tag.rsplit('}', 1)[-1]: (node.text or '').strip() for node in package.iter()}
    if official['url'] != PLEOS_URL or official['checksum'] != PLEOS_SHA1 or int(official['size']) != PLEOS_SIZE:
        raise RuntimeError('Official Pleos image changed; explicitly review the pinned version before testing')
    metadata['official_image_metadata_verified'] = True
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding='utf-8')

    image_zip = scratch / 'pleos-2.1.0.zip'
    download(PLEOS_URL, image_zip, PLEOS_SIZE, 'sha1', PLEOS_SHA1)
    image_parent = scratch / 'system-image'
    extract(image_zip, image_parent)
    image = image_parent / 'crp_avd_out_x86_64'
    for required in ['system.img', 'vendor.img', 'kernel-ranchu', 'ramdisk.img', 'userdata.img']:
        if not (image / required).is_file():
            raise RuntimeError(f'Incomplete official image: {required}')
    if 'Pleos Connect v2.1.0' not in (image / 'devices.xml').read_text():
        raise RuntimeError('Unexpected Pleos device definition')

    avd_home = scratch / 'avd'
    avd_dir = avd_home / 'ArrivalCarePleosCI.avd'
    avd_dir.mkdir(parents=True)
    (avd_home / 'ArrivalCarePleosCI.ini').write_text(
        f'avd.ini.encoding=UTF-8\npath={avd_dir}\ntarget=android-34\n')
    config = {
        'AvdId': 'ArrivalCarePleosCI', 'avd.ini.displayname': 'Pleos Connect 2.1.0 CI',
        'abi.type': 'x86_64', 'image.sysdir.1': str(image) + '/',
        'tag.id': 'android-automotive', 'tag.display': 'Pleos Connect v2.1.0 APIs',
        'hw.cpu.arch': 'x86_64', 'hw.cpu.ncore': '4', 'hw.ramSize': '4096',
        'hw.lcd.width': '2560', 'hw.lcd.height': '1440', 'hw.lcd.density': '160',
        'hw.gpu.enabled': 'yes', 'hw.gpu.mode': 'swiftshader_indirect',
        'hw.multi_display_window': 'no', 'hw.hotplug_multi_display': 'no',
        'hw.keyboard': 'yes', 'hw.dPad': 'no', 'hw.mainKeys': 'no',
        'hw.audioInput': 'yes', 'hw.sensors.orientation': 'yes',
        'hw.sensors.proximity': 'no', 'hw.sdCard': 'no',
        'disk.dataPartition.size': '4G', 'showDeviceFrame': 'no',
        'fastboot.forceColdBoot': 'yes', 'runtime.network.latency': 'none',
        'runtime.network.speed': 'full', 'skin.name': '2560x1440',
    }
    (avd_dir / 'config.ini').write_text(''.join(f'{key}={value}\n' for key, value in config.items()))
    env = os.environ.copy()
    env['ANDROID_AVD_HOME'] = str(avd_home)
    env['ANDROID_SDK_ROOT'] = str(sdk)
    subprocess.run([str(adb), 'start-server'], check=True)
    command = [str(emulator), '-avd', 'ArrivalCarePleosCI', '-no-window', '-no-snapshot',
               '-no-boot-anim', '-gpu', 'swiftshader_indirect', '-feature', '-Vulkan',
               '-no-metrics', '-no-audio', '-memory', '4096', '-cores', '4', '-port', '5554']
    with (out / 'emulator-stdout.txt').open('wb') as stdout, (out / 'emulator-stderr.txt').open('wb') as stderr:
        process = subprocess.Popen(command, env=env, stdout=stdout, stderr=stderr, start_new_session=True)
    metadata.update({'emulator_pid': process.pid, 'free_disk_after_setup_bytes': shutil.disk_usage(scratch).free,
                     'process_started': True, 'adb_serial': 'emulator-5554'})
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(f'Pleos emulator process {process.pid} started. Boot and APK verification remain pending.', flush=True)


if __name__ == '__main__':
    main()
