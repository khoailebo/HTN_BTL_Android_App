package com.dung.htn_btl_android_app

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraExecutor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MonitorActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var overlayView: FaceDetectionOverlay
    private lateinit var faceDetector: FaceDetector
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)
        setUpComponent()
        setUpCamera()
        setUpFaceDetector()
    }

    fun setUpFaceDetector() {
        val option = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)  // Set to accurate mode
//            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)  // Optional: Detect all landmarks (eyes, nose, etc.)
//            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)  // Optional: You can adjust the minimum face size for detection
            .build()
        faceDetector = FaceDetection.getClient(option)
    }

    override fun onStart() {
        super.onStart()
        startCamera()
    }

    fun setUpComponent() {
        previewView = findViewById(R.id.preview_view)
        overlayView = findViewById(R.id.overlayView)
    }

    fun setUpCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private var playingMsg = false
    private var playingAlert = false
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this@MonitorActivity)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@MonitorActivity)) { imageProxy ->
                val bitmap = imageProxy.toProperlyRotatedBitmap()
                faceDetector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            overlayView.setFaces(
                                faces,
                                bitmap.width,
                                bitmap.height,
                                imageProxy.imageInfo.rotationDegrees,
                                true
                            )
                            val maxFace =
                                faces?.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            maxFace?.let {
                                if (it.headEulerAngleX < -15) {
                                    if (!playingMsg) {
                                        playingMsg = true
                                        lifecycleScope.launch {
                                            Utilities.messagePlayer?.playFocusWarning()
                                            playingMsg = false
                                        }
                                    }
                                    if (!playingAlert) {
                                        playingAlert = true
                                        lifecycleScope.launch {
                                            Utilities.messagePlayer?.playTingSE()
                                            playingAlert = false
                                        }
                                    }
                                }
                                Log.d(
                                    "MONITOR",
                                    "yaw = ${it.headEulerAngleY}\npitch = ${it.headEulerAngleX}\nroll = ${it.headEulerAngleZ}\n" +
                                            "left eye open = ${it.leftEyeOpenProbability}\nright eye open = ${it.rightEyeOpenProbability}"
                                )
                            }
                        } else {
                            overlayView.setFaces(listOf<Face>(), 0, 0, 0, false)
                        }
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        imageProxy.close()
                    }

            }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this@MonitorActivity,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this@MonitorActivity))
    }

    override fun onDestroy() {
        super.onDestroy()
        faceDetector.close()
        cameraExecutor.shutdown()
    }
}