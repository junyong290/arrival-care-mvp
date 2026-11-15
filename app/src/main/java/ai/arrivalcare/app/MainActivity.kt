package ai.arrivalcare.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import ai.arrivalcare.app.data.*
import ai.arrivalcare.app.domain.*

class MainActivity : Activity() {
    private val navy = Color.rgb(9,29,51)
    private val panel = Color.rgb(22,54,78)
    private val blue = Color.rgb(39,104,245)
    private val lime = Color.rgb(200,238,148)
    private val muted = Color.rgb(180,198,215)
    private val warning = Color.rgb(255,206,126)
    private var scale = 1f
    private val journey = Journey()
    private lateinit var navigation: NavigationGateway
    private lateinit var vehicle: VehicleGateway
    private var lastRequested = -1L
    private val events = ArrayDeque<String>()
    private var lastStage: Stage? = null
    private var ready = false
    private var resumed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var renderPosted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        scale = (resources.configuration.screenWidthDp / 1220f).coerceIn(0.78f, 1.6f)
        navigation = Integration.navigation(this)
        vehicle = Integration.vehicle(this)
        if (savedInstanceState != null) log("앱 재생성: 안전을 위해 확인 상태와 진행 여정을 초기화했습니다.")
        ready = true
        vehicle.connect({ door -> runOnUiThread { journey.setDoor(door); log("도어: $door"); refresh() } },
            { moving, connected -> runOnUiThread { journey.setMotion(moving, connected); log("입력 제한: moving=$moving ready=$connected"); refresh() } })
        refresh()
    }
    override fun onResume() { super.onResume(); resumed = true; if (ready) refresh() }
    override fun onPause() {
        resumed = false
        // Returning to the app always requires the person to re-check safety-critical confirmations.
        journey.checkParked(false); journey.checkExited(false); journey.checkDoorsManually(false)
        super.onPause()
    }
    override fun onDestroy() { ready = false; mainHandler.removeCallbacksAndMessages(null); navigation.close(); vehicle.close(); super.onDestroy() }
    @Deprecated("Back is intentionally guarded")
    override fun onBackPressed() { if (journey.state.inputAllowed) askReset() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density * scale).toInt()
    private fun bg(color: Int, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(15).toFloat(); if (stroke) setStroke(dp(1), Color.rgb(71,98,122))
    }
    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Int = 18, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size * scale; setTextColor(color); setLineSpacing(dp(3).toFloat(), 1f)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun LinearLayout.label(value: String, size: Int = 18, color: Int = muted, bold: Boolean = false, bottom: Int = 10) {
        addView(text(value,size,color,bold), LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(bottom) })
    }
    private fun button(value: String, selected: Boolean = false, enabled: Boolean = true, redraw: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 18 * scale; isAllCaps = false; minHeight = dp(52); minimumHeight = dp(52)
        setPadding(dp(12),dp(8),dp(12),dp(8)); setTextColor(if (enabled) Color.WHITE else Color.rgb(123,146,163))
        background = bg(if (selected && enabled) blue else panel, !selected)
        isEnabled = enabled; contentDescription = value
        setOnClickListener { action(); if(redraw) refresh() }
    }
    private fun LinearLayout.action(value: String, primary: Boolean = true, enabled: Boolean = true, action: () -> Unit) {
        addView(button(value,primary,enabled,action=action),LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(8); bottomMargin = dp(6) })
    }
    private fun LinearLayout.options(items: List<Pair<String, Boolean>>, onClick: (Int) -> Unit) {
        val r = row()
        items.forEachIndexed { i, item -> r.addView(button(item.first,item.second) { onClick(i) }, LinearLayout.LayoutParams(0,-2,1f).apply { if(i>0) leftMargin=dp(8) }) }
        addView(r,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(9) })
    }
    private fun card(parent: LinearLayout, tag: String, title: String, detail: String, accent: Int = blue) {
        val r=row().apply { background=bg(panel);setPadding(dp(15),dp(12),dp(15),dp(12)) }
        r.addView(text(tag,23,Color.WHITE,true).apply { gravity=Gravity.CENTER; background=bg(accent) },LinearLayout.LayoutParams(dp(48),dp(48)))
        val c=col(); c.label(title,21,Color.WHITE,true,4); c.label(detail,16,muted,false,0)
        r.addView(c,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(16)})
        parent.addView(r,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
    }
    private fun check(parent: LinearLayout, title: String, checked: Boolean, action: (Boolean) -> Unit) {
        parent.action((if(checked) "✓  " else "□  ")+title, checked) { action(!checked) }
    }
    private fun log(message: String) { Log.i("ArrivalCare",message);events.addLast(message);while(events.size>35)events.removeFirst() }
    private fun refresh() {
        if (!ready || renderPosted) return
        renderPosted = true
        mainHandler.post { renderPosted = false; renderState() }
    }
    private fun renderState() {
        if (!ready || isFinishing || isDestroyed) return
        val s = journey.state
        if (s.stage != lastStage) { log("STAGE ${s.stage} mode=${s.plan?.mode}");lastStage=s.stage }
        render()
        val req=s.request
        if (req != null && req.id != lastRequested && s.error==null) {
            lastRequested=req.id;log("ROUTE request=${req.id} destination=${req.destinationId}")
            navigation.request(req) { id,destination,success,error -> runOnUiThread {
                if(ready) { journey.routeResult(id,destination,success,error);log("ROUTE result=$id success=$success ignored=${journey.state.ignoredResponses}");refresh() }
            } }
        }
    }
    private fun render() {
        val s=journey.state
        val root=col().apply { setBackgroundColor(navy);setPadding(dp(26),dp(12),dp(26),dp(8)) }
        val head=row()
        val brand=col();brand.label("도착케어",25,Color.WHITE,true,0);brand.label("ARRIVAL CARE",10,muted,false,0)
        head.addView(brand,LinearLayout.LayoutParams(dp(165),-2))
        head.addView(text(if(BuildConfig.PLEOS) "PLEOS SDK 검증 · 가상 시설" else "DEMO · 가상 시설 · 신호 시뮬레이션",14,lime,true),LinearLayout.LayoutParams(0,-2,1f))
        head.addView(button(if(BuildConfig.PLEOS) "연결 상태" else "시뮬레이터",false,redraw=false) { simulator() },LinearLayout.LayoutParams(dp(146),dp(48)))
        head.addView(button("새 여정",false,s.inputAllowed,redraw=false){ askReset() },LinearLayout.LayoutParams(dp(100),dp(48)).apply{leftMargin=dp(8)})
        root.addView(head,LinearLayout.LayoutParams(-1,dp(60)))
        root.addView(View(this).apply{setBackgroundColor(Color.rgb(53,80,102))},LinearLayout.LayoutParams(-1,dp(1)).apply{topMargin=dp(6);bottomMargin=dp(17)})
        if(!s.inputAllowed) {
            val paused=col().apply{gravity=Gravity.CENTER}
            paused.label(if(s.uxReady) "주행 중에는 안내에 집중해 주세요" else "차량 입력 제한 상태를 확인하고 있어요",34,Color.WHITE,true,20)
            paused.label("동행 조건 변경과 하차·주차 확인은 정차 후 이용할 수 있어요.\n진행 중인 단계는 그대로 유지됩니다.",23,muted,false,18)
            paused.label(if(BuildConfig.PLEOS) "차량 UX 제한 신호를 기다립니다. 연결되지 않으면 입력을 허용하지 않습니다." else "시연은 오른쪽 위 ‘시뮬레이터’에서 정차 상태로 바꿔 주세요.",18,lime)
            root.addView(paused,LinearLayout.LayoutParams(-1,0,1f))
        } else {
            val body=row().apply{gravity=Gravity.TOP}
            val left=col();val right=col()
            val ls=ScrollView(this).apply{isFillViewport=false;addView(left);isVerticalScrollBarEnabled=false}
            val rs=ScrollView(this).apply{isFillViewport=false;addView(right);isVerticalScrollBarEnabled=false}
            body.addView(ls,LinearLayout.LayoutParams(0,-1,0.46f).apply{rightMargin=dp(28)})
            body.addView(rs,LinearLayout.LayoutParams(0,-1,0.54f))
            when(s.stage) {
                Stage.SETUP -> setup(left,right)
                Stage.ROUTING_DROPOFF,Stage.ROUTING_PARKING,Stage.EN_ROUTE_DROPOFF,Stage.EN_ROUTE_PARKING -> route(left,right)
                Stage.EXIT_CHECK -> exitScreen(left,right)
                Stage.PARKING_CHECK -> parking(left,right)
                Stage.REUNION,Stage.DONE -> reunion(left,right)
            }
            root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        }
        val step=when(s.stage){Stage.SETUP->0;Stage.ROUTING_DROPOFF,Stage.EN_ROUTE_DROPOFF,Stage.EXIT_CHECK->1;else->2}
        val foot=row()
        listOf("01  도착 계획","02  하차 확인","03  주차 후 재합류").forEachIndexed{i,title->foot.addView(text(title,15,if(step==i)lime else muted,step==i),LinearLayout.LayoutParams(0,-2,1f))}
        foot.addView(text("현장 표지와 안내를 확인하세요",12,muted),LinearLayout.LayoutParams(-2,-2))
        root.addView(foot,LinearLayout.LayoutParams(-1,dp(44)))
        setContentView(root)
    }
    private fun setup(left: LinearLayout,right: LinearLayout) {
        val c=journey.state.conditions;val p=Plan.create(c)
        left.label("우리의 도착 계획",32,Color.WHITE,true,5)
        left.label("출발 전, 주차한 상태에서 정해 주세요",16,muted,false,12)
        left.options(DemoFacilities.all.map{it.name to (it.id==c.facilityId)}){i->journey.configure(c.copy(facilityId=DemoFacilities.all[i].id))}
        Passenger.entries.chunked(2).forEach { pair -> left.options(pair.map{it.label to (it in c.passengers)}) {i->
            val chosen=pair[i];val next=if(chosen in c.passengers)c.passengers-chosen else c.passengers+chosen
            if(next.isEmpty()) Toast.makeText(this,"동승자 조건을 하나 이상 선택해 주세요",Toast.LENGTH_SHORT).show() else journey.configure(c.copy(passengers=next))
        } }
        left.label("운전자 외 보호자",16,muted,false,5)
        left.options(listOf("별도 보호자 있음" to c.caregiver,"별도 보호자 없음" to !c.caregiver)){i->journey.configure(c.copy(caregiver=i==0))}
        left.options(listOf("먼저 내려주기" to (p.mode==Mode.DROP_FIRST),"함께 이동하기" to (p.mode==Mode.TOGETHER))){i->
            if(i==0&&!c.caregiver) Toast.makeText(this,"유일한 보호자는 동승자와 함께 이동해요",Toast.LENGTH_LONG).show()
            journey.configure(c.copy(requestedMode=if(i==0)Mode.DROP_FIRST else Mode.TOGETHER))
        }
        left.action("이 계획으로 안내 시작"){journey.begin()}
        right.label("한 번 정해, 끝까지 이어갑니다",25,Color.WHITE,true,12)
        right.label(p.reason,18,lime,false,14)
        if(p.mode==Mode.DROP_FIRST)card(right,"D",p.facility.drop,"동승자는 보호자와 함께 하차")
        else card(right,"P","분리 하차 없이 함께 주차","유일한 보호자이거나 함께 이동을 선택했어요",Color.rgb(70,125,80))
        card(right,"P",p.facility.parking,if(p.mode==Mode.DROP_FIRST) "하차 완료를 직접 확인한 다음 안내" else "일행과 함께 주차한 뒤 이동해요")
        card(right,"M",p.facility.meeting,"여정 시작 때 정한 고정 만남 장소",Color.rgb(70,125,80))
        right.label("시연용 가상 시설입니다. 실제 승하차 허용 구역·접근성·운영 여부를 검증한 정보가 아닙니다.",14,warning,false,4)
    }
    private fun route(left: LinearLayout,right: LinearLayout) {
        val s=journey.state;val p=s.plan!!
        val drop=s.stage in setOf(Stage.ROUTING_DROPOFF,Stage.EN_ROUTE_DROPOFF)
        val loading=s.stage in setOf(Stage.ROUTING_DROPOFF,Stage.ROUTING_PARKING)
        left.label(if(drop)"먼저, 하차 장소로" else if(p.mode==Mode.TOGETHER)"모두 함께 주차해요" else "이제, 주차장으로",34,Color.WHITE,true,16)
        left.label(p.facility.name,20,lime,true,20)
        card(left,if(drop)"D" else "P",if(drop)p.facility.drop else p.facility.parking,if(drop)"보호자와 함께 내릴 곳" else "주차 완료는 직접 확인해 주세요")
        left.label(if(drop)"도착 후 안전하게 정차했는지와\n동승자 하차를 직접 확인해 주세요." else p.reason,21,muted,false,20)
        if(s.error!=null) {
            left.label(s.error,18,warning,true)
            left.action("경로 안내 다시 시도"){journey.retry()}
        } else if(loading) {
            left.label("경로 요청 중…",23,lime,true)
            left.label("완료 응답을 기다린 뒤 다음 단계를 열어요.",16)
        } else {
            left.action(if(drop)"[시연] 하차 지점 도착" else "[시연] 주차장 도착"){journey.arrived()}
            left.label("도착 버튼은 위치 신호를 대신하는 시뮬레이션입니다.",14,warning)
        }
        right.label(if(loading)"연결 상태 확인" else "가상 시설 동선",25,Color.WHITE,true,10)
        right.addView(JourneyDiagram(if(drop)0 else 1),LinearLayout.LayoutParams(-1,dp(190)))
        right.label(navigation.description,16,lime,false,12)
        card(right,"M",p.facility.meeting,"처음 정한 만남 장소를 유지해요",Color.rgb(70,125,80))
        right.label("도식은 실제 지도·경로가 아닙니다. 현장 표지와 시설 직원 안내를 우선 확인하세요.",15,warning)
    }
    private fun exitScreen(left: LinearLayout,right: LinearLayout) {
        val s=journey.state;val p=s.plan!!
        left.label("모두 내렸나요",36,Color.WHITE,true,14)
        left.label("정차와 하차를 직접 확인한 뒤\n주차장 안내로 넘어갑니다",22,muted,false,25)
        card(left,"D",p.facility.drop,p.facility.name)
        left.label("다음 목적지",16,muted,false,5);left.label(p.facility.parking,25,Color.WHITE,true,24)
        left.label("도어 열림·닫힘만으로 하차 완료를 판단하지 않습니다.",17,lime)
        right.label("직접 확인해 주세요",24,Color.WHITE,true,8)
        check(right,"안전하게 정차·주차했어요",s.parked){journey.checkParked(it)}
        check(right,"보호자와 동승자가 모두 내렸어요",s.exited){journey.checkExited(it)}
        val status=when(s.door){Door.CLOSED->"도어 닫힘 수신 · 하차 완료를 뜻하지 않아요";Door.OPEN->"도어 열림 · 닫힘을 확인할 때까지 진행할 수 없어요";Door.UNKNOWN->"도어 신호 없음 · 현장에서 직접 확인해 주세요";Door.DISCONNECTED->"차량 연결 끊김 · 현장에서 직접 확인해 주세요"}
        right.label(status,17,if(s.door==Door.CLOSED)lime else warning,false,8)
        if(s.door in setOf(Door.UNKNOWN,Door.DISCONNECTED))check(right,"모든 문이 닫힌 것을 직접 확인했어요",s.manualDoorCheck){journey.checkDoorsManually(it)}
        right.action("확인하고 주차장 안내",true,s.canConfirmExit){journey.confirmExit()}
        if(!s.canConfirmExit)right.label("필요한 확인을 모두 마치면 버튼이 활성화됩니다.",14,muted)
    }
    private fun parking(left: LinearLayout,right: LinearLayout) {
        val s=journey.state;val p=s.plan!!
        left.label("주차를 마쳤나요",36,Color.WHITE,true,18)
        card(left,"P",p.facility.parking,p.facility.name)
        left.label(if(p.mode==Mode.TOGETHER)"동승자와 함께 내린 뒤\n약속 장소로 이동해요." else "처음 정한 만남 장소까지\n다음 동선을 확인해요.",24,muted,false,25)
        left.label("주차장 도착과 주차 완료는 다릅니다.",17,lime)
        right.addView(JourneyDiagram(1),LinearLayout.LayoutParams(-1,dp(160)))
        check(right,"주차를 마치고 안전하게 내릴 수 있어요",s.parked){journey.checkParked(it)}
        right.action("주차 완료 · 만남 장소 보기",true,s.parked){journey.confirmParking()}
        right.label("자동 주차 감지나 차량 제어 기능이 아닙니다.",15,warning)
    }
    private fun reunion(left: LinearLayout,right: LinearLayout) {
        val s=journey.state;val p=s.plan!!;val done=s.stage==Stage.DONE
        left.label(if(done)"도착을 함께 마쳤어요" else if(p.mode==Mode.TOGETHER)"함께 여기로 이동해요" else "여기서 다시 만나요",32,Color.WHITE,true,14)
        left.label("처음 정한 만남 장소",18,muted,false,16)
        card(left,"M",p.facility.meeting,p.facility.name,Color.rgb(70,125,80))
        left.label("일행의 실시간 위치가 아닌,\n미리 합의한 고정 장소입니다.",20,muted,false,24)
        left.label(if(done)"만남 장소를 확인했습니다. 실제 재합류 여부를 자동으로 감지하지 않습니다." else p.reason,17,lime)
        right.label("주차 후 이동 순서",25,Color.WHITE,true,12)
        right.label(p.facility.walking,21,Color.WHITE,true,15)
        right.label(p.mobility,18,muted,false,16)
        right.label("접근성 미검증 · 무단차나 안전한 경로를 보장하지 않습니다. 현장 층·구역 표지와 시설 직원 안내를 확인하세요.",16,warning,false,16)
        if(done)right.action("새로운 도착 계획 만들기"){journey.reset()}
        else right.action("만남 장소 확인했어요"){journey.finish()}
    }
    private fun askReset() {
        if(journey.state.stage==Stage.SETUP){journey.reset();return}
        AlertDialog.Builder(this).setTitle("새 여정을 시작할까요?").setMessage("현재 여정의 확인 상태가 초기화됩니다. 이전 경로 응답은 무시됩니다.")
            .setNegativeButton("계속 안내",null).setPositiveButton("새 여정 시작"){_,_->journey.reset();refresh()}.show()
    }
    private fun simulator() {
        val content=col().apply{setPadding(dp(18),dp(12),dp(18),dp(10));setBackgroundColor(navy)}
        content.label("시뮬레이터 / 연결 상태",24,Color.WHITE,true)
        content.label(navigation.description+"\n"+vehicle.description,15,lime)
        val v=vehicle as? MockVehicleGateway
        var dialog:AlertDialog?=null
        if(v!=null){
            content.options(listOf("정차 상태" to !journey.state.moving,"주행 상태" to journey.state.moving)){i->v.motion(i==1);dialog?.dismiss()}
            listOf(Door.CLOSED to "도어 닫힘",Door.OPEN to "도어 열림",Door.UNKNOWN to "도어 신호 누락",Door.DISCONNECTED to "차량 연결 실패").chunked(2).forEach{ pair->
                content.options(pair.map{it.second to (journey.state.door==it.first)}){i->v.door(pair[i].first);dialog?.dismiss()}
            }
        }
        val n=navigation as? MockNavigationGateway
        if(n!=null){
            content.action("다음 내비 요청 실패",false){n.failNext=true;n.delayNext=false;log("다음 경로 실패 예약");dialog?.dismiss()}
            content.action("다음 내비 응답 지연",false){n.delayNext=true;n.failNext=false;log("타임아웃 후 늦은 응답 예약");dialog?.dismiss()}
        }
        content.label("무시한 이전 응답: ${journey.state.ignoredResponses}건\n현재 단계: ${journey.state.stage}",15,warning)
        content.label(events.takeLast(6).joinToString("\n"),12,muted,false,0)
        dialog=AlertDialog.Builder(this).setView(ScrollView(this).apply{addView(content)}).setPositiveButton("닫기",null).create()
        dialog.show();dialog.window?.setLayout(dp(690),-2)
    }
    private inner class JourneyDiagram(private val active:Int): View(this) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas:Canvas){
            super.onDraw(canvas)
            val w=width.toFloat();val h=height.toFloat();val y=h*0.46f
            paint.color=panel;canvas.drawRoundRect(0f,0f,w,h,dp(15).toFloat(),dp(15).toFloat(),paint)
            paint.color=Color.rgb(71,98,122);paint.strokeWidth=dp(4).toFloat();canvas.drawLine(w*.17f,y,w*.83f,y,paint)
            listOf("D","P","M").forEachIndexed{i,label->
                val x=w*(.17f+i*.33f)
                paint.color=if(i==active)blue else if(i==2)Color.rgb(70,125,80) else Color.rgb(48,77,100)
                canvas.drawCircle(x,y,dp(28).toFloat(),paint)
                paint.color=Color.WHITE;paint.textSize=dp(23).toFloat();paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.DEFAULT_BOLD
                canvas.drawText(label,x,y+dp(8),paint)
                paint.color=if(i==active)lime else muted;paint.textSize=dp(16).toFloat()
                canvas.drawText(listOf("하차 확인","주차 완료","다시 만남")[i],x,y+dp(57),paint)
            }
            contentDescription="가상 동선: 하차 확인, 주차 완료, 다시 만남. 실제 지도 아님."
        }
    }
}
