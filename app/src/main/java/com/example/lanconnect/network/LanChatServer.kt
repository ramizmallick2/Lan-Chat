package com.example.lanconnect.network

import android.util.Log
import com.example.lanconnect.models.ChatMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class LanChatServer(private val port: Int = 0) {
    private var serverSocket: ServerSocket? = null
    var localPort: Int = -1
        private set
    
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val messageAdapter = moshi.adapter(ChatMessage::class.java)

    private val _incomingMessages = MutableSharedFlow<ChatMessage>()
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    @Volatile
    private var isRunning = false

    suspend fun startServer() {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                localPort = serverSocket!!.localPort
                isRunning = true
                Log.d("LanChatServer", "Server started on port $localPort")

                while (isRunning) {
                    val socket = serverSocket!!.accept()
                    handleClient(socket)
                }
            } catch (e: Exception) {
                Log.e("LanChatServer", "Server error", e)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val senderIp = socket.inetAddress.hostAddress ?: ""
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    val payload = reader.readLine()
                    if (payload != null) {
                        try {
                            val message = messageAdapter.fromJson(payload)
                            if (message != null) {
                                val realMessage = if (senderIp.isNotEmpty()) message.copy(senderIp = senderIp) else message
                                _incomingMessages.emit(realMessage)
                            }
                        } catch (e: Exception) {
                            Log.e("LanChatServer", "Failed to parse message", e)
                        }
                    }
                }
            } catch (e: Exception) {
                 Log.e("LanChatServer", "Error reading client socket", e)
            } finally {
                socket.close()
            }
        }
    }

    fun stopServer() {
        isRunning = false
        serverSocket?.close()
    }
}
