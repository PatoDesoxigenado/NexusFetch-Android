package io.github.patodesoxigenado.nexusfetch.data.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkTransport {
    WIFI_OR_ETHERNET,
    CELLULAR,
    OFFLINE,
}

/**
 * Evaluates current connectivity so mobile data protection ("Wi-Fi Only" / 4G-5G confirmation) stays accurate.
 */
@Singleton
class NetworkGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun currentTransport(): NetworkTransport {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkTransport.OFFLINE
        val network = cm.activeNetwork ?: return NetworkTransport.OFFLINE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkTransport.OFFLINE

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkTransport.OFFLINE
        }

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.WIFI_OR_ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            else -> NetworkTransport.WIFI_OR_ETHERNET
        }
    }
}
