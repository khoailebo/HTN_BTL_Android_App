package com.dung.htn_btl_android_app

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object BitmapUtils {
    @OptIn(ExperimentalGetImage::class)
    fun ImageProxy.toProperlyRotatedBitmap(): Bitmap {
        val image = this.image ?: throw IllegalStateException("Image is null")
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val yuvImage = YuvImage(
            bytes,
            ImageFormat.NV21,
            this.width,
            this.height,
            null
        )

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, this.width, this.height),
            100,
            outputStream
        )

        val jpegBytes = outputStream.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        // Rotate the bitmap according to the rotation degrees
        val matrix = Matrix().apply {
            postRotate(this@toProperlyRotatedBitmap.imageInfo.rotationDegrees.toFloat())
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    fun ImageProxy.cropFace(face: Rect, bitmap: Bitmap): Bitmap {

        // Create a copy of the face rect that we'll adjust
        val adjustedRect = Rect(face)

        // Adjust coordinates

        return Bitmap.createBitmap(
            bitmap,
            adjustedRect.left,
            adjustedRect.top,
            adjustedRect.width(),
            adjustedRect.height()
        )
    }
}