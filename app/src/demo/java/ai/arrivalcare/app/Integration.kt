package ai.arrivalcare.app
import android.app.Activity
import ai.arrivalcare.app.data.*
object Integration {
    fun navigation(activity: Activity): NavigationGateway = MockNavigationGateway()
    fun vehicle(activity: Activity): VehicleGateway = MockVehicleGateway()
}
