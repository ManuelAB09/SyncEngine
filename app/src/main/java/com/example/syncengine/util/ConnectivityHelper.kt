package com.example.syncengine.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Helper para verificar la disponibilidad de conexión a internet.
 */
object ConnectivityHelper {
    
    /**
     * Verifica si el dispositivo tiene conexión a internet.
     * @param context Contexto de la aplicación
     * @return true si hay conexión, false en caso contrario
     */
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
