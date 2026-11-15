"""Bounded Pleos 2.1 diagnostic. A timed-out installer is checked by installed APK hash.
Run only after stopping other emulators and Gradle to leave memory for the 4 GB AVD.
"""
import argparse,pathlib,subprocess,time,json,hashlib,urllib.request
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--apks',required=True);p.add_argument('--out',required=True)
a=p.parse_args();out=pathlib.Path(a.out);out.mkdir(parents=True,exist_ok=True);adb=[a.adb,'-s','emulator-5554'];observations=[]
def run(name,args,timeout=35):
    try:
        r=subprocess.run(adb+args,capture_output=True,timeout=timeout)
        (out/name).write_bytes(r.stdout if name.endswith('.png') else r.stdout+r.stderr)
        observations.append({'step':name,'exit':r.returncode,'bytes':len(r.stdout)})
        return r
    except subprocess.TimeoutExpired:
        observations.append({'step':name,'result':'TIMEOUT'});return None
    finally:save()
def save():
    (out/'observations.json').write_text(json.dumps(observations,indent=2),encoding='utf-8')
def note(step,result):
    observations.append({'step':step,'result':result});save();print(step,result,flush=True)
try:
    deadline=time.monotonic()+300
    while time.monotonic()<deadline:
        try:
            r=subprocess.run(adb+['shell','getprop','sys.boot_completed'],capture_output=True,timeout=8)
            if r.stdout.strip()==b'1':break
        except subprocess.TimeoutExpired:pass
        time.sleep(6)
    else:raise RuntimeError('No boot completion within 300 seconds')
    note('boot','sys.boot_completed=1')
    run('virtual-console-forward.txt',['forward','tcp:8088','tcp:8088'])
    try:
        with urllib.request.urlopen('http://127.0.0.1:8088/',timeout=15) as response:
            content=response.read();(out/'virtual-console.html').write_bytes(content);note('virtual-console-http',str(response.status))
    except Exception as e:note('virtual-console-http',type(e).__name__)
    for variant in ['demo','pleos']:
        package='ai.arrivalcare.app.'+variant;apk=pathlib.Path(a.apks)/f'arrivalcare-{variant}-offline-0.3.0.apk'
        expected=hashlib.sha256(apk.read_bytes()).hexdigest()
        install=run(variant+'-install.txt',['install','-r',str(apk)],70)
        path=run(variant+'-installed-path.txt',['shell','pm','path',package],25)
        actual=None
        if path and path.returncode==0:
            installed=path.stdout.decode(errors='replace').strip().removeprefix('package:').splitlines()[0]
            digest=run(variant+'-installed-sha256.txt',['shell','sha256sum',installed],30)
            if digest and digest.returncode==0:actual=digest.stdout.decode().split()[0]
        note(variant+'-installed-apk-match','PASS' if actual==expected else 'NOT_VERIFIED')
        # Even if install -r timed out, a verified installed package is safe to launch.
        if actual!=expected and (install is None or install.returncode!=0):continue
        run(variant+'-launch.txt',['shell','am','start','-W','-n',package+'/ai.arrivalcare.app.MainActivity'],45)
        time.sleep(6)
        run(variant+'-screen.png',['exec-out','screencap','-p'],35)
        run(variant+'-ui-dump.txt',['shell','uiautomator','dump','--compressed','/sdcard/v03-pleos.xml'],25)
        run(variant+'-ui.xml',['shell','cat','/sdcard/v03-pleos.xml'],15)
        run(variant+'-logcat.txt',['logcat','-d','-s','ArrivalCareSDK:V','ArrivalCare:I','AndroidRuntime:E'],30)
        run(variant+'-activity.txt',['shell','dumpsys','activity','activities'],25)
        run(variant+'-package.txt',['shell','dumpsys','package',package],25)
    run('system-logcat.txt',['logcat','-d','-t','150'],30)
finally:save()
