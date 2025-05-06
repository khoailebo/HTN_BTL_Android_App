package com.dung.htn_btl_android_app

import android.util.Log
import com.google.gson.JsonParser
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import java.nio.charset.StandardCharsets

class MQTTConnector {
    private var driverListener: ((String?) -> Unit)? = null
    private var _connected = false
    val connected
        get() = _connected

    private constructor() {
        connectToMQTTBroker()
    }

    companion object {
        private lateinit var client: Mqtt5AsyncClient
        private var instance: MQTTConnector? = null
        fun getInstance(): MQTTConnector {
            if (instance == null) {
                instance = MQTTConnector()
            }
            return instance!!
        }
    }

    fun connectToMQTTBroker() {
        client = MqttClient.builder()
            .useMqttVersion5()
            .serverHost("0610ea90c79f47308af595e1d8fd47eb.s1.eu.hivemq.cloud") // ✅ KHÔNG có ssl://
            .serverPort(8883) // ✅ đúng port SSL của HiveMQ Cloud
            .sslWithDefaultConfig() // ✅ tự động bật TLS/SSL
            .simpleAuth()
            .username("nguyenhaiyen")
            .password("B21dccn129@".toByteArray())
            .applySimpleAuth()
            .buildAsync()


        client.connect()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    throwable.printStackTrace()
                    Log.e("MQTT", "Connection failed: ${throwable.message}")
                } else {
                    Log.i("MQTT", "Connected!")

                    // Sau khi kết nối thành công, subscribe:
                    client.subscribeWith()
                        .topicFilter("information/response")
                        .callback { publish ->
                            val byteBuffer = publish.payload.get()
                            val bytes = ByteArray(byteBuffer.remaining())
                            byteBuffer.get(bytes)  // Fill the ByteArray with data from ByteBuffer

                            // Now convert to String
                            val msg = String(bytes, StandardCharsets.UTF_8)
                            Log.d("MQTT", "Received message: $msg")
                            driverListener?.let {
                                val response = JsonParser.parseString(msg).asJsonObject
                                Log.d("MQTT", response["driverId"].toString())
                                Log.d("MQTT", Utilities.driver?.id.toString())
                                if (response["driverId"].asString.equals(Utilities.driver?.id)) {
                                    it.invoke(if(response["data"].isJsonNull) null else response["data"].toString())
                                }
                            }
                        }
                        .send()
                    client.toAsync().publishWith()
                        .topic("information")
                        .payload("Hello from Android!".toByteArray())
                        .send()
                }
            }


//         Subscribe đến một topic
//        client.toAsync().subscribeWith()
//            .topicFilter("information")
//            .qos(MqttQos.AT_LEAST_ONCE)
//            .callback { publish ->
//                val message = publish.payload.get().toString()
//                Log.d("TEST MQ",message)
//            }
//            .send()

        // Gửi một message
//        client.toAsync().publishWith()
//            .topic("information")
//            .payload("Hello from Android!".toByteArray())
//            .send()
    }

    fun sendDriveID(listener: (data: String?) -> Unit) {
        driverListener = listener
        client.toAsync().publishWith()
            .topic("information/request")
            .payload(Utilities.driver?.id?.toByteArray())
            .send()
    }

    fun sendStartDriving(data:String){
        client.toAsync().publishWith()
            .topic("trip")
            .payload(data.toByteArray())
            .send()
    }
    fun sendStopDriving(data:String){
        client.toAsync().publishWith()
            .topic("trip")
            .payload(data.toByteArray())
            .send()
    }

    fun overlimitAlcohol(data:String){
        client.toAsync().publishWith()
            .topic("alcohol")
            .payload(data.toByteArray())
            .send()
    }

    fun drowsinessSend(){
        client.toAsync().publishWith()
            .topic("drowsiness")
            .payload(Utilities.driver?.id?.toByteArray())
            .send()
    }
}