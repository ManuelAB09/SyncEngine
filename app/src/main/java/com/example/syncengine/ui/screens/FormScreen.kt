package com.example.syncengine.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.syncengine.ui.viewmodel.IncidenciaViewModel
import java.io.File


/**
 * Pantalla de formulario para crear o editar una incidencia.
 * Si hay una incidencia seleccionada en el ViewModel → modo edición.
 * Si no → modo creación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(
    viewModel: IncidenciaViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedIncidencia by viewModel.selectedIncidencia.collectAsState()
    val isEditing = selectedIncidencia != null

    var titulo by remember(selectedIncidencia) {
        mutableStateOf(selectedIncidencia?.titulo ?: "")
    }

    var descripcion by remember(selectedIncidencia) {
        mutableStateOf(selectedIncidencia?.descripcion ?: "")
    }
    var estado by remember(selectedIncidencia) {
        mutableStateOf(selectedIncidencia?.estado ?: "pendiente")
    }
    var latitudText by remember(selectedIncidencia) {
        mutableStateOf(selectedIncidencia?.latitud?.toString() ?: "")
    }
    var longitudText by remember(selectedIncidencia) {
        mutableStateOf(selectedIncidencia?.longitud?.toString() ?: "")
    }
    var googleMapsUrlText by remember(selectedIncidencia) {
        mutableStateOf(selectedIncidencia?.google_maps_url ?: "")
    }

    val fillCurrentLocation = fillCurrentLocation@{
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                Log.d("MiApp", "LocationManager no disponible")
                return@fillCurrentLocation
            }

            val hasFinePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarsePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFinePermission && !hasCoarsePermission) {
                Log.d("MiApp", "No hay permisos de ubicación")
                return@fillCurrentLocation
            }

            val location = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: Exception) {
                Log.d("MiApp", "Error al obtener ubicación: ${e.message}")
                null
            }

            if (location != null) {
                val coords = "${location.latitude}, ${location.longitude}"
                Log.d("MiApp", coords)
                latitudText = location.latitude.toString()
                longitudText = location.longitude.toString()
                googleMapsUrlText = "https://maps.google.com/maps?q=${location.latitude},${location.longitude}"
            } else {
                Log.d("MiApp", "No se pudo obtener ubicación actual")
            }
        } catch (e: Exception) {
            Log.d("MiApp", "Error al obtener ubicación: ${e.message}")
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fillCurrentLocation()
        } else {
            Log.d("MiApp", "Permiso de ubicación denegado")
        }
    }



    // ── Estado de la foto ──
    // Prioridad: foto local nueva > foto local existente > foto_url remota
    var localPhotoPath by remember(selectedIncidencia) {
        mutableStateOf(selectedIncidencia?.foto_path)
    }
    val existingPhotoUrl = selectedIncidencia?.foto_url

    // Lo que se muestra como preview: uri local o url remota
    val photoPreview: Any? = when {
        localPhotoPath != null -> File(localPhotoPath!!)
        existingPhotoUrl != null -> existingPhotoUrl
        else -> null
    }

    // ── Launcher para elegir foto de la galería ──
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copiar la imagen al almacenamiento interno de la app
            val destFile = File(context.filesDir, "foto_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(it)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            localPhotoPath = destFile.absolutePath
        }
    }

    val estados = listOf("pendiente", "En progreso", "resuelta")
    var estadoExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Incidencia" else "Nueva Incidencia") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSelection()
                        onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Descripción (obligatoria)
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción *") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                isError = descripcion.isBlank() && titulo.isNotBlank(),
                supportingText = {
                    if (descripcion.isBlank() && titulo.isNotBlank()) {
                        Text("La descripción es obligatoria")
                    }
                }
            )

            // ── Foto ──
            Text(
                "Foto (opcional)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (photoPreview != null) {
                // Preview de la foto seleccionada
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoPreview)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Foto de la incidencia",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Botón para quitar la foto
                    IconButton(
                        onClick = { localPhotoPath = null },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Quitar foto",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                // Placeholder para seleccionar foto
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Toca para seleccionar una foto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Botón para cambiar foto si ya hay una
            if (photoPreview != null) {
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cambiar foto")
                }
            }

            // Estado (solo en modo edición)
            if (isEditing) {
                ExposedDropdownMenuBox(
                    expanded = estadoExpanded,
                    onExpandedChange = { estadoExpanded = !estadoExpanded }
                ) {
                    OutlinedTextField(
                        value = estado.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estado") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = estadoExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = estadoExpanded,
                        onDismissRequest = { estadoExpanded = false }
                    ) {
                        estados.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    estado = opcion
                                    estadoExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val hasFinePermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCoarsePermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasFinePermission || hasCoarsePermission) {
                        fillCurrentLocation()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Usar ubicación actual")
            }

            // Coordenadas (opcionales)
            Text(
                "Ubicación (opcional)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = latitudText,
                    onValueChange = { latitudText = it },
                    label = { Text("Latitud") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = longitudText,
                    onValueChange = { longitudText = it },
                    label = { Text("Longitud") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = googleMapsUrlText,
                onValueChange = { googleMapsUrlText = it },
                label = { Text("Google Maps URL (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (latitudText.isNotBlank() && longitudText.isNotBlank() && googleMapsUrlText.isBlank()) {
                OutlinedButton(
                    onClick = {
                        googleMapsUrlText = "https://maps.google.com/maps?q=$latitudText,$longitudText"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Usar coordenadas para crear url")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón guardar
            Button(
                onClick = {
                    val lat = latitudText.toDoubleOrNull()
                    val lng = longitudText.toDoubleOrNull()
                    val url = googleMapsUrlText.ifBlank { null }

                    if (isEditing) {
                        viewModel.updateIncidencia(titulo, descripcion, estado, localPhotoPath, url)
                    } else {
                        viewModel.createIncidencia(titulo, descripcion, lat, lng, localPhotoPath, url)
                    }
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = titulo.isNotBlank() && descripcion.isNotBlank()
            ) {
                Text(if (isEditing) "Guardar Cambios" else "Crear Incidencia")
            }
        }
    }
}
