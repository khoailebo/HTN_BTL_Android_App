package com.dung.htn_btl_android_app

import com.google.gson.Gson

data class AlcoholRequest(val value: Float){
    override fun toString(): String {
        return Gson().toJson(this)
    }
}
