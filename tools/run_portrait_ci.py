"""Exercise the same application source with a separate CI debug certificate."""
from pathlib import Path
import subprocess,hashlib,json,os,sys
apk=Path('app/build/outputs/apk/demoOffline/debug/app-demo-offline-debug.apk')
expected=hashlib.sha256(apk.read_bytes()).hexdigest()
def adb(*args):return subprocess.check_output(['adb','-s','emulator-5554',*args],text=True,timeout=40).strip()
print(adb('install','-r',str(apk)),flush=True)
installed=adb('shell','pm','path','ai.arrivalcare.app.demo').removeprefix('package:').splitlines()[0]
assert adb('shell','sha256sum',installed).split()[0]==expected
app_commit=subprocess.check_output(['git','log','-1','--format=%H','--','app'],text=True).strip()
# Checkout is shallow: use the frozen application revision after checking the tree.
subprocess.run(['git','fetch','--depth=1','origin','4f5ff2a83b62bd84ec6a2c1b83a67396eb5ae96f'],check=True)
subprocess.run(['git','diff','--exit-code','4f5ff2a83b62bd84ec6a2c1b83a67396eb5ae96f','HEAD','--','app'],check=True)
scope={'application_commit':'4f5ff2a83b62bd84ec6a2c1b83a67396eb5ae96f','variant':'demoOfflineDebug','certificate_matches_release':False,'apk':apk.name,'installed_apk_sha256':expected,'installed_bytes_match':True,'emulator':True,'host':'GitHub Actions Ubuntu KVM','workflow_run':os.environ.get('GITHUB_RUN_ID'),'model':adb('shell','getprop','ro.product.model'),'api':adb('shell','getprop','ro.build.version.sdk'),'abi':adb('shell','getprop','ro.product.cpu.abi'),'size':adb('shell','wm','size'),'density':adb('shell','wm','density')}
for case,folder in [('happy','portrait'),('missing','portrait-missing'),('night','portrait-night'),('markers','portrait-markers')]:
    out=Path('ci-evidence')/folder;out.mkdir(parents=True,exist_ok=True)
    (out/'runtime-scope.json').write_text(json.dumps(scope,indent=2))
    subprocess.run([sys.executable,'tools/adb_v03_scenarios.py','--serial','emulator-5554','--orientation','current','--only',case,'--out',str(out)],check=True)
print('All four portrait scenarios passed on the same app source with the CI debug certificate',flush=True)
