package ai.arrivalcare.app.data

import android.os.Handler
import android.os.Looper
import ai.arrivalcare.app.domain.*

interface NavigationGateway {
    val description: String
    fun request(request: RouteRequest, result: (Long, String, Boolean, String) -> Unit)
    fun close()
}
interface VehicleGateway {
    val description: String
    fun connect(onDoor: (Door) -> Unit, onRestriction: (Boolean, Boolean) -> Unit)
    fun onForeground() {}
    fun onBackground() {}
    fun close()
}
class MockNavigationGateway : NavigationGateway {
    override val description = "경로 요청 시뮬레이션 · 실제 내비 미연결"
    private val handler = Handler(Looper.getMainLooper())
    var failNext = false
    var delayNext = false
    override fun request(request: RouteRequest, result: (Long, String, Boolean, String) -> Unit) {
        val fail = failNext; val delay = delayNext; failNext = false; delayNext = false
        if (delay) {
            handler.postDelayed({ result(request.id, request.destinationId, false, "응답 시간이 초과됐습니다. 다시 요청해 주세요.") }, 1800)
            handler.postDelayed({ result(request.id, request.destinationId, true, "") }, 6500)
        } else handler.postDelayed({ result(request.id, request.destinationId, !fail, "시뮬레이션: 내비 연결 실패. 다시 시도하면 복구됩니다.") }, 800)
    }
    override fun close() { handler.removeCallbacksAndMessages(null) }
}
class MockVehicleGateway : VehicleGateway {
    override val description = "도어·주행 신호 시뮬레이션"
    private var doorCallback: ((Door) -> Unit)? = null
    private var restrictionCallback: ((Boolean, Boolean) -> Unit)? = null
    override fun connect(onDoor: (Door) -> Unit, onRestriction: (Boolean, Boolean) -> Unit) {
        doorCallback = onDoor; restrictionCallback = onRestriction
        onDoor(Door.CLOSED); onRestriction(false, true)
    }
    fun door(value: Door) { doorCallback?.invoke(value) }
    fun motion(value: Boolean) { restrictionCallback?.invoke(value, true) }
    override fun close() { doorCallback = null; restrictionCallback = null }
}
