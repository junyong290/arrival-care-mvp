package ai.arrivalcare.app.domain

import org.junit.Assert.*
import org.junit.Test

class JourneyTest {
    private fun readyAtExit(door: Door = Door.CLOSED): Journey {
        val j=Journey();j.setDoor(door);j.begin();val r=j.state.request!!
        j.routeResult(r.id,r.destinationId,true);j.arrived();return j
    }
    private fun resolve(j: Journey) { val r=j.state.request!!;j.routeResult(r.id,r.destinationId,true) }
    @Test fun parentsHospitalEndToEnd() {
        val j=readyAtExit();val meeting=j.state.plan!!.facility.meeting
        j.checkParked(true);j.checkExited(true);j.confirmExit()
        assertEquals(Stage.ROUTING_PARKING,j.state.stage)
        resolve(j);j.arrived();j.checkParked(true);j.confirmParking()
        assertEquals(Stage.REUNION,j.state.stage);assertEquals(meeting,j.state.plan!!.facility.meeting)
        j.finish();assertEquals(Stage.DONE,j.state.stage)
    }
    @Test fun soleCaregiverAlwaysSkipsDropOff() {
        val j=Journey();j.configure(Conditions(caregiver=false));j.begin()
        assertEquals(Mode.TOGETHER,j.state.plan!!.mode);assertEquals(Stage.ROUTING_PARKING,j.state.stage)
        resolve(j);j.arrived();j.checkParked(true);j.confirmParking();assertEquals(Stage.REUNION,j.state.stage)
    }
    @Test fun selectedTogetherSkipsDropOff() {
        val j=Journey();j.configure(Conditions(requestedMode=Mode.TOGETHER));j.begin();assertEquals(Stage.ROUTING_PARKING,j.state.stage)
    }
    @Test fun doorCycleNeverConfirmsExit() {
        val j=readyAtExit();j.setDoor(Door.OPEN);j.setDoor(Door.CLOSED);j.confirmExit()
        assertEquals(Stage.EXIT_CHECK,j.state.stage);assertFalse(j.state.exited)
    }
    @Test fun bothHumanChecksAreRequired() {
        val j=readyAtExit();j.checkParked(true);j.confirmExit();assertEquals(Stage.EXIT_CHECK,j.state.stage)
        j.checkParked(false);j.checkExited(true);j.confirmExit();assertEquals(Stage.EXIT_CHECK,j.state.stage)
    }
    @Test fun openDoorBlocksEvenWithChecks() {
        val j=readyAtExit(Door.OPEN);j.checkParked(true);j.checkExited(true);j.checkDoorsManually(true);j.confirmExit()
        assertFalse(j.state.canConfirmExit);assertEquals(Stage.EXIT_CHECK,j.state.stage)
    }
    @Test fun unknownDoorRequiresAdditionalManualCheck() {
        val j=readyAtExit(Door.UNKNOWN);j.checkParked(true);j.checkExited(true);j.confirmExit();assertEquals(Stage.EXIT_CHECK,j.state.stage)
        j.checkDoorsManually(true);j.confirmExit();assertEquals(Stage.ROUTING_PARKING,j.state.stage)
    }
    @Test fun disconnectedDoorAllowsExplicitFallback() {
        val j=readyAtExit(Door.DISCONNECTED);j.checkParked(true);j.checkExited(true);j.checkDoorsManually(true)
        assertTrue(j.state.canConfirmExit)
    }
    @Test fun changedDoorInvalidatesManualCheck() {
        val j=readyAtExit(Door.UNKNOWN);j.checkDoorsManually(true);j.setDoor(Door.DISCONNECTED);assertFalse(j.state.manualDoorCheck)
    }
    @Test fun repeatedMissingSignalDoesNotEraseHumanFallback() {
        val j=readyAtExit(Door.UNKNOWN);j.checkDoorsManually(true);j.setDoor(Door.UNKNOWN);assertTrue(j.state.manualDoorCheck)
    }
    @Test fun movementInvalidatesChecksAndBlocksConfirmation() {
        val j=readyAtExit();j.checkParked(true);j.checkExited(true);j.setMotion(true);j.confirmExit()
        assertEquals(Stage.EXIT_CHECK,j.state.stage);assertFalse(j.state.parked);assertFalse(j.state.exited)
        j.setMotion(false);assertFalse(j.state.canConfirmExit)
    }
    @Test fun unknownRestrictionFailsClosed() {
        val j=Journey();j.setMotion(false,false);j.begin();j.configure(Conditions(facilityId="mall"))
        assertEquals(Stage.SETUP,j.state.stage);assertEquals("hospital",j.state.conditions.facilityId)
    }
    @Test fun failedNavigationStaysPendingUntilRetryCompletes() {
        val j=Journey();j.begin();val first=j.state.request!!;j.routeResult(first.id,first.destinationId,false)
        j.arrived();assertEquals(Stage.ROUTING_DROPOFF,j.state.stage);assertNotNull(j.state.error)
        j.retry();val second=j.state.request!!;assertTrue(second.id>first.id);resolve(j);assertEquals(Stage.EN_ROUTE_DROPOFF,j.state.stage)
    }
    @Test fun lateResponseFromPreviousRequestIsIgnored() {
        val j=Journey();j.begin();val first=j.state.request!!;j.routeResult(first.id,first.destinationId,false);j.retry()
        j.routeResult(first.id,first.destinationId,true);assertEquals(Stage.ROUTING_DROPOFF,j.state.stage);assertEquals(1,j.state.ignoredResponses)
        resolve(j);assertEquals(Stage.EN_ROUTE_DROPOFF,j.state.stage)
    }
    @Test fun wrongDestinationIsIgnored() {
        val j=Journey();j.begin();j.routeResult(j.state.request!!.id,"mall:park",true)
        assertEquals(Stage.ROUTING_DROPOFF,j.state.stage);assertEquals(1,j.state.ignoredResponses)
    }
    @Test fun timeoutCannotBeOverriddenByLateSuccessBeforeRetry() {
        val j=Journey();j.begin();val r=j.state.request!!;j.routeResult(r.id,r.destinationId,false);j.routeResult(r.id,r.destinationId,true)
        assertNotNull(j.state.error);assertEquals(Stage.ROUTING_DROPOFF,j.state.stage)
    }
    @Test fun resetInvalidatesPreviousJourneyResponse() {
        val j=Journey();j.begin();val r=j.state.request!!;j.reset();j.begin();j.routeResult(r.id,r.destinationId,true)
        assertEquals(Stage.ROUTING_DROPOFF,j.state.stage);assertTrue(j.state.request!!.id>r.id)
    }
    @Test fun repeatedCallbackCannotAdvanceExit() {
        val j=Journey();j.begin();val r=j.state.request!!;resolve(j);j.arrived();j.routeResult(r.id,r.destinationId,true)
        assertEquals(Stage.EXIT_CHECK,j.state.stage)
    }
    @Test fun parkingArrivalIsNotParkingCompletion() {
        val j=Journey();j.configure(Conditions(caregiver=false));j.begin();resolve(j);j.arrived();j.confirmParking()
        assertEquals(Stage.PARKING_CHECK,j.state.stage)
    }
    @Test fun facilityAndMobilityChangeRecommendation() {
        val a=Plan.create(Conditions());val b=Plan.create(Conditions("mall",setOf(Passenger.WHEELCHAIR,Passenger.STROLLER)))
        assertNotEquals(a.facility.parking,b.facility.parking);assertTrue(b.mobility.contains("휠체어"));assertTrue(b.mobility.contains("유모차"));assertFalse(b.mobility.contains("부모님"))
    }
    @Test fun planIsFrozenOnceStarted() {
        val j=Journey();j.begin();j.configure(Conditions(facilityId="mall"));assertEquals("hospital",j.state.plan!!.facility.id)
    }
    @Test fun emptyPassengerSelectionIsRejected() {
        val j=Journey();j.configure(Conditions(passengers=emptySet()));assertEquals(setOf(Passenger.PARENT),j.state.conditions.passengers)
    }
}
