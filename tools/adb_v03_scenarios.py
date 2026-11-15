"""Real UI interactions for the responsive v0.3 UI. No direct state injection.
Run: python tools/adb_v03_scenarios.py --serial DEVICE --out evidence/v0.3.0/portrait
Physical-device rotation settings are restored in finally.
"""
import argparse,json,pathlib,re,struct,subprocess,time,xml.etree.ElementTree as ET
p=argparse.ArgumentParser()
p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True)
p.add_argument('--package',default='ai.arrivalcare.app.demo');p.add_argument('--out',required=True)
p.add_argument('--only',choices=['all','happy','markers','rotate','movie'],default='all')
p.add_argument('--orientation',choices=['portrait','landscape','current'],default='portrait')
p.add_argument('--tap-tape',help='Save observed taps/swipes for replay on the SAME emulator geometry')
p.add_argument('--start-at',type=int,default=0,help='Resume all cases at a zero-based index; preserves earlier results')
a=p.parse_args();out=pathlib.Path(a.out);out.mkdir(parents=True,exist_ok=True)
prefix=[a.adb,'-s',a.serial]
tape=[]

def remember(command):
    tape.append(command)
    if a.tap_tape:pathlib.Path(a.tap_tape).write_text(json.dumps(tape,ensure_ascii=False,indent=2),encoding='utf-8')
def adb(*args,binary=False,timeout=45):
    return subprocess.check_output(prefix+list(args),timeout=timeout,text=not binary,encoding=None if binary else 'utf-8',errors=None if binary else 'replace')
def tree(compressed=True):
    for _ in range(8):
        result=adb('shell','uiautomator','dump',*(['--compressed'] if compressed else []),'/sdcard/arrival-v03.xml')
        if 'dumped to' in result:return ET.fromstring(adb('shell','cat','/sdcard/arrival-v03.xml'))
        time.sleep(1.2)
    raise RuntimeError('No fresh UI hierarchy; stale XML not accepted.')
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds',''))))
def match(root,label,enabled=None):
    for n in root.iter('node'):
        if label in [n.get('text'),n.get('content-desc')] and (enabled is None or n.get('enabled')==str(enabled).lower()):
            rect=bounds(n)
            if len(rect)==4 and rect[2]>rect[0] and rect[3]>rect[1]:return n
def find(label,enabled=None,contains=False):
    previous=None;direction=1
    for attempt in range(12):
        root=tree();node=match(root,label,enabled)
        if node is None and contains:
            node=next((n for n in root.iter('node') if (label in n.get('text','') or label in n.get('content-desc','')) and (enabled is None or n.get('enabled')==str(enabled).lower())),None)
        if node is not None:return node
        scrolls=[n for n in root.iter('node') if n.get('scrollable')=='true']
        if not scrolls:break
        signature=ET.tostring(scrolls[-1])
        if signature==previous:direction=-direction
        previous=signature
        x1,y1,x2,y2=bounds(scrolls[-1]);x=x2-20
        start,end=(y2-30,y1+30) if direction>0 else (y1+30,y2-30)
        command=['swipe',str(x),str(start),str(x),str(end),'650'];remember(command)
        adb('shell','input',*command);time.sleep(1)
    (out/'last-ui-failure.xml').write_bytes(ET.tostring(root,encoding='utf-8'))
    raise AssertionError('UI element not found: '+label)
def tap(label):
    n=find(label,True);x1,y1,x2,y2=bounds(n)
    command=['tap',str((x1+x2)//2),str((y1+y2)//2)];remember(command)
    adb('shell','input',*command);time.sleep(.4)
def expect(label,enabled=None):return find(label,enabled,True)
def shot(name):(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
def rotate(value):
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation',str(value));time.sleep(1.5)
def start():
    remember(['start'])
    adb('shell','am','force-stop',a.package)
    adb('shell','am','start','-W','-n',a.package+'/ai.arrivalcare.app.MainActivity')
    time.sleep(1.2)
    expect('목적지 변경')
def setup_change(*labels):
    tap('동행 조건 변경')
    for label in labels:tap(label)
    tap('조건 적용')
def sim(label):tap('시뮬레이터');tap(label)
def to_exit():
    tap('이 계획으로 안내 시작');tap('동의하고 시연 경로 요청');time.sleep(1);tap('[시연] 하차 지점 도착');expect('모두 내렸나요')
def happy():
    layout();shot('01-plan');to_exit();shot('02-exit')
    tap('안전하게 정차·주차했어요');tap('보호자와 동승자가 모두 내렸어요');tap('확인하고 주차장 안내')
    expect('동의하고 시연 경로 요청');shot('03-parking-approval');tap('동의하고 시연 경로 요청');time.sleep(1);shot('03-parking-route')
    tap('[시연] 주차장 도착');tap('주차를 마치고 안전하게 내릴 수 있어요');tap('주차 완료 · 만남 장소 보기')
    expect('여기서 다시 만나요');expect('1층 안내데스크 옆 대기공간');shot('04-reunion')
    tap('만남 장소 확인했어요');expect('도착을 함께 마쳤어요');shot('05-done')
    tap('새로운 도착 계획 만들기');expect('목적지 변경')

def layout():
    root=tree(False)
    def rect(suffix):
        return bounds(next(n for n in root.iter('node') if n.get('resource-id','').endswith('/'+suffix)))
    m,g=rect('map_panel'),rect('guide_panel')
    display=adb('shell','wm','density')
    density=int(re.findall(r'(?:Physical|Override) density: (\d+)',display)[-1])
    screen=bounds(next(root.iter('node')))
    wide=screen[2]*160/density>=720 and screen[2]>screen[3]
    assert (m[2]<=g[0] if wide else m[3]<=g[1]),(m,g,screen,density)
    (out/'layout.json').write_text(json.dumps({'map':m,'guide':g,'density':density,'layout':'side_by_side' if wide else 'stacked','result':'PASS'},indent=2),encoding='utf-8')
def alone():
    setup_change('별도 보호자 없음');expect('처음부터 끝까지, 함께 이동해요')
    tap('이 계획으로 안내 시작');tap('동의하고 시연 경로 요청');time.sleep(1);expect('모두 함께 주차해요')
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
    sim('정차 상태');expect('목적지 변경')
def retry():
    sim('다음 내비 요청 실패');tap('이 계획으로 안내 시작');tap('동의하고 시연 경로 요청');time.sleep(1);expect('경로 안내 다시 시도');shot('09-route-error')
    tap('경로 안내 다시 시도');time.sleep(1);expect('[시연] 하차 지점 도착')
def late():
    sim('다음 내비 응답 지연');tap('이 계획으로 안내 시작');tap('동의하고 시연 경로 요청');time.sleep(2);tap('경로 안내 다시 시도');time.sleep(5)
    expect('[시연] 하차 지점 도착');tap('시뮬레이터');expect('무시한 이전 응답: 1건');shot('10-stale-response')
def wheelchair():
    tap('목적지 변경');tap('가상 B쇼핑몰 · 시연');setup_change('휠체어 이용자','함께 이동하기')
    tap('이 계획으로 안내 시작');tap('동의하고 시연 경로 요청');time.sleep(1);tap('[시연] 주차장 도착')
    tap('주차를 마치고 안전하게 내릴 수 있어요');tap('주차 완료 · 만남 장소 보기')
    expect('1층 승강기 앞 만남 공간');expect('휠체어 이동 전');shot('11-wheelchair')
def markers():
    for letter,title in [('D','하차'),('P','주차'),('M','재합류')]:
        tap(letter+' 마커 상세');expect(letter+' · '+title+' 지점');expect('UI 시연용 가상 위치');shot('12-marker-'+letter);tap('확인')
    expect('목적지 변경')
    tap('지도 상태');expect('지도 연결');expect('ai.arrivalcare.app.demo');shot('13-map-status');tap('확인')
def rotation():
    to_exit()
    emulator=adb('shell','getprop','ro.kernel.qemu').strip()=='1'
    old_size=adb('shell','wm','size');old_density=adb('shell','wm','density')
    try:
        if emulator:
            # Some headless API 28 images ignore user_rotation. Exercise actual window
            # configuration changes instead; never label a no-op rotation as passed.
            adb('shell','wm','size','1280x720');adb('shell','wm','density','160');time.sleep(2)
        else:rotate(1)
        expect('모두 내렸나요');find('확인하고 주차장 안내',False);layout();shot('14-rotated-landscape')
        (out/'landscape-layout.json').write_bytes((out/'layout.json').read_bytes())
        if emulator:assert json.loads((out/'landscape-layout.json').read_text())['layout']=='side_by_side'
        else:
            data=(out/'14-rotated-landscape.png').read_bytes();w,h=struct.unpack('>II',data[16:24]);assert w>h,(w,h)
        if emulator:
            adb('shell','wm','size','720x1280');adb('shell','wm','density','320');time.sleep(2)
        else:rotate(0)
        expect('모두 내렸나요');find('확인하고 주차장 안내',False);layout();shot('15-rotated-portrait')
        (out/'portrait-layout.json').write_bytes((out/'layout.json').read_bytes())
        if emulator:assert json.loads((out/'portrait-layout.json').read_text())['layout']=='stacked'
        else:
            data=(out/'15-rotated-portrait.png').read_bytes();w,h=struct.unpack('>II',data[16:24]);assert w<h,(w,h)
        (out/'resize-method.json').write_text(json.dumps({'method':'emulator wm size/density configuration changes' if emulator else 'physical device user rotation','unconfirmed_exit_remained_disabled':True},indent=2),encoding='utf-8')
    finally:
        if emulator:
            size=re.search(r'Override size: (\d+x\d+)',old_size)
            density=re.search(r'Override density: (\d+)',old_density)
            adb('shell','wm','size',size.group(1) if size else 'reset')
            adb('shell','wm','density',density.group(1) if density else 'reset')

def missing_accessibility():
    setup_change('휠체어 이용자','별도 보호자 없음')
    sim('접근성 정보 누락 시연');find('도착 계획 보류',False);shot('16-accessibility-hold')
    tap('확인이 필요한 정보 보기');expect('무단차 연결 정보가 없어');tap('확인')
    sim('가상 접근성 정보 복원');tap('이 계획으로 안내 시작')
    expect('모두 함께 주차해요');expect('P4 · 승강기 연결 주차장');shot('17-together-plan')

def actual_venue():
    tap('목적지 변경');tap('서울아산병원 · 공식 정보');find('도착 계획 보류',False)
    tap('병원 대표 위치 상세');expect('승하차 구역');shot('18-venue-evidence');tap('확인')

def night():
    tap('화면 테마');tap('다크');time.sleep(2);expect('목적지 변경');shot('19-night')
    tap('화면 테마');tap('라이트');time.sleep(2);expect('목적지 변경')

def background():
    to_exit();tap('안전하게 정차·주차했어요');tap('보호자와 동승자가 모두 내렸어요')
    find('확인하고 주차장 안내',True)
    adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(1)
    adb('shell','am','start','-W','-n',a.package+'/ai.arrivalcare.app.MainActivity');time.sleep(1)
    find('확인하고 주차장 안내',False);expect('모두 내렸나요');shot('20-background-recheck')

def no_implicit_route():
    tap('이 계획으로 안내 시작');time.sleep(2)
    expect('동의하고 시연 경로 요청')
    tap('시뮬레이터');expect('현재 단계: ROUTING_DROPOFF');tap('닫기')
    tap('동의하고 시연 경로 요청');time.sleep(1);tap('[시연] 하차 지점 도착')
    # Door state alone and tapping the disabled action must never advance the stage.
    sim('도어 열림');sim('도어 닫힘');find('확인하고 주차장 안내',False)
    expect('모두 내렸나요')

def movie():
    happy()
    start();setup_change('휠체어 이용자','별도 보호자 없음');sim('접근성 정보 누락 시연')
    find('도착 계획 보류',False);shot('21-movie-accessibility')
    start();retry();shot('22-movie-retry-recovered')

original={k:adb('shell','settings','get','system',k).strip() for k in ['accelerometer_rotation','user_rotation']}
results=json.loads((out/'ui-results.json').read_text(encoding='utf-8')) if a.start_at and (out/'ui-results.json').exists() else []
cases=[('parents_hospital_end_to_end',happy),('sole_caregiver_skips_dropoff',alone),('door_cycle_never_auto_completes',doors),
       ('disconnected_door_manual_fallback',missing),('driving_restriction',driving),('navigation_retry',retry),
       ('late_callback_ignored',late),('mall_wheelchair_conditions',wheelchair),('marker_details',markers),('window_configuration_preserves_unconfirmed_stage',rotation),('missing_accessibility_holds_plan',missing_accessibility),('real_venue_has_no_invented_DPM',actual_venue),('dark_light_theme',night),('background_requires_fresh_safety_checks',background),('route_requires_explicit_consent',no_implicit_route)]
selected=cases[a.start_at:] if a.only=='all' else ([('three_scenario_movie',movie)] if a.only=='movie' else [cases[{'happy':0,'markers':8,'rotate':9}[a.only]]])
try:
    if a.orientation!='current':rotate(0 if a.orientation=='portrait' else 1)
    metadata={}
    for key,prop in [('model','ro.product.model'),('android','ro.build.version.release'),('api','ro.build.version.sdk'),('abi','ro.product.cpu.abi'),('emulator','ro.kernel.qemu')]:
        metadata[key]=adb('shell','getprop',prop).strip()
    metadata['display']=adb('shell','wm','size').strip();metadata['requested_orientation']=a.orientation;metadata['package']=a.package
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
