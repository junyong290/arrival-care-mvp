package ai.arrivalcare.app
import android.app.Activity
import ai.arrivalcare.app.data.*
object Integration {
    fun navigation(activity: Activity): NavigationGateway = PleosProbeNavigationGateway(activity)
    fun vehicle(activity: Activity): VehicleGateway = PleosVehicleGateway(activity)
}
