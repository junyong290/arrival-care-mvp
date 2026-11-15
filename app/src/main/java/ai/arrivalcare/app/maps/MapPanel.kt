package ai.arrivalcare.app.maps

import android.app.ActivityManager
import android.content.Context
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import ai.arrivalcare.app.BuildConfig
import ai.arrivalcare.app.domain.*
import ai.arrivalcare.app.ui.*

enum class Waypoint(val letter:String,val title:String){DROP("D","하차"),PARK("P","주차"),MEET("M","재합류")}
data class MapSpot(val id:String,val title:String,val point:GeoPoint,val waypoint:Waypoint?=null)
data class MapScene(val id:String,val venue:Venue,val spots:List<MapSpot>){
    companion object{
        fun from(s:State):MapScene{
            val v=VenueRepository.get(s.conditions.facilityId)
            if(!v.simulated)return MapScene(v.id,v,listOf(MapSpot("H",v.name,v.center)))
            val recommendation=ArrivalRecommendationEngine.recommend(s.conditions)
            val f=s.plan?.facility?:recommendation.plan?.facility?:DemoFacilities.get(s.conditions.facilityId)
            val offset=when(recommendation.variant){"level"->.00025;"family"->-.00025;"central"->.0001;else->0.0}
            return MapScene(f.id,v,listOf(
                MapSpot("D",f.drop,GeoPoint(v.center.latitude+.0001+offset,v.center.longitude-.0011),Waypoint.DROP),
                MapSpot("P",f.parking,GeoPoint(v.center.latitude-.0007,v.center.longitude+.0005+offset),Waypoint.PARK),
                MapSpot("M",f.meeting,GeoPoint(v.center.latitude+.0006+offset,v.center.longitude+.0008),Waypoint.MEET)))
        }
    }
}
interface MapRenderer{
    val view:View
    fun setScene(scene:MapScene)
    fun highlight(point:Waypoint)
    fun setVehicle(point:GeoPoint?)
    fun zoom(inward:Boolean)
    fun recenter()
    fun resume()
    fun pause()
    fun close()
}
class MapPanel(context:Context,private val onPoint:(Waypoint?)->Unit,private val onInfo:()->Unit):FrameLayout(context){
    private val ui=Ui(context);private val handler=Handler(Looper.getMainLooper())
    private val host=FrameLayout(context);private val sample=CareMap(context){onPoint(it)}
    private val badge=ui.pill("시연 동선",Palette.navy,Palette.surface)
    private val caption=ui.text("가상 장소 · 차량 위치 시뮬레이션",12,Palette.muted)
    private val shield=View(context).apply{setOnTouchListener{_,_->true};visibility=GONE;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    private val controls=ui.column();private val buttons=mutableListOf<View>()
    private var renderer:MapRenderer?=null;private var epoch=0;private var foreground=false;private var disposed=false
    private var allowed=true;private var active=Waypoint.DROP;private var scene=MapScene.from(State());private var lastFocus:String?=null
    var status="샘플 동선도";private set
    var realMapReady=false;private set
    private val timeout=Runnable{if(!disposed&&foreground&&!realMapReady&&renderer!=null)fallback("연결 지연 · 동선도로 계속 안내해요")}
    init{
        background=ui.shape(Palette.paper,24);clipToOutline=true
        host.addView(sample,LayoutParams(-1,-1));addView(host,LayoutParams(-1,-1));addView(shield,LayoutParams(-1,-1))
        val labels=ui.column().apply{setPadding(ui.dp(4),0,ui.dp(4),0)}
        labels.addView(badge);labels.addView(caption,LinearLayout.LayoutParams(-2,-2).apply{topMargin=ui.dp(7)})
        addView(labels,LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply{leftMargin=ui.dp(14);topMargin=ui.dp(14);rightMargin=ui.dp(76)})
        badge.contentDescription="지도 상태";badge.setOnClickListener{if(allowed)onInfo()}
        fun control(name:String,icon:String,action:()->Unit){
            val button=ui.iconButton(name,icon){if(allowed)action()};buttons.add(button)
            controls.addView(button,LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply{bottomMargin=ui.dp(5)})
        }
        control("지도 확대","plus"){renderer?.zoom(true);sample.zoom(true)}
        control("지도 축소","minus"){renderer?.zoom(false);sample.zoom(false)}
        control("전체 동선 보기","target"){renderer?.recenter();sample.resetZoom()}
        addView(controls,LayoutParams(-2,-2,Gravity.END or Gravity.TOP).apply{rightMargin=ui.dp(12);topMargin=ui.dp(12)})
        post{connect()}
    }
    private fun connect(){
        if(disposed)return
        val am=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val reason=MapAvailability.reason(BuildConfig.KAKAO_INCLUDED,Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),BuildConfig.KAKAO_MAP_NATIVE_KEY,am.deviceConfigurationInfo.reqGlEsVersion)
        if(reason!=null){status=reason;updateCaption();return}
        val generation=++epoch;status="카카오 지도 연결 중";badge.text="지도 불러오는 중"
        try{
            renderer=MapProvider.create(context,{post{
                if(!disposed&&generation==epoch){realMapReady=true;handler.removeCallbacks(timeout);status="카카오 SDK 준비 완료 · 실제 타일은 화면에서 확인";sample.visibility=GONE;renderer?.setScene(scene);renderer?.highlight(active);updateCaption()}
            }},{error->post{if(!disposed&&generation==epoch)fallback(error)}},{point->post{if(allowed&&generation==epoch)onPoint(point)}})
            renderer?.let{host.addView(it.view,0,LayoutParams(-1,-1));it.setScene(scene);if(foreground)it.resume()}
            if(foreground)handler.postDelayed(timeout,15000)
        }catch(_:LinkageError){fallback("이 기기는 지도 엔진을 지원하지 않아요")}
        catch(_:Exception){fallback("지도 연결 오류 · 동선도로 계속 안내해요")}
    }
    private fun updateCaption(){
        badge.text=if(realMapReady)"카카오 지도" else if(BuildConfig.KAKAO_INCLUDED)"대체 화면 · 지도 연결 확인" else if(scene.venue.simulated)"시연 동선" else "시설 정보"
        caption.text=if(scene.venue.simulated)"가상 D/P/M · 차량 위치 시뮬레이션" else if(realMapReady)"공식 대표 위치 · D/P/M 확인 필요" else "지도 미연결 · 대표 위치 정보만 표시"
    }
    private fun fallback(reason:String){
        epoch++;handler.removeCallbacks(timeout);realMapReady=false;status=reason
        renderer?.let{runCatching{it.pause()};runCatching{it.close()};host.removeView(it.view)};renderer=null
        sample.visibility=VISIBLE;updateCaption()
    }
    fun retry(){fallback("다시 연결 중");connect()}
    fun update(s:State){
        val next=MapScene.from(s)
        if(scene!=next){scene=next;sample.scene=scene;renderer?.setScene(scene);lastFocus=null}
        active=when(s.stage){Stage.ROUTING_PARKING,Stage.EN_ROUTE_PARKING,Stage.PARKING_CHECK->Waypoint.PARK;Stage.REUNION,Stage.DONE->Waypoint.MEET;else->if((s.plan?:ArrivalRecommendationEngine.recommend(s.conditions).plan)?.mode==Mode.TOGETHER)Waypoint.PARK else Waypoint.DROP}
        val focus=scene.id+active.name
        if(focus!=lastFocus){sample.highlight(active);renderer?.highlight(active);lastFocus=focus}
        allowed=s.inputAllowed;shield.visibility=if(allowed)GONE else VISIBLE
        sample.setControlsEnabled(allowed);buttons.forEach{it.isEnabled=allowed};badge.isEnabled=allowed
        host.importantForAccessibility=if(allowed)IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        updateCaption()
    }
    fun vehicle(point:GeoPoint?){if(!scene.venue.simulated)renderer?.setVehicle(point)}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
        super.onSizeChanged(w,h,oldw,oldh)
        // Compact safety-confirmation panes keep their markers, without clipped map controls.
        controls.visibility=if(h<ui.dp(180))GONE else VISIBLE
        caption.visibility=if(h<ui.dp(180))GONE else VISIBLE
        badge.visibility=if(h<ui.dp(100))GONE else VISIBLE
    }
    fun resume(){foreground=true;renderer?.resume();if(renderer!=null&&!realMapReady){handler.removeCallbacks(timeout);handler.postDelayed(timeout,15000)}}
    fun pause(){foreground=false;handler.removeCallbacks(timeout);renderer?.pause()}
    fun close(){disposed=true;epoch++;handler.removeCallbacksAndMessages(null);renderer?.close();renderer=null}
}
private class CareMap(context:Context,private val click:(Waypoint?)->Unit):FrameLayout(context){
    private val ui=Ui(context);private var active=Waypoint.DROP;private var zoom=1f
    var scene=MapScene.from(State());set(v){field=v;updateVisibility();backgroundView.invalidate();requestLayout()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundView=object:View(context){override fun onDraw(c:Canvas){
        val w=width.toFloat();val h=height.toFloat();c.drawColor(if(Palette.night)Color.rgb(21,35,45) else Color.rgb(230,238,239))
        if(!scene.venue.simulated){c.drawColor(Palette.paper);return}
        c.save();c.scale(zoom,zoom,w/2,h/2)
        fun block(x:Float,y:Float,r:Float,b:Float,color:Int){paint.color=color;paint.style=Paint.Style.FILL;c.drawRoundRect(w*x,h*y,w*r,h*b,ui.dp(16).toFloat(),ui.dp(16).toFloat(),paint)}
        val park=if(Palette.night)Color.rgb(33,59,50) else Color.rgb(204,225,212)
        val building=if(Palette.night)Color.rgb(39,52,67) else Color.rgb(217,225,231)
        block(-.1f,.15f,.18f,.50f,park);block(.79f,.64f,1.1f,1.1f,park)
        block(.36f,.18f,.63f,.47f,building);block(.12f,.61f,.34f,.93f,building);block(.72f,.1f,1.1f,.28f,building)
        paint.style=Paint.Style.STROKE;paint.color=if(Palette.night)Color.rgb(51,65,76) else Color.WHITE;paint.strokeWidth=ui.dp(20).toFloat();paint.strokeCap=Paint.Cap.ROUND
        val roads=Path().apply{moveTo(-w*.1f,h*.60f);cubicTo(w*.2f,h*.65f,w*.55f,h*.37f,w*1.2f,h*.5f);moveTo(w*.68f,-h*.1f);cubicTo(w*.49f,h*.28f,w*.57f,h*.7f,w*.55f,h*1.1f)};c.drawPath(roads,paint)
        if(scene.venue.simulated){
            paint.color=if(Palette.night)Color.rgb(71,110,167) else Color.rgb(150,182,218);paint.strokeWidth=ui.dp(3).toFloat();paint.pathEffect=DashPathEffect(floatArrayOf(ui.dp(3).toFloat(),ui.dp(7).toFloat()),0f)
            val path=Path().apply{moveTo(w*.28f,h*.47f);cubicTo(w*.41f,h*.4f,w*.5f,h*.73f,w*.66f,h*.72f);cubicTo(w*.83f,h*.7f,w*.82f,h*.4f,w*.70f,h*.34f)};c.drawPath(path,paint);paint.pathEffect=null
            val carX=w*(if(height<ui.dp(235)).10f else .20f);val carY=h*.73f
            paint.style=Paint.Style.FILL;paint.color=Palette.blue;c.drawCircle(carX,carY,ui.dp(16).toFloat(),paint)
            val car=IconDrawable(context,"car",Color.WHITE);car.setBounds((carX-ui.dp(10)).toInt(),(carY-ui.dp(10)).toInt(),(carX+ui.dp(10)).toInt(),(carY+ui.dp(10)).toInt());car.draw(c)
        }
        c.restore()
    }}
    private val pins=Waypoint.entries.associateWith{p->ui.button(p.letter){click(p)}.apply{textSize=20f;minHeight=0;minimumHeight=0;setPadding(0,0,0,0);elevation=ui.dp(3).toFloat();contentDescription=p.letter+" 마커 상세"}}
    private val labels=Waypoint.entries.associateWith{p->ui.pill(p.title,Palette.ink,Palette.surface)}
    private val hospital=ui.button("H",icon="hospital"){click(null)}.apply{contentDescription="병원 대표 위치 상세"}
    init{addView(backgroundView,LayoutParams(-1,-1));pins.forEach{(p,v)->addView(v,LayoutParams(ui.dp(54),ui.dp(54)));addView(labels.getValue(p),LayoutParams(-2,-2))};addView(hospital,LayoutParams(ui.dp(80),ui.dp(56)));updateVisibility();highlight(active)}
    private fun updateVisibility(){pins.values.forEach{it.visibility=if(scene.venue.simulated)VISIBLE else GONE};labels.values.forEach{it.visibility=if(scene.venue.simulated)VISIBLE else GONE};hospital.visibility=if(scene.venue.simulated)GONE else VISIBLE}
    fun setControlsEnabled(allow:Boolean){pins.values.forEach{it.isEnabled=allow};hospital.isEnabled=allow}
    fun highlight(p:Waypoint){active=p;pins.forEach{(key,v)->v.background=ui.shape(if(key==p)Palette.blue else Palette.surface,18);v.setTextColor(if(key==p)Color.WHITE else Palette.navy);v.isSelected=key==p};invalidate()}
    fun zoom(inside:Boolean){zoom=(zoom+if(inside).15f else -.15f).coerceIn(.75f,1.45f);backgroundView.invalidate();requestLayout()}
    fun resetZoom(){zoom=1f;backgroundView.invalidate();requestLayout()}
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        backgroundView.layout(0,0,width,height)
        val compact=height<ui.dp(235)
        val points=if(compact)listOf(.24f to .60f,.49f to .74f,.72f to .52f)else listOf(.28f to .47f,.66f to .72f,.70f to .34f)
        pins.entries.forEachIndexed{i,(p,v)->
            val size=ui.dp(54);val x=(width*(.5f+(points[i].first-.5f)*zoom)).toInt().coerceIn(size/2,width-size/2)
            val minY=minOf(ui.dp(70),maxOf(size/2,height-size/2))
            val maxY=maxOf(minY,height-ui.dp(if(height<ui.dp(160))30 else 55))
            val y=(height*(.5f+(points[i].second-.5f)*zoom)).toInt().coerceIn(minY,maxY)
            v.layout(x-size/2,y-size/2,x+size/2,y+size/2)
            val label=labels.getValue(p);label.visibility=if(scene.venue.simulated&&height>=ui.dp(160))VISIBLE else GONE
            label.layout(x-label.measuredWidth/2,y+size/2+ui.dp(3),x+label.measuredWidth/2,y+size/2+ui.dp(3)+label.measuredHeight)
        }
        hospital.layout(width/2-ui.dp(40),height/2-ui.dp(28),width/2+ui.dp(40),height/2+ui.dp(28))
    }
}
