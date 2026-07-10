package com.example.lanconnect.network

import android.util.Log
import com.example.lanconnect.models.ChatMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class LanChatClient {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val messageAdapter = moshi.adapter(ChatMessage::class.java)

    suspend fun sendMessage(ip: String, port: Int, message: ChatMessage): Boolean {
        return withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 2000)
                
                val output = PrintWriter(socket.getOutputStream(), true)
                val json = messageAdapter.toJson(message)
                output.println(json)
                true
            } catch (e: Exception) {
                Log.e("LanChatClient", "Failed to send message", e)
                false
            } finally {
                socket?.close()
            }
        }
    }
}
