package ai.arrivalcare.app.maps

import android.content.Context
import android.graphics.*
import android.view.View
import ai.arrivalcare.app.BuildConfig
import ai.arrivalcare.app.ui.Palette
import ai.arrivalcare.app.domain.*
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions

object MapProvider {
    fun create(context:Context,onReady:()->Unit,onError:(String)->Unit,onPoint:(Waypoint?)->Unit):MapRenderer =
        KakaoRenderer(context,onReady,onError,onPoint)
}

private class KakaoRenderer(context:Context,onReady:()->Unit,onError:(String)->Unit,onPoint:(Waypoint?)->Unit):MapRenderer{
    private val mapView:MapView
    override val view:View get()=mapView
    private var map:KakaoMap?=null
    private val labels=mutableMapOf<String,Label>()
    private val bitmaps=mutableMapOf<Pair<String,Boolean>,Bitmap>()
    private var scene=MapScene.from(State())
    private var vehicleLabel:Label?=null
    private var active:Waypoint?=null
    private var closed=false
    private val density=context.resources.displayMetrics.density
    init{
        if(!KakaoMapSdk.isInitialized())KakaoMapSdk.init(context.applicationContext,BuildConfig.KAKAO_MAP_NATIVE_KEY)
        mapView=MapView(context).apply{setFinishManually(true)}
        mapView.start(object:MapLifeCycleCallback(){
            override fun onMapDestroy(){if(!closed)onError("지도 종료")}
            override fun onMapError(error:Exception){if(!closed){
                val code=(error as? MapAuthException)?.errorCode
                val reason=when(code){
                    401->"지도 인증 실패 · 앱 키·패키지·키 해시 확인"
                    403->"지도 사용 권한 없음 · 개발자 콘솔 사용 설정 확인"
                    429->"지도 요청 한도 초과 · 잠시 후 다시 연결"
                    -2,-3,-4,499->"지도 네트워크 연결 실패 · 인터넷 확인"
                    else->"지도 연결 오류 · 다시 연결해 주세요"
                }
                onError(reason+if(code!=null)" ("+code+")" else "")
            }}
        },object:KakaoMapReadyCallback(){
            override fun getPosition()=LatLng.from(scene.venue.center.latitude,scene.venue.center.longitude)
            override fun getZoomLevel()=16
            override fun onMapReady(kakaoMap:KakaoMap){
                mapView.post{
                    if(!closed){
                        map=kakaoMap
                        if(kakaoMap.labelManager?.layer==null){onError("마커 레이어 없음");return@post}
                        drawScene()
                        kakaoMap.setOnLabelClickListener{_,_,label->
                            scene.spots.firstOrNull{it.id==label.tag?.toString()}?.let{onPoint(it.waypoint)}
                            true
                        }
                        active=null
                        onReady()
                    }
                }
            }
        })
    }
    private fun pin(point:String,selected:Boolean):Bitmap=bitmaps.getOrPut(point to selected){
        val size=(64*density).toInt()
        Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also{bitmap->
            val c=Canvas(bitmap);val p=Paint(Paint.ANTI_ALIAS_FLAG)
            p.color=Color.WHITE;c.drawCircle(size*.5f,size*.5f,size*.45f,p)
            p.color=if(selected)Palette.blue else if(point=="M")Palette.green else Color.rgb(20,35,61)
            c.drawCircle(size*.5f,size*.5f,size*.39f,p)
            p.color=Color.WHITE;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=size*.37f;p.textAlign=Paint.Align.CENTER
            c.drawText(point,size*.5f,size*.64f,p)
        }
    }
    override fun highlight(point:Waypoint){
        if(closed || active==point)return
        active=point;labels.forEach{(p,label)->label.setStyles(pin(p,p==point.letter))}
        scene.spots.firstOrNull{it.waypoint==point}?.let{map?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(it.point.latitude,it.point.longitude),17))}
    }
    override fun setScene(scene:MapScene){if(closed)return;this.scene=scene;drawScene();recenter()}
    private fun drawScene(){
        val layer=map?.labelManager?.layer?:return
        layer.removeAll();labels.clear();vehicleLabel=null;active=null
        scene.spots.forEach{s->labels[s.id]=layer.addLabel(LabelOptions.from(s.id,LatLng.from(s.point.latitude,s.point.longitude)).setStyles(pin(s.id,false)).setClickable(true).setRank(100).setTag(s.id))}
        if(scene.venue.simulated){val p=scene.venue.center;vehicleLabel=layer.addLabel(LabelOptions.from("simulation",LatLng.from(p.latitude-.0005,p.longitude-.0012)).setStyles(pin("C",true)).setRank(90))}
    }
    override fun setVehicle(point:GeoPoint?){
        if(closed||scene.venue.simulated)return
        if(point==null||!point.valid){vehicleLabel?.remove();vehicleLabel=null;return}
        val p=LatLng.from(point.latitude,point.longitude)
        if(vehicleLabel==null)vehicleLabel=map?.labelManager?.layer?.addLabel(LabelOptions.from("vehicle",p).setStyles(pin("V",true)).setRank(110))else vehicleLabel?.moveTo(p)
    }
    override fun zoom(inward:Boolean){if(!closed)map?.moveCamera(if(inward)CameraUpdateFactory.zoomIn()else CameraUpdateFactory.zoomOut())}
    override fun recenter(){if(!closed)map?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(scene.venue.center.latitude,scene.venue.center.longitude),16))}
    override fun resume(){if(!closed)mapView.resume()}
    override fun pause(){if(!closed)mapView.pause()}
    override fun close(){if(closed)return;closed=true;map?.setOnLabelClickListener(null);mapView.finish();labels.clear();map=null}
}
