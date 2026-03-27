package com.example.syncengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.syncengine.sync.SyncWorker
import com.example.syncengine.ui.navigation.AppNavigation
import com.example.syncengine.ui.theme.SyncEngineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Programar sincronización periódica con WorkManager
        SyncWorker.schedule(applicationContext)

        setContent {
            SyncEngineTheme {
                AppNavigation()
            }
        }
    }
}
