package com.dung.htn_btl_android_app

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

class Communicator(
    val socket: BluetoothSocket,
    val scope: CoroutineScope,
    val reconnectListener: () -> Unit
) : Runnable {
    private val input: BufferedReader
    private val output: PrintWriter

    //    val appScope = (context as MyApp).appScope
    init {
        input = BufferedReader(InputStreamReader(socket.inputStream))
        output = PrintWriter(socket.outputStream, true)
        startCommunicate()
    }

    private val callbackMap = mutableMapOf<String, MutableList<(String?) -> Unit>>()

    fun sendEvent(
        eventName: String,
        eventData: String = "",
        responseCallback: ((data: String?) -> Unit)? = null
    ): Unit {
        val msg = eventName.plus("|").plus(eventData)
        responseCallback?.let {
            if (callbackMap.containsKey(eventName)) {
                callbackMap[eventName]?.let {
                    it.add(responseCallback)
                }
            } else {
                callbackMap.put(eventName, mutableListOf(responseCallback))
            }
        }
        sendMsg(msg)
    }

    private fun sendMsg(msg: String) {
        scope.launch(Dispatchers.IO) {
            output.println(msg)
        }
    }

    fun startCommunicate() {
//        Thread(this).apply {
//            priority = Thread.MAX_PRIORITY
//            name = "CommunicateThread"
//            start()
//        }
        scope.launch(Dispatchers.IO) {
            run()
        }
    }

    override fun run() {
        try {
            while (socket.isConnected) {
                val recievedMsg = input.readLine()
                processResponse(recievedMsg)
                Log.d("BLUETOOTH", "recieve: ${recievedMsg}")
            }
        } catch (exception: Exception) {
            Log.d("BLUETOOTH ERROR", "${exception.message}")
        } finally {
            close()
            reconnectListener()
        }
    }

    fun processResponse(response: String){
        val buffers = response.split("|")
        val eventName = buffers[0]
        var eventData:String? = null
        if(buffers.size > 1 && !buffers[1].equals(""))
        {
            eventData = buffers[1]
        }
        if(callbackMap.containsKey(eventName)){
            val callback = callbackMap[eventName]?.removeAt(0)
            callback?.let {
                it.invoke(eventData)
            }
            if(callbackMap[eventName]?.size == 0){
                callbackMap.remove(eventName)
            }
        }
    }

    fun close() {
        input.close()
        output.close()
        socket.close()
    }

}