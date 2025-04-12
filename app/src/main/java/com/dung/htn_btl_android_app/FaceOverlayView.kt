package com.dung.htn_btl_android_app
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.google.android.gms.vision.face.Landmark
import com.google.mlkit.vision.face.Face

class FaceOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var faceBounds: Rect? = null

    fun setFace(rect: Rect?) {
        faceBounds = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        faceBounds?.let { rect ->
            val paint = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawRect(rect, paint)
        }
    }
}
