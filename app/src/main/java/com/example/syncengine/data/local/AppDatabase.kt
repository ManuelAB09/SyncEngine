package com.example.syncengine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [IncidenciaEntity::class, PendingConflictEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incidenciaDao(): IncidenciaDao
    abstract fun conflictDao(): ConflictDao

    companion object {
        /**
         * Migración v1→v2:
         * - Añade columnas foto_path, foto_url.
         * - Cambia descripcion de nullable a NOT NULL (requiere recrear tabla).
         * - Añade remote_foto_url a pending_conflicts.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Recrear incidencias_locales con el esquema correcto
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS incidencias_locales_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        usuario_id TEXT NOT NULL,
                        titulo TEXT NOT NULL,
                        descripcion TEXT NOT NULL DEFAULT '',
                        estado TEXT NOT NULL,
                        latitud REAL,
                        longitud REAL,
                        foto_path TEXT,
                        foto_url TEXT,
                        version INTEGER NOT NULL,
                        creado_en INTEGER NOT NULL,
                        actualizado_en INTEGER NOT NULL,
                        borrado_en INTEGER,
                        sync_status TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                // 2) Copiar datos existentes (COALESCE para que NULLs → '')
                db.execSQL(
                    """
                    INSERT INTO incidencias_locales_new
                        (id, usuario_id, titulo, descripcion, estado, latitud, longitud,
                         version, creado_en, actualizado_en, borrado_en, sync_status)
                    SELECT
                        id, usuario_id, titulo, COALESCE(descripcion, ''), estado,
                        latitud, longitud, version, creado_en, actualizado_en,
                        borrado_en, sync_status
                    FROM incidencias_locales
                    """.trimIndent()
                )

                // 3) Reemplazar tabla
                db.execSQL("DROP TABLE incidencias_locales")
                db.execSQL("ALTER TABLE incidencias_locales_new RENAME TO incidencias_locales")

                // 4) Añadir columna de foto remota a pending_conflicts
                db.execSQL("ALTER TABLE pending_conflicts ADD COLUMN remote_foto_url TEXT")
            }
        }

        /**
         * Migración v3→v4:
         * - Añade columna google_maps_url a incidencias_locales.
         * - Añade columna remote_google_maps_url a pending_conflicts.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incidencias_locales ADD COLUMN google_maps_url TEXT")
                db.execSQL("ALTER TABLE pending_conflicts ADD COLUMN remote_google_maps_url TEXT")
            }
        }

        /**
         * Migración v2→v3:
         * - Añade columna synced_checksum para detección de conflictos por contenido.
         *   Almacena un hash MD5 del estado del servidor en el último sync,
         *   permitiendo detectar cambios incluso sin triggers en Supabase.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE incidencias_locales ADD COLUMN synced_checksum TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
