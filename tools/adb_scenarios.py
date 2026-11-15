"""Real-device UI checks and screen recording. Requires Python 3 and adb on PATH.
Run: python tools/adb_scenarios.py --serial emulator-5554 --out evidence
"""
import argparse, pathlib, re, subprocess, time, xml.etree.ElementTree as ET, json

p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',default='emulator-5554');p.add_argument('--out',default='evidence');p.add_argument('--video',action='store_true')
args=p.parse_args();out=pathlib.Path(args.out);out.mkdir(parents=True,exist_ok=True)
prefix=[args.adb,'-s',args.serial];package='ai.arrivalcare.app.demo'
coordinates=json.loads((out/'tap-locations.json').read_text(encoding='utf-8')) if args.video and (out/'tap-locations.json').exists() else {}
def adb(*cmd, binary=False):
    return subprocess.check_output(prefix+list(cmd),timeout=35,text=not binary,encoding=None if binary else 'utf-8',errors=None if binary else 'replace')
def tree():
    for attempt in range(4):
        result=adb('shell','uiautomator','dump','/sdcard/arrival-ui.xml')
        if 'dumped to' in result:
            return ET.fromstring(adb('shell','cat','/sdcard/arrival-ui.xml'))
        time.sleep(.8)
    raise RuntimeError('UIAutomator did not provide a fresh hierarchy; stale XML was not used')
def find(label, exact=False, enabled=None):
    for attempt in range(3):
        current=tree()
        for node in current.iter('node'):
            texts=[node.get('text',''),node.get('content-desc','')]
            if any((text==label if exact else label in text) for text in texts) and (enabled is None or node.get('enabled')==str(enabled).lower()): return node
        time.sleep(.6)
    (out/'last-ui-failure.xml').write_bytes(ET.tostring(current,encoding='utf-8'))
    raise AssertionError('Visible UI node not found: '+label)
def tap(label, exact=False):
    if args.video:
        if label not in coordinates:raise RuntimeError('Run UI verification first to collect observed touch bounds: '+label)
        x,y=coordinates[label]
    else:
        node=find(label,exact,True);x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']));x,y=(x1+x2)//2,(y1+y2)//2
        coordinates.setdefault(label,[x,y]);(out/'tap-locations.json').write_text(json.dumps(coordinates,ensure_ascii=False,indent=2),encoding='utf-8')
    adb('shell','input','tap',str(x),str(y));time.sleep(.45)
def expect(label, enabled=None): return find(label,enabled=enabled)
def screenshot(name): (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
def start():
    adb('shell','am','force-stop',package);adb('shell','am','start','-W','-n',package+'/ai.arrivalcare.app.MainActivity');time.sleep(1)
def simulator(label):tap('시뮬레이터',True);tap(label,True)
def arrive_exit():tap('이 계획으로 안내 시작',True);time.sleep(1.2);tap('[시연] 하차 지점 도착',True)
results=[]
def record(name, f):
    start();f();results.append({'scenario':name,'result':'PASS'});print('PASS '+name,flush=True)
def happy():
    screenshot('01-plan');arrive_exit();expect('모두 내렸나요')
    tap('안전하게 정차·주차했어요');tap('보호자와 동승자가 모두 내렸어요');screenshot('02-exit')
    tap('확인하고 주차장 안내',True);time.sleep(1.2);expect('이제, 주차장으로');screenshot('03-parking-route')
    tap('[시연] 주차장 도착',True);tap('주차를 마치고 안전하게 내릴 수 있어요');tap('주차 완료 · 만남 장소 보기',True)
    expect('여기서 다시 만나요');expect('1층 안내데스크 옆 대기공간');screenshot('04-reunion')
    tap('만남 장소 확인했어요',True);expect('도착을 함께 마쳤어요')
def alone():
    tap('별도 보호자 없음',True);expect('운전자가 유일한 보호자');tap('이 계획으로 안내 시작',True);time.sleep(1.2)
    expect('모두 함께 주차해요');tap('[시연] 주차장 도착',True);tap('주차를 마치고 안전하게 내릴 수 있어요');tap('주차 완료 · 만남 장소 보기',True)
    expect('함께 여기로 이동해요');screenshot('05-together')
def doors():
    arrive_exit();simulator('도어 열림');expect('확인하고 주차장 안내',False)
    tap('안전하게 정차·주차했어요');tap('보호자와 동승자가 모두 내렸어요');expect('확인하고 주차장 안내',False)
    simulator('도어 닫힘');expect('모두 내렸나요');expect('확인하고 주차장 안내',True)
def missing():
    arrive_exit();simulator('차량 연결 실패');tap('안전하게 정차·주차했어요');tap('보호자와 동승자가 모두 내렸어요')
    expect('확인하고 주차장 안내',False);tap('모든 문이 닫힌 것을 직접 확인했어요');expect('확인하고 주차장 안내',True);screenshot('06-manual-fallback')
def driving():
    simulator('주행 상태');expect('주행 중에는 안내에 집중해 주세요');screenshot('07-driving-lock')
    simulator('정차 상태');expect('우리의 도착 계획')
def retry():
    simulator('다음 내비 요청 실패');tap('이 계획으로 안내 시작',True);time.sleep(1.2);expect('경로 안내 다시 시도');screenshot('08-route-error')
    tap('경로 안내 다시 시도',True);time.sleep(1.2);expect('[시연] 하차 지점 도착')
def delayed():
    simulator('다음 내비 응답 지연');tap('이 계획으로 안내 시작',True);time.sleep(2.2);tap('경로 안내 다시 시도',True);time.sleep(5)
    expect('[시연] 하차 지점 도착');tap('시뮬레이터',True);expect('무시한 이전 응답: 1건');screenshot('09-stale-response')
def selection():
    tap('가상 B쇼핑몰',True);tap('휠체어 이용자',True);tap('함께 이동하기',True);tap('이 계획으로 안내 시작',True);time.sleep(1.2)
    tap('[시연] 주차장 도착',True);tap('주차를 마치고 안전하게 내릴 수 있어요');tap('주차 완료 · 만남 장소 보기',True)
    expect('1층 가족 라운지 앞');expect('휠체어 이동 전');screenshot('10-mall-wheelchair')
if args.video:
    if not coordinates:raise RuntimeError('Run the UI scenarios on this device first; recording uses their observed button coordinates.')
    if '1280x720' not in adb('shell','wm','size'):raise RuntimeError('This recording profile requires the tested 1280x720 landscape display.')
    start();adb('shell','settings','put','system','show_touches','1')
    proc=subprocess.Popen(prefix+['shell','screenrecord','--size','1280x720','--bit-rate','3000000','--time-limit','85','/sdcard/arrivalcare-demo.mp4'])
    time.sleep(8);tap('이 계획으로 안내 시작',True);time.sleep(6);tap('[시연] 하차 지점 도착',True);time.sleep(4)
    simulator('도어 열림');time.sleep(3);simulator('도어 닫힘');time.sleep(4)
    tap('안전하게 정차·주차했어요');time.sleep(3);tap('보호자와 동승자가 모두 내렸어요');time.sleep(4)
    tap('확인하고 주차장 안내',True);time.sleep(7);tap('[시연] 주차장 도착',True);time.sleep(4)
    tap('주차를 마치고 안전하게 내릴 수 있어요');time.sleep(3);tap('주차 완료 · 만남 장소 보기',True);time.sleep(9)
    tap('만남 장소 확인했어요',True)
    stages=re.findall(r'STAGE (\w+)',adb('logcat','-d','-s','ArrivalCare:I'))
    if not stages or stages[-1]!='DONE':raise AssertionError('Recorded touch sequence did not reach DONE')
    proc.wait(timeout=95);adb('pull','/sdcard/arrivalcare-demo.mp4',str(out/'arrivalcare-demo.mp4'));adb('shell','settings','put','system','show_touches','0')
else:
    for name,f in [('parents_hospital_end_to_end',happy),('sole_caregiver_skips_dropoff',alone),('door_cycle_never_auto_completes',doors),('disconnected_door_manual_fallback',missing),('driving_restriction',driving),('navigation_retry',retry),('late_callback_ignored',delayed),('mall_wheelchair_conditions',selection)]:record(name,f)
    (out/'ui-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    (out/'app-logcat.txt').write_text(adb('logcat','-d','-s','ArrivalCare:I','AndroidRuntime:E'),encoding='utf-8')
