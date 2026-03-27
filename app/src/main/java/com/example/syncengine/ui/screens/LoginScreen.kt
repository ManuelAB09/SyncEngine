@file:OptIn(
    kotlinx.serialization.InternalSerializationApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class
)

package com.example.syncengine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.syncengine.data.remote.SupabaseNetwork
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch

/**
 * Pantalla de Login / Registro con email y contraseña.
 * Usa Supabase Auth (GoTrue) directamente.
 */
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Mostrar mensajes de éxito en snackbar
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            successMessage = null
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título
            Text(
                text = "🔄 SyncEngine",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Gestión de incidencias offline-first",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Mensaje de error visible ──
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it.trim()
                    errorMessage = null // Limpiar error al escribir
                },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isLoading,
                isError = errorMessage != null
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Contraseña
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading,
                isError = errorMessage != null
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Botón Iniciar Sesión
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            SupabaseNetwork.client.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMessage = parseAuthError(e, isLogin = true)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && password.length >= 6 && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Iniciar Sesión")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón Registrarse
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            SupabaseNetwork.client.auth.signUpWith(Email) {
                                this.email = email
                                this.password = password
                            }
                            successMessage = "✅ Cuenta creada. Ya puedes iniciar sesión."
                        } catch (e: Exception) {
                            errorMessage = parseAuthError(e, isLogin = false)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && password.length >= 6 && !isLoading
            ) {
                Text("Crear Cuenta")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "La contraseña debe tener mínimo 6 caracteres",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Traduce los errores técnicos de Supabase Auth a mensajes legibles en español.
 */
private fun parseAuthError(e: Exception, isLogin: Boolean): String {
    val raw = e.message?.lowercase() ?: ""
    return when {
        // Credenciales incorrectas
        "invalid login credentials" in raw ||
        "invalid_credentials" in raw ->
            "❌ Email o contraseña incorrectos. Comprueba tus datos."

        // Usuario no encontrado
        "user not found" in raw ->
            "❌ No existe una cuenta con ese email. ¿Quieres crear una?"

        // Email ya registrado
        "already registered" in raw ||
        "user already registered" in raw ->
            "❌ Ya existe una cuenta con ese email. Prueba a iniciar sesión."

        // Email inválido
        "invalid email" in raw ||
        "unable to validate email" in raw ->
            "❌ El formato del email no es válido."

        // Contraseña débil
        "password" in raw && ("weak" in raw || "short" in raw || "length" in raw) ->
            "❌ La contraseña es demasiado corta. Usa al menos 6 caracteres."

        // Demasiados intentos
        "rate limit" in raw || "too many requests" in raw ->
            "⏳ Demasiados intentos. Espera un momento antes de volver a intentarlo."

        // Email no confirmado
        "email not confirmed" in raw ->
            "📧 Debes confirmar tu email antes de iniciar sesión. Revisa tu bandeja de entrada."

        // Sin conexión a internet
        "unable to resolve host" in raw ||
        "network" in raw ||
        "timeout" in raw ||
        "connect" in raw ->
            "📡 Sin conexión a internet. Comprueba tu conexión e inténtalo de nuevo."

        // Error genérico con contexto
        else -> if (isLogin) {
            "❌ No se pudo iniciar sesión. Comprueba tus credenciales."
        } else {
            "❌ No se pudo crear la cuenta. Inténtalo de nuevo."
        }
    }
}
