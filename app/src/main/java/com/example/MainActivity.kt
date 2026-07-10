package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lanconnect.MainViewModel
import com.example.lanconnect.models.PeerDevice
import com.example.lanconnect.ui.screens.ChatScreen
import com.example.lanconnect.ui.screens.DiscoveryScreen
import com.example.lanconnect.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LanConnectApp()
            }
        }
    }
}

@Composable
fun LanConnectApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val currentUsername by viewModel.username.collectAsState()
    val callState by viewModel.callState.collectAsState()
    
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val peerAdapter = moshi.adapter(PeerDevice::class.java)

    if (callState != null) {
        com.example.lanconnect.ui.screens.CallScreen(
            viewModel = viewModel,
            state = callState!!
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (currentUsername == null) "setup" else "discovery"
    ) {
        composable("setup") {
            SetupScreen(viewModel = viewModel, onSetupComplete = {
                navController.navigate("discovery") {
                    popUpTo("setup") { inclusive = true }
                }
            })
        }
        composable("discovery") {
            DiscoveryScreen(viewModel = viewModel, onPeerSelected = { peer ->
                val json = peerAdapter.toJson(peer)
                val encodedJson = URLEncoder.encode(json, "UTF-8")
                navController.navigate("chat/$encodedJson")
            })
        }
        composable("chat/{peerJson}") { backStackEntry ->
            val peerJson = backStackEntry.arguments?.getString("peerJson")
            val decodedJson = URLDecoder.decode(peerJson, "UTF-8")
            val peer = peerAdapter.fromJson(decodedJson)
            
            if (peer != null) {
                ChatScreen(viewModel = viewModel, peer = peer, onBack = {
                    navController.popBackStack()
                }, onStartCall = { isVideo ->
                    viewModel.startCall(peer.hostIp, peer.port, peer.username, isVideo)
                })
            } else {
                Text("Error Loading Chat")
            }
        }
    }
}
