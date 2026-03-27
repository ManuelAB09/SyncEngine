package com.example.syncengine

import android.app.Application
import androidx.room.Room
import com.example.syncengine.data.local.AppDatabase

/**
 * Application class que inicializa la base de datos Room como singleton.
 * Todas las capas de la app acceden a la DB a través de SyncEngineApp.database.
 */
class SyncEngineApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "sync_engine_db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    companion object {
        lateinit var instance: SyncEngineApp
            private set
    }
}

