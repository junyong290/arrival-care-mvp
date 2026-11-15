package ai.arrivalcare.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Repositions the same map View without detaching or recreating its GL surface. */
class AdaptiveJourneyLayout(context:Context,val map:View,val guide:View):ViewGroup(context){
    private val ui=Ui(context)
    var wide=false;private set
    var minimumGuideDp=220
        set(value){if(field!=value){field=value;requestLayout()}}
    private var mapWidth=0
    private var mapHeight=0
    init{addView(map);addView(guide);clipToPadding=false}
    override fun onMeasure(widthSpec:Int,heightSpec:Int){
        val w=MeasureSpec.getSize(widthSpec);val h=MeasureSpec.getSize(heightSpec)
        val density=resources.displayMetrics.density
        wide=ResponsivePolicy.sideBySide((w/density).toInt(),(h/density).toInt())
        val gap=ui.dp(if(wide)16 else 0)
        if(wide){
            val guideWidth=(w*.41f).toInt().coerceIn(ui.dp(320),ui.dp(600)).coerceAtMost(w-ui.dp(280))
            mapWidth=w-guideWidth-gap;mapHeight=h
            map.measure(MeasureSpec.makeMeasureSpec(mapWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY))
            guide.measure(MeasureSpec.makeMeasureSpec(guideWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY))
        }else{
            mapWidth=w
            val heightDp=(h/density).toInt()
            mapHeight=if(heightDp>=400)(h*.52f).toInt().coerceAtMost(h-ui.dp(minimumGuideDp)).coerceAtLeast(ui.dp(70)) else ui.dp(ResponsivePolicy.mapHeight(heightDp))
            map.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(mapHeight,MeasureSpec.EXACTLY))
            guide.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((h-mapHeight).coerceAtLeast(0),MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w,h)
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        map.layout(0,0,mapWidth,mapHeight)
        if(wide)guide.layout(mapWidth+ui.dp(16),0,width,height) else guide.layout(0,mapHeight,width,height)
    }
}
