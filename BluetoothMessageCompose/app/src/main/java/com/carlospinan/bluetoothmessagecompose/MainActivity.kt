package com.carlospinan.bluetoothmessagecompose

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.carlospinan.bluetoothmessagecompose.bluetooth.ClientConnectionThread
import com.carlospinan.bluetoothmessagecompose.bluetooth.ConnectionState
import com.carlospinan.bluetoothmessagecompose.bluetooth.SenderReceiverThread
import com.carlospinan.bluetoothmessagecompose.bluetooth.ServerConnectionThread
import com.carlospinan.bluetoothmessagecompose.bluetooth.getBluetoothManager
import com.carlospinan.bluetoothmessagecompose.bluetooth.providePairedDevices
import com.carlospinan.bluetoothmessagecompose.bluetooth.requestBluetoothPermissions
import com.carlospinan.bluetoothmessagecompose.bluetooth.requiredBluetoothPermissions
import com.carlospinan.bluetoothmessagecompose.bluetooth.requiresBluetoothPermission
import com.carlospinan.bluetoothmessagecompose.bluetooth.startBluetooth
import com.carlospinan.bluetoothmessagecompose.bluetooth.startDiscoveringDevices
import com.carlospinan.bluetoothmessagecompose.ui.theme.BluetoothMessageComposeTheme
import java.util.UUID

// 1 = client, 2 = server
data class BluetoothMessage(val message: String, val sender: Int)

private var bluetoothAdapter: BluetoothAdapter? = null
private var senderReceiverThread: SenderReceiverThread? = null
private var bluetoothEnabledState by mutableStateOf(false)
private var requiresPermissionsState by mutableStateOf(false)
private var bluetoothDevicesList = mutableStateListOf<BluetoothDevice>()
private var connectionState by mutableStateOf<ConnectionState?>(null)
private var messageStateList = mutableStateListOf<BluetoothMessage>()

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
        val MY_UUID: UUID = UUID.fromString("318c6089-985c-4773-b7ca-4c6130e4209e")
    }

    // https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices
    // Create a BroadcastReceiver for ACTION_FOUND.
    private val lookDevicesReceiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "lookDevicesReceiver onReceive")
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        IntentCompat.getParcelableExtra(
                            intent,
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    val deviceName = device?.name
                    val deviceHardwareAddress = device?.address // MAC address
                    Log.d(
                        TAG,
                        "Discovered device: name = $deviceName ; address = $deviceHardwareAddress"
                    )
                    device?.also {
                        bluetoothDevicesList.add(it)
                    }
                }
            }
        }
    }

    // https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices
    // Note: If Bluetooth has not been enabled on the device, then making the device discoverable automatically enables Bluetooth.
    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Log.d(TAG, "Make device discoverable")
        Log.d(TAG, "Discoverable result $result")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBluetooth()
        setContent {
            BluetoothMessageComposeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BluetoothScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(lookDevicesReceiver)
    }

    private fun setupBluetooth() {
        bluetoothAdapter = getBluetoothManager().adapter

        if (bluetoothAdapter == null) {
            Log.d(TAG, "Device does not support bluetooth")
            throw RuntimeException("Device does not support bluetooth")
        }

        bluetoothEnabledState = bluetoothAdapter?.isEnabled == true
        requiresPermissionsState = requiresBluetoothPermission()
        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(lookDevicesReceiver, filter)
    }

}

@Composable
fun BluetoothScreen() {
    Column {
        BluetoothSettings()
        DisplayServerOption()
        DisplayConnectionState()
        DisplayNearbyDevices()
        DisplayChatInfo()
    }
}

@Composable
fun DisplayChatInfo() {
    val displayChat = when (connectionState) {
        ConnectionState.SERVER_CONNECTED,
        ConnectionState.CLIENT_CONNECTED -> true

        else -> false
    }
    if (!displayChat) {
        return
    }

    var text by rememberSaveable { mutableStateOf("") }
    val reversedList = messageStateList.reversed()

    Spacer(modifier = Modifier.height(4.dp))
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color.LightGray)
    ) {
        Text(text = "Chat History")
        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn {
            reversedList.forEach { message ->
                item { RenderChatInformation(message) }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Row {
            TextField(
                value = text,
                onValueChange = {
                    text = it
                },
                label = { Text("Insert your text here") },
                modifier = Modifier.weight(0.8F)
            )
            Button(onClick = {
                sendMessage(message = text)
                text = ""
            }) {
                Text(text = "Send")
            }
        }
    }
}

@Composable
fun RenderChatInformation(message: BluetoothMessage) {
    Spacer(modifier = Modifier.height(4.dp))
    Card(modifier = Modifier.padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (message.sender == 0) {
                Text(text = "From Client")
            } else {
                Text(text = "From Server")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message.message)
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
fun DisplayServerOption() {
    if (requiresPermissionsState || !bluetoothEnabledState) {
        return
    }
    if (canCreateServer()) {
        Button(onClick = {
            startServer()
        }) {
            Text(text = "Create my server")
        }
    }
}

@Composable
fun DisplayConnectionState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceTint,
        ),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {

            when (connectionState) {
                ConnectionState.SERVER_WAITING_FOR_CONNECTION -> {
                    Text(text = "Server is waiting for connection")
                }

                ConnectionState.SERVER_CONNECTION_FAILED -> {
                    Text(text = "Server connection has failed")
                }

                ConnectionState.SERVER_CONNECTED -> {
                    Text(text = "Server is now connected")
                }

                ConnectionState.CLIENT_DISCOVERY_CANCEL -> {
                    Text(text = "Client discovery has been cancelled")
                }

                ConnectionState.CLIENT_CONNECTING -> {
                    Text(text = "Client is connecting")
                }

                ConnectionState.CLIENT_CONNECTED -> {
                    Text(text = "Client is now connected")
                }

                ConnectionState.CLIENT_CONNECTION_FAILED -> {
                    Text(text = "Client connection has failed")
                }

                null -> {
                    Text(text = "Waiting for some connection state")
                }
            }
        }
    }
}

@Composable
fun DisplayNearbyDevices() {
    if (!canCreateServer()) {
        return
    }
    if (!bluetoothEnabledState || requiresPermissionsState) {
        return
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "Nearby devices list. Tap to connect to one")

    Spacer(modifier = Modifier.height(8.dp))
    LazyColumn {
        bluetoothDevicesList.forEach {
            item {
                DisplayBluetoothDevice(it)
            }
        }
    }
    bluetoothAdapter?.startDiscoveringDevices()
}

@SuppressLint("MissingPermission")
@Composable
fun DisplayBluetoothDevice(bluetoothDevice: BluetoothDevice) {
    Spacer(modifier = Modifier.height(4.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                makeClientRequest(bluetoothDevice = bluetoothDevice)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = "Name = ${bluetoothDevice.name}")
            Text(text = "Address = ${bluetoothDevice.address}")
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
fun BluetoothSettings() {
    val activity = LocalContext.current as Activity

    // 1. Permissions launcher
    val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            var nonGrantedPermission: String? = null
            requiredBluetoothPermissions.forEach {
                if (!result.containsKey(it)) {
                    // Permission not granted. Fallback to non granted permissions
                    nonGrantedPermission = it
                    return@forEach
                }
            }
            if (nonGrantedPermission != null) {
                Log.d(MainActivity.TAG, "Permission non granted for $nonGrantedPermission")
            } else {
                Log.d(MainActivity.TAG, "All permissions are granted")
            }
            requiresPermissionsState = activity.requiresBluetoothPermission()
        }

    // 2. Bluetooth launcher
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == ComponentActivity.RESULT_OK && bluetoothAdapter?.isEnabled == true) {
            Log.d(MainActivity.TAG, "bluetooth is enabled")
        } else {
            Log.d(MainActivity.TAG, "${result.resultCode} -- bluetooth is still off")
        }
        bluetoothEnabledState = bluetoothAdapter?.isEnabled == true
    }

    if (requiresPermissionsState) {
        Button(onClick = {
            activity.requestBluetoothPermissions(requestPermissionLauncher)
        }) {
            Text(text = "Missing permission. Grant them?")
        }
    } else if (!bluetoothEnabledState) {
        Button(onClick = {
            bluetoothAdapter?.startBluetooth(launcher = enableBluetoothLauncher)
        }) {
            Text(text = "Bluetooth is disabled. Turn it on?")
        }
    } else {
        Text(text = "Bluetooth is already enabled")
        bluetoothAdapter?.providePairedDevices()?.let { bluetoothDevicesList.addAll(it) }
    }
}

private fun startServer() {
    bluetoothAdapter?.also {
        ServerConnectionThread(
            uuid = MainActivity.MY_UUID,
            bluetoothAdapter = it,
            connectionStateCallback = ::bluetoothConnectionCallback
        ).apply {
            start()
        }
    }
}

private fun makeClientRequest(bluetoothDevice: BluetoothDevice) {
    bluetoothAdapter?.also {
        ClientConnectionThread(
            uuid = MainActivity.MY_UUID,
            device = bluetoothDevice,
            bluetoothAdapter = it,
            connectionStateCallback = ::bluetoothConnectionCallback,
        ).apply {
            start()
        }
    }
}


// Callbacks

private fun sendMessage(message: String) {
    val sender = when (connectionState) {
        ConnectionState.CLIENT_CONNECTED -> 0
        ConnectionState.SERVER_CONNECTED -> 1
        else -> -1
    }
    if (message.isNotEmpty() && sender != -1) {
        messageStateList.add(BluetoothMessage(message = message, sender = sender))
        senderReceiverThread?.write(data = message, sender = sender)
    }
}

private fun bluetoothMessageCallback(bytes: Int, buffer: ByteArray) {
    val data = String(buffer, 0, bytes)
    val message = data.substring(0, data.length - 1)
    val sender = data.substring(data.length - 1).toInt()
    Log.d(MainActivity.TAG, "Message received = $data")
    if (data.isNotEmpty()) {
        messageStateList.add(BluetoothMessage(message = message, sender = sender))
    }
}

private fun bluetoothConnectionCallback(state: ConnectionState, socket: BluetoothSocket?) {
    when (state) {
        ConnectionState.SERVER_WAITING_FOR_CONNECTION -> {
            Log.d(MainActivity.TAG, "Server is waiting for a connection")
        }

        ConnectionState.SERVER_CONNECTION_FAILED -> {
            Log.d(MainActivity.TAG, "Something wrong has happened on server")
        }

        ConnectionState.CLIENT_DISCOVERY_CANCEL -> {
            Log.d(MainActivity.TAG, "Client has requested a cancellation on discovering")
        }

        ConnectionState.CLIENT_CONNECTING -> {
            Log.d(MainActivity.TAG, "Client is now connecting")
        }

        ConnectionState.CLIENT_CONNECTION_FAILED -> {
            Log.d(MainActivity.TAG, "Something wrong has happened on client")
        }

        ConnectionState.CLIENT_CONNECTED,
        ConnectionState.SERVER_CONNECTED -> {
            Log.d(MainActivity.TAG, "Server / Client is now connected == $state")
            socket?.also {
                senderReceiverThread?.also { thread ->
                    thread.interrupt()
                }
                senderReceiverThread = null
                senderReceiverThread = SenderReceiverThread(
                    bluetoothSocket = it,
                    messageHandler = ::bluetoothMessageCallback
                ).apply {
                    start()
                }
            }
        }
    }
    connectionState = state
}

private fun canCreateServer(): Boolean {
    return when (connectionState) {
        ConnectionState.SERVER_CONNECTED,
        ConnectionState.CLIENT_CONNECTED,
        ConnectionState.SERVER_WAITING_FOR_CONNECTION,
        ConnectionState.CLIENT_CONNECTING -> {
            false
        }

        else -> true
    }
}