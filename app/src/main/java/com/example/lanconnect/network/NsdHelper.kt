package com.example.lanconnect.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.lanconnect.models.PeerDevice

class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    companion object {
        const val SERVICE_TYPE = "_lanconnect._tcp."
        const val TAG = "NsdHelper"
    }

    private var serviceName = "LanConnect"
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    fun registerService(port: Int, username: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "LanConnect_$username"
            serviceType = SERVICE_TYPE
            setAttribute("username", username)
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                serviceName = NsdServiceInfo.serviceName
                Log.d(TAG, "Service registered: $serviceName")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${arg0.serviceName}")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun discoverServices() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success $service")
                if (service.serviceType == SERVICE_TYPE) {
                    if (service.serviceName == serviceName) {
                        Log.d(TAG, "Same machine: $serviceName")
                    } else {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                Log.e(TAG, "Resolve failed: $errorCode")
                            }
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                Log.d(TAG, "Resolve Succeeded: $serviceInfo")
                                val port: Int = serviceInfo.port
                                val host: String = serviceInfo.host.hostAddress ?: ""
                                val user = serviceInfo.attributes?.get("username")?.let { String(it) } ?: "Unknown"
                                
                                val newPeer = PeerDevice(serviceInfo.serviceName, host, port, user)
                                val currentList = _discoveredPeers.value.toMutableList()
                                if (currentList.none { it.nsdServiceName == newPeer.nsdServiceName }) {
                                    currentList.add(newPeer)
                                    _discoveredPeers.value = currentList
                                }
                            }
                        })
                    }
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "service lost: $service")
                val currentList = _discoveredPeers.value.toMutableList()
                currentList.removeAll { it.nsdServiceName == service.serviceName }
                _discoveredPeers.value = currentList
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun tearDown() {
        registrationListener?.let { nsdManager.unregisterService(it) }
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
    }
}
