package ai.arrivalcare.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.*
import android.widget.*
import ai.arrivalcare.app.data.*
import ai.arrivalcare.app.domain.*
import ai.arrivalcare.app.maps.*
import ai.arrivalcare.app.ui.*
import java.security.MessageDigest

class MainActivity:Activity(){
    private lateinit var ui:Ui
    private lateinit var journey:Journey
    private lateinit var navigation:NavigationGateway
    private lateinit var vehicle:VehicleGateway
    private lateinit var mapPanel:MapPanel
    private lateinit var content:LinearLayout
    private lateinit var actions:LinearLayout
    private lateinit var scroll:ScrollView
    private lateinit var stageBar:LinearLayout
    private lateinit var adaptive:AdaptiveJourneyLayout
    private lateinit var resetButton:ImageButton
    private val handler=Handler(Looper.getMainLooper())
    private val events=ArrayDeque<String>()
    private var lastStage:Stage?=null
    private var lastRequested=-1L
    private var renderPosted=false
    private var ready=false
    private var resumed=false
    private var dialog:AlertDialog?=null

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        ui=Ui(this);journey=Journey(SessionState.restore(savedInstanceState,BuildConfig.PLEOS))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Keep the OS/window chrome visible; never cover Pleos Driving view or window controls.
        navigation=Integration.navigation(this);vehicle=Integration.vehicle(this)
        createLayout();ready=true
        vehicle.connect({door->runOnUiThread{
            if(ready){journey.setDoor(door);log("도어: "+door);refresh()}
        }},{moving,connected->runOnUiThread{
            if(ready){
                journey.setMotion(moving,connected)
                if(!journey.state.inputAllowed)dialog?.dismiss()
                log("입력 제한: moving="+moving+" ready="+connected);refresh()
            }
        }})
        refresh()
    }
    override fun onResume(){super.onResume();resumed=true;if(ready){mapPanel.resume();vehicle.onForeground();refresh()}}
    override fun onPause(){
        resumed=false
        if(ready){content.animate().cancel();mapPanel.pause();vehicle.onBackground();journey.checkParked(false);journey.checkExited(false);journey.checkDoorsManually(false)}
        super.onPause()
    }
    override fun onSaveInstanceState(outState:Bundle){if(ready)SessionState.save(journey.state,outState);super.onSaveInstanceState(outState)}
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);dialog?.dismiss();adaptive.requestLayout();refresh()}
    override fun onMultiWindowModeChanged(isInMultiWindowMode:Boolean,newConfig:Configuration){super.onMultiWindowModeChanged(isInMultiWindowMode,newConfig);adaptive.requestLayout();refresh()}
    override fun onDestroy(){
        ready=false;dialog?.dismiss();handler.removeCallbacksAndMessages(null)
        if(::mapPanel.isInitialized)mapPanel.close()
        if(::navigation.isInitialized)navigation.close()
        if(::vehicle.isInitialized)vehicle.close()
        super.onDestroy()
    }
    @Deprecated("Guarded journey back action")
    override fun onBackPressed(){if(journey.state.inputAllowed)askReset()}

    private fun createLayout(){
        val root=ui.column().apply{setBackgroundColor(Palette.paper)}
        val header=ui.row().apply{setBackgroundColor(Color.WHITE);setPadding(ui.dp(16),ui.dp(4),ui.dp(12),ui.dp(4))}
        header.addView(ui.iconTile("brand",Color.WHITE,Palette.navy,36))
        val brand=ui.column()
        ui.label(brand,"도착케어",20,Palette.navy,true,2);ui.label(brand,"ARRIVAL CARE",9,Palette.muted,true,0)
        header.addView(brand,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=ui.dp(9)})
        header.addView(ui.pill(if(BuildConfig.PLEOS)"SDK 시연" else "시연 모드"),LinearLayout.LayoutParams(-2,-2).apply{rightMargin=ui.dp(8)})
        header.addView(ui.iconButton("시뮬레이터","more"){showSimulator()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        resetButton=ui.iconButton("새 여정","pin"){if(journey.state.inputAllowed)askReset()}
        header.addView(resetButton,LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply{leftMargin=ui.dp(6)})
        root.addView(header,LinearLayout.LayoutParams(-1,ui.dp(60)))
        stageBar=ui.row().apply{setPadding(ui.dp(18),ui.dp(6),ui.dp(18),ui.dp(8));setBackgroundColor(Color.WHITE)}
        root.addView(stageBar,LinearLayout.LayoutParams(-1,ui.dp(50)))
        mapPanel=MapPanel(this,{showPoint(it)},{showMapInfo()}).apply{id=R.id.map_panel}
        val guide=ui.column().apply{id=R.id.guide_panel;background=ui.shape(Color.WHITE,24);setPadding(ui.dp(18),ui.dp(18),ui.dp(18),ui.dp(10))}
        content=ui.column()
        scroll=ScrollView(this).apply{addView(content);isFillViewport=false;isVerticalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
        guide.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        actions=ui.column().apply{setPadding(0,ui.dp(8),0,0)}
        guide.addView(actions,LinearLayout.LayoutParams(-1,-2))
        adaptive=AdaptiveJourneyLayout(this,mapPanel,guide)
        root.addView(adaptive,LinearLayout.LayoutParams(-1,0,1f).apply{leftMargin=ui.dp(12);rightMargin=ui.dp(12);topMargin=ui.dp(10);bottomMargin=ui.dp(8)})
        setContentView(root)
    }
    private fun log(value:String){Log.i("ArrivalCare",value);events.addLast(value);while(events.size>30)events.removeFirst()}
    private fun refresh(){
        if(!ready || renderPosted)return
        renderPosted=true
        handler.post{
            renderPosted=false
            if(!ready || isFinishing || isDestroyed)return@post
            val s=journey.state
            val changed=lastStage!=s.stage
            if(changed){log("STAGE "+s.stage+" mode="+s.plan?.mode);lastStage=s.stage}
            if(resumed){render(changed)}
            val request=s.request
            if(request!=null && request.id!=lastRequested && s.error==null){
                lastRequested=request.id
                log("ROUTE request="+request.id+" destination="+request.destinationId)
                navigation.request(request){id,destination,success,error->runOnUiThread{
                    if(ready){journey.routeResult(id,destination,success,error);log("ROUTE result="+id+" success="+success+" ignored="+journey.state.ignoredResponses);refresh()}
                }}
            }
        }
    }
    private fun render(changed:Boolean){
        val oldY=if(changed)0 else scroll.scrollY
        val s=journey.state
        resetButton.isEnabled=s.inputAllowed;resetButton.alpha=if(s.inputAllowed)1f else .35f
        stageBar.removeAllViews();renderProgress()
        content.removeAllViews();actions.removeAllViews();mapPanel.update(s)
        if(!s.inputAllowed)locked()else when(s.stage){
            Stage.SETUP->setup()
            Stage.ROUTING_DROPOFF,Stage.EN_ROUTE_DROPOFF,Stage.ROUTING_PARKING,Stage.EN_ROUTE_PARKING->route()
            Stage.EXIT_CHECK->exitCheck()
            Stage.PARKING_CHECK->parkingCheck()
            Stage.REUNION,Stage.DONE->reunion()
        }
        scroll.post{scroll.scrollTo(0,oldY)}
        if(changed && s.inputAllowed){
            content.alpha=.25f;content.translationY=ui.dp(6).toFloat()
            content.animate().alpha(1f).translationY(0f).setDuration(160).start()
        }
    }
    private fun renderProgress(){
        val s=journey.state;val plan=s.plan?:Plan.create(s.conditions)
        val active=when(s.stage){Stage.SETUP->-1;Stage.ROUTING_DROPOFF,Stage.EN_ROUTE_DROPOFF,Stage.EXIT_CHECK->0;Stage.REUNION,Stage.DONE->2;else->1}
        Waypoint.entries.forEachIndexed{i,p->
            val skipped=i==0 && plan.mode==Mode.TOGETHER
            val selected=i==active
            val segment=ui.row()
            segment.addView(ui.text(if(skipped)"–" else p.letter,14,if(selected)Color.WHITE else Palette.muted,true).apply{
                gravity=Gravity.CENTER;background=ui.shape(if(selected)Palette.blue else Palette.paper,12)
            },LinearLayout.LayoutParams(ui.dp(30),ui.dp(30)))
            segment.addView(ui.text(if(skipped)"하차 생략" else p.title,13,if(selected)Palette.blue else Palette.muted,selected),
                LinearLayout.LayoutParams(-2,-2).apply{leftMargin=ui.dp(7)})
            stageBar.addView(segment,LinearLayout.LayoutParams(0,-1,1f))
            if(i<2)stageBar.addView(ImageView(this).apply{setImageDrawable(IconDrawable(this@MainActivity,"chevron",Palette.muted));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(ui.dp(14),ui.dp(16)).apply{rightMargin=ui.dp(8)})
        }
    }
    private fun heading(eyebrow:String,title:String,subtitle:String){
        ui.label(content,eyebrow,12,Palette.blue,true,7)
        ui.label(content,title,25,Palette.navy,true,9)
        ui.label(content,subtitle,14,Palette.muted,false,17)
    }
    private fun primary(title:String,enabled:Boolean=true,description:String=title,note:String?=null,action:()->Unit){
        actions.addView(ui.button(title,primary=true,enabled=enabled){action();refresh()}.apply{contentDescription=description;textSize=16f},LinearLayout.LayoutParams(-1,-2))
        note?.let{actions.addView(ui.text(it,11,Palette.muted).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply{topMargin=ui.dp(7)})}
    }
    private fun note(text:String,warning:Boolean=false){
        val v=ui.text(text,13,if(warning)Palette.amber else Palette.muted)
        v.background=ui.shape(if(warning)Palette.amberSoft else Palette.paper,14);v.setPadding(ui.dp(12),ui.dp(11),ui.dp(12),ui.dp(11))
        content.addView(v,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=ui.dp(12)})
    }
    private fun setup(){
        val c=journey.state.conditions;val p=Plan.create(c)
        heading("우리의 도착 계획","어디로 함께 갈까요?","마지막 이동까지, 일행의 속도에 맞춰요.")
        val facilities=ui.row()
        DemoFacilities.all.forEachIndexed{i,f->
            facilities.addView(ui.button(f.name,selected=f.id==c.facilityId,icon=if(i==0)"hospital" else "mall"){
                journey.configure(c.copy(facilityId=f.id));refresh()
            },LinearLayout.LayoutParams(0,-2,1f).apply{if(i>0)leftMargin=ui.dp(8)})
        }
        content.addView(facilities,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=ui.dp(14)})
        ui.card(content,if(c.facilityId=="hospital")"hospital" else "mall",p.facility.name,"하차 · 주차 · 만남 장소를 한 번에")
        ui.label(content,"누구와 함께하나요?",17,Palette.navy,true,9)
        val companion=ui.row().apply{background=ui.shape(Color.WHITE,18,Palette.line);setPadding(ui.dp(13),ui.dp(12),ui.dp(13),ui.dp(12))}
        companion.addView(ui.iconTile("meeting",Palette.navy,Palette.paper,42))
        val detail=ui.column()
        ui.label(detail,c.passengers.joinToString(" · "){it.label},16,Palette.navy,true,5)
        ui.label(detail,if(c.caregiver)"별도 보호자 동행" else "운전자가 유일한 보호자",13,Palette.muted,false,0)
        companion.addView(detail,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=ui.dp(10)})
        content.addView(companion,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=ui.dp(9)})
        content.addView(ui.button("동행 조건 변경",icon="person"){showConditions()},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=ui.dp(15)})
        ui.label(content,if(p.mode==Mode.DROP_FIRST)"먼저 내려드리고, 다시 만나요" else "주차부터 함께 이동해요",18,Palette.navy,true,8)
        note(p.reason,!c.caregiver)
        ui.card(content,"M",p.facility.meeting,"처음 정한 만남 장소를 끝까지 유지해요",Palette.green)
        primary("이 계획으로 안내 시작",note="가상 시설 시연 · 실제 경로 안내 아님"){journey.begin()}
    }
    private fun route(){
        val s=journey.state;val p=s.plan!!
        val drop=s.stage in setOf(Stage.ROUTING_DROPOFF,Stage.EN_ROUTE_DROPOFF)
        val loading=s.stage in setOf(Stage.ROUTING_DROPOFF,Stage.ROUTING_PARKING)
        heading(if(drop)"01 · 하차 장소 안내" else "02 · 주차장 안내",
            if(drop)"먼저, 하차 장소로" else if(p.mode==Mode.TOGETHER)"모두 함께 주차해요" else "이제, 주차장으로",
            if(drop)"동승자와 보호자가 안전하게 내릴 곳을 확인해요." else p.reason)
        ui.card(content,if(drop)"D" else "P",if(drop)p.facility.drop else p.facility.parking,p.facility.name)
        ui.card(content,"M",p.facility.meeting,"이곳에서 다시 만나요",Palette.green)
        note("지도 표시는 내비게이션 경로 안내와 달라요. 현재 경로·도착 신호는 시뮬레이션입니다.")
        if(s.error!=null){
            note(s.error,true);primary("경로 안내 다시 시도"){journey.retry()}
        }else if(loading){
            content.addView(ProgressBar(this),LinearLayout.LayoutParams(ui.dp(32),ui.dp(32)).apply{gravity=Gravity.CENTER_HORIZONTAL})
            primary("안내를 준비하고 있어요",enabled=false){}
        }else{
            primary(if(drop)"하차 지점 도착" else "주차장 도착",description=if(drop)"[시연] 하차 지점 도착" else "[시연] 주차장 도착",note="도착 버튼은 위치 신호를 대신하는 시연 동작입니다"){journey.arrived()}
        }
    }
    private fun exitCheck(){
        val s=journey.state;val p=s.plan!!
        heading("01 · 하차 확인","모두 내렸나요","정차와 동승자 하차를 직접 확인해 주세요.")
        ui.card(content,"D",p.facility.drop,p.facility.name)
        val door=when(s.door){Door.CLOSED->"도어 닫힘";Door.OPEN->"도어 열림";Door.UNKNOWN->"도어 신호 없음";Door.DISCONNECTED->"차량 연결 끊김"}
        content.addView(ui.pill(door,if(s.door==Door.CLOSED)Palette.green else Palette.amber,if(s.door==Door.CLOSED)Palette.greenSoft else Palette.amberSoft),LinearLayout.LayoutParams(-2,-2).apply{bottomMargin=ui.dp(12)})
        ui.check(content,"안전하게 정차·주차했어요",s.parked){journey.checkParked(!s.parked);refresh()}
        ui.check(content,"보호자와 동승자가 모두 내렸어요",s.exited){journey.checkExited(!s.exited);refresh()}
        if(s.door in setOf(Door.UNKNOWN,Door.DISCONNECTED))ui.check(content,"모든 문이 닫힌 것을 직접 확인했어요",s.manualDoorCheck){journey.checkDoorsManually(!s.manualDoorCheck);refresh()}
        note(if(s.door==Door.OPEN)"문이 열려 있어요. 닫힘을 확인할 때까지 진행할 수 없어요." else "도어 신호는 참고 정보예요. 문이 닫혀도 하차가 자동으로 완료되지 않아요.",s.door!=Door.CLOSED)
        primary("확인하고 주차장 안내",s.canConfirmExit,note="다음 장소 · "+p.facility.parking){journey.confirmExit()}
    }
    private fun parkingCheck(){
        val s=journey.state;val p=s.plan!!
        heading("02 · 주차 완료 확인","주차를 마쳤나요",if(p.mode==Mode.TOGETHER)"이제 일행과 함께 약속 장소로 이동해요." else "차를 세운 뒤, 처음 정한 만남 장소로 이동해요.")
        ui.card(content,"P",p.facility.parking,p.facility.name)
        ui.check(content,"주차를 마치고 안전하게 내릴 수 있어요",s.parked){journey.checkParked(!s.parked);refresh()}
        note("주차장 도착과 주차 완료는 달라요. 주변을 살피고 직접 확인해 주세요.")
        primary("주차 완료 · 만남 장소 보기",s.parked){journey.confirmParking()}
    }
    private fun reunion(){
        val s=journey.state;val p=s.plan!!;val done=s.stage==Stage.DONE
        heading(if(done)"ARRIVAL COMPLETE" else "03 · 재합류 안내",
            if(done)"도착을 함께 마쳤어요" else if(p.mode==Mode.TOGETHER)"함께 여기로 이동해요" else "여기서 다시 만나요",
            "일행의 실시간 위치가 아닌, 처음 약속한 장소예요.")
        ui.card(content,if(done)"check" else "M",p.facility.meeting,p.facility.name,Palette.green)
        ui.label(content,"주차 후 이동 순서",17,Palette.navy,true,10)
        ui.label(content,p.facility.walking,16,Palette.ink,true,14)
        ui.label(content,p.mobility,14,Palette.muted,false,14)
        note("접근성 미검증 · 단차, 승강기 운행과 통로 폭은 현장 안내를 확인해 주세요.",true)
        primary(if(done)"새로운 도착 계획 만들기" else "만남 장소 확인했어요"){if(done)journey.reset()else journey.finish()}
    }
    private fun locked(){
        heading("안전을 먼저 생각해요",if(journey.state.uxReady)"주행 중에는 안내에 집중해 주세요" else "차량 상태를 확인하고 있어요","동행 조건 변경과 하차·주차 확인은 정차 후 이용할 수 있어요.")
        ui.card(content,"shield","현재 여정은 그대로 유지돼요","안전 확인 항목은 다시 확인해 주세요.")
        note(if(BuildConfig.PLEOS)"차량 UX 제한 신호가 확인되면 조작을 다시 사용할 수 있어요." else "시연 설정에서 정차 상태로 바꾸면 조작을 다시 사용할 수 있어요.")
        primary("정차 후 이용해 주세요",enabled=false){}
    }
    private fun showConditions(){
        if(!journey.state.inputAllowed)return
        var draft=journey.state.conditions
        val body=ui.column().apply{setPadding(ui.dp(20),ui.dp(14),ui.dp(20),ui.dp(8))}
        val scroller=ScrollView(this).apply{addView(body)}
        fun choices(items:List<Pair<String,Boolean>>,action:(Int)->Unit){
            val row=ui.row()
            items.forEachIndexed{i,item->row.addView(ui.button(item.first,selected=item.second){action(i)},LinearLayout.LayoutParams(0,-2,1f).apply{if(i>0)leftMargin=ui.dp(8)})}
            body.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=ui.dp(8)})
        }
        fun renderDraft(){
            val y=scroller.scrollY;body.removeAllViews()
            ui.label(body,"동승자",16,Palette.navy,true,10)
            Passenger.entries.chunked(2).forEach{pair->choices(pair.map{it.label to (it in draft.passengers)}){i->
                val person=pair[i];val next=if(person in draft.passengers)draft.passengers-person else draft.passengers+person
                if(next.isEmpty())Toast.makeText(this,"동승자를 하나 이상 선택해 주세요",Toast.LENGTH_SHORT).show()else{draft=draft.copy(passengers=next);renderDraft()}
            }}
            ui.spacer(body,10);ui.label(body,"운전자 외 보호자",16,Palette.navy,true,10)
            choices(listOf("별도 보호자 있음" to draft.caregiver,"별도 보호자 없음" to !draft.caregiver)){draft=draft.copy(caregiver=it==0);renderDraft()}
            ui.spacer(body,10);ui.label(body,"어떻게 도착할까요?",16,Palette.navy,true,10)
            val plan=Plan.create(draft)
            choices(listOf("먼저 내려주기" to (plan.mode==Mode.DROP_FIRST),"함께 이동하기" to (plan.mode==Mode.TOGETHER))){draft=draft.copy(requestedMode=if(it==0)Mode.DROP_FIRST else Mode.TOGETHER);renderDraft()}
            ui.label(body,plan.reason,14,if(draft.caregiver)Palette.muted else Palette.amber,false,12)
            scroller.post{scroller.scrollTo(0,y)}
        }
        renderDraft()
        dialog=AlertDialog.Builder(this).setTitle("동행 조건").setView(scroller).setNegativeButton("취소",null)
            .setPositiveButton("조건 적용"){_,_->if(journey.state.inputAllowed){journey.configure(draft);refresh()}}.create()
        showDialogWindow()
    }
    private fun showPoint(point:Waypoint){
        if(!journey.state.inputAllowed)return
        val f=journey.state.plan?.facility?:DemoFacilities.get(journey.state.conditions.facilityId)
        val title=when(point){Waypoint.DROP->f.drop;Waypoint.PARK->f.parking;Waypoint.MEET->f.meeting}
        dialog=AlertDialog.Builder(this).setTitle(point.letter+" · "+point.title+" 지점")
            .setMessage(title+"\n\n"+f.name+"\n\nUI 시연용 가상 위치입니다. 실제 시설의 하차·주차·만남 지점이나 검증된 접근성 정보가 아닙니다.")
            .setPositiveButton("확인",null).create();showDialogWindow()
    }
    private fun keyHash():String=runCatching{
        val info=packageManager.getPackageInfo(packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(info.signingInfo!!.apkContentsSigners.first().toByteArray()),Base64.NO_WRAP)
    }.getOrDefault("확인 불가")
    private fun showMapInfo(){
        if(!journey.state.inputAllowed)return
        val hash=keyHash()
        dialog=AlertDialog.Builder(this).setTitle("지도 연결")
            .setMessage(mapPanel.status+"\n\n실제 지도 타일과 내비게이션 경로 안내는 별개입니다. D/P/M은 시연용 샘플 위치이며 실제 경로를 요청하지 않습니다.\n\n패키지: "+packageName+"\n키 해시: "+hash+"\n버전: "+BuildConfig.VERSION_NAME+" / "+BuildConfig.FLAVOR)
            .setNegativeButton("키 해시 복사"){_,_->(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Kakao key hash",hash));Toast.makeText(this,"키 해시를 복사했어요",Toast.LENGTH_SHORT).show()}
            .setPositiveButton(if(BuildConfig.KAKAO_INCLUDED)"지도 다시 연결" else "확인"){_,_->if(BuildConfig.KAKAO_INCLUDED)mapPanel.retry()}.create()
        showDialogWindow()
    }
    private fun askReset(){
        if(!journey.state.inputAllowed)return
        if(journey.state.stage==Stage.SETUP){journey.reset();refresh();return}
        dialog=AlertDialog.Builder(this).setTitle("새 여정을 시작할까요?").setMessage("현재 확인 상태를 초기화해요. 이전 경로 응답은 무시됩니다.")
            .setNegativeButton("계속 안내",null).setPositiveButton("새 여정 시작"){_,_->if(journey.state.inputAllowed){journey.reset();refresh()}}.create()
        showDialogWindow()
    }
    private fun showSimulator(){
        val body=ui.column().apply{setPadding(ui.dp(18),ui.dp(12),ui.dp(18),ui.dp(8))}
        ui.label(body,navigation.description+"\n"+vehicle.description,13,Palette.muted,false,12)
        fun command(label:String,action:()->Unit){body.addView(ui.button(label){action();dialog?.dismiss();refresh()},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=ui.dp(7)})}
        (vehicle as? MockVehicleGateway)?.let{v->
            val r=ui.row()
            r.addView(ui.button("정차 상태",selected=!journey.state.moving){v.motion(false);dialog?.dismiss();refresh()},LinearLayout.LayoutParams(0,-2,1f))
            r.addView(ui.button("주행 상태",selected=journey.state.moving){v.motion(true);dialog?.dismiss();refresh()},LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=ui.dp(8)})
            body.addView(r,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=ui.dp(8)})
            listOf(Door.CLOSED to "도어 닫힘",Door.OPEN to "도어 열림",Door.UNKNOWN to "도어 신호 누락",Door.DISCONNECTED to "차량 연결 실패").forEach{(door,title)->command(title){v.door(door)}}
        }
        (navigation as? MockNavigationGateway)?.let{n->
            command("다음 내비 요청 실패"){n.failNext=true;n.delayNext=false}
            command("다음 내비 응답 지연"){n.delayNext=true;n.failNext=false}
        }
        ui.label(body,"무시한 이전 응답: "+journey.state.ignoredResponses+"건\n현재 단계: "+journey.state.stage,13,Palette.blue,true,8)
        ui.label(body,"지도: "+mapPanel.status,12,Palette.muted,false,0)
        dialog=AlertDialog.Builder(this).setTitle("시뮬레이터 / 연결 상태").setView(ScrollView(this).apply{addView(body)}).setPositiveButton("닫기",null).create()
        showDialogWindow()
    }
    private fun showDialogWindow(){
        dialog?.show()
        val width=(resources.displayMetrics.widthPixels-ui.dp(32)).coerceAtMost(ui.dp(560))
        dialog?.window?.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
