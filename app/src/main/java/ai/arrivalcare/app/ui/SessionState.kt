package ai.arrivalcare.app.ui

import android.os.Bundle
import ai.arrivalcare.app.domain.*

/** Persist planning/progress, never physical safety checks or an assumed vehicle connection. */
object SessionState {
    fun save(state:State,out:Bundle){
        out.putString("facility",state.conditions.facilityId)
        out.putStringArrayList("passengers",ArrayList(state.conditions.passengers.map{it.name}))
        out.putBoolean("caregiver",state.conditions.caregiver)
        out.putString("mode",state.conditions.requestedMode.name)
        out.putStringArrayList("preferences",ArrayList(state.conditions.preferences.map{it.name}))
        out.putString("stage",state.stage.name)
        out.putLong("sequence",state.sequence)
    }
    fun restore(bundle:Bundle?,pleos:Boolean):State{
        if(bundle==null)return State(uxReady=!pleos)
        return runCatching{
            val people=bundle.getStringArrayList("passengers").orEmpty().mapNotNull{n->Passenger.entries.firstOrNull{it.name==n}}.toSet().ifEmpty{setOf(Passenger.PARENT)}
            val prefs=bundle.getStringArrayList("preferences").orEmpty().mapNotNull{n->Preference.entries.firstOrNull{it.name==n}}.toSet()
            val conditions=Conditions(bundle.getString("facility")?:"hospital",people,bundle.getBoolean("caregiver",true),Mode.valueOf(bundle.getString("mode")?:"DROP_FIRST"),prefs)
            var stage=Stage.valueOf(bundle.getString("stage")?:"SETUP")
            val plan=if(stage==Stage.SETUP)null else ArrivalRecommendationEngine.recommend(conditions).plan
            if(plan==null)stage=Stage.SETUP
            val sequence=bundle.getLong("sequence")+1
            val routing=stage in setOf(Stage.ROUTING_DROPOFF,Stage.ROUTING_PARKING)
            val drop=stage==Stage.ROUTING_DROPOFF
            val request=if(routing && plan!=null)RouteRequest(sequence,plan.facility.id+if(drop)":drop" else ":park",if(drop)plan.facility.drop else plan.facility.parking)else null
            State(conditions=conditions,plan=plan,stage=stage,uxReady=!pleos,sequence=sequence,request=request,
                requireRouteApproval=true,routeApproved=false,
                notice=if(stage!=Stage.SETUP)"여정을 복원했어요. 안전 확인은 다시 진행해 주세요." else null)
        }.getOrElse{State(uxReady=!pleos)}
    }
}
