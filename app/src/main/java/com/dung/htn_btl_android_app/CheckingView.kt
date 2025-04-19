package com.dung.htn_btl_android_app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import kotlin.math.pow


class CheckingView : FrameLayout {
    private var mode = CheckingMode.RFID
    private lateinit var imageView: ImageView
    private lateinit var loadingAnimator: ValueAnimator

    init {
        setWillNotDraw(false)
    }

    fun setMode(mode: CheckingMode) {
        this.mode = mode
        if (mode != CheckingMode.NONE) {
            visibility = View.VISIBLE
            imageView.setImageResource(
                when (mode) {
                    CheckingMode.RFID -> R.drawable.rfid
                    CheckingMode.ALCOHOL -> R.drawable.alcohol_test
                    else -> R.drawable.rfid
                }
            )
        }
        else{
            visibility = View.INVISIBLE
        }
        invalidate()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        loadLayout(context)
    }

    constructor(context: Context) : super(context) {
        loadLayout(context)
    }

    fun loadLayout(context: Context) {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val layoutInflater = LayoutInflater.from(context)
        val view = layoutInflater.inflate(R.layout.checking_view_layout, this, true)
        imageView = view.findViewById(R.id.checking_image)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Circle.xCenter = w / 2f
        Circle.yCenter = h / 2f
        Circle.radios = dpToPx(Circle.RADIOS_DP).toFloat()
        startAnimator()
    }

    fun startAnimator() {
        loadingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10f.pow(3).toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                val fragtion = it.animatedValue as Float
                animatedRadios = fragtion * dpToPx(30f)
                checkingPaint.alpha = (255 - 255 * fragtion).toInt()
                postInvalidate()
            }
            start()
        }
    }

    private var animatedRadios = 0f
    private val checkingPaint = Paint().apply {
        color = Color.parseColor("#00ccff")
        style = Paint.Style.FILL
    }
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#ffffff")
        style = Paint.Style.FILL
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val layerPaint = Paint()
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), layerPaint)

        val outerPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }

        // Draw big red circle (background)
        canvas.drawCircle(
            Circle.xCenter,
            Circle.yCenter,
            Circle.radios + animatedRadios,
            checkingPaint
        )

        // Prepare erasing paint (cut-out inner circle)
        val eraser = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }

        // Erase inner circle
        canvas.drawCircle(Circle.xCenter, Circle.yCenter, Circle.radios, eraser)

        canvas.restoreToCount(layer)
//        canvas.drawCircle(Circle.xCenter,Circle.yCenter,Circle.radios + animatedRadios,checkingPaint)
//        canvas.drawCircle(Circle.xCenter,Circle.yCenter,Circle.radios,backgroundPaint)
    }

    fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    object Circle {
        var xCenter = 0f
        var yCenter = 0f
        const val RADIOS_DP = 100f
        var radios = 0f
    }
}
