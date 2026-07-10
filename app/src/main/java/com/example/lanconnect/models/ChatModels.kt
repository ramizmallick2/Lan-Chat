package com.example.lanconnect.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PeerDevice(
    val nsdServiceName: String,
    val hostIp: String,
    val port: Int,
    val username: String = "Unknown User"
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val id: String,
    val senderUsername: String,
    val senderIp: String,
    val senderPort: Int = 0,
    val text: String,
    val timestampMs: Long,
    val type: String = "TEXT",
    val audioPort: Int = 0,
    val videoPort: Int = 0
)
