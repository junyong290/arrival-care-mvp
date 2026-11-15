package ai.arrivalcare.app

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Build
import android.util.Log

/** Real UX permission signal; never interpreted as a physical parking-brake sensor. */
class AutomotiveUxObserver(private val context:Context,private val update:(Boolean,Boolean)->Unit) {
    private var car:Car?=null
    private var manager:CarUxRestrictionsManager?=null
    private var closed=false
    fun start(){
        update(false,false)
        if(Build.VERSION.SDK_INT<29)return
        try {
            car=Car.createCar(context,null,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT){c,ready->
                if(!closed){
                    if(!ready){update(false,false);Log.w("ArrivalCareSDK","Car UX disconnected")}
                    else try {
                        val m=c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        manager=m
                        m.registerListener { restriction->if(!closed) update(restriction.isRequiresDistractionOptimization,true) }
                        update(m.currentCarUxRestrictions.isRequiresDistractionOptimization,true)
                        Log.i("ArrivalCareSDK","Car UX manager connected")
                    }catch(e:Exception){update(false,false);Log.e("ArrivalCareSDK","Car UX unavailable",e)}
                }
            }
        }catch(e:Exception){update(false,false);Log.e("ArrivalCareSDK","Car connect failed",e)}
    }
    fun close(){closed=true;runCatching{manager?.unregisterListener()};runCatching{car?.disconnect()}}
}
