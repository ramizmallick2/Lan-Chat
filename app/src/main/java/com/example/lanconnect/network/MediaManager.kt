package com.example.lanconnect.network

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class MediaManager {
    private var audioSocket: DatagramSocket? = null
    private var audioSenderSocket: DatagramSocket? = null
    private var videoServerSocket: ServerSocket? = null
    private var videoClientSocket: Socket? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val videoSendMutex = Mutex()

    @Volatile private var isRunning = false

    val localAudioPort: Int get() = audioSocket?.localPort ?: 0
    val localVideoPort: Int get() = videoServerSocket?.localPort ?: 0

    private val _remoteVideoFrame = MutableStateFlow<Bitmap?>(null)
    val remoteVideoFrame: StateFlow<Bitmap?> = _remoteVideoFrame.asStateFlow()

    fun startReceivers(isVideo: Boolean) {
        if (isRunning) return
        audioSocket = DatagramSocket(0)
        if (isVideo) {
            videoServerSocket = ServerSocket(0)
        }
        isRunning = true
    }

    suspend fun startAudioReceiver() {
        withContext(Dispatchers.IO) {
            try {
                val sampleRate = 16000
                val minSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(minSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack?.play()

                val buffer = ByteArray(minSize)
                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    audioSocket?.receive(packet)
                    audioTrack?.write(packet.data, 0, packet.length)
                }
            } catch (e: Exception) {
                Log.e("MediaManager", "Audio receive error", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun startAudioSender(peerIp: String, peerAudioPort: Int, isMicMuted: () -> Boolean) {
        withContext(Dispatchers.IO) {
            try {
                audioSenderSocket = DatagramSocket()
                val addr = InetAddress.getByName(peerIp)
                val sampleRate = 16000
                val minSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minSize)
                audioRecord?.startRecording()

                val buffer = ByteArray(minSize)
                while (isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0 && !isMicMuted()) {
                        val packet = DatagramPacket(buffer, read, addr, peerAudioPort)
                        audioSenderSocket?.send(packet)
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaManager", "Audio send error", e)
            }
        }
    }

    suspend fun startVideoReceiver() {
        withContext(Dispatchers.IO) {
            try {
                val server = videoServerSocket ?: return@withContext
                val socket = server.accept() // Wait for caller
                val dis = DataInputStream(socket.getInputStream())
                while (isRunning) {
                    val size = dis.readInt()
                    if (size in 1..2000000) {
                        val bytes = ByteArray(size)
                        dis.readFully(bytes)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, size)
                        _remoteVideoFrame.value = bitmap
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaManager", "Video receive error", e)
            }
        }
    }

    suspend fun sendVideoFrame(bitmap: Bitmap, peerIp: String, peerVideoPort: Int) {
        if (!videoSendMutex.tryLock()) return // If already sending a frame, drop this one frame
        try {
            withContext(Dispatchers.IO) {
                try {
                    if (videoClientSocket == null) {
                        videoClientSocket = Socket(peerIp, peerVideoPort)
                    }
                    val socket = videoClientSocket ?: return@withContext
                    val dos = DataOutputStream(socket.getOutputStream())

                    val stream = ByteArrayOutputStream()
                    val ratio = 240f / Math.max(bitmap.width, bitmap.height)
                    val width = (bitmap.width * ratio).toInt()
                    val height = (bitmap.height * ratio).toInt()
                    val scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, width), Math.max(1, height), false)
                    scaled.compress(Bitmap.CompressFormat.JPEG, 40, stream)
                    val bytes = stream.toByteArray()
                    
                    dos.writeInt(bytes.size)
                    dos.write(bytes)
                    dos.flush()
                } catch (e: Exception) {
                    // Keep quiet
                }
            }
        } finally {
            videoSendMutex.unlock()
        }
    }

    fun stop() {
        isRunning = false
        try { audioRecord?.stop() } catch (e: Exception) {}
        try { audioRecord?.release() } catch (e: Exception) {}
        try { audioTrack?.stop() } catch (e: Exception) {}
        try { audioTrack?.release() } catch (e: Exception) {}
        audioSocket?.close()
        audioSenderSocket?.close()
        videoServerSocket?.close()
        videoClientSocket?.close()
        
        audioSocket = null
        audioSenderSocket = null
        videoServerSocket = null
        videoClientSocket = null
        audioRecord = null
        audioTrack = null
        
        _remoteVideoFrame.value = null
    }
}
