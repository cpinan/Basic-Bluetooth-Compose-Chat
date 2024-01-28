package com.carlospinan.bluetoothmessagecompose.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class ClientConnectionThread(
    private val uuid: UUID,
    private val device: BluetoothDevice,
    private val bluetoothAdapter: BluetoothAdapter,
    private val connectionStateCallback: ((state: ConnectionState, socket: BluetoothSocket?) -> Unit)? = null,
) : Thread(), ConnectionThread {

    companion object {
        private const val TAG = "ClientConnectionThread"
    }

    private val bluetoothSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(uuid)
    }

    override fun run() {
        // Cancel discovery because it otherwise slows down the connection.
        connectionStateCallback?.invoke(ConnectionState.CLIENT_DISCOVERY_CANCEL, null)
        bluetoothAdapter.cancelDiscovery()

        bluetoothSocket?.let { socket ->
            connectionStateCallback?.invoke(ConnectionState.CLIENT_CONNECTING, null)
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                socket.connect()

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                manageMyConnectedSocket(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Failed connecting", e)
                connectionStateCallback?.invoke(ConnectionState.CLIENT_CONNECTION_FAILED, null)
            }
        } ?: {
            connectionStateCallback?.invoke(ConnectionState.CLIENT_CONNECTION_FAILED, null)
        }
    }

    override fun cancel() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the client socket", e)
        }
    }

    override fun manageMyConnectedSocket(socket: BluetoothSocket) {
        Log.d(TAG, "Successfully called manageMyConnectedSocket")
        connectionStateCallback?.invoke(ConnectionState.CLIENT_CONNECTED, socket)
    }
}