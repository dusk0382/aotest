package net.spin.ao3.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes network availability so the UI can show an offline banner and
 * tailor error messages ("estás sin conexión" vs. "AO3 falló").
 *
 * One registered callback, no polling, no permission prompt (ACCESS_NETWORK_STATE
 * is a normal permission). Best-effort: if the callback can't be registered we
 * simply stay "online" (the app already handled no-network gracefully).
 */
class ConnectivityMonitor(context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(computeOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = true
        }

        override fun onLost(network: Network) {
            _online.value = computeOnline()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _online.value = hasInternet(capabilities)
        }
    }

    init {
        // registerDefaultNetworkCallback needs API 24; the NetworkRequest
        // overload works since API 21 (with ACCESS_NETWORK_STATE).
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, callback)
            }
        }
    }

    private fun computeOnline(): Boolean = runCatching {
        cm.activeNetwork?.let { hasInternet(cm.getNetworkCapabilities(it)) } ?: false
    }.getOrDefault(false)

    private fun hasInternet(caps: NetworkCapabilities?): Boolean =
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
