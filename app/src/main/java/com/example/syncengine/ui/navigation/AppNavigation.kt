@file:OptIn(
    kotlinx.serialization.InternalSerializationApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class
)

package com.example.syncengine.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.syncengine.data.remote.SupabaseNetwork
import com.example.syncengine.ui.screens.ConflictScreen
import com.example.syncengine.ui.screens.FormScreen
import com.example.syncengine.ui.screens.ListScreen
import com.example.syncengine.ui.screens.LoginScreen
import com.example.syncengine.ui.viewmodel.IncidenciaViewModel
import io.github.jan.supabase.gotrue.auth

/**
 * Rutas de navegación de la app.
 */
object Routes {
    const val LOGIN = "login"
    const val LIST = "list"
    const val FORM_NEW = "form/new"
    const val FORM_EDIT = "form/edit/{id}"
    const val CONFLICTS = "conflicts"

    fun formEdit(id: String) = "form/edit/$id"
}

/**
 * Grafo de navegación principal.
 * Arranca en LOGIN. Si ya hay sesión activa, salta automáticamente a LIST.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: IncidenciaViewModel = viewModel()

    // Si ya hay sesión activa, saltar el login
    LaunchedEffect(Unit) {
        val user = SupabaseNetwork.client.auth.currentUserOrNull()
        if (user != null) {
            navController.navigate(Routes.LIST) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {
        // Pantalla de login
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // Pantalla de lista
        composable(Routes.LIST) {
            ListScreen(
                viewModel = viewModel,
                onNavigateToForm = { navController.navigate(Routes.FORM_NEW) },
                onNavigateToEdit = { id -> navController.navigate(Routes.formEdit(id)) },
                onNavigateToConflicts = { navController.navigate(Routes.CONFLICTS) },
                onLogout = {
                    viewModel.logout {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Formulario: crear nueva
        composable(Routes.FORM_NEW) {
            FormScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Formulario: editar existente
        composable(Routes.FORM_EDIT) {
            FormScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Pantalla de conflictos
        composable(Routes.CONFLICTS) {
            ConflictScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

