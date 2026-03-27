package com.example.syncengine.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.syncengine.SyncEngineApp
import java.util.concurrent.TimeUnit

/**
 * Worker de WorkManager que ejecuta la sincronización periódica en segundo plano.
 * Se ejecuta cada 15 minutos cuando hay conexión a internet.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val UNIQUE_WORK_NAME = "sync_engine_periodic"

        /**
         * Programa la sincronización periódica.
         * Se llama una vez al iniciar la app.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Solo con internet
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES // Mínimo permitido por WorkManager
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // No duplicar si ya existe
                request
            )

            Log.d(TAG, "Sincronización periódica programada (cada 15 min)")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Ejecutando sincronización en segundo plano...")

        val app = applicationContext as SyncEngineApp
        val db = app.database

        val syncEngine = SyncEngine(
            incidenciaDao = db.incidenciaDao(),
            conflictDao = db.conflictDao(),
            context = applicationContext
        )

        return try {
            val result = syncEngine.sync()
            if (result.isSuccess) {
                Log.d(TAG, "Sync OK: ↑${result.pushed} ↓${result.pulled} ⚠${result.conflicts}")
                Result.success()
            } else {
                Log.w(TAG, "Sync con errores: ${result.errors}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync falló completamente", e)
            Result.retry() // WorkManager reintentará con backoff exponencial
        }
    }
}

