package com.dung.htn_btl_android_app

import androidx.lifecycle.ViewModel

class MonitorViewModel : ViewModel() {
    var starTime:Long = 0
    var startUndefineTime:Long = 0
    var stopEngineByUndifine = false
    var stopEngineByUnSimilar = false
    var startUnSimilarityTime:Long = 0
    var drownessTime = 0L
    var drownessSend = false
}