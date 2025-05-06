package com.dung.htn_btl_android_app

import androidx.lifecycle.ViewModel

class FaceDetectorViewModel : ViewModel() {
    var authenticate = false
    var startTime: Long = 0
    var startUndefineTime:Long = 0
    var downLoadFaceEmbedded: FloatArray? = null
    var faceCameraEmbedded: FloatArray? = null
}