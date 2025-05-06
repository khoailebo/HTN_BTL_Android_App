package com.dung.htn_btl_android_app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.pow

class WarningView : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private lateinit var bitmap: Bitmap
    private var bitmapPaint = Paint()
    private val offSetHorizontalDP = 150
    private lateinit var warningAnimation: ValueAnimator
    private var animationPlaying = false
    private var imageX = 0f
    private var imageY = 0f
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        bitmapPaint.alpha = 0
//        visibility = View.INVISIBLE

        bitmap = BitmapFactory.decodeResource(resources, R.drawable.warning_signs)
        val dx = (w.toFloat() - dpToPx(offSetHorizontalDP.toFloat())) / bitmap.width * 1f
        val dy = h.toFloat() / bitmap.height * 1f
        val d = Math.min(dx, dy)
        val bitmapNewWidth = bitmap.width * d
        val bitmapNewHeight = bitmap.height * d
        bitmap = Bitmap.createScaledBitmap(
            bitmap,
            bitmapNewWidth.toInt(),
            bitmapNewHeight.toInt(),
            false
        )
        imageX = (width - bitmapNewWidth) / 2
        imageY = (height - bitmapNewHeight) / 2
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(bitmap, imageX, imageY, bitmapPaint)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
            .toInt()
    }

    fun showWarning() {
        if (!animationPlaying) {
            animationPlaying = true
            visibility = View.VISIBLE
            warningAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 10f.pow(3).toLong()
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    val fragtion = it.animatedValue as Float
                    bitmapPaint.alpha = (255 * fragtion).toInt()
                    postInvalidate()
                }
                start()
            }
        }
    }

    fun hidewarning() {
        if (animationPlaying) {
            animationPlaying = false
            visibility = View.INVISIBLE
            warningAnimation.cancel()
        }
    }
}