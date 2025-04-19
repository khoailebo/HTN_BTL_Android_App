package com.dung.htn_btl_android_app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.cos
import kotlin.math.sin

class FaceDetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val landmarkPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    private val contourPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val anglePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val angleTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }

    // Detection data
    private var faces: List<Face> = emptyList()
    private var scaleFactor = 1f
    private var xOffset = 0f
    private var yOffset = 0f
    private var imageWidth = 0
    private var imageHeight = 0
    private var sensorOrientation = 0
    private var isFrontCamera = false
    private var isFliped = false

    fun setFaces(
        detectedFaces: List<Face>,
        imageWidth: Int,
        imageHeight: Int,
        sensorOrientation: Int,
        isFrontCamera: Boolean = false
    ) {
        this.faces = detectedFaces
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.sensorOrientation = sensorOrientation
        this.isFrontCamera = isFrontCamera

        calculateTransformations()
        invalidate()
    }

    private fun calculateTransformations() {
        val viewAspect = width.toFloat() / height
        val imageAspect = imageWidth.toFloat() / imageHeight

        // Determine if we need to swap width/height based on rotation
        isFliped = sensorOrientation == 0
        val displayAspect = imageAspect

        if (displayAspect > viewAspect) {
            // Image is wider than view (scale to fit width)
            scaleFactor = width.toFloat() / imageWidth
            xOffset = 0f
            yOffset = (height - ( imageHeight) * scaleFactor) / 2f
        } else {
            // Image is taller than view (scale to fit height)
            scaleFactor = height.toFloat() / imageHeight
            xOffset = (width - (imageWidth) * scaleFactor) / 2f
            yOffset = 0f
        }
    }

    private fun translateX(x: Float): Float {
        return when (sensorOrientation) {
            90 -> (imageHeight - x) * scaleFactor + xOffset  // Portrait
            270 -> x * scaleFactor + xOffset                // Reverse portrait
            180 -> (x) * scaleFactor + xOffset // Landscape flipped
            else -> x * scaleFactor + xOffset               // Normal landscape
        }
    }

    private fun translateY(y: Float): Float {
        return when (sensorOrientation) {
            90 -> y * scaleFactor + yOffset                // Portrait
            270 -> (y) * scaleFactor + yOffset // Reverse portrait
            180 -> (y) * scaleFactor + yOffset // Landscape flipped
            else -> y * scaleFactor + yOffset              // Normal landscape
        }
    }

    private fun transformPoint(point: PointF): PointF {
        return PointF(translateX(point.x), translateY(point.y))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)


        // Apply front camera mirroring if needed
        if (isFrontCamera) {
            canvas.save()
            canvas.scale(
                if(isFliped) -1.5f else 1.5f,
                1.5f,
                width / 2f,
                height / 2f
            )
        }

        for (face in faces) {
            // Draw bounding box
            drawBoundingBox(canvas, face)


            // Draw face angles
//            drawFaceAngles(canvas, face)
        }
        if (faces.size > 0) {
            val maxFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            maxFace?.let {
                // Draw all contours
                drawAllContours(canvas, it)

                // Draw key landmarks
                drawKeyLandmarks(canvas, it)
            }
        }

        if (isFrontCamera) {
            canvas.restore()
        }
    }

    private fun drawBoundingBox(canvas: Canvas, face: Face) {
        val bounds = face.boundingBox
        val rect = RectF(
            translateX(bounds.left.toFloat()),
            translateY(bounds.top.toFloat()),
            translateX(bounds.right.toFloat()),
            translateY(bounds.bottom.toFloat())
        )
        canvas.drawRect(rect, boxPaint)
    }

    private fun drawAllContours(canvas: Canvas, face: Face) {
        val allContours = listOf(
            FaceContour.FACE,
            FaceContour.LEFT_EYEBROW_TOP,
            FaceContour.LEFT_EYEBROW_BOTTOM,
            FaceContour.RIGHT_EYEBROW_TOP,
            FaceContour.RIGHT_EYEBROW_BOTTOM,
            FaceContour.LEFT_EYE,
            FaceContour.RIGHT_EYE,
            FaceContour.UPPER_LIP_TOP,
            FaceContour.UPPER_LIP_BOTTOM,
            FaceContour.LOWER_LIP_TOP,
            FaceContour.LOWER_LIP_BOTTOM,
            FaceContour.NOSE_BRIDGE,
            FaceContour.NOSE_BOTTOM
        )

        allContours.forEach { contourType ->
            drawSingleContour(canvas, face, contourType)
        }
    }

    private fun drawSingleContour(canvas: Canvas, face: Face, contourType: Int) {
        val contour = face.getContour(contourType)
        contour?.points?.let { points ->
            if (points.isNotEmpty()) {
                val path = Path()
                val firstPoint = transformPoint(points[0])
                path.moveTo(firstPoint.x, firstPoint.y)

                for (i in 1 until points.size) {
                    val point = transformPoint(points[i])
                    path.lineTo(point.x, point.y)
                }

                if (contourType == FaceContour.FACE) {
                    path.close()
                }
                canvas.drawPath(path, contourPaint)
            }
        }
    }

    private fun drawKeyLandmarks(canvas: Canvas, face: Face) {
        val landmarks = listOf(
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.NOSE_BASE,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.RIGHT_CHEEK,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.MOUTH_BOTTOM
        )

        landmarks.forEach { landmarkType ->
            drawSingleLandmark(canvas, face, landmarkType)
        }
    }

    private fun drawSingleLandmark(canvas: Canvas, face: Face, landmarkType: Int) {
        val landmark = face.getLandmark(landmarkType)
        landmark?.position?.let { point ->
            val transformed = transformPoint(point)
            canvas.drawCircle(transformed.x, transformed.y, 4f, landmarkPaint)
        }
    }

    private fun drawFaceAngles(canvas: Canvas, face: Face) {
        val bounds = face.boundingBox
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        val center = transformPoint(PointF(centerX, centerY))

        // Draw roll angle (rotation around Z-axis)
        canvas.drawText(
            "Roll: ${"%.1f°".format(face.headEulerAngleZ)}",
            center.x + 20f,
            center.y - 40f,
            angleTextPaint
        )

        // Draw pitch angle (rotation around X-axis)
        canvas.drawText(
            "Pitch: ${"%.1f°".format(face.headEulerAngleX)}",
            center.x + 20f,
            center.y,
            angleTextPaint
        )

        // Draw yaw angle (rotation around Y-axis)
        canvas.drawText(
            "Yaw: ${"%.1f°".format(face.headEulerAngleY)}",
            center.x + 20f,
            center.y + 40f,
            angleTextPaint
        )

        // Draw angle indicators
        val length = 100f
        val rollLine = PointF(
            center.x + length * cos(Math.toRadians(face.headEulerAngleZ.toDouble())).toFloat(),
            center.y + length * sin(Math.toRadians(face.headEulerAngleZ.toDouble())).toFloat()
        )
        canvas.drawLine(center.x, center.y, rollLine.x, rollLine.y, anglePaint)
    }
}