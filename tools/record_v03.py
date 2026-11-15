"""Record real v0.3 scenarios from observed tap coordinates.
Collect with adb_v03_scenarios.py --only movie --tap-tape FILE on identical geometry.
No application state injection, generated UI, or animation compositing.
"""
import argparse,json,pathlib,subprocess,time,re,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);p.add_argument('--tape',required=True);p.add_argument('--out',required=True);p.add_argument('--story',choices=['parents','all'],default='parents')
a=p.parse_args();adb=[a.adb,'-s',a.serial];out=pathlib.Path(a.out);out.parent.mkdir(parents=True,exist_ok=True);checkpoints=[]
def run(*args,timeout=50):return subprocess.check_output(adb+list(args),timeout=timeout)
def start():
    run('shell','am','force-stop','ai.arrivalcare.app.demo')
    run('shell','am','start','-W','-n','ai.arrivalcare.app.demo/ai.arrivalcare.app.MainActivity');time.sleep(1)
actions=json.loads(pathlib.Path(a.tape).read_text(encoding='utf-8'))
assert actions[0]==['start'];actions=actions[1:]
if a.story=='parents':actions=actions[:next(i for i,c in enumerate(actions) if c==['checkpoint','parents-done'])+1]
start();remote='/sdcard/arrivalcare-v03-demo.mp4'
record=subprocess.Popen(adb+['shell','screenrecord','--bit-rate','2500000','--time-limit','150',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
started=time.monotonic();time.sleep(2);segments=[];scene_start=0
for i,command in enumerate(actions):
    if command==['start']:
        start();scene_start=time.monotonic()-started
    elif command[0]=='checkpoint':
        name=command[1];assert re.fullmatch(r'[a-z-]+',name)
        time.sleep(.5)
        checkpoint=out.with_name(out.stem+'-'+name+'.png');checkpoint.write_bytes(run('exec-out','screencap','-p'))
        elapsed=time.monotonic()-started
        checkpoints.append({'name':name,'elapsed_seconds':round(elapsed,2),'screenshot':checkpoint.name})
        segments.append({'name':name,'start':round(scene_start,2),'end':round(elapsed,2)})
    else:
        assert command[0] in ['tap','swipe'] and all(re.fullmatch(r'\d+',str(v)) for v in command[1:])
        run('shell','input',*command);time.sleep(.45)
    print('ACTION',i+1,len(actions),round(time.monotonic()-started,1),flush=True)
    if a.story=='parents':
        remaining=4+(i+1)*68/len(actions)-(time.monotonic()-started)
        if remaining>0:time.sleep(remaining)
assert time.monotonic()-started<140,'Replay exceeded source recording window'
time.sleep(2)
pids=run('shell','pidof','screenrecord').decode().split();assert pids and all(p.isdigit() for p in pids)
run('shell','kill','-2',*pids)
record.communicate(timeout=30);assert record.returncode==0
out=pathlib.Path(a.out);out.parent.mkdir(parents=True,exist_ok=True);run('pull',remote,str(out))
out.with_suffix('.png').write_bytes(run('exec-out','screencap','-p'))
for attempt in range(8):
    r=run('shell','uiautomator','dump','--compressed','/sdcard/arrival-v03-record.xml')
    if b'dumped to' in r:break
    time.sleep(1)
else:raise RuntimeError('No fresh final hierarchy')
xml=run('shell','cat','/sdcard/arrival-v03-record.xml');out.with_suffix('.xml').write_bytes(xml)
if a.story=='all':
    assert any(n.get('content-desc')=='확인하고 주차장 안내' and n.get('enabled')=='true' for n in ET.fromstring(xml).iter('node')),'Final explicit door fallback was not reached'
else:assert any('도착을 함께 마쳤어요' in n.get('text','') for n in ET.fromstring(xml).iter('node')),'Parents journey did not reach completion'
out.with_suffix('.checkpoints.json').write_text(json.dumps(checkpoints,indent=2),encoding='utf-8')
out.with_suffix('.segments.json').write_text(json.dumps(segments,indent=2),encoding='utf-8')
print('Replay complete; final state verified: '+a.story,flush=True)
