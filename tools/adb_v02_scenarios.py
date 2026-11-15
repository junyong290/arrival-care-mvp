"""Real UI interactions for the responsive v0.2 UI. No direct state injection.
Run: python tools/adb_v02_scenarios.py --serial DEVICE --out evidence/v0.2.0/portrait
Physical-device rotation settings are restored in finally.
"""
import argparse,json,pathlib,re,subprocess,time,xml.etree.ElementTree as ET
p=argparse.ArgumentParser()
p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True)
p.add_argument('--package',default='ai.arrivalcare.app.demo');p.add_argument('--out',required=True)
p.add_argument('--only',choices=['all','happy','markers','rotate'],default='all')
p.add_argument('--orientation',choices=['portrait','landscape'],default='portrait')
a=p.parse_args();out=pathlib.Path(a.out);out.mkdir(parents=True,exist_ok=True)
prefix=[a.adb,'-s',a.serial]
def adb(*args,binary=False,timeout=45):
    return subprocess.check_output(prefix+list(args),timeout=timeout,text=not binary,encoding=None if binary else 'utf-8',errors=None if binary else 'replace')
def tree():
    for _ in range(4):
        result=adb('shell','uiautomator','dump','/sdcard/arrival-v02.xml')
        if 'dumped to' in result:return ET.fromstring(adb('shell','cat','/sdcard/arrival-v02.xml'))
        time.sleep(.6)
    raise RuntimeError('No fresh UI hierarchy; stale XML not accepted.')
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds',''))))
def match(root,label,enabled=None):
    for n in root.iter('node'):
        if label in [n.get('text'),n.get('content-desc')] and (enabled is None or n.get('enabled')==str(enabled).lower()):
            rect=bounds(n)
            if len(rect)==4 and rect[2]>rect[0] and rect[3]>rect[1]:return n
def find(label,enabled=None,contains=False):
    for attempt in range(9):
        root=tree();node=match(root,label,enabled)
        if node is None and contains:
            node=next((n for n in root.iter('node') if label in n.get('text','') or label in n.get('content-desc','')),None)
        if node is not None:return node
        scrolls=[n for n in root.iter('node') if n.get('scrollable')=='true']
        if not scrolls:break
        x1,y1,x2,y2=bounds(scrolls[-1]);x=(x1+x2)//2
        # Down through content first; then back to the top if the target is above.
        start,end=(y2-24,y1+24) if attempt<5 else (y1+24,y2-24)
        adb('shell','input','swipe',str(x),str(start),str(x),str(end),'280');time.sleep(.35)
    (out/'last-ui-failure.xml').write_bytes(ET.tostring(root,encoding='utf-8'))
    raise AssertionError('UI element not found: '+label)
def tap(label):
    n=find(label,True);x1,y1,x2,y2=bounds(n)
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)
def expect(label,enabled=None):return find(label,enabled,True)
def shot(name):(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
def rotate(value):
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation',str(value));time.sleep(1.5)
def start():
    adb('shell','am','force-stop',a.package)
    adb('shell','am','start','-W','-n',a.package+'/ai.arrivalcare.app.MainActivity')
    time.sleep(1.2)
def setup_change(*labels):
    tap('동행 조건 변경')
    for label in labels:tap(label)
    tap('조건 적용')
def sim(label):tap('시뮬레이터');tap(label)
def to_exit():
    tap('이 계획으로 안내 시작');time.sleep(1);tap('[시연] 하차 지점 도착');expect('모두 내렸나요')
def happy():
    shot('01-plan');to_exit();shot('02-exit')
    tap('안전하게 정차·주차했어요');tap('보호자와 동승자가 모두 내렸어요');tap('확인하고 주차장 안내')
    time.sleep(1);expect('이제, 주차장으로');shot('03-parking-route')
    tap('[시연] 주차장 도착');tap('주차를 마치고 안전하게 내릴 수 있어요');tap('주차 완료 · 만남 장소 보기')
    expect('여기서 다시 만나요');expect('1층 안내데스크 옆 대기공간');shot('04-reunion')
    tap('만남 장소 확인했어요');expect('도착을 함께 마쳤어요');shot('05-done')
    tap('새로운 도착 계획 만들기');expect('어디로 함께 갈까요?')
def alone():
    setup_change('별도 보호자 없음');expect('운전자가 유일한 보호자')
    tap('이 계획으로 안내 시작');time.sleep(1);expect('모두 함께 주차해요')
    tap('[시연] 주차장 도착');tap('주차를 마치고 안전하게 내릴 수 있어요');tap('주차 완료 · 만남 장소 보기')
    expect('함께 여기로 이동해요');shot('06-together')
def doors():
    to_exit();sim('도어 열림');tap('안전하게 정차·주차했어요');tap('보호자와 동승자가 모두 내렸어요')
    find('확인하고 주차장 안내',False);sim('도어 닫힘');expect('모두 내렸나요');find('확인하고 주차장 안내',True)
def missing():
    to_exit();sim('차량 연결 실패');tap('안전하게 정차·주차했어요');tap('보호자와 동승자가 모두 내렸어요')
    find('확인하고 주차장 안내',False);tap('모든 문이 닫힌 것을 직접 확인했어요');find('확인하고 주차장 안내',True);shot('07-manual-fallback')
def driving():
    sim('주행 상태');expect('주행 중에는 안내에 집중해 주세요')
    find('정차 후 이용해 주세요',False);shot('08-driving-lock')
    sim('정차 상태');expect('어디로 함께 갈까요?')
def retry():
    sim('다음 내비 요청 실패');tap('이 계획으로 안내 시작');time.sleep(1);expect('경로 안내 다시 시도');shot('09-route-error')
    tap('경로 안내 다시 시도');time.sleep(1);expect('[시연] 하차 지점 도착')
def late():
    sim('다음 내비 응답 지연');tap('이 계획으로 안내 시작');time.sleep(2);tap('경로 안내 다시 시도');time.sleep(5)
    expect('[시연] 하차 지점 도착');tap('시뮬레이터');expect('무시한 이전 응답: 1건');shot('10-stale-response')
def wheelchair():
    tap('가상 B쇼핑몰');setup_change('휠체어 이용자','함께 이동하기')
    tap('이 계획으로 안내 시작');time.sleep(1);tap('[시연] 주차장 도착')
    tap('주차를 마치고 안전하게 내릴 수 있어요');tap('주차 완료 · 만남 장소 보기')
    expect('1층 가족 라운지 앞');expect('휠체어 이동 전');shot('11-wheelchair')
def markers():
    for letter,title in [('D','하차'),('P','주차'),('M','재합류')]:
        tap(letter+' 마커 상세');expect(letter+' · '+title+' 지점');expect('UI 시연용 가상 위치');shot('12-marker-'+letter);tap('확인')
    expect('어디로 함께 갈까요?')
    tap('지도 상태');expect('지도 연결');expect('ai.arrivalcare.app.demo');shot('13-map-status');tap('확인')
def rotation():
    to_exit();rotate(1);expect('모두 내렸나요');shot('14-rotated-landscape')
    find('확인하고 주차장 안내',False)
    rotate(0);expect('모두 내렸나요');find('확인하고 주차장 안내',False);shot('15-rotated-portrait')

original={k:adb('shell','settings','get','system',k).strip() for k in ['accelerometer_rotation','user_rotation']}
results=[]
cases=[('parents_hospital_end_to_end',happy),('sole_caregiver_skips_dropoff',alone),('door_cycle_never_auto_completes',doors),
       ('disconnected_door_manual_fallback',missing),('driving_restriction',driving),('navigation_retry',retry),
       ('late_callback_ignored',late),('mall_wheelchair_conditions',wheelchair),('marker_details',markers),('rotation_preserves_unconfirmed_stage',rotation)]
selected=cases if a.only=='all' else [cases[{'happy':0,'markers':8,'rotate':9}[a.only]]]
try:
    rotate(0 if a.orientation=='portrait' else 1)
    metadata={}
    for key,prop in [('model','ro.product.model'),('android','ro.build.version.release'),('api','ro.build.version.sdk'),('abi','ro.product.cpu.abi'),('emulator','ro.kernel.qemu')]:
        metadata[key]=adb('shell','getprop',prop).strip()
    metadata['display']=adb('shell','wm','size').strip();metadata['orientation']=a.orientation;metadata['package']=a.package
    (out/'device.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
    for name,test in selected:
        start();test();results.append({'scenario':name,'result':'PASS'})
        (out/'ui-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
        print('PASS '+name,flush=True)
    (out/'app-logcat.txt').write_text(adb('logcat','-d','-s','ArrivalCare:I','AndroidRuntime:E'),encoding='utf-8')
finally:
    for key,value in original.items():
        if value=='null':adb('shell','settings','delete','system',key)
        else:adb('shell','settings','put','system',key,value)
