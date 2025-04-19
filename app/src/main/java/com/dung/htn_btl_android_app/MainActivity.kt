package com.dung.htn_btl_android_app


import android.Manifest
import android.app.Activity
import android.app.ComponentCaller
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.math.min

private const val REQUEST_CODE_PERMISSIONS = 10

class MainActivity : AppCompatActivity() {
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            Log.d("BLUETOOTH", "${device?.name}")
            if (device?.name == "ESP_32") {
                bluetoothAdapter?.cancelDiscovery()
                connectToDevice(device)
            }
        }
    }

    private lateinit var button: Button
    private lateinit var connectingView: ConnectingView
    private lateinit var checkingView: CheckingView

    val faceActivityRegistor =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d("FACE COMPARE", "ok")
                val data = result.data
                data?.let {
                    val isSimilar = it.getBooleanExtra(FaceDetectorActivity.SIMILARITY, false)
                    faceContinuation.resume(isSimilar)
                }
            } else {
                Log.d("FACE COMPARE", "fail")
                faceContinuation.resume(false)
            }
        }

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.INTERNET,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setUpComponents()
        if (allPermissionsGranted()) {

        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        lifecycleScope.launch (Dispatchers.IO) {
            Utilities.mqttConnector = MQTTConnector()
        }
        lifecycleScope.launch(Dispatchers.Default) {
            // Enable Bluetooth if not already
            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                }
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                startBluetooth()
            }
        }
        Utilities.messagePlayer = MessagePlayer.getInstance(this)
    }

    fun setUpComponents() {
        button = findViewById<Button>(R.id.button)
        button.visibility = Button.INVISIBLE
        button.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Default) {
                authenProcess()
            }
        }

        checkingView = findViewById(R.id.checking_view)
        connectingView = findViewById<ConnectingView>(R.id.connecting_view)
    }

    fun displayFRID() {
        button.visibility = View.INVISIBLE
        checkingView.setMode(CheckingMode.RFID)
    }

    fun hideChecking() {
        checkingView.setMode(CheckingMode.NONE)
    }

    fun hideCheckingShowButton() {
        checkingView.setMode(CheckingMode.NONE)
        button.visibility = View.VISIBLE
    }

    fun displayAlcohol() {
        button.visibility = View.INVISIBLE
        checkingView.setMode(CheckingMode.ALCOHOL)
    }

    private lateinit var faceContinuation: Continuation<Boolean>
    suspend fun authenProcess() {
        Utilities.messagePlayer?.playStartAuthenMsg()
        lifecycleScope.launch(Dispatchers.Main) {
            displayFRID()
        }
        Utilities.messagePlayer?.playScanCardMsg()
        val readCardSuccessful = suspendCancellableCoroutine<Boolean> { cont ->
            Utilities.communicator?.sendEvent("GetDriverId") { data ->
                data?.let {
                    Utilities.driver = Driver(it)
                }
                cont.resume(data != null, null)
            }
        }
        if (readCardSuccessful) {
            Utilities.messagePlayer?.playTingSE()
            Utilities.messagePlayer?.playReadCardSuccessMsg()
            lifecycleScope.launch(Dispatchers.Main) {
                hideChecking()
            }
            Utilities.messagePlayer?.playFaceAuthenMsg()
            val faceAuthenSuccess = suspendCancellableCoroutine<Boolean> { cont ->
                faceContinuation = cont
                faceActivityRegistor.launch(
                    Intent(
                        this@MainActivity,
                        FaceDetectorActivity::class.java
                    )
                )
            }

            if (faceAuthenSuccess) {
                Utilities.messagePlayer?.playTingSE()
                Utilities.messagePlayer?.playFaceAuthenSuccessMsg()
                lifecycleScope.launch(Dispatchers.Main) {
                    displayAlcohol()
                }
                Utilities.messagePlayer?.playAlcoholCheckMsg()
                var alcoholCheckSuccess: Boolean?
                var counter = 0
                var minAlcoholValue = suspendCancellableCoroutine<Float> { cont ->
                    Utilities.communicator?.sendEvent("InitialAlcohol") { data ->
                        cont.resume(if (data != null) data.toFloat() else 0.0035f)
                    }
                }
                Log.d("InitialAlcohol", minAlcoholValue.toString())

                do {
                    alcoholCheckSuccess = suspendCancellableCoroutine<Boolean?> { cont ->
                        Utilities.communicator?.sendEvent("GetAlcoholLevel") { data ->
                            Log.d("ALCOHOL CHECK", data ?: "NULL")
                            val alcoholLevel = if (data != null) data.toFloat() else 0f;
                            if (
//                                !(alcoholLevel > minAlcoholValue)
                                ((alcoholLevel * 10000).toInt() - (minAlcoholValue * 10000).toInt()) < 2
                            ) {
                                Log.d(
                                    "Alcohol",
                                    "${(alcoholLevel * 10000).toInt() - (minAlcoholValue * 10000).toInt()}"
                                )
                                cont.resume(null)
                            } else {
                                cont.resume(alcoholLevel < 0.1)
                            }
                        }
                    }
                    if (alcoholCheckSuccess == null && counter < 2) {
                        Utilities.messagePlayer?.playAlcoholTryAgainMsg()
                    }
                    counter++
                } while (alcoholCheckSuccess == null && counter < 3)
                if (alcoholCheckSuccess == true) {
                    Utilities.messagePlayer?.playTingSE()
                    Utilities.messagePlayer?.playAlcoholInLimit()
                    Utilities.messagePlayer?.playFinishAuthenMsg()
                    lifecycleScope.launch(Dispatchers.Main) {
                        startActivity(Intent(this@MainActivity, MonitorActivity::class.java))
                    }
                    lifecycleScope.launch(Dispatchers.Main) {
                        hideCheckingShowButton()
                    }
                } else if (alcoholCheckSuccess == false) {
                    Utilities.messagePlayer?.playAlcoholOverLimit()
                    Utilities.messagePlayer?.playAlcoholCheckLaterMsg()
                    lifecycleScope.launch(Dispatchers.Main) {
                        hideCheckingShowButton()
                    }
                } else if (alcoholCheckSuccess == null) {
                    Utilities.messagePlayer?.playAlcoholCheckLaterMsg()
                    lifecycleScope.launch(Dispatchers.Main) {
                        hideCheckingShowButton()
                    }
                }
            } else {
                Utilities.messagePlayer?.playFaceAuthenFailMsg()
            }

        } else {
            lifecycleScope.launch(Dispatchers.Main) {
                hideCheckingShowButton()
            }
            Utilities.messagePlayer?.playReadCardFailMsg()
        }
    }

    fun showConnecting(show: Boolean) {
        connectingView.setShow(show)
        button.visibility = if (show) View.INVISIBLE else View.VISIBLE
        checkingView.setMode(CheckingMode.NONE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch(Dispatchers.Default) {
                    startBluetooth()
                }
            } else {
                finish()
            }
        }
    }

    private val REQUEST_ENABLE_BT = 1
    fun startBluetooth() {

// Discover devices
        lifecycleScope.launch(Dispatchers.Main) {
            showConnecting(true)
        }

        bluetoothAdapter?.startDiscovery()

    }

    fun connectToDevice(device: BluetoothDevice) {
        lifecycleScope.launch(Dispatchers.Main) {
            showConnecting(false)
        }
        val uuid = device.uuids?.firstOrNull()?.uuid
            ?: UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val socket = device.createRfcommSocketToServiceRecord(uuid)
        socket.connect()

        Utilities.communicator = Communicator(socket, lifecycleScope) {
            startBluetooth()
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    override fun onDestroy() {
        super.onDestroy()
        Utilities.communicator?.close()
        Utilities.messagePlayer?.release()
    }
}