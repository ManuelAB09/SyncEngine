package com.example.syncengine.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utilidades para convertir entre timestamps ISO 8601 (Supabase/PostgreSQL)
 * y epoch millis (Room local).
 */
object DateUtils {

    /**
     * Parsea una cadena ISO 8601 (como "2024-01-15T10:30:00.123456+00:00")
     * a epoch millis (Long).
     */
    fun parseIsoToEpochMillis(isoString: String): Long {
        // Supabase devuelve: "2024-01-15T10:30:00.123456+00:00" o "2024-01-15T10:30:00+00:00"
        // Normalizamos: quitamos microsegundos extra (dejamos solo 3 dígitos de ms)
        val normalized = normalizeIso(isoString)

        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(normalized)?.time ?: continue
            } catch (_: Exception) {
                continue
            }
        }
        return 0L
    }

    /**
     * Convierte epoch millis a ISO 8601 para enviar a Supabase.
     */
    fun epochMillisToIso(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMillis))
    }

    /**
     * Normaliza la cadena ISO: trunca microsegundos a milisegundos.
     * "2024-01-15T10:30:00.123456+00:00" → "2024-01-15T10:30:00.123+00:00"
     */
    private fun normalizeIso(iso: String): String {
        // Regex para capturar la parte fraccional de segundos
        val regex = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})\.(\d+)(.*)""")
        val match = regex.matchEntire(iso) ?: return iso

        val datePart = match.groupValues[1]
        val fraction = match.groupValues[2]
        val rest = match.groupValues[3]

        // Tomar solo los primeros 3 dígitos (milisegundos)
        val millis = fraction.take(3).padEnd(3, '0')
        return "$datePart.$millis$rest"
    }
}

