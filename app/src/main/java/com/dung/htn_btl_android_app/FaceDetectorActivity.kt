package com.dung.htn_btl_android_app

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.dung.htn_btl_android_app.BitmapUtils.cropFace
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetectorActivity : AppCompatActivity() {
    private var checking: Boolean = false
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FaceDetectionOverlay
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceComparator: FaceComparator
    private var storedEmbedding: FloatArray? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageView: ImageView

    companion object {
        const val SIMILARITY = "similarity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_detector)
        setUpCompoent()
        setUpFaceDetector()
        setUpCamera()
    }

    fun setUpCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun setUpFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)  // Set to accurate mode
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)  // Optional: Detect all landmarks (eyes, nose, etc.)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.1f)  // Optional: You can adjust the minimum face size for detection
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    fun setUpCompoent() {
        previewView = findViewById<PreviewView>(R.id.preview_view)
        overlayView = findViewById<FaceDetectionOverlay>(R.id.overlayView)
    }

    override fun onStart() {
        super.onStart()
        startCamera()
    }

    private var comparing = true
    private var finished = false
    private lateinit var cameraProvider: ProcessCameraProvider

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this@FaceDetectorActivity)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

//            imageCapture = ImageCapture.Builder().build()


            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.preview_view).surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@FaceDetectorActivity)) { imageProxy ->
                try {
                    if (comparing) {
                        processImageForFaceDetection(imageProxy)
                    } else {
                        handleComparisonFinished(imageProxy)
                    }
                } catch (e: Exception) {
                    Log.e("FaceDetection", "Error in analyzer", e)
                    safeCloseImageProxy(imageProxy)
                }
//                if (comparing) {
//                    val bitmap = imageProxy.toProperlyRotatedBitmap()
//                    if (bitmap != null) {
//                        val inputImage =
//                            InputImage.fromBitmap(bitmap, 0)
//                        val rotationDegree = imageProxy.imageInfo.rotationDegrees
//                        lifecycleScope.launch(Dispatchers.Default) {
//                            faceDetector.process(inputImage)
//                                .addOnSuccessListener { faces ->
//                                    if (faces.isNotEmpty()) {
//                                        overlayView.setFaces(
//                                            faces,
//                                            bitmap.width,
//                                            bitmap.height,
//                                            rotationDegree,
//                                            true
//                                        )
//                                        val face = faces[0]
//                                        val faceBitmap =
//                                            imageProxy.cropFace(face.boundingBox, bitmap)
//                                        if (!checking) {
//                                            checking = true
//                                            Handler(Looper.getMainLooper()).postDelayed({
//                                                comparing = false
//                                            }, 5000)
////                                        imageUri?.let {
////                                            lifecycleScope.launch(Dispatchers.Default) {
////                                                compareFaces(faceBitmap, it)
////                                            }
////                                        }
//                                        }
//
//                                    } else {
//                                        overlayView.setFaces(listOf<Face>(), 0, 0, 0)
//                                    }
//                                    imageProxy.close()
//                                }
//                                .addOnFailureListener {
//                                    imageProxy.close()
//                                }
//                        }
//
//                    } else {
//                        imageProxy.close()
//                    }
//                } else {
//                    imageProxy.close()
//                    if (!finished) {
//                        finished = true
//                        Handler(Looper.getMainLooper()).post {
//                            imageProxy.close()
//                            cameraProvider.unbindAll() // This stops all camera use cases
//                            Log.d("CAMERA", "Camera stopped")
//
//                            val resultIntent = Intent()
//
//                            setResult(Activity.RESULT_OK, resultIntent)
//                            Log.d("RESULT","set result")
//                            finish()
//                        }
//                    }
//                }
            }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this@FaceDetectorActivity, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this@FaceDetectorActivity))
    }

    private fun processImageForFaceDetection(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toProperlyRotatedBitmap() ?: run {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val rotationDegree = imageProxy.imageInfo.rotationDegrees

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    overlayView.setFaces(
                        faces,
                        bitmap.width,
                        bitmap.height,
                        rotationDegree,
                        true
                    )

                    if (!checking) {
                        checking = true
                        Handler(Looper.getMainLooper()).postDelayed({
                            comparing = false
                        }, 5000)
                    }
                } else {
                    overlayView.setFaces(listOf<Face>(), 0, 0, 0)
                }
                imageProxy.close()
            }
            .addOnFailureListener {
                imageProxy.close()
            }
    }


    private fun handleComparisonFinished(imageProxy: ImageProxy) {
        if (finished) {
            imageProxy.close()
            return
        }

        finished = true
        safeCloseImageProxy(imageProxy)

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                try {

                    cameraProvider.unbindAll()
                    val resultIntent = Intent().apply {
                        putExtra(SIMILARITY,true)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)

                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("Camera", "Error during cleanup", e)
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }
            }
        }
    }

    private fun safeCloseImageProxy(imageProxy: ImageProxy) {
        try {
                imageProxy.close()
        } catch (e: Exception) {
            Log.e("Camera", "Error closing image proxy", e)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

//Tham khao
//package com.dung.htn_btl_android_app
//
//
//import android.Manifest
//import android.app.Activity
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.Matrix
//import android.net.Uri
//import android.os.Bundle
//import android.provider.MediaStore
//import android.util.Log
//import android.util.Size
//import android.widget.ImageView
//import android.widget.Toast
//import androidx.activity.enableEdgeToEdge
//import androidx.activity.result.PickVisualMediaRequest
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.annotation.OptIn
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ExperimentalGetImage
//import androidx.camera.core.ImageAnalysis
//import java.nio.ByteBuffer
//import androidx.camera.core.ImageCapture
//import androidx.camera.core.ImageCaptureException
//import androidx.camera.core.ImageProxy
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.core.view.ViewCompat
//import android.graphics.Rect
//import android.media.ExifInterface
//import androidx.core.view.WindowInsetsCompat
//import androidx.lifecycle.lifecycleScope
//import com.dung.htn_btl_android_app.BitmapUtils.cropFace
//import com.google.android.gms.vision.face.Landmark
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.face.Face
//import com.google.mlkit.vision.face.FaceDetection
//import com.google.mlkit.vision.face.FaceDetector
//import com.google.mlkit.vision.face.FaceDetectorOptions
//import com.google.mlkit.vision.face.FaceLandmark
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.GlobalScope
//import kotlinx.coroutines.coroutineScope
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking
//import kotlinx.coroutines.suspendCancellableCoroutine
//import kotlinx.coroutines.tasks.await
//import org.tensorflow.lite.Interpreter
//import java.io.IOException
//import java.nio.ByteOrder
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import kotlin.coroutines.Continuation
//import kotlin.coroutines.resume
//import kotlin.math.pow
//import kotlin.math.sqrt
//
//private const val REQUEST_CODE_PERMISSIONS = 10
//
//class MainActivity : AppCompatActivity() {
//    private var checking: Boolean = false
//    private lateinit var previewView: PreviewView
//    private lateinit var overlayView: FaceDetectionOverlay
//    private lateinit var faceDetector: FaceDetector
//    private lateinit var faceComparator: FaceComparator
//    private var storedEmbedding: FloatArray? = null
//    private val REQUIRED_PERMISSIONS = arrayOf(
//        Manifest.permission.CAMERA,
//        Manifest.permission.INTERNET,
//        Manifest.permission.READ_MEDIA_IMAGES,
//        Manifest.permission.BLUETOOTH,
//        Manifest.permission.BLUETOOTH_ADMIN,
//        Manifest.permission.BLUETOOTH_CONNECT,
//        Manifest.permission.ACCESS_FINE_LOCATION,
//        Manifest.permission.ACCESS_COARSE_LOCATION,
//    )
//
//    private lateinit var cameraExecutor: ExecutorService
//    private lateinit var imageView: ImageView
//    private lateinit var imageCapture: ImageCapture
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        faceComparator = FaceComparator(this)
//
//        previewView = findViewById<PreviewView>(R.id.preview_view)
//        overlayView = findViewById<FaceDetectionOverlay>(R.id.overlayView)
//        imageView = findViewById<ImageView>(R.id.imageView)
//
//        val options = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)  // Set to accurate mode
//            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)  // Optional: Detect all landmarks (eyes, nose, etc.)
//            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
//            .setMinFaceSize(0.1f)  // Optional: You can adjust the minimum face size for detection
//            .build()
//        faceDetector = FaceDetection.getClient(options)
//        cameraExecutor = Executors.newSingleThreadExecutor()
//        if (allPermissionsGranted()) {
////            startCamera()
////            pickImageFromGallery()
//            findViewById<PreviewView>(R.id.preview_view).setOnClickListener {
////                captureImage()
////                Log.d("Test click","HELLO")
//            }
////            lifecycleScope.launch(Dispatchers.Main) {
////                startCamera()
////                delay(1000)
////                    for(i in arrayOf(1,2,3)){
////                        Log.d("Coroutine check","check")
////                        captureImage()
////                    }
////            }
////            startCamera()
//
//        } else {
//            ActivityCompat.requestPermissions(
//                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
//            )
//        }
//    }
//
//    fun getCorrectlyOrientedImage(context: Context, uri: Uri): Bitmap {
//        val inputStream = context.contentResolver.openInputStream(uri)
//        val exif = inputStream?.let { ExifInterface(it) }
//        val orientation = exif?.getAttributeInt(
//            ExifInterface.TAG_ORIENTATION,
//            ExifInterface.ORIENTATION_NORMAL
//        ) ?: ExifInterface.ORIENTATION_NORMAL
//
//        val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
//
//        val matrix = Matrix()
//        when (orientation) {
//            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
//            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
//            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
//        }
//        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//    }
//
//    var imageUri: Uri? = null
//    override fun onStart() {
//        super.onStart()
//        lifecycleScope.launch(Dispatchers.Main) {
//            imageUri = pickImageFromGallery()
//            imageUri?.let {
//                lifecycleScope.launch(Dispatchers.IO) {
//                    selectedFace = getCorrectlyOrientedImage(this@MainActivity, it)
//                    launch(Dispatchers.Default) {
//                        val storedFaces =
//                            faceDetector.process(InputImage.fromBitmap(selectedFace, 0)).await()
//
//                        if (storedFaces.isNotEmpty()) {
//                            val storeFace = storedFaces[0]
//                            val faceCenterBitmap = Bitmap.createBitmap(
//                                selectedFace,
//                                storeFace.boundingBox.left,
//                                storeFace.boundingBox.top,
//                                storeFace.boundingBox.width(),
//                                storeFace.boundingBox.height()
//                            )
//                            storedEmbedding = faceComparator.getFaceEmbedding(faceCenterBitmap)
//                        }
//                    }
//                }
//                startCamera()
//            }
//        }
//    }
//
//    var continuation: Continuation<Uri?>? = null
//    val pickMedia =
//        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
//            if (uri != null) {
//                try {
//                    selectedFace =
//                        MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
//                    Toast.makeText(this, "Image selected!", Toast.LENGTH_SHORT).show()
////                    startCamera()
//                } catch (e: IOException) {
//                    e.printStackTrace()
//                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
//                }
//            } else {
//                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
//            }
//            continuation?.resume(uri)
//        }
//    private lateinit var selectedFace: Bitmap
//    private suspend fun pickImageFromGallery(): Uri? {
//        return suspendCancellableCoroutine { cont ->
//            continuation = cont
//            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
//        }
//
////        val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
////        intent.type = "image/*"
////        startActivityForResult(intent, REQUEST_PICK_IMAGE)
//    }
//
//    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
//        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_CODE_PERMISSIONS) {
//            if (allPermissionsGranted()) {
//                lifecycleScope.launch {
//
//                    pickImageFromGallery()
//                }
//            } else {
//                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    @OptIn(ExperimentalGetImage::class)
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this@MainActivity)
//
//        cameraProviderFuture.addListener({
//            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//
////            imageCapture = ImageCapture.Builder().build()
//
//            imageCapture = ImageCapture.Builder().build()
//
//            val preview = Preview.Builder().build().also {
//                it.setSurfaceProvider(findViewById<PreviewView>(R.id.preview_view).surfaceProvider)
//            }
//
//            val imageAnalysis = ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build()
//            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@MainActivity)) { imageProxy ->
//                val bitmap = imageProxy.toProperlyRotatedBitmap()
//                if (bitmap != null) {
//                    val inputImage =
////                        InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
//                        InputImage.fromBitmap(bitmap, 0)
//                    val rotationDegree = imageProxy.imageInfo.rotationDegrees
//                    lifecycleScope.launch(Dispatchers.Default) {
//                        faceDetector.process(inputImage)
//                            .addOnSuccessListener { faces ->
//                                if (faces.isNotEmpty()) {
//                                    overlayView.setFaces(
//                                        faces,
//                                        bitmap.width,
//                                        bitmap.height,
//                                        rotationDegree,
//                                        true
//                                    )
//                                    val face = faces[0]
//                                    val faceBitmap = imageProxy.cropFace(face.boundingBox, bitmap)
//                                    if (!checking) {
//                                        checking = true
//                                        imageUri?.let {
//                                            lifecycleScope.launch(Dispatchers.Default) {
//                                                compareFaces(faceBitmap, it)
//                                            }
//                                        }
//                                    }
//
//                                } else {
//                                    overlayView.setFaces(listOf<Face>(), 0, 0, 0)
//                                }
//                                imageProxy.close()
//                            }
//                            .addOnFailureListener {
//                                imageProxy.close()
//                            }
//                    }
//
//                } else {
//                    imageProxy.close()
//                }
//            }
//            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//
//            try {
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    this@MainActivity, cameraSelector, preview, imageAnalysis
//                )
//            } catch (exc: Exception) {
//                Log.e("CameraX", "Use case binding failed", exc)
//            }
//        }, ContextCompat.getMainExecutor(this@MainActivity))
//    }
//
//
//    private lateinit var bitmap: Bitmap
//    private fun captureImage() {
//        val outputOptions = ImageCapture.OutputFileOptions.Builder(
//            contentResolver,
//            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            android.content.ContentValues()
//        ).build()
//
//        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
//            object : ImageCapture.OnImageCapturedCallback() {
//                override fun onCaptureSuccess(image: ImageProxy) {
//                    bitmap = image.toBitmap()
//                    image.close()
//
////                    processFaceComparison(
////                        InputImage.fromBitmap(
////                            bitmap, image.imageInfo.rotationDegrees
////                        )
////                    )
////                    this@MainActivity.findViewById<ImageView>(R.id.image_view_one)
////                        .setImageBitmap(bitmap)
////                    this@MainActivity.findViewById<ImageView>(R.id.image_view_two)
////                        .setImageBitmap(selectedFace)
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                    Log.e("CAPTURE", "Capture failed: ${exception.message}", exception)
//                }
//            })
//    }
//
//
//    @OptIn(ExperimentalGetImage::class)
//    private suspend fun compareFaces(cameraFaceBitmap: Bitmap, imageUri: Uri) {
//        try {
//            // Get the face from stored image
////            val storedBitmap = getCorrectlyOrientedImage(this@MainActivity, imageUri)
////            val storedFaces = faceDetector.process(InputImage.fromBitmap(storedBitmap, 0)).await()
////
////            if (storedFaces.isEmpty()) {
////                checking = false
////                return
////            }
////
////            val storedFace = storedFaces[0]
////            val storedFaceBitmap = Bitmap.createBitmap(
////                storedBitmap,
////                storedFace.boundingBox.left,
////                storedFace.boundingBox.top,
////                storedFace.boundingBox.width(),
////                storedFace.boundingBox.height()
////            )
//
//            // Get embeddings and compare
//            storedEmbedding?.let {
//                val cameraEmbedding = faceComparator.getFaceEmbedding(cameraFaceBitmap)
////            val storedEmbedding = faceComparator.getFaceEmbedding(storedFaceBitmap)
//                val similarity =
//                    faceComparator.calculateSimilarity(cameraEmbedding, it)
//                runOnUiThread {
//                    Toast.makeText(
//                        this@MainActivity,
//                        "Face similarity: ${"%.2f".format(similarity * 100)}%",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//
//        } catch (e: Exception) {
//            Log.e("FaceComparison", "Error comparing faces", e)
//        } finally {
//            checking = false
//        }
//    }
//
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//    }
//}