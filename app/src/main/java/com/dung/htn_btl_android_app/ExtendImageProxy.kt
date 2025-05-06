package com.dung.htn_btl_android_app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toProperlyRotatedBitmap(): Bitmap {
    // YUV to Bitmap conversion with rotation handling
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize).apply {
        yBuffer.get(this, 0, ySize)
        vBuffer.get(this, ySize, vSize)
        uBuffer.get(this, ySize + vSize, uSize)
    }

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val jpegBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size).run {
        val rotation = imageInfo.rotationDegrees.toFloat()
        if (rotation != 0f) {
            Matrix().apply {
                postRotate(rotation)
                if (true) {
                    postScale(-1f, 1f) // Mirror for front camera
                }
            }.let { matrix ->
                Bitmap.createBitmap(this, 0, 0, width, height, matrix, false).also {
                    recycle()
                }
            }
        } else {
            this
        }
    }
}

