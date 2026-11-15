"""Record real UI using taps observed by adb_v02_scenarios --only happy --tap-tape.
Only replay on the SAME emulator, installed APK, orientation and display geometry.
No app-state injection; validate the final UI and inspect the resulting video.
"""
import argparse,json,pathlib,subprocess,time,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--tape',required=True);p.add_argument('--out',required=True)
a=p.parse_args();adb=['adb','-s',a.serial]
def run(*args):return subprocess.check_output(adb+list(args),timeout=50)
taps=json.loads(pathlib.Path(a.tape).read_text(encoding='utf-8'))
# The happy test's final tap creates a new journey. End the recording at DONE instead.
assert taps[-1][0]=='tap';taps=taps[:-1]
run('shell','am','force-stop','ai.arrivalcare.app.demo')
run('shell','am','start','-W','-n','ai.arrivalcare.app.demo/ai.arrivalcare.app.MainActivity');time.sleep(2)
remote='/sdcard/arrivalcare-v02-demo.mp4'
record=subprocess.Popen(adb+['shell','screenrecord','--bit-rate','3500000','--time-limit','84',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
started=time.monotonic();time.sleep(6)
step=62/max(len(taps),1)
for index,command in enumerate(taps):
    run('shell','input',*command)
    print('ACTION',index+1,len(taps),flush=True)
    remaining=6+(index+1)*step-(time.monotonic()-started)
    if remaining>0:time.sleep(remaining)
record.communicate(timeout=45)
assert record.returncode==0,'screenrecord failed'
target=pathlib.Path(a.out);target.parent.mkdir(parents=True,exist_ok=True)
run('pull',remote,str(target))
target.with_suffix('.png').write_bytes(run('exec-out','screencap','-p'))
for attempt in range(8):
    r=run('shell','uiautomator','dump','--compressed','/sdcard/arrival-record.xml')
    if b'dumped to' in r:break
    time.sleep(1)
else:raise RuntimeError('No fresh final UI hierarchy')
xml=run('shell','cat','/sdcard/arrival-record.xml')
target.with_suffix('.xml').write_bytes(xml)
assert any(n.get('text')=='도착을 함께 마쳤어요' for n in ET.fromstring(xml).iter('node')),'Video must reach DONE'
print('Recorded real UI; final DONE verified',flush=True)
