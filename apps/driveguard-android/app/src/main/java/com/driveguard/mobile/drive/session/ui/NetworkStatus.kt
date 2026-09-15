package com.driveguard.mobile.drive.session.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class NetworkStatus(
    val isConnected: Boolean,
    val label: String,
    val detail: String,
)

@Composable
fun rememberNetworkStatus(): NetworkStatus {
    val appContext = LocalContext.current.applicationContext
    val connectivityManager = remember(appContext) {
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var networkStatus by remember(connectivityManager) {
        mutableStateOf(
            connectivityManager?.snapshot()
                ?: NetworkStatus(
                    isConnected = false,
                    label = "Offline",
                    detail = "No network service is available on this device.",
                ),
        )
    }

    DisposableEffect(connectivityManager) {
        if (connectivityManager == null) {
            return@DisposableEffect onDispose { }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkStatus = connectivityManager.snapshot()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                networkStatus = networkCapabilities.toNetworkStatus()
            }

            override fun onLost(network: Network) {
                networkStatus = connectivityManager.snapshot()
            }

            override fun onUnavailable() {
                networkStatus = NetworkStatus(
                    isConnected = false,
                    label = "Offline",
                    detail = "No validated network is available right now.",
                )
            }
        }

        networkStatus = connectivityManager.snapshot()
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }

        onDispose {
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
    }

    return networkStatus
}

private fun ConnectivityManager.snapshot(): NetworkStatus {
    val capabilities = getNetworkCapabilities(activeNetwork)
        ?: return NetworkStatus(
            isConnected = false,
            label = "Offline",
            detail = "No validated network is available right now.",
        )

    return capabilities.toNetworkStatus()
}

private fun NetworkCapabilities.toNetworkStatus(): NetworkStatus {
    val transportLabel = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Network"
    }
    val validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    return if (validated) {
        NetworkStatus(
            isConnected = true,
            label = transportLabel,
            detail = "$transportLabel connection is available for backend sync.",
        )
    } else {
        NetworkStatus(
            isConnected = false,
            label = "$transportLabel limited",
            detail = "$transportLabel is present but internet access is not validated yet.",
        )
    }
}
