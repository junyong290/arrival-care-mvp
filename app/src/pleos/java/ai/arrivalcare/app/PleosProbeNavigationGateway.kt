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

/** Read-only real SDK probe. Fictional sites intentionally have no navigable coordinates. */
class PleosProbeNavigationGateway(context: Context) : NavigationGateway {
    private val sdk=NaviHelper(context.applicationContext)
    private val mock=MockNavigationGateway()
    private val handler=Handler(Looper.getMainLooper())
    private var closed=false
    private var status="연결 확인 중"
    override val description get()="경로·도착은 시뮬레이션 / NaviHelper SDK: $status"
    private val listener=object:NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) { report("목적지 정보 콜백 수신") }
        override fun onError(errorCode: NaviErrorCode) { report("SDK 오류: $errorCode") }
    }
    init {
        handler.postDelayed({ if(!closed && status=="연결 확인 중") report("연결 시간 초과 · 미연결") },12000)
        try {
            sdk.initialize(onConnected={
                if(!closed) {
                    report("서비스 연결 성공")
                    try { sdk.addListener(listener);sdk.getDestinationInfo() } catch(e:Exception){report("조회 실패: ${e.javaClass.simpleName}")}
                }
            },onFailed={e->if(!closed)report("연결 실패: ${e.javaClass.simpleName}")})
        } catch(e:Exception){report("초기화 실패: ${e.javaClass.simpleName}")}
    }
    private fun report(value:String){status=value;Log.i("ArrivalCareSDK","NaviHelper $value")}
    override fun request(request:RouteRequest,result:(Long,String,Boolean,String)->Unit) {
        // Never send a made-up hospital/drop-off coordinate to the real navigation service.
        // Probe callbacks cannot advance the journey; only the isolated mock request carries its token.
        mock.request(request,result)
    }
    override fun close(){closed=true;handler.removeCallbacksAndMessages(null);mock.close();runCatching{sdk.removeListener(listener)};runCatching{sdk.release()}}
}
