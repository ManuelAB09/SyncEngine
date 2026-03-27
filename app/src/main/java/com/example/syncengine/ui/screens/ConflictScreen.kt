package com.example.syncengine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.syncengine.data.local.IncidenciaEntity
import com.example.syncengine.data.local.PendingConflictEntity
import com.example.syncengine.ui.viewmodel.IncidenciaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de resolución de conflictos (Fase 6).
 * Muestra lado a lado la versión LOCAL vs la versión REMOTA
 * y permite al usuario elegir cuál conservar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictScreen(
    viewModel: IncidenciaViewModel,
    onNavigateBack: () -> Unit
) {
    val conflicts by viewModel.conflicts.collectAsState()
    val remoteDataList by viewModel.conflictRemoteData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resolver Conflictos (${conflicts.size})") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (conflicts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "✓ No hay conflictos pendientes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(conflicts, key = { it.id }) { localEntity ->
                    val remoteData = remoteDataList.find { it.incidencia_id == localEntity.id }
                    ConflictCard(
                        local = localEntity,
                        remote = remoteData,
                        onKeepLocal = { viewModel.resolveConflictKeepLocal(localEntity.id) },
                        onKeepRemote = { viewModel.resolveConflictKeepRemote(localEntity.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictCard(
    local: IncidenciaEntity,
    remote: PendingConflictEntity?,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚠ Conflicto detectado",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Versión LOCAL
            VersionSection(
                label = "📱 TU VERSIÓN (Local)",
                titulo = local.titulo,
                descripcion = local.descripcion,
                estado = local.estado,
                version = local.version,
                actualizado = local.actualizado_en
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Versión REMOTA
            if (remote != null) {
                VersionSection(
                    label = "☁️ VERSIÓN DEL SERVIDOR",
                    titulo = remote.remote_titulo,
                    descripcion = remote.remote_descripcion,
                    estado = remote.remote_estado,
                    version = remote.remote_version,
                    actualizado = remote.remote_actualizado_en
                )
            } else {
                Text(
                    "Datos remotos no disponibles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de resolución
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onKeepLocal,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mantener Local")
                }
                Button(
                    onClick = onKeepRemote,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Usar Servidor")
                }
            }
        }
    }
}

@Composable
private fun VersionSection(
    label: String,
    titulo: String,
    descripcion: String?,
    estado: String,
    version: Int,
    actualizado: Long
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row {
            Text("Título: ", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(titulo, style = MaterialTheme.typography.bodyMedium)
        }

        if (!descripcion.isNullOrBlank()) {
            Row {
                Text("Desc: ", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                Text(descripcion, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row {
            Text("Estado: ", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            Text(estado, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.width(16.dp))
            Text("v$version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                formatTime(actualizado),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

