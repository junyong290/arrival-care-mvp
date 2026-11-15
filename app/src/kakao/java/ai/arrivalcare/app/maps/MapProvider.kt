package ai.arrivalcare.app.maps

import android.content.Context
import android.graphics.*
import android.view.View
import ai.arrivalcare.app.BuildConfig
import ai.arrivalcare.app.ui.Palette
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions

object MapProvider {
    fun create(context:Context,onReady:()->Unit,onError:(String)->Unit,onPoint:(Waypoint)->Unit):MapRenderer =
        KakaoRenderer(context,onReady,onError,onPoint)
}

private class KakaoRenderer(context:Context,onReady:()->Unit,onError:(String)->Unit,onPoint:(Waypoint)->Unit):MapRenderer{
    private val mapView:MapView
    override val view:View get()=mapView
    private var map:KakaoMap?=null
    private val labels=mutableMapOf<Waypoint,Label>()
    private val bitmaps=mutableMapOf<Pair<Waypoint,Boolean>,Bitmap>()
    private var active:Waypoint?=null
    private var closed=false
    private val density=context.resources.displayMetrics.density
    init{
        if(!KakaoMapSdk.isInitialized())KakaoMapSdk.init(context.applicationContext,BuildConfig.KAKAO_MAP_NATIVE_KEY)
        mapView=MapView(context).apply{setFinishManually(true)}
        mapView.start(object:MapLifeCycleCallback(){
            override fun onMapDestroy(){if(!closed)onError("지도 종료")}
            override fun onMapError(error:Exception){if(!closed)onError(error.javaClass.simpleName)}
        },object:KakaoMapReadyCallback(){
            override fun getPosition()=LatLng.from(DemoMapPoints.center.first,DemoMapPoints.center.second)
            override fun getZoomLevel()=16
            override fun onMapReady(kakaoMap:KakaoMap){
                mapView.post{
                    if(!closed){
                        map=kakaoMap
                        val layer=kakaoMap.labelManager?.layer
                        if(layer==null){onError("마커 레이어 없음");return@post}
                        Waypoint.entries.forEach{point->
                            val coord=DemoMapPoints.coordinate(point)
                            labels[point]=layer.addLabel(LabelOptions.from(point.name,LatLng.from(coord.first,coord.second))
                                .setStyles(pin(point,false)).setClickable(true).setRank(100).setTag(point.name))
                        }
                        kakaoMap.setOnLabelClickListener{_,_,label->
                            Waypoint.entries.firstOrNull{it.name==label.tag?.toString()}?.let{onPoint(it)}
                            true
                        }
                        active=null
                        onReady()
                    }
                }
            }
        })
    }
    private fun pin(point:Waypoint,selected:Boolean):Bitmap=bitmaps.getOrPut(point to selected){
        val size=(64*density).toInt()
        Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also{bitmap->
            val c=Canvas(bitmap);val p=Paint(Paint.ANTI_ALIAS_FLAG)
            p.color=Color.WHITE;c.drawCircle(size*.5f,size*.5f,size*.45f,p)
            p.color=if(selected)Palette.blue else if(point==Waypoint.MEET)Palette.green else Palette.navy
            c.drawCircle(size*.5f,size*.5f,size*.39f,p)
            p.color=Color.WHITE;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=size*.37f;p.textAlign=Paint.Align.CENTER
            c.drawText(point.letter,size*.5f,size*.64f,p)
        }
    }
    override fun highlight(point:Waypoint){
        if(closed || active==point)return
        active=point;labels.forEach{(p,label)->label.setStyles(pin(p,p==point))}
    }
    override fun recenter(){if(!closed)map?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(DemoMapPoints.center.first,DemoMapPoints.center.second),16))}
    override fun resume(){if(!closed)mapView.resume()}
    override fun pause(){if(!closed)mapView.pause()}
    override fun close(){if(closed)return;closed=true;map?.setOnLabelClickListener(null);mapView.finish();labels.clear();map=null}
}
