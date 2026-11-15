package ai.arrivalcare.app.domain
import org.junit.Assert.*
import org.junit.Test

class ArrivalRecommendationTest{
    @Test fun realVenueCannotFallBackToLegacyDemoPlan(){val j=Journey();j.configure(Conditions(facilityId="asan"));j.begin();assertEquals(Stage.SETUP,j.state.stage);assertNull(j.state.request)}
    @Test fun repeatedBeginDoesNotDuplicateRoute(){val j=Journey();val p=ArrivalRecommendationEngine.recommend(j.state.conditions).plan;j.begin(p,true);val first=j.state.request;j.begin(p,true);assertEquals(first,j.state.request)}
    @Test fun familyChangesAllThreePlaces(){val a=ArrivalRecommendationEngine.recommend(Conditions()).plan!!;val b=ArrivalRecommendationEngine.recommend(Conditions(passengers=setOf(Passenger.CHILD))).plan!!;assertNotEquals(a.facility.drop,b.facility.drop);assertNotEquals(a.facility.parking,b.facility.parking);assertNotEquals(a.facility.meeting,b.facility.meeting)}
    @Test fun wheelchairChoosesLevelFixture(){assertEquals("level",ArrivalRecommendationEngine.recommend(Conditions(passengers=setOf(Passenger.WHEELCHAIR))).variant)}
    @Test fun missingStepFreeEvidenceHoldsRecommendation(){assertFalse(ArrivalRecommendationEngine.recommend(Conditions(passengers=setOf(Passenger.WHEELCHAIR)),false).canStart)}
    @Test fun ordinaryPeopleCanChooseShortWalk(){val c=Conditions(passengers=setOf(Passenger.GENERAL));assertEquals("central",ArrivalRecommendationEngine.recommend(c).variant);assertEquals("near",ArrivalRecommendationEngine.recommend(c.copy(preferences=setOf(Preference.SHORT_WALK))).variant)}
    @Test fun driverOnlyAlwaysStaysTogether(){assertEquals(Mode.TOGETHER,ArrivalRecommendationEngine.recommend(Conditions(caregiver=false)).plan!!.mode)}
    @Test fun realHospitalDoesNotInventDropoff(){assertNull(ArrivalRecommendationEngine.recommend(Conditions(facilityId="asan")).plan)}
    @Test fun demoCannotReachRealNavigation(){assertNull(ArrivalRecommendationEngine.recommend(Conditions()).plan!!.facility.dropTarget);assertNotNull(LiveRoutePolicy.rejection(RouteTarget(GeoPoint(1.0,2.0),"id","",true,true),true,true))}
    @Test fun coordinateValidationRejectsNan(){assertFalse(GeoPoint(Double.NaN,1.0).valid)}
    @Test fun liveRouteRequiresPermissionAndConnection(){val t=RouteTarget(GeoPoint(37.0,127.0),"approved","",true,false);assertNotNull(LiveRoutePolicy.rejection(t,false,true));assertNotNull(LiveRoutePolicy.rejection(t,true,false));assertNull(LiveRoutePolicy.rejection(t,true,true))}
    @Test fun matchingCoordinatesDoNotReplacePoiIdentity(){val t=RouteTarget(GeoPoint(37.0,127.0),"approved","",true,false);assertFalse(LiveRoutePolicy.destinationMatches(t,t.point,"different"));assertTrue(LiveRoutePolicy.destinationMatches(t,t.point,"approved"))}
    @Test fun noRouteResponseBeforeExplicitApproval(){val j=Journey();j.begin(ArrivalRecommendationEngine.recommend(j.state.conditions).plan,true);val r=j.state.request!!;j.routeResult(r.id,r.destinationId,true);assertEquals(Stage.ROUTING_DROPOFF,j.state.stage);assertEquals(1,j.state.ignoredResponses);j.approveRoute();j.routeResult(r.id,r.destinationId,true);assertEquals(Stage.EN_ROUTE_DROPOFF,j.state.stage)}
    @Test fun exitConfirmationPreparesParkingButDoesNotApproveIt(){val j=Journey();j.begin(ArrivalRecommendationEngine.recommend(j.state.conditions).plan,true);j.approveRoute();val r=j.state.request!!;j.routeResult(r.id,r.destinationId,true);j.arrived();j.setDoor(Door.CLOSED);j.checkParked(true);j.checkExited(true);j.confirmExit();assertEquals(Stage.ROUTING_PARKING,j.state.stage);assertFalse(j.state.routeApproved);val next=j.state.request!!;j.approveRoute();j.approveRoute();assertEquals(next,j.state.request)}
    @Test fun movingCannotApprovePreparedRoute(){val j=Journey();j.begin(null,true);j.setMotion(true);j.approveRoute();assertFalse(j.state.routeApproved)}
    @Test fun retryGetsNewTokenAndNeedsApproval(){val j=Journey();j.begin(null,true);j.approveRoute();val a=j.state.request!!;j.routeResult(a.id,a.destinationId,false);j.retry();assertFalse(j.state.routeApproved);assertNotEquals(a.id,j.state.request!!.id);j.routeResult(a.id,a.destinationId,true);assertEquals(Stage.ROUTING_DROPOFF,j.state.stage)}
}
