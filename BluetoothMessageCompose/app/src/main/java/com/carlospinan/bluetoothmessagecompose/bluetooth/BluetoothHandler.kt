package com.carlospinan.bluetoothmessagecompose.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat

private const val TAG = "BluetoothHandler"

val requiredBluetoothPermissions = mutableListOf<String>().apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        add(Manifest.permission.BLUETOOTH)
        add(Manifest.permission.BLUETOOTH_ADMIN)
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
}

fun Context.getBluetoothManager(): BluetoothManager = getSystemService(BluetoothManager::class.java)

@SuppressLint("MissingPermission")
fun BluetoothAdapter.providePairedDevices(): Set<BluetoothDevice> {
    val result = mutableSetOf<BluetoothDevice>()
    val pairedDevices: Set<BluetoothDevice>? = bondedDevices
    if (pairedDevices?.isEmpty() == true) {
        Log.d(TAG, "No paired devices found")
    }
    pairedDevices?.forEachIndexed { index, device ->
        val deviceName = device.name
        val deviceHardwareAddress = device.address // MAC address
        Log.d(TAG, "pos = $index ; name = $deviceName ; hw address = $deviceHardwareAddress")
        result.add(device)
    }
    return result
}

fun BluetoothAdapter.startBluetooth(launcher: ActivityResultLauncher<Intent>) {
    if (!isEnabled) {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        launcher.launch(intent)
    } else {
        Log.d(TAG, "BluetoothHandler/startBluetooth/bluetooth is already enabled")
    }
}

@SuppressLint("MissingPermission")
fun BluetoothAdapter.startDiscoveringDevices() {
    if (!isEnabled) {
        Log.d(TAG, "startDiscoveringDevices/bluetooth not enabled")
        return
    }
    if (!isDiscovering) {
        Log.d(TAG, "startDiscoveringDevices/starting discovery")
        startDiscovery()
    }
}

@SuppressLint("MissingPermission")
fun makeDeviceDiscoverable(
    bluetoothAdapter: BluetoothAdapter?,
    launcher: ActivityResultLauncher<Intent>,
    timeout: Int = 300,
) {
    if (bluetoothAdapter?.isEnabled == true) {
        val discoverableIntent: Intent =
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, timeout)
            }
        launcher.launch(discoverableIntent)
    }
}

fun Activity.requestBluetoothPermissions(
    launcher: ActivityResultLauncher<Array<String>>
) {
    if (requiresBluetoothPermission()) {
        launcher.launch(requiredBluetoothPermissions.toTypedArray())
    }
}

fun Activity.requiresBluetoothPermission(): Boolean {
    requiredBluetoothPermissions.forEach { permission ->
        if (shouldRequestPermission(permission)) {
            return true
        }
    }
    return false
}

private fun Activity.shouldRequestPermission(permission: String): Boolean {
    return ActivityCompat.checkSelfPermission(
        this,
        permission
    ) != PackageManager.PERMISSION_GRANTED && !ActivityCompat.shouldShowRequestPermissionRationale(
        this,
        permission
    )
}