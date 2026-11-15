package ai.arrivalcare.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.arrivalcare.app.data.VehicleGateway
import ai.arrivalcare.app.domain.Door
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.DoorValue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Poll actual supported passenger-door positions; never control a vehicle actuator. */
class PleosVehicleGateway(private val context:Context):VehicleGateway {
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var sdk:Vehicle?=null
    private var ux:AutomotiveUxObserver?=null
    @Volatile private var closed=false
    private var initializing=true
    private var foreground=true
    private val pollTask=Runnable{poll()}
    private val worker=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue(8),
        { task -> Thread(task,"ArrivalCare-Vehicle").apply{isDaemon=true} })
    private var generation=0L
    private var callback:((Door)->Unit)?=null
    private var status="연결 확인 중"
    override val description get()="Vehicle SDK 2.3.2: $status / 도어 신호는 하차 확인을 대신하지 않음"
    private val required=setOf(DoorValue.ROW_1_LEFT,DoorValue.ROW_1_RIGHT,DoorValue.ROW_2_LEFT,DoorValue.ROW_2_RIGHT)
    override fun connect(onDoor:(Door)->Unit,onRestriction:(Boolean,Boolean)->Unit){
        callback=onDoor;onDoor(Door.UNKNOWN);onRestriction(false,false)
        if(!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)){
            status="차량 플랫폼 없음 · 미연결";onDoor(Door.DISCONNECTED);Log.w("ArrivalCareSDK",status);return
        }
        ux=AutomotiveUxObserver(context,onRestriction).also{it.start()}
        // A slow or unavailable system binder must never hold the Activity's UI thread.
        handler.postDelayed({if(!closed && initializing){status="SDK 초기화 응답 대기 · 미연결";callback?.invoke(Door.DISCONNECTED);Log.w("ArrivalCareSDK",status)}},8000)
        background({
            val instance=Vehicle(context.applicationContext)
            sdk=instance
            if(closed){runCatching{instance.release()};return@background}
            instance.initialize()
            handler.post{if(!closed){initializing=false;Log.i("ArrivalCareSDK","Vehicle 초기화 요청 반환 · 신호 검증 대기");poll()}}
        },{initializing=false;fail(it)})
    }
    private fun background(action:()->Unit,onFailure:(Exception)->Unit){
        if(closed)return
        try{worker.execute{if(!closed)try{action()}catch(e:Exception){handler.post{if(!closed)onFailure(e)}}}}
        catch(e:Exception){if(!closed)onFailure(e)}
    }
    private fun fail(e:Exception){if(!closed){++generation;status="연결·조회 실패: ${e.javaClass.simpleName}";callback?.invoke(Door.DISCONNECTED);Log.w("ArrivalCareSDK",status)}}
    private fun poll(){
        if(closed || !foreground)return
        handler.removeCallbacks(pollTask)
        val epoch=++generation
        // A response for an older polling cycle cannot overwrite fresher data.
        handler.postDelayed({if(!closed && epoch==generation){status="도어 응답 시간 초과";callback?.invoke(Door.UNKNOWN)}},2500)
        background({
            sdk?.getDoor()?.checkDoorPositionCapabilities(onComplete={areas:Map<DoorArea,AreaBounds<Int>>->
                handler.post{
                    if(!closed && epoch==generation){
                        val supported=areas.filterKeys{a->a.values.any{it in required}}
                        val coverage=supported.keys.flatMap{it.values.toList()}.toSet()
                        if(!coverage.containsAll(required)){
                            status="필수 4개 도어 지원 미확인";callback?.invoke(Door.UNKNOWN)
                        }else{
                            val values=mutableMapOf<DoorArea,Door>()
                            supported.forEach{(area,bounds)->
                                background({
                                sdk?.getDoor()?.getCurrentDoorPosition(area=area,onComplete={position:Int?->handler.post{
                                    if(!closed && epoch==generation){
                                        values[area]=when{position==null || bounds.minValue!=0 || position !in bounds.minValue..bounds.maxValue->Door.UNKNOWN;position==0->Door.CLOSED;else->Door.OPEN}
                                        if(values.size==supported.size){
                                            ++generation // invalidate this cycle's timeout after a complete fresh snapshot
                                            val aggregate=when{Door.OPEN in values.values->Door.OPEN;Door.UNKNOWN in values.values->Door.UNKNOWN;else->Door.CLOSED}
                                            status="지원 도어 ${supported.size}개 조회: $aggregate";callback?.invoke(aggregate)
                                            Log.i("ArrivalCareSDK",status)
                                        }
                                    }
                                }},onFailed={e->handler.post{if(epoch==generation)fail(e)}})
                                },{if(epoch==generation)fail(it)})
                            }
                        }
                    }
                }
            },onFailed={e->handler.post{if(epoch==generation)fail(e)}})
        },{if(epoch==generation)fail(it)})
        handler.postDelayed(pollTask,3000)
    }
    override fun onBackground(){foreground=false;++generation;handler.removeCallbacks(pollTask)}
    override fun onForeground(){val wasActive=foreground;foreground=true;if(!wasActive && !initializing && !closed)poll()}
    override fun close(){
        closed=true;++generation;handler.removeCallbacksAndMessages(null);ux?.close();callback=null
        worker.shutdownNow()
        Thread({runCatching{sdk?.release()}},"ArrivalCare-Vehicle-close").apply{isDaemon=true;start()}
    }
}
