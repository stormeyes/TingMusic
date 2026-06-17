package com.tingmusic.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 发现到的一台 Mac 同步服务:可直连的 baseUrl(http://host:port)。 */
data class DiscoveredServer(val name: String, val host: String, val port: Int) {
    val baseUrl: String get() = "http://$host:$port"
}

private const val SERVICE_TYPE = "_tingmusic._tcp."

/**
 * 浏览 _tingmusic._tcp;每发现并解析成功一台就 emit。调用方在协程里 collect,
 * 取消即停止浏览。NsdManager 的 resolve 是逐个排队的,这里串行 resolve。
 */
fun discoverServers(context: Context): Flow<DiscoveredServer> = callbackFlow {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    val resolveListener = object : NsdManager.ResolveListener {
        override fun onServiceResolved(info: NsdServiceInfo) {
            val host = info.host?.hostAddress ?: return
            trySend(DiscoveredServer(info.serviceName ?: "TingMusic", host, info.port))
        }
        override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) { /* ignore one failure */ }
    }

    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
            if (info.serviceType.trimEnd('.').endsWith("_tingmusic._tcp")) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, resolveListener)
            }
        }
        override fun onServiceLost(info: NsdServiceInfo?) {}
        override fun onDiscoveryStarted(serviceType: String?) {}
        override fun onDiscoveryStopped(serviceType: String?) {}
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) { close() }
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
    }

    nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    awaitClose {
        try { nsd.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
    }
}
