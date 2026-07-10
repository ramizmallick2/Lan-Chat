package com.example.lanconnect

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanconnect.models.ChatMessage
import com.example.lanconnect.models.PeerDevice
import com.example.lanconnect.network.LanChatClient
import com.example.lanconnect.network.LanChatServer
import com.example.lanconnect.network.MediaManager
import com.example.lanconnect.network.NsdHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class CallState(
    val peerIp: String,
    val peerPort: Int,
    val peerUsername: String,
    val isVideo: Boolean,
    val isIncoming: Boolean,
    val status: String, // "RINGING", "CONNECTED", "ENDED"
    val remoteAudioPort: Int = 0,
    val remoteVideoPort: Int = 0,
    val localAudioPort: Int = 0,
    val localVideoPort: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val nsdHelper = NsdHelper(application)
    private val chatServer = LanChatServer(0) // Random port
    private val chatClient = LanChatClient()
    val mediaManager = MediaManager()

    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    private val _callState = MutableStateFlow<CallState?>(null)
    val callState: StateFlow<CallState?> = _callState.asStateFlow()
    
    val isMicMuted = MutableStateFlow(false)

    val discoveredPeers: StateFlow<List<PeerDevice>> = nsdHelper.discoveredPeers

    init {
        viewModelScope.launch(Dispatchers.IO) {
            chatServer.startServer()
        }
        viewModelScope.launch {
            chatServer.incomingMessages.collect { msg ->
                val peerKey = msg.senderIp 
                when (msg.type) {
                    "TEXT" -> addMessageToHistory(peerKey, msg)
                    "CALL_REQ_AUDIO", "CALL_REQ_VIDEO" -> {
                        if (_callState.value == null) {
                            _callState.value = CallState(
                                peerIp = msg.senderIp,
                                peerPort = msg.senderPort,
                                peerUsername = msg.senderUsername,
                                isVideo = msg.type == "CALL_REQ_VIDEO",
                                isIncoming = true,
                                status = "RINGING",
                                remoteAudioPort = msg.audioPort,
                                remoteVideoPort = msg.videoPort
                            )
                        } else {
                            sendSignalingMessage(msg.senderIp, msg.senderPort, "CALL_REJECT", 0, 0)
                        }
                    }
                    "CALL_ACCEPT" -> {
                        val state = _callState.value
                        if (state != null && state.peerIp == msg.senderIp) {
                            val newState = state.copy(
                                status = "CONNECTED",
                                remoteAudioPort = msg.audioPort,
                                remoteVideoPort = msg.videoPort
                            )
                            _callState.value = newState
                            startMediaLoops(newState)
                        }
                    }
                    "CALL_REJECT", "CALL_END" -> {
                        if (_callState.value?.peerIp == msg.senderIp) {
                            endCallLocal()
                        }
                    }
                }
            }
        }
    }

    fun setUsername(name: String) {
        _username.value = name
        // Now register our NSD service
        nsdHelper.registerService(chatServer.localPort, name)
        nsdHelper.discoverServices()
    }

    fun sendMessage(peerIp: String, peerPort: Int, text: String) {
        val user = _username.value ?: return
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderUsername = user,
            senderIp = "127.0.0.1", // In a real app we'd get our actual local IP
            senderPort = chatServer.localPort,
            text = text,
            timestampMs = System.currentTimeMillis(),
            type = "TEXT"
        )
        // Add to our local history
        addMessageToHistory(peerIp, msg)

        viewModelScope.launch {
            chatClient.sendMessage(peerIp, peerPort, msg)
        }
    }

    fun startCall(peerIp: String, peerPort: Int, peerUsername: String, isVideo: Boolean) {
        mediaManager.startReceivers(isVideo)
        _callState.value = CallState(
            peerIp = peerIp,
            peerPort = peerPort,
            peerUsername = peerUsername,
            isVideo = isVideo,
            isIncoming = false,
            status = "RINGING",
            localAudioPort = mediaManager.localAudioPort,
            localVideoPort = mediaManager.localVideoPort
        )
        sendSignalingMessage(peerIp, peerPort, if (isVideo) "CALL_REQ_VIDEO" else "CALL_REQ_AUDIO", mediaManager.localAudioPort, mediaManager.localVideoPort)
    }

    fun acceptCall() {
        val state = _callState.value ?: return
        mediaManager.startReceivers(state.isVideo)
        val newState = state.copy(
            status = "CONNECTED",
            localAudioPort = mediaManager.localAudioPort,
            localVideoPort = mediaManager.localVideoPort
        )
        _callState.value = newState
        sendSignalingMessage(state.peerIp, state.peerPort, "CALL_ACCEPT", mediaManager.localAudioPort, mediaManager.localVideoPort)

        startMediaLoops(newState)
    }

    fun endCall() {
        val state = _callState.value
        endCallLocal()
        if (state != null) {
            sendSignalingMessage(state.peerIp, state.peerPort, if (state.status == "RINGING" && state.isIncoming) "CALL_REJECT" else "CALL_END", 0, 0)
        }
    }
    
    private fun endCallLocal() {
        _callState.value = null
        mediaManager.stop()
    }

    private fun startMediaLoops(state: CallState) {
        viewModelScope.launch { mediaManager.startAudioReceiver() }
        viewModelScope.launch { mediaManager.startAudioSender(state.peerIp, state.remoteAudioPort) { isMicMuted.value } }
        if (state.isVideo) {
            viewModelScope.launch { mediaManager.startVideoReceiver() }
        }
    }

    fun toggleMic() {
        isMicMuted.value = !isMicMuted.value
    }

    fun sendVideoFrame(bitmap: Bitmap) {
        val state = _callState.value
        if (state?.status == "CONNECTED" && state.isVideo && state.remoteVideoPort > 0) {
            viewModelScope.launch {
                mediaManager.sendVideoFrame(bitmap, state.peerIp, state.remoteVideoPort)
            }
        }
    }

    private fun sendSignalingMessage(ip: String, port: Int, type: String, audioPort: Int, videoPort: Int) {
        val user = _username.value ?: return
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderUsername = user,
            senderIp = "127.0.0.1",
            senderPort = chatServer.localPort,
            text = "",
            timestampMs = System.currentTimeMillis(),
            type = type,
            audioPort = audioPort,
            videoPort = videoPort
        )
        viewModelScope.launch {
            chatClient.sendMessage(ip, port, msg)
        }
    }

    private fun addMessageToHistory(peerKey: String, message: ChatMessage) {
        val currentHistory = _messages.value.toMutableMap()
        val peerMessages = currentHistory[peerKey]?.toMutableList() ?: mutableListOf()
        peerMessages.add(message)
        currentHistory[peerKey] = peerMessages
        _messages.value = currentHistory
    }

    override fun onCleared() {
        super.onCleared()
        nsdHelper.tearDown()
        chatServer.stopServer()
    }
}
