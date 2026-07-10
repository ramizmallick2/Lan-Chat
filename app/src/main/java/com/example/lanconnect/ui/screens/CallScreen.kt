package com.example.lanconnect.ui.screens

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.example.lanconnect.models.PeerDevice
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(viewModel: com.example.lanconnect.MainViewModel, state: com.example.lanconnect.CallState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    val isMicMuted by viewModel.isMicMuted.collectAsState()
    var isVideoEnabled by remember { mutableStateOf(state.isVideo) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }

    var callDuration by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.allPermissionsGranted) {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(state.status) {
        if (state.status == "CONNECTED") {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    val formatDuration = { seconds: Int ->
        val m = seconds / 60
        val s = seconds % 60
        String.format("%02d:%02d", m, s)
    }

    if (state.isIncoming && state.status == "RINGING") {
        // Incoming Call UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Incoming ${if (state.isVideo) "Video " else "Voice "}Call",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.peerUsername.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.peerUsername,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(64.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.endCall() },
                        containerColor = Color.Red,
                        contentColor = Color.White,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Reject",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    FloatingActionButton(
                        onClick = { viewModel.acceptCall() },
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isVideo) Icons.Default.Videocam else Icons.Default.Phone,
                            contentDescription = "Accept",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val remoteFrame by viewModel.mediaManager.remoteVideoFrame.collectAsState()
        if (state.status == "CONNECTED" && state.isVideo && remoteFrame != null) {
            Image(
                bitmap = remoteFrame!!.asImageBitmap(),
                contentDescription = "Remote Video",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!state.isVideo) {
            // Audio only or video disabled UI
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.peerUsername.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isVideoEnabled && state.status == "CONNECTED" && cameraPermissionState.allPermissionsGranted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 120.dp, end = 16.dp)
                    .size(100.dp, 150.dp)
                    .background(Color.DarkGray)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                                .setTargetResolution(android.util.Size(240, 320))
                                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            
                            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                val bitmap = imageProxy.toBitmap()
                                viewModel.sendVideoFrame(bitmap)
                                imageProxy.close()
                            }

                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {}

                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = {}
                )
            }
        }

        // Overlay status and controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.peerUsername,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.status == "CONNECTED") formatDuration(callDuration) else "Calling...",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = { viewModel.toggleMic() },
                    containerColor = if (isMicMuted) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(alpha = 0.2f),
                    contentColor = if (isMicMuted) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Mic"
                    )
                }

                if (state.isVideo) {
                    FloatingActionButton(
                        onClick = { isVideoEnabled = !isVideoEnabled },
                        containerColor = if (!isVideoEnabled) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(alpha = 0.2f),
                        contentColor = if (!isVideoEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            imageVector = if (!isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Toggle Video"
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                                CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                        },
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera"
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { viewModel.endCall() },
                    containerColor = Color.Red,
                    contentColor = Color.White,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
