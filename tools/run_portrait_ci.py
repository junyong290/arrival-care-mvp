"""Exercise the exact signed v0.3.0 candidate; no keys or signing secrets in CI."""
from pathlib import Path
import subprocess,hashlib,json,os,sys
apk=Path('ci-apk/arrivalcare-demo-offline-0.3.0.apk')
expected='bfd4384dcd391e888021febb8d61c89ccd44ee0144e3ae96ce4fdcdfb31734e3'
assert hashlib.sha256(apk.read_bytes()).hexdigest()==expected
def adb(*args):return subprocess.check_output(['adb','-s','emulator-5554',*args],text=True,timeout=40).strip()
print(adb('install','-r',str(apk)),flush=True)
installed=adb('shell','pm','path','ai.arrivalcare.app.demo').removeprefix('package:').splitlines()[0]
assert adb('shell','sha256sum',installed).split()[0]==expected
scope={'application_commit':'4f5ff2a83b62bd84ec6a2c1b83a67396eb5ae96f','apk':apk.name,'installed_apk_sha256':expected,'installed_bytes_match':True,'emulator':True,'host':'GitHub Actions Ubuntu KVM','workflow_run':os.environ.get('GITHUB_RUN_ID'),'model':adb('shell','getprop','ro.product.model'),'api':adb('shell','getprop','ro.build.version.sdk'),'abi':adb('shell','getprop','ro.product.cpu.abi'),'size':adb('shell','wm','size'),'density':adb('shell','wm','density')}
for case,folder in [('happy','portrait'),('missing','portrait-missing'),('night','portrait-night'),('markers','portrait-markers')]:
    out=Path('ci-evidence')/folder;out.mkdir(parents=True,exist_ok=True)
    (out/'runtime-scope.json').write_text(json.dumps(scope,indent=2))
    subprocess.run([sys.executable,'tools/adb_v03_scenarios.py','--serial','emulator-5554','--orientation','current','--only',case,'--out',str(out)],check=True)
print('All four portrait scenarios passed on the exact signed APK',flush=True)
