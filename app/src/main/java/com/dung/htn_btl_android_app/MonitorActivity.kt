package com.dung.htn_btl_android_app

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraExecutor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.dung.htn_btl_android_app.BitmapUtils.cropFace
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MonitorActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var overlayView: FaceDetectionOverlay
    private lateinit var faceDetector: FaceDetector
    private lateinit var viewModel: MonitorViewModel
    private lateinit var stopEngineBtn: Button
    private lateinit var faceComparator: FaceComparator
    private lateinit var warningView: WarningView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        faceComparator = FaceComparator(this)
        viewModel = ViewModelProvider(this).get(MonitorViewModel::class.java)
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
        warningView = findViewById(R.id.warning)
        warningView.hidewarning()

        previewView = findViewById(R.id.preview_view)
        overlayView = findViewById(R.id.overlayView)
        stopEngineBtn = findViewById(R.id.stop_engine)
        stopEngineBtn.setOnClickListener {
            Utilities.communicator?.sendEvent("StopEngine")
            Utilities.mqttConnector?.sendStopDriving(
                Gson().toJson(
                    Request(
                        driverId = Utilities.driver?.id!!,
                        data = Utilities.vehicle?.apply { running = false }
                            .toString()))
            )
            finish()
        }
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
//                val bitmap = imageProxy.toProperlyRotatedBitmap()
                var bitmap = imageProxy.toBitmap()
                val matrix = Matrix()
                when(imageProxy.imageInfo.rotationDegrees){
                    90 -> matrix.postRotate(90f)
                    270 -> matrix.postRotate(270f)
                    180 -> matrix.postRotate(108f)
                }
                matrix.postScale(-1f,1f)
                bitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,false)
                faceDetector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            viewModel.startUndefineTime = System.currentTimeMillis()
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
                                checkSleeping(it)
                                val faceBitmap = imageProxy.cropFace(it.boundingBox,bitmap)
                                val similarity = compareFace(faceBitmap)
                                Log.d("MonitorActivity",similarity.toString())
                                if (similarity < 0.5 && it.headEulerAngleX > -15){
                                    warningView.showWarning()
                                    if(System.currentTimeMillis()- (viewModel.startUnSimilarityTime) > 5000 && !viewModel.stopEngineByUnSimilar)
                                    {
                                        viewModel.stopEngineByUnSimilar = true
                                        lifecycleScope.launch {
                                            Utilities.messagePlayer?.playDriverUndefineMsg()
                                            stopEngineBtn.performClick()
                                        }
                                    }
                                }
                                else {
                                    warningView.hidewarning()
                                    viewModel.startUnSimilarityTime = System.currentTimeMillis()
                                }

                            }
                        } else {
                            warningView.showWarning()
                            overlayView.setFaces(listOf<Face>(), 0, 0, 0, false)
                            if (System.currentTimeMillis() - viewModel.startUndefineTime > 5000 && !viewModel.stopEngineByUndifine) {
                                viewModel.stopEngineByUndifine = true
                                lifecycleScope.launch {
                                    Utilities.messagePlayer?.playDriverUndefineMsg()
                                    stopEngineBtn.performClick()
                                }
                            }
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
        viewModel.starTime = System.currentTimeMillis()
        viewModel.startUndefineTime = System.currentTimeMillis()
        viewModel.startUnSimilarityTime = System.currentTimeMillis()
    }


    private fun checkSleeping(face: Face){
        if (face.headEulerAngleX < -15) {
            if(System.currentTimeMillis() - viewModel.drownessTime > 5000 && !viewModel.drownessSend)
            {
                viewModel.drownessSend = true
                Utilities.mqttConnector?.drowsinessSend()
            }
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
        else{
            viewModel.drownessTime = System.currentTimeMillis()
        }
        Log.d(
            "MONITOR",
            "yaw = ${face.headEulerAngleY}\npitch = ${face.headEulerAngleX}\nroll = ${face.headEulerAngleZ}\n" +
                    "left eye open = ${face.leftEyeOpenProbability}\nright eye open = ${face.rightEyeOpenProbability}"
        )
    }

    private fun compareFace(faceBitmap: Bitmap):Float{
        Utilities.imageEmbedded?.let {
            val faceBitmapEmbedded = faceComparator.getFaceEmbedding(faceBitmap)
            return faceComparator.calculateSimilarity(it,faceBitmapEmbedded)
        }
        return 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        faceDetector.close()
        cameraExecutor.shutdown()
    }
}