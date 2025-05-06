package com.dung.htn_btl_android_app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.delay

class MessagePlayer(val context: Context) {
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(20)
        .setAudioAttributes(audioAttributes)
        .build()

    private val soundMap = mutableMapOf<String,Int>()
    init {
        soundMap.put("start_authen",soundPool.load(context,R.raw.startauthen,1))
        soundMap["try_again"] = soundPool.load(context,R.raw.tryagain,1)
        soundMap["scan_card"] = soundPool.load(context,R.raw.scancard,1)
        soundMap["restart_authen"] = soundPool.load(context,R.raw.restartauthen,1)
        soundMap["read_card_fail"] = soundPool.load(context,R.raw.readcardfail,1)
        soundMap["face_authen_fail"] = soundPool.load(context,R.raw.faceauthenfail,1)
        soundMap["face_authen"] = soundPool.load(context,R.raw.faceauthen,1)
        soundMap["alcohol_check"] = soundPool.load(context,R.raw.alcoholcheck,1)
        soundMap["alcohol_over_limit"] = soundPool.load(context,R.raw.alcoholoverlimit,1)
        soundMap["read_card_success"] = soundPool.load(context,R.raw.readcardsuccess,1)
        soundMap["face_authen_success"] = soundPool.load(context,R.raw.faceauthensuccess,1)
        soundMap["ting_se"] = soundPool.load(context,R.raw.ting_se,2)
        soundMap["alcohol_in_limit"] = soundPool.load(context,R.raw.alcoholinlimit,1)
        soundMap["alcohol_check_later"] = soundPool.load(context,R.raw.alcoholchecklater,1)
        soundMap["finish_authen"] = soundPool.load(context,R.raw.finishauthen,1)
        soundMap["focus_warning"] = soundPool.load(context,R.raw.focuswarning,5)
        soundMap["health_problem"] = soundPool.load(context,R.raw.healthproblem,1)
        soundMap["alcohol_try_again"] = soundPool.load(context,R.raw.alcoholtryagain,1)
        soundMap["driver_undefine"] = soundPool.load(context,R.raw.driverundefine,1)

    }

    fun playSound(soundName: String){
        soundMap[soundName]?.let {id ->
            soundPool.play(id,1f,1f,1,0,1f)
        }
    }

    suspend fun playStartAuthenMsg(){
        playSound("start_authen")
        delay(1500)
    }

    suspend fun playScanCardMsg(){
        playSound("scan_card")
        delay(2000)
    }

    suspend fun playReadCardSuccessMsg(){
        playSound("read_card_success")
        delay(1200)
    }

    suspend fun playTingSE(){
        playSound("ting_se")
        delay(500)
    }

    suspend fun playFaceAuthenMsg(){
        playSound("face_authen")
        delay(1000)
    }
    suspend fun playFaceAuthenSuccessMsg(){
        playSound("face_authen_success")
        delay(2000)
    }

    suspend fun playAlcoholCheckLaterMsg(){
        playSound("alcohol_check_later")
        delay(2000)
    }
    suspend fun playFaceAuthenFailMsg(){
        playSound("face_authen_fail")
        delay(2200)
        playSound("restart_authen")
    }

    suspend fun playFinishAuthenMsg(){
        playSound("finish_authen")
        delay(1000)
    }

    suspend fun playReadCardFailMsg(){
        playSound("read_card_fail")
        delay(2200)
        playSound("restart_authen")
    }

    suspend fun playAlcoholCheckMsg(){
        playSound("alcohol_check")
        delay(1000)
    }

    suspend fun playAlcoholOverLimit(){
        playSound("alcohol_over_limit")
        delay(1500)
    }

    suspend fun playAlcoholInLimit(){
        playSound("alcohol_in_limit")
        delay(2000)
    }

    suspend fun playFocusWarning(){
        playSound("focus_warning")
        delay(2000)
    }

    suspend fun playHealthProblemMsg(){
        playSound("health_problem")
        delay(2500)
    }
    suspend fun playAlcoholTryAgainMsg(){
        playSound("alcohol_try_again")
        delay(3000)
    }
    suspend fun playDriverUndefineMsg(){
        playSound("driver_undefine")
        delay(2000)
    }
    fun release(){
        soundPool.release()
    }
    companion object {
        var instance: MessagePlayer? = null
        fun getInstance(context: Context): MessagePlayer {
            instance?.let{
                return it
            }
            instance = MessagePlayer(context)
            return instance!!
        }
    }
}