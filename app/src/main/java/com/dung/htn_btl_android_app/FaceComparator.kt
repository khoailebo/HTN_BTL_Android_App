package com.dung.htn_btl_android_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer

class FaceComparator(context: Context) {
    private var interpreter: Interpreter
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(112, 112, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f)) // Normalize to [0,1]
        .build()

    init {
        // Load the TFLite model (you'll need to add the model file to your assets)
        val modelFile = FileUtil.loadMappedFile(context, "mobilefacenet.tflite")
        interpreter = Interpreter(modelFile)
    }

    fun getFaceEmbedding(bitmap: Bitmap): FloatArray {
        // Convert bitmap to TensorImage
        var tensorImage = TensorImage.fromBitmap(bitmap)

        // Preprocess the image
        tensorImage = imageProcessor.process(tensorImage)

        // Allocate output buffer
        val output = Array(1) { FloatArray(192) } // 192 is the typical embedding size

        // Run inference
        interpreter.run(tensorImage.buffer, output)

        return output[0]
    }

    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        // Calculate cosine similarity between two embeddings
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        return dotProduct / (Math.sqrt(norm1.toDouble()) * Math.sqrt(norm2.toDouble())).toFloat()
    }

    fun close() {
        interpreter.close()
    }
}