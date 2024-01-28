package com.carlospinan.bluetoothmessagecompose.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class ServerConnectionThread(
    private val uuid: UUID,
    private val bluetoothAdapter: BluetoothAdapter,
    private val connectionStateCallback: ((state: ConnectionState, socket: BluetoothSocket?) -> Unit)? = null,
) : Thread(), ConnectionThread {

    companion object {
        private const val TAG = "ServerConnectionThread"
    }

    private val serverSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
        bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
            CONNECTION_NAME,
            uuid,
        )
    }

    override fun run() {
        // Keep listening until exception occurs or a socket is returned.
        // TODO Add timeout
        var shouldLoop = true
        while (shouldLoop) {
            val socket: BluetoothSocket? = try {
                connectionStateCallback?.invoke(ConnectionState.SERVER_WAITING_FOR_CONNECTION, null)
                serverSocket?.accept()
            } catch (e: IOException) {
                connectionStateCallback?.invoke(ConnectionState.SERVER_CONNECTION_FAILED, null)
                Log.e(TAG, "Socket's accept() method failed", e)
                shouldLoop = false
                null
            }
            socket?.also {
                manageMyConnectedSocket(it)
                serverSocket?.close()
                shouldLoop = false
            }
        }
    }

    override fun cancel() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the connect socket", e)
        }
    }

    override fun manageMyConnectedSocket(socket: BluetoothSocket) {
        connectionStateCallback?.invoke(ConnectionState.SERVER_CONNECTED, socket)
    }
}