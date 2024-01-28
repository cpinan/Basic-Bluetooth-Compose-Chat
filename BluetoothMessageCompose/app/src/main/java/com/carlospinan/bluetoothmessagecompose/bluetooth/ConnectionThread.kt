package com.carlospinan.bluetoothmessagecompose.bluetooth

import android.bluetooth.BluetoothSocket

const val CONNECTION_NAME = "BluetoothMessageCompose"

enum class ConnectionState {
    SERVER_WAITING_FOR_CONNECTION,
    SERVER_CONNECTION_FAILED,
    SERVER_CONNECTED,
    CLIENT_DISCOVERY_CANCEL,
    CLIENT_CONNECTING,
    CLIENT_CONNECTED,
    CLIENT_CONNECTION_FAILED,
}

interface ConnectionThread {

    fun cancel()

    fun manageMyConnectedSocket(socket: BluetoothSocket)
}