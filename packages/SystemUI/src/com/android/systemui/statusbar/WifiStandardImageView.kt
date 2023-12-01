/*
 * Copyright (C) 2023 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.systemui.statusbar

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.AttributeSet
import android.widget.ImageView
import com.android.systemui.R
import com.android.systemui.Dependency
import com.android.systemui.tuner.TunerService

class WifiStandardImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val tunerService: TunerService by lazy { Dependency.get(TunerService::class.java) }
    private val connectivityManager: ConnectivityManager by lazy { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val wifiManager: WifiManager by lazy { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiStandardEnabled = false
    private var isTunerRegistered = false
    private var isRegistered = false

    init {
        registerTunerService()
        showWifiStandard()
    }

    private fun registerTunerService() {
        if (isTunerRegistered) return
        tunerService.addTunable({ key, value ->
            wifiStandardEnabled = TunerService.parseIntegerSwitch(value, false)
            if (wifiStandardEnabled) {
                showWifiStandard()
            } else {
                unregisterNetworkCallback()
            }
        }, "system:wifi_standard_icon")
        isTunerRegistered = true
    }
    
     private fun showWifiStandard() {
        if (!wifiStandardEnabled || networkCallback != null) return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                networkCapabilities?.let { setWifiStandard(it) }
            }
            override fun onLost(network: Network) {
                post {
                    visibility = GONE
                }
            }
        }
        registerNetworkCallback()
    }

    private fun setWifiStandard(networkCapabilities: NetworkCapabilities) {
        if (!wifiStandardEnabled) return
        val wifiStandard = getWifiStandard()
        if (wifiStandard >= 4) {
            val identifier = resources.getIdentifier(
                "ic_wifi_standard_$wifiStandard", 
                "drawable", 
                context.packageName
            )
            if (identifier > 0) {
                post {
                    setImageDrawable(context.getDrawable(identifier))
                    visibility = VISIBLE
                }
            }
        }
    }

    private fun getWifiStandard(): Int {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo.wifiStandard
        } else {
            -1 
        }
    }

    private fun registerNetworkCallback() {
        if (isRegistered || networkCallback == null) return
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        isRegistered = true
    }

    private fun unregisterNetworkCallback() {
        if (!isRegistered || networkCallback == null) return
        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
            post {
                visibility = GONE
            }
        } catch (e: IllegalArgumentException) {
        } finally {
            isRegistered = false
        }
    }

}
