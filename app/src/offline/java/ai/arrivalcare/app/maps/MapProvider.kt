package ai.arrivalcare.app.maps
import android.content.Context

object MapProvider {
    fun create(context:Context,onReady:()->Unit,onError:(String)->Unit,onPoint:(Waypoint)->Unit):MapRenderer?=null
}
