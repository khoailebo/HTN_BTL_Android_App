package com.dung.htn_btl_android_app

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.net.ssl.SSLContext

class MQTTConnector {
    constructor(){
        connectToMQTTBroker()
    }

    fun connectToMQTTBroker() {
        val client = MqttClient.builder()
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
                        .topicFilter("information")
                        .callback { publish ->
                            val byteBuffer = publish.payload.get()
                            val bytes = ByteArray(byteBuffer.remaining())
                            byteBuffer.get(bytes)  // Fill the ByteArray with data from ByteBuffer

                            // Now convert to String
                            val msg = String(bytes, StandardCharsets.UTF_8)
                            Log.d("MQTT", "Received message: $msg")
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

}