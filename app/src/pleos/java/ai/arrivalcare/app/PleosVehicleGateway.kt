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

/** Poll actual supported passenger-door positions; never control a vehicle actuator. */
class PleosVehicleGateway(private val context:Context):VehicleGateway {
    private val handler=Handler(Looper.getMainLooper())
    private var sdk:Vehicle?=null
    private var ux:AutomotiveUxObserver?=null
    private var closed=false
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
        try{sdk=Vehicle(context.applicationContext).also{it.initialize()};poll()}catch(e:Exception){fail(e)}
    }
    private fun fail(e:Exception){if(!closed){status="연결·조회 실패: ${e.javaClass.simpleName}";callback?.invoke(Door.DISCONNECTED);Log.w("ArrivalCareSDK",status)}}
    private fun poll(){
        if(closed)return
        val epoch=++generation
        // A response for an older polling cycle cannot overwrite fresher data.
        handler.postDelayed({if(!closed && epoch==generation){status="도어 응답 시간 초과";callback?.invoke(Door.UNKNOWN)}},2500)
        try{
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
                            }
                        }
                    }
                }
            },onFailed={e->handler.post{if(epoch==generation)fail(e)}})
        }catch(e:Exception){fail(e)}
        handler.postDelayed({poll()},3000)
    }
    override fun close(){closed=true;++generation;handler.removeCallbacksAndMessages(null);ux?.close();runCatching{sdk?.release()};callback=null}
}
