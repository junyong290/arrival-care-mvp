package ai.arrivalcare.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.*
import android.widget.*

object Palette {
    var night=false
    val surface get()=if(night)Color.rgb(26,35,48) else Color.WHITE
    val navy get()=if(night)Color.rgb(239,245,255) else Color.rgb(20,35,61)
    val blue=Color.rgb(40,100,240)
    val blueSoft get()=if(night)Color.rgb(37,58,93) else Color.rgb(234,241,255)
    val ink get()=if(night)Color.rgb(222,232,245) else Color.rgb(32,47,69)
    val muted get()=if(night)Color.rgb(163,180,202) else Color.rgb(99,115,137)
    val paper get()=if(night)Color.rgb(15,23,35) else Color.rgb(244,247,250)
    val line get()=if(night)Color.rgb(49,65,85) else Color.rgb(225,232,242)
    val green=Color.rgb(17,130,108)
    val positiveText get()=if(night)Color.rgb(126,224,197) else green
    val accentText get()=if(night)Color.rgb(145,180,255) else blue
    val greenSoft get()=if(night)Color.rgb(24,62,55) else Color.rgb(231,246,239)
    val amber get()=if(night)Color.rgb(255,199,116) else Color.rgb(145,90,15)
    val amberSoft get()=if(night)Color.rgb(64,48,27) else Color.rgb(255,246,226)
}

class Ui(val context:Context) {
    fun dp(value:Int)=(value*context.resources.displayMetrics.density).toInt()
    fun column()=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
    fun row()=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    fun shape(color:Int,radius:Int=18,stroke:Int?=null)=GradientDrawable().apply{
        setColor(color);cornerRadius=dp(radius).toFloat()
        stroke?.let{setStroke(dp(1).coerceAtLeast(1),it)}
    }
    fun text(value:String,size:Int=16,color:Int=Palette.ink,bold:Boolean=false)=TextView(context).apply{
        text=value;textSize=size.toFloat();setTextColor(color)
        typeface=Typeface.create(if(bold)"sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        includeFontPadding=false;setLineSpacing(dp(3).toFloat(),1f)
    }
    fun label(parent:LinearLayout,value:String,size:Int=16,color:Int=Palette.ink,bold:Boolean=false,bottom:Int=10):TextView{
        val v=text(value,size,color,bold)
        parent.addView(v,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(bottom)})
        return v
    }
    fun pill(value:String,color:Int=Palette.accentText,fill:Int=Palette.blueSoft)=text(value,12,color,true).apply{
        background=shape(fill,30);setPadding(dp(10),dp(6),dp(10),dp(6));gravity=Gravity.CENTER
    }
    fun button(value:String,primary:Boolean=false,selected:Boolean=false,enabled:Boolean=true,icon:String?=null,action:()->Unit)=Button(context).apply{
        text=value;contentDescription=value;isAllCaps=false;textSize=15f
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        minHeight=dp(52);minimumHeight=dp(52);minimumWidth=0;minWidth=0
        setPadding(dp(14),dp(10),dp(14),dp(10));gravity=Gravity.CENTER
        isEnabled=enabled;isSelected=selected
        val fill=when{!enabled->Palette.line;primary->Palette.blue;selected->Palette.blueSoft;else->Palette.surface}
        val foreground=when{!enabled->Palette.muted;primary->Color.WHITE;selected->Palette.accentText;else->Palette.ink}
        setTextColor(foreground)
        background=RippleDrawable(ColorStateList.valueOf(Color.argb(40,60,110,220)),shape(fill,16,if(selected&&!primary)Palette.blue else null),null)
        stateListAnimator=null
        icon?.let{setCompoundDrawablesRelativeWithIntrinsicBounds(IconDrawable(context,it,foreground),null,null,null);compoundDrawablePadding=dp(7)}
        setOnClickListener{action()}
        setOnTouchListener{v,event->
            if(isEnabled) when(event.actionMasked){
                MotionEvent.ACTION_DOWN->v.animate().scaleX(.985f).scaleY(.985f).setDuration(70).start()
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            };false
        }
    }
    fun iconButton(name:String,icon:String,action:()->Unit)=object:ImageButton(context){
        private val glyph=IconDrawable(context,icon,Palette.navy)
        override fun onDraw(canvas:Canvas){
            super.onDraw(canvas)
            glyph.setBounds(paddingLeft,paddingTop,width-paddingRight,height-paddingBottom)
            glyph.draw(canvas)
        }
    }.apply{
        contentDescription=name;tooltipText=name
        background=RippleDrawable(ColorStateList.valueOf(Palette.blueSoft),shape(Palette.surface,16),null)
        setPadding(dp(13),dp(13),dp(13),dp(13));setOnClickListener{action()}
    }
    fun iconTile(icon:String,color:Int=Palette.blue,fill:Int=Palette.blueSoft,size:Int=48)=ImageView(context).apply{
        setImageDrawable(IconDrawable(context,icon,when(color){Palette.blue->Palette.accentText;Palette.green->Palette.positiveText;else->color}));setPadding(dp(12),dp(12),dp(12),dp(12))
        background=shape(fill,16);layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun spacer(parent:LinearLayout,height:Int){parent.addView(View(context),LinearLayout.LayoutParams(1,dp(height)))}
    fun card(parent:LinearLayout,icon:String,title:String,detail:String,color:Int=Palette.blue):LinearLayout{
        val r=row().apply{background=shape(Palette.paper,18);setPadding(dp(14),dp(14),dp(14),dp(14))}
        r.addView(iconTile(icon,color,if(color==Palette.green)Palette.greenSoft else Palette.blueSoft))
        val c=column();label(c,title,18,Palette.navy,true,5);label(c,detail,13,Palette.muted,false,0)
        r.addView(c,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(12)})
        parent.addView(r,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})
        return r
    }
    fun check(parent:LinearLayout,title:String,selected:Boolean,action:()->Unit){
        parent.addView(button(title,selected=selected,icon=if(selected)"check" else "square",action=action).apply{
            gravity=Gravity.CENTER_VERTICAL or Gravity.START;textSize=15f
        },LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})
    }
}

/** Small original vector icons, independent of emoji/font availability. */
class IconDrawable(private val context:Context,private val name:String,private val tint:Int):Drawable(){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun getIntrinsicWidth()=(24*context.resources.displayMetrics.density).toInt()
    override fun getIntrinsicHeight()=intrinsicWidth
    override fun draw(c:Canvas){
        c.save();c.translate(bounds.left.toFloat(),bounds.top.toFloat());c.scale(bounds.width()/24f,bounds.height()/24f)
        p.color=tint;p.style=Paint.Style.STROKE;p.strokeWidth=1.8f;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
        fun line(x:Float,y:Float,a:Float,b:Float)=c.drawLine(x,y,a,b,p)
        fun path(vararg xy:Float){val q=Path();q.moveTo(xy[0],xy[1]);for(i in 2 until xy.size step 2)q.lineTo(xy[i],xy[i+1]);c.drawPath(q,p)}
        when(name){
            "pin","brand"->{val q=Path();q.moveTo(12f,22f);q.cubicTo(7f,16f,4f,12f,4f,9f);q.cubicTo(4f,-1f,20f,-1f,20f,9f);q.cubicTo(20f,12f,17f,16f,12f,22f);c.drawPath(q,p);c.drawCircle(12f,9f,2.8f,p)}
            "hospital"->{c.drawRoundRect(4f,3f,20f,22f,2f,2f,p);line(9f,8f,15f,8f);line(12f,5f,12f,11f);path(9f,22f,9f,16f,15f,16f,15f,22f)}
            "mall"->{path(3f,7f,21f,7f,20f,21f,4f,21f,3f,7f);c.drawArc(8f,1f,16f,13f,180f,180f,false,p)}
            "person","parent","child"->{c.drawCircle(12f,5f,3f,p);path(5f,21f,5f,16f,8f,11f,16f,11f,19f,16f,19f,21f);if(name=="child")line(7f,4f,9f,2f)}
            "stroller"->{path(4f,3f,7f,3f,10f,15f,19f,15f,21f,9f,9f,9f);c.drawArc(9f,2f,21f,16f,180f,90f,false,p);c.drawCircle(11f,20f,1.5f,p);c.drawCircle(18f,20f,1.5f,p)}
            "wheelchair"->{c.drawCircle(13f,3f,2f,p);path(12f,7f,12f,13f,18f,13f,21f,20f);line(12f,9f,18f,9f);c.drawArc(3f,10f,17f,23f,300f,300f,false,p)}
            "shield"->{path(12f,2f,21f,6f,19f,16f,12f,22f,5f,16f,3f,6f,12f,2f);path(8f,12f,11f,15f,16f,9f)}
            "check"->path(4f,12f,9f,17f,20f,6f)
            "square"->c.drawRoundRect(4f,4f,20f,20f,4f,4f,p)
            "arrow"-> {line(3f,12f,21f,12f);path(15f,6f,21f,12f,15f,18f)}
            "chevron"->path(9f,5f,16f,12f,9f,19f)
            "close"->{line(5f,5f,19f,19f);line(19f,5f,5f,19f)}
            "target"->{c.drawCircle(12f,12f,7f,p);c.drawCircle(12f,12f,2f,p);line(12f,1f,12f,5f);line(12f,19f,12f,23f);line(1f,12f,5f,12f);line(19f,12f,23f,12f)}
            "car","door"->{c.drawRoundRect(2f,9f,22f,19f,2f,2f,p);path(5f,9f,7f,3f,17f,3f,19f,9f);line(5f,19f,5f,22f);line(19f,19f,19f,22f);line(6f,13f,7f,13f);line(17f,13f,18f,13f)}
            "meeting"->{c.drawCircle(8f,6f,3f,p);c.drawCircle(17f,8f,2.5f,p);c.drawArc(2f,12f,14f,25f,180f,180f,false,p);c.drawArc(13f,13f,23f,25f,180f,180f,false,p)}
            "info"->{c.drawCircle(12f,12f,9f,p);line(12f,11f,12f,17f);line(12f,7f,12f,7.1f)}
            "more"->{p.style=Paint.Style.FILL;listOf(5f,12f,19f).forEach{c.drawCircle(it,12f,1.6f,p)}}
            "moon"->{c.drawArc(3f,3f,21f,21f,45f,270f,false,p);c.drawArc(9f,-1f,25f,15f,65f,190f,false,p)}
            "sun"->{c.drawCircle(12f,12f,4f,p);for(i in 0..7){val a=i*Math.PI/4;line(12f+8*kotlin.math.cos(a).toFloat(),12f+8*kotlin.math.sin(a).toFloat(),12f+10*kotlin.math.cos(a).toFloat(),12f+10*kotlin.math.sin(a).toFloat())}}
            "plus"->{line(5f,12f,19f,12f);line(12f,5f,12f,19f)}
            "minus"->line(5f,12f,19f,12f)
            else->{p.style=Paint.Style.FILL;p.textSize=19f;p.typeface=Typeface.DEFAULT_BOLD;p.textAlign=Paint.Align.CENTER;c.drawText(name,12f,19f,p)}
        }
        c.restore()
    }
    override fun setAlpha(alpha:Int){p.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?){p.colorFilter=filter}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
