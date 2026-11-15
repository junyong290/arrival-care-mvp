package ai.arrivalcare.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.arrivalcare.app.data.*
import ai.arrivalcare.app.domain.RouteRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import java.util.concurrent.Executors

/** Read-only real SDK probe. Fictional sites intentionally have no navigable coordinates. */
class PleosProbeNavigationGateway(context: Context) : NavigationGateway {
    @Volatile private var sdk:NaviHelper?=null
    private val mock=MockNavigationGateway()
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var closed=false
    @Volatile private var status="연결 확인 중"
    private val worker=Executors.newSingleThreadExecutor{task->Thread(task,"ArrivalCare-Navi").apply{isDaemon=true}}
    override val description get()="경로·도착은 시뮬레이션 / NaviHelper SDK: $status"
    private val listener=object:NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) { report("목적지 정보 콜백 수신") }
        override fun onError(errorCode: NaviErrorCode) { report("SDK 오류: $errorCode") }
    }
    init {
        handler.postDelayed({ if(!closed && status=="연결 확인 중") report("연결 시간 초과 · 미연결") },12000)
        worker.execute{try {
            val instance=NaviHelper(context.applicationContext)
            sdk=instance
            if(closed){runCatching{instance.release()};return@execute}
            instance.initialize(onConnected={
                if(!closed) {
                    report("서비스 연결 성공")
                    try{worker.execute{if(!closed)try {instance.addListener(listener);instance.getDestinationInfo()} catch(e:Exception){report("조회 실패: ${e.javaClass.simpleName}")}}}catch(_:java.util.concurrent.RejectedExecutionException){}
                }
            },onFailed={e->if(!closed)report("연결 실패: ${e.javaClass.simpleName}")})
        } catch(e:Exception){report("초기화 실패: ${e.javaClass.simpleName}")}}
    }
    private fun report(value:String){if(!closed){status=value;Log.i("ArrivalCareSDK","NaviHelper $value")}}
    override fun request(request:RouteRequest,result:(Long,String,Boolean,String)->Unit) {
        // Never send a made-up hospital/drop-off coordinate to the real navigation service.
        // Probe callbacks cannot advance the journey; only the isolated mock request carries its token.
        mock.request(request,result)
    }
    override fun close(){closed=true;handler.removeCallbacksAndMessages(null);mock.close();worker.shutdownNow();Thread({runCatching{sdk?.removeListener(listener)};runCatching{sdk?.release()}},"ArrivalCare-Navi-close").apply{isDaemon=true;start()}}
}
