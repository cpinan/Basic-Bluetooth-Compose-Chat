package com.carlospinan.bluetoothmessagecompose.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SenderReceiverThread(
    bluetoothSocket: BluetoothSocket,
    private val messageHandler: (bytes: Int, buffer: ByteArray) -> Unit,
) : Thread() {

    companion object {
        private const val MAX_BUFFER_SIZE = 1024
        private const val TAG = "SenderReceiverThread"
    }

    private val inputStream: InputStream? = bluetoothSocket.inputStream
    private val outputStream: OutputStream? = bluetoothSocket.outputStream

    override fun run() {
        val buffer = ByteArray(MAX_BUFFER_SIZE)
        while (true) {
            try {
                inputStream?.read(buffer)?.also { bytes ->
                    messageHandler.invoke(bytes, buffer)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error on input stream", e)
            }
        }
    }

    fun write(data: String, sender: Int) {
        val info = StringBuilder().apply {
            append(data)
            append(sender)
        }
        outputStream?.write(info.toString().toByteArray())
    }
}