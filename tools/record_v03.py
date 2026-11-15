"""Record three real v0.3 scenarios from observed tap coordinates.
Collect with adb_v03_scenarios.py --only movie --tap-tape FILE on identical geometry.
No application state injection, generated UI, or animation compositing.
"""
import argparse,json,pathlib,subprocess,time,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);p.add_argument('--tape',required=True);p.add_argument('--out',required=True)
a=p.parse_args();adb=[a.adb,'-s',a.serial]
def run(*args,timeout=50):return subprocess.check_output(adb+list(args),timeout=timeout)
def start():
    run('shell','am','force-stop','ai.arrivalcare.app.demo')
    run('shell','am','start','-W','-n','ai.arrivalcare.app.demo/ai.arrivalcare.app.MainActivity');time.sleep(1)
actions=json.loads(pathlib.Path(a.tape).read_text(encoding='utf-8'))
assert actions[0]==['start'];actions=actions[1:]
start();remote='/sdcard/arrivalcare-v03-demo.mp4'
record=subprocess.Popen(adb+['shell','screenrecord','--bit-rate','3500000','--time-limit','89',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
started=time.monotonic();time.sleep(4)
for i,command in enumerate(actions):
    if command==['start']:start()
    else:run('shell','input',*command)
    print('ACTION',i+1,len(actions),round(time.monotonic()-started,1),flush=True)
    remaining=4+(i+1)*72/max(len(actions),1)-(time.monotonic()-started)
    if remaining>0:time.sleep(remaining)
assert time.monotonic()-started<87,'Replay exceeded recording window; repeat with a shorter tape.'
record.communicate(timeout=45);assert record.returncode==0
out=pathlib.Path(a.out);out.parent.mkdir(parents=True,exist_ok=True);run('pull',remote,str(out))
out.with_suffix('.png').write_bytes(run('exec-out','screencap','-p'))
for attempt in range(8):
    r=run('shell','uiautomator','dump','--compressed','/sdcard/arrival-v03-record.xml')
    if b'dumped to' in r:break
    time.sleep(1)
else:raise RuntimeError('No fresh final hierarchy')
xml=run('shell','cat','/sdcard/arrival-v03-record.xml');out.with_suffix('.xml').write_bytes(xml)
assert any(n.get('content-desc')=='[시연] 하차 지점 도착' for n in ET.fromstring(xml).iter('node')),'Final retry recovery was not reached'
print('Three-scenario replay complete; final route recovery verified',flush=True)
