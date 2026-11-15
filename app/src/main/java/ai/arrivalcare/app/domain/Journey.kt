package ai.arrivalcare.app.domain

enum class Passenger(val label: String) { PARENT("부모님"), CHILD("아이"), STROLLER("유모차"), WHEELCHAIR("휠체어 이용자") }
enum class Mode { DROP_FIRST, TOGETHER }
enum class Door { CLOSED, OPEN, UNKNOWN, DISCONNECTED }
enum class Stage { SETUP, ROUTING_DROPOFF, EN_ROUTE_DROPOFF, EXIT_CHECK, ROUTING_PARKING, EN_ROUTE_PARKING, PARKING_CHECK, REUNION, DONE }
data class Conditions(val facilityId: String = "hospital", val passengers: Set<Passenger> = setOf(Passenger.PARENT), val caregiver: Boolean = true, val requestedMode: Mode = Mode.DROP_FIRST)
data class Facility(val id: String, val name: String, val drop: String, val parking: String, val meeting: String, val walking: String)
object DemoFacilities {
    // Fictional identifiers only: deliberately no latitude/longitude for unverified facilities.
    val all = listOf(
        Facility("hospital", "가상 A병원", "D1 · 정문 승하차 구역", "P1 · 동관 주차장", "1층 안내데스크 옆 대기공간", "P1 주차장 → E1 승강기 → G1 출입구 → 1층 안내데스크"),
        Facility("mall", "가상 B쇼핑몰", "D2 · 동문 승하차 구역", "P2 · 동문 주차장", "1층 가족 라운지 앞", "P2 주차장 → E2 승강기 → G2 동문 → 1층 가족 라운지")
    )
    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}
data class Plan(val conditions: Conditions, val facility: Facility, val mode: Mode, val reason: String, val mobility: String) {
    companion object {
        fun create(c: Conditions): Plan {
            val mode = if (!c.caregiver) Mode.TOGETHER else c.requestedMode
            val reason = when {
                !c.caregiver -> "운전자가 유일한 보호자이므로 함께 주차하고 이동해요."
                mode == Mode.TOGETHER -> "일행과 함께 주차한 뒤 약속 장소까지 이동해요."
                else -> "동승자는 별도 보호자와 하차하고, 운전자는 주차 후 합류해요."
            }
            val mobility = buildList {
                if (Passenger.PARENT in c.passengers) add("부모님 보행 속도에 맞춰 이동하고, 필요하면 안내데스크에 휴식 공간을 문의하세요.")
                if (Passenger.CHILD in c.passengers) add("아이와 보호자가 함께 이동하고, 주차장에서는 손을 잡아 주세요.")
                if (Passenger.STROLLER in c.passengers) add("유모차는 승강기 이용 여부와 출입구 단차를 현장에서 확인하세요.")
                if (Passenger.WHEELCHAIR in c.passengers) add("휠체어 이동 전 승강기 운행·통로 폭·단차를 시설 직원에게 확인하세요.")
            }.joinToString("\n")
            return Plan(c, DemoFacilities.get(c.facilityId), mode, reason, mobility)
        }
    }
}
data class RouteRequest(val id: Long, val destinationId: String, val name: String)
data class State(
    val conditions: Conditions = Conditions(), val plan: Plan? = null, val stage: Stage = Stage.SETUP,
    val moving: Boolean = false, val uxReady: Boolean = true, val door: Door = Door.UNKNOWN,
    val parked: Boolean = false, val exited: Boolean = false, val manualDoorCheck: Boolean = false,
    val sequence: Long = 0, val request: RouteRequest? = null, val error: String? = null, val ignoredResponses: Int = 0,
    val notice: String? = null
) {
    val inputAllowed get() = !moving && uxReady
    val canConfirmExit get() = stage == Stage.EXIT_CHECK && inputAllowed && parked && exited && (door == Door.CLOSED || (door in setOf(Door.UNKNOWN, Door.DISCONNECTED) && manualDoorCheck))
}

/** Pure state machine. Signals never stand in for the passenger's explicit confirmation. */
class Journey(initial: State = State()) {
    var state = initial; private set
    private fun update(s: State) { state = s }
    fun configure(c: Conditions) {
        if (state.stage == Stage.SETUP && state.inputAllowed && c.passengers.isNotEmpty()) update(state.copy(conditions = c, notice = null))
    }
    fun setMotion(moving: Boolean, ready: Boolean = true) {
        update(state.copy(moving = moving, uxReady = ready, parked = if (moving || !ready) false else state.parked,
            exited = if (moving || !ready) false else state.exited, manualDoorCheck = if (moving || !ready) false else state.manualDoorCheck))
    }
    fun setDoor(door: Door) { if (door != state.door) update(state.copy(door = door, manualDoorCheck = false)) }
    fun begin() {
        if (state.stage != Stage.SETUP || !state.inputAllowed) return
        val p = Plan.create(state.conditions)
        update(state.copy(plan = p, parked = false, exited = false, manualDoorCheck = false, notice = null))
        route(if (p.mode == Mode.DROP_FIRST) Stage.ROUTING_DROPOFF else Stage.ROUTING_PARKING)
    }
    private fun route(stage: Stage) {
        val p = state.plan ?: return
        val drop = stage == Stage.ROUTING_DROPOFF
        val id = state.sequence + 1
        update(state.copy(stage = stage, sequence = id,
            request = RouteRequest(id, p.facility.id + if (drop) ":drop" else ":park", if (drop) p.facility.drop else p.facility.parking), error = null))
    }
    fun routeResult(id: Long, destinationId: String, success: Boolean, message: String = "경로 요청에 실패했습니다. 연결을 확인하고 다시 시도하세요.") {
        val r = state.request
        if (r == null || r.id != id || r.destinationId != destinationId || state.error != null || state.stage !in setOf(Stage.ROUTING_DROPOFF, Stage.ROUTING_PARKING)) {
            update(state.copy(ignoredResponses = state.ignoredResponses + 1)); return
        }
        update(if (success) state.copy(stage = if (state.stage == Stage.ROUTING_DROPOFF) Stage.EN_ROUTE_DROPOFF else Stage.EN_ROUTE_PARKING, request = null, error = null)
        else state.copy(error = message))
    }
    fun retry() {
        if (!state.inputAllowed || state.error == null) return
        if (state.stage in setOf(Stage.ROUTING_DROPOFF, Stage.ROUTING_PARKING)) route(state.stage)
    }
    fun arrived() {
        if (!state.inputAllowed) return
        when (state.stage) {
            Stage.EN_ROUTE_DROPOFF -> update(state.copy(stage = Stage.EXIT_CHECK, parked = false, exited = false, manualDoorCheck = false))
            Stage.EN_ROUTE_PARKING -> update(state.copy(stage = Stage.PARKING_CHECK, parked = false))
            else -> Unit
        }
    }
    fun checkParked(value: Boolean) { if (state.inputAllowed && state.stage in setOf(Stage.EXIT_CHECK, Stage.PARKING_CHECK)) update(state.copy(parked = value)) }
    fun checkExited(value: Boolean) { if (state.inputAllowed && state.stage == Stage.EXIT_CHECK) update(state.copy(exited = value)) }
    fun checkDoorsManually(value: Boolean) { if (state.inputAllowed && state.stage == Stage.EXIT_CHECK && state.door in setOf(Door.UNKNOWN, Door.DISCONNECTED)) update(state.copy(manualDoorCheck = value)) }
    fun confirmExit() { if (state.canConfirmExit) route(Stage.ROUTING_PARKING) }
    fun confirmParking() { if (state.inputAllowed && state.stage == Stage.PARKING_CHECK && state.parked) update(state.copy(stage = Stage.REUNION)) }
    fun finish() { if (state.inputAllowed && state.stage == Stage.REUNION) update(state.copy(stage = Stage.DONE)) }
    fun reset() { if (state.inputAllowed) update(State(conditions = state.conditions, door = state.door, sequence = state.sequence + 1, ignoredResponses = state.ignoredResponses)) }
}
