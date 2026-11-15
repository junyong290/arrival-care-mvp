package ai.arrivalcare.app.maps

import android.app.ActivityManager
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import ai.arrivalcare.app.BuildConfig
import ai.arrivalcare.app.domain.*
import ai.arrivalcare.app.ui.*

enum class Waypoint(val letter:String,val title:String){DROP("D","하차"),PARK("P","주차"),MEET("M","재합류")}

interface MapRenderer {
    val view:View
    fun highlight(point:Waypoint)
    fun recenter()
    fun resume()
    fun pause()
    fun close()
}

/** Coordinates are map-overlay demonstrations, NEVER navigation destinations or verified facilities. */
object DemoMapPoints {
    val center=37.5665 to 126.9780
    fun coordinate(point:Waypoint)=when(point){
        Waypoint.DROP->37.5666 to 126.9769
        Waypoint.PARK->37.5658 to 126.9785
        Waypoint.MEET->37.5671 to 126.9788
    }
}

class MapPanel(context:Context,private val onPoint:(Waypoint)->Unit,private val onInfo:()->Unit):FrameLayout(context){
    private val ui=Ui(context)
    private val handler=Handler(Looper.getMainLooper())
    private val host=FrameLayout(context)
    private val sample=SampleMap(context){select(it)}
    private val badge=ui.pill("샘플 동선도",Palette.navy,Color.WHITE)
    private val caption=ui.text("가상 시설 · 실제 장소/경로 아님",11,Palette.muted,true)
    private val recenter=ui.iconButton("전체 동선 보기","target"){renderer?.recenter();sample.highlight(active)}
    private val blocker=View(context).apply{setOnTouchListener{_,_->true};visibility=GONE;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    private var renderer:MapRenderer?=null
    private var generation=0
    private var ready=false
    private var foreground=false
    private var disposed=false
    private var inputAllowed=true
    private var active=Waypoint.DROP
    var status="샘플 동선도";private set
    var realMapReady=false;private set
    private val startupTimeout=Runnable{if(!disposed && foreground && !ready && renderer!=null)fallback("지도 연결 시간이 초과됐어요")}
    init{
        background=ui.shape(Palette.paper,24);clipToOutline=true
        addView(host,LayoutParams(-1,-1));host.addView(sample,LayoutParams(-1,-1))
        addView(blocker,LayoutParams(-1,-1))
        val title=ui.column().apply{setPadding(ui.dp(2),0,ui.dp(2),0)}
        title.addView(badge,LinearLayout.LayoutParams(-2,-2))
        title.addView(caption,LinearLayout.LayoutParams(-2,-2).apply{topMargin=ui.dp(6)})
        addView(title,LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply{leftMargin=ui.dp(14);topMargin=ui.dp(12);rightMargin=ui.dp(64)})
        badge.contentDescription="지도 상태";badge.setOnClickListener{if(inputAllowed)onInfo()}
        addView(recenter,LayoutParams(ui.dp(48),ui.dp(48),Gravity.TOP or Gravity.END).apply{rightMargin=ui.dp(12);topMargin=ui.dp(12)})
        post{connect()}
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);caption.visibility=if(h<ui.dp(150))GONE else VISIBLE}
    private fun select(point:Waypoint){if(inputAllowed){sample.highlight(point);renderer?.highlight(point);onPoint(point)}}
    private fun connect(){
        if(disposed)return
        val am=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val reason=MapAvailability.reason(BuildConfig.KAKAO_INCLUDED,Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),BuildConfig.KAKAO_MAP_NATIVE_KEY,am.deviceConfigurationInfo.reqGlEsVersion)
        if(reason!=null){status=reason;return}
        val epoch=++generation;status="카카오 지도 연결 중";badge.text="지도 연결 중";ready=false
        try{
            renderer=MapProvider.create(context,{post{
                if(!disposed && epoch==generation){
                    ready=true;realMapReady=true;handler.removeCallbacks(startupTimeout)
                    status="카카오 지도 표시 · 마커는 시연용 가상 위치";badge.text="카카오 지도 · 샘플 마커"
                    sample.visibility=GONE;renderer?.highlight(active)
                }
            }},{error->post{if(!disposed && epoch==generation)fallback("지도를 불러오지 못했어요 · "+error)}},{point->post{if(epoch==generation)select(point)}})
            renderer?.let{host.addView(it.view,0,LayoutParams(-1,-1));if(foreground)it.resume()}
            handler.postDelayed(startupTimeout,15000)
        }catch(e:LinkageError){fallback("지도 엔진을 지원하지 않아요")}
        catch(e:Exception){fallback("지도 연결을 확인해 주세요")}
    }
    private fun fallback(reason:String){
        generation++;handler.removeCallbacks(startupTimeout);ready=false;realMapReady=false;status=reason
        renderer?.let{runCatching{it.pause()};runCatching{it.close()};host.removeView(it.view)};renderer=null
        sample.visibility=VISIBLE;badge.text="샘플 동선도";sample.highlight(active)
    }
    fun retry(){fallback("샘플 동선도");connect()}
    fun update(s:State){
        active=when(s.stage){
            Stage.ROUTING_PARKING,Stage.EN_ROUTE_PARKING,Stage.PARKING_CHECK->Waypoint.PARK
            Stage.REUNION,Stage.DONE->Waypoint.MEET
            else->if((s.plan?:Plan.create(s.conditions)).mode==Mode.TOGETHER)Waypoint.PARK else Waypoint.DROP
        }
        sample.facility=s.plan?.facility?:DemoFacilities.get(s.conditions.facilityId)
        sample.highlight(active);renderer?.highlight(active)
        inputAllowed=s.inputAllowed;sample.setControlsEnabled(inputAllowed)
        blocker.visibility=if(inputAllowed)GONE else VISIBLE;recenter.isEnabled=inputAllowed;badge.isEnabled=inputAllowed
        host.importantForAccessibility=if(inputAllowed)IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    fun resume(){foreground=true;renderer?.resume();if(renderer!=null && !ready)handler.postDelayed(startupTimeout,15000)}
    fun pause(){foreground=false;handler.removeCallbacks(startupTimeout);renderer?.pause()}
    fun close(){disposed=true;generation++;handler.removeCallbacksAndMessages(null);renderer?.close();renderer=null}
}

/** Deliberately schematic. No road names, GPS position, travel time or navigation claims. */
private class SampleMap(context:Context,private val onSelect:(Waypoint)->Unit):FrameLayout(context){
    private val ui=Ui(context)
    private var selected=Waypoint.DROP
    var facility:Facility=DemoFacilities.all.first()
        set(value){field=value;backgroundMap.invalidate()}
    private val backgroundMap=object:View(context){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){
            val w=width.toFloat();val h=height.toFloat()
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(235,241,248);c.drawPaint(paint)
            fun block(x:Float,y:Float,a:Float,b:Float,color:Int){
                paint.color=color;c.drawRoundRect(w*x,h*y,w*a,h*b,ui.dp(15).toFloat(),ui.dp(15).toFloat(),paint)
            }
            block(.02f,.32f,.20f,.64f,Color.rgb(221,234,229))
            block(.46f,.09f,.66f,.29f,Color.rgb(223,232,243))
            block(.80f,.52f,1.04f,.91f,Color.rgb(221,234,229))
            block(.24f,.48f,.47f,.74f,Color.rgb(214,225,242))
            block(.51f,.82f,.73f,1.12f,Color.rgb(221,229,240))
            val road=Path().apply{moveTo(-w*.1f,h*.79f);cubicTo(w*.2f,h*.75f,w*.36f,h*.24f,w*1.1f,h*.48f)}
            paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.color=Color.WHITE;paint.strokeWidth=ui.dp(27).toFloat();c.drawPath(road,paint)
            val road2=Path().apply{moveTo(w*.54f,-h*.2f);cubicTo(w*.50f,h*.25f,w*.71f,h*.55f,w*.60f,h*1.2f)}
            paint.strokeWidth=ui.dp(20).toFloat();c.drawPath(road2,paint)
            val route=Path().apply{moveTo(w*.31f,h*.43f);cubicTo(w*.43f,h*.44f,w*.46f,h*.73f,w*.64f,h*.73f);cubicTo(w*.79f,h*.73f,w*.82f,h*.37f,w*.78f,h*.37f)}
            paint.color=Color.rgb(166,190,234);paint.strokeWidth=ui.dp(4).toFloat();paint.pathEffect=DashPathEffect(floatArrayOf(ui.dp(5).toFloat(),ui.dp(7).toFloat()),0f);c.drawPath(route,paint);paint.pathEffect=null
            paint.style=Paint.Style.FILL;paint.color=Palette.muted;paint.textSize=ui.dp(11).toFloat();paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            c.drawText(facility.name,w*.35f,h*.65f,paint)
        }
    }
    private val pins=Waypoint.entries.associateWith{point->
        ui.button(point.letter,selected=point==selected){onSelect(point)}.apply{
            contentDescription=point.letter+" 마커 상세";textSize=20f;minHeight=0;minimumHeight=0
            setPadding(0,0,0,0);elevation=ui.dp(4).toFloat()
        }
    }
    private val labels=Waypoint.entries.associateWith{ui.pill(it.title,Palette.ink,Color.WHITE).apply{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}}
    init{
        addView(backgroundMap,LayoutParams(-1,-1))
        Waypoint.entries.forEach{addView(pins.getValue(it),LayoutParams(ui.dp(52),ui.dp(52)));addView(labels.getValue(it),LayoutParams(-2,-2))}
        contentDescription="샘플 동선도. 가상 시설의 하차, 주차, 재합류 단계. 실제 지도나 경로가 아닙니다."
    }
    fun highlight(point:Waypoint){
        if(selected==point && pins.getValue(point).tag==point)return
        selected=point
        pins.forEach{(p,v)->v.tag=p;v.background=ui.shape(if(p==selected)Palette.blue else Color.WHITE,18,if(p==selected)null else Palette.line);v.setTextColor(if(p==selected)Color.WHITE else if(p==Waypoint.MEET)Palette.green else Palette.blue);v.isSelected=p==selected}
    }
    fun setControlsEnabled(enabled:Boolean){pins.values.forEach{it.isEnabled=enabled}}
    override fun onMeasure(widthSpec:Int,heightSpec:Int){
        val compact=MeasureSpec.getSize(heightSpec)<ui.dp(190)
        labels.values.forEach{it.visibility=if(compact)GONE else VISIBLE}
        super.onMeasure(widthSpec,heightSpec)
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        backgroundMap.layout(0,0,width,height)
        val positions=if(height<ui.dp(190))listOf(.24f to .66f,.50f to .66f,.76f to .66f)else listOf(.31f to .43f,.64f to .69f,.75f to .40f)
        Waypoint.entries.forEachIndexed{i,p->
            val x=(width*positions[i].first).toInt();val y=(height*positions[i].second).toInt()
            val pin=pins.getValue(p);val size=ui.dp(52);pin.layout(x-size/2,y-size/2,x+size/2,y+size/2)
            val label=labels.getValue(p);val lw=label.measuredWidth;val lh=label.measuredHeight
            label.layout(x-lw/2,y+size/2+ui.dp(4),x+lw/2,y+size/2+ui.dp(4)+lh)
        }
    }
}
