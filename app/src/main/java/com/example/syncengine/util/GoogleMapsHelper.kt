package com.example.syncengine.util

/**
 * Helper para generar URLs de Google Maps.
 */
object GoogleMapsHelper {

    /**
     * Genera una URL de Google Maps para una ubicación específica.
     * @param latitud Latitud de la ubicación
     * @param longitud Longitud de la ubicación
     * @return URL de Google Maps
     */
    fun createMapsUrl(latitud: Double, longitud: Double): String {
        return "https://www.google.com/maps?q=$latitud,$longitud"
    }
}