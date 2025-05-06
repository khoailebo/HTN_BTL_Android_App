package com.dung.htn_btl_android_app

import com.google.gson.Gson

data class Vehicle(val vehicleId: Long, var running:Boolean = false){
    override fun toString(): String {
        return Gson().toJson(this)
    }
}
