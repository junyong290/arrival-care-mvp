package ai.arrivalcare.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import ai.arrivalcare.app.data.*
import ai.arrivalcare.app.domain.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.constants.NaviErrorCode
import java.util.concurrent.Executors

/** Explicit live mode. Unverified fixtures never cross the native routing boundary. */
class PleosProbeNavigationGateway(private val context:Context):NavigationGateway {
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor{r->Thread(r,"ArrivalCare-Navi").apply{isDaemon=true}}
    private val mock=MockNavigationGateway()
    @Volatile private var sdk:NaviHelper?=null
    @Volatile private var closed=false
    private var connected=false
    private var status="연결 확인 중"
    private var epoch=0L
    private var callback:((Long,String,Boolean,String)->Unit)?=null
    private var pending:RouteRequest?=null
    private var routingCompleted=false
    private var position:GeoPoint?=null
    private var positionTime=0L
    override var liveMode=false;private set
    override val simulator get()=if(liveMode)null else mock
    override val currentPosition get()=position?.takeIf{SystemClock.elapsedRealtime()-positionTime<10000}
    override val description get()=(if(liveMode)"실제 차량 내비 모드" else "시연 내비 모드")+" / NaviHelper: "+status
    private fun permission(name:String)=context.checkSelfPermission("pleos.car.permission."+name)==PackageManager.PERMISSION_GRANTED
    private fun io(task:()->Unit){if(!closed)runCatching{worker.execute{if(!closed)runCatching(task).onFailure{e->handler.post{if(!closed){status="SDK 호출 실패: "+e.javaClass.simpleName;finish(false,status)}}}}}}
    private fun report(value:String){status=value;Log.i("ArrivalCareSDK","NaviHelper "+value)}
    private var listener:NaviHelperEventListener?=null
    private val locationPoll=object:Runnable{override fun run(){if(!closed){if(connected&&permission("NAVI_ROUTE"))io{sdk?.getCurrentLocationInfo()};handler.postDelayed(this,5000)}}}
    init{connect()}
    private fun connect(){
        val generation=++epoch;connected=false;report("연결 확인 중")
        handler.postDelayed({if(!closed&&generation==epoch&&!connected)report("연결 시간 초과 · 미연결")},12000)
        io{
            val instance=NaviHelper(context.applicationContext);sdk=instance
            instance.initialize(onConnected={handler.post{
                if(!closed&&generation==epoch){connected=true;report("서비스 연결 성공");installListener(generation);handler.removeCallbacks(locationPoll);handler.post(locationPoll)}
            }},onFailed={e->handler.post{if(!closed&&generation==epoch){connected=false;report("연결 실패: "+e.javaClass.simpleName);finish(false,status)}}})
        }
    }
    private fun installListener(generation:Long){
        val l=object:NaviHelperEventListener{
            private fun current(action:()->Unit){handler.post{if(!closed&&generation==epoch)action()}}
            override fun onCurrentLocationInfo(info:CurrentLocationInfo){current{GeoPoint(info.latitude,info.longitude).takeIf{it.valid}?.let{position=it;positionTime=SystemClock.elapsedRealtime()}}}
            override fun onRoutingCompleted(){current{if(pending!=null){routingCompleted=true;io{sdk?.getDestinationInfo()}}}}
            override fun onDestinationInfo(info:DestinationInfo){current{
                val target=pending?.target
                if(routingCompleted&&target!=null){
                    if(LiveRoutePolicy.destinationMatches(target,GeoPoint(info.latitude,info.longitude),info.poiId.orEmpty()))finish(true,"")
                    else finish(false,"차량 내비 목적지가 요청한 장소와 달라요. 다시 확인해 주세요.")
                }
            }}
            override fun onError(errorCode:NaviErrorCode){current{report("SDK 오류: "+errorCode);finish(false,status)}}
            override fun onRouteCancelled(){current{finish(false,"차량 내비 경로가 취소됐어요")}}
        }
        listener=l;io{sdk?.addListener(l)}
    }
    override fun setLiveMode(enabled:Boolean){
        if(pending!=null)return
        liveMode=enabled;position=null
    }
    override fun request(request:RouteRequest,result:(Long,String,Boolean,String)->Unit){
        if(!liveMode){mock.request(request,result);return}
        val reason=LiveRoutePolicy.rejection(request.target,permission("NAVI_ROUTE_SEARCH")&&permission("NAVI_ROUTE"),connected)
        if(reason!=null){result(request.id,request.destinationId,false,reason);return}
        if(pending!=null){result(request.id,request.destinationId,false,"이전 차량 내비 요청을 확인 중이에요");return}
        val target=request.target!!;pending=request;callback=result;routingCompleted=false
        val token=request.id;val generation=epoch
        handler.postDelayed({if(!closed&&generation==epoch&&pending?.id==token){finish(false,"차량 내비 응답 시간 초과 · 연결을 다시 확인해 주세요");reconnect()}},15000)
        io{sdk?.requestRoute(RouteInfo(latitude=target.point.latitude,longitude=target.point.longitude,poiName=request.name,poiId=target.poiId,address=target.address))}
    }
    private fun finish(success:Boolean,message:String){
        val request=pending?:return;val cb=callback
        pending=null;callback=null;routingCompleted=false
        cb?.invoke(request.id,request.destinationId,success,message)
    }
    private fun reconnect(){
        ++epoch;connected=false;position=null
        val old=sdk;val oldListener=listener;sdk=null;listener=null
        io{oldListener?.let{old?.removeListener(it)};old?.release();handler.post{if(!closed)connect()}}
    }
    override fun close(){closed=true;++epoch;handler.removeCallbacksAndMessages(null);mock.close();worker.shutdownNow();pending=null;callback=null;Thread({runCatching{listener?.let{sdk?.removeListener(it)}};runCatching{sdk?.release()}},"ArrivalCare-Navi-close").apply{isDaemon=true;start()}}
}
