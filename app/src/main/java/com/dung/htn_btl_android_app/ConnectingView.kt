package com.dung.htn_btl_android_app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.transition.Visibility
import kotlin.math.pow

class ConnectingView : View {
    private var background: Int
    private val backgroundPaint: Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val textPaint: Paint = Paint().apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        textSize = dpToPx(25f).toFloat()
    }
    private val connectString: String = "Connecting"
    constructor(context: Context,attrs:AttributeSet) : super(context,attrs){
        val data = context.obtainStyledAttributes(attrs,R.styleable.ConnectingView)
        background = data.getColor(R.styleable.ConnectingView_custom_backgournd,Color.BLUE)
        backgroundPaint.color = background
        startAnimation()
    }
    fun setShow(show: Boolean){
        visibility = if(show) VISIBLE else INVISIBLE
    }
    private fun startAnimation(){
        valueAnimator = ValueAnimator.ofFloat(0f,1f).apply {
            duration = 10f.pow(3).toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                val value = it.animatedValue as Float
                Circle.Padding = dpToPx(value.toFloat() * 30).toFloat()
                postInvalidate()
            }
            start()
        }
    }
    private lateinit var valueAnimator: ValueAnimator

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Circle.CenterX = w / 2f
        Circle.CenterY = h / 2f
        Circle.Radios = dpToPx(Circle.RADIOS_DP).toFloat()

        val rect = Rect()
        textPaint.getTextBounds(Text.content,0,Text.content.length,rect)
        Text.X = Circle.CenterX + dpToPx(Text.XOffset_DP)
        Text.Y = Circle.CenterY - (textPaint.ascent() + textPaint.descent()) / 2 + dpToPx(Text.YOffset_DP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundPaint.color = Color.YELLOW
        canvas.drawCircle(Circle.CenterX,Circle.CenterY,Circle.Radios + Circle.Padding,backgroundPaint)
        backgroundPaint.color = background
        canvas.drawCircle(Circle.CenterX,Circle.CenterY,Circle.Radios,backgroundPaint)

        canvas.drawText(Text.content,Text.X,Text.Y,textPaint)
    }

    fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
    companion object{
        const val RADIOS_DP = 20f
    }
    object Circle{
        var CenterX = 0f
        var CenterY = 0f
        const val RADIOS_DP = 100f
        var Radios = 0f
        var Padding = 0f
    }
    object Text {
        const val content = "Connecting"
        var XOffset_DP = 0f
        var YOffset_DP = 0f
        var X = 0f
        var Y = 0f

    }
}