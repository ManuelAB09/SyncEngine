# SyncEngine

Aplicación Android con **sincronización offline-first** entre una base de datos local (Room) y un backend remoto (Supabase). Utiliza Jetpack Compose para la UI y WorkManager para la sincronización en segundo plano.

---

## 📋 Tabla de contenidos

- [Arquitectura](#-arquitectura)
- [Tech Stack](#-tech-stack)
- [Requisitos previos](#-requisitos-previos)
- [Instalación y configuración](#-instalación-y-configuración)
- [Estructura del proyecto](#-estructura-del-proyecto)
- [Dependencias](#-dependencias)
- [Licencia](#-licencia)

---

## 🏗 Arquitectura

El proyecto sigue una arquitectura por capas:

```
ui/              → Screens (Compose) + ViewModels + Navigation
data/
  ├── local/     → Room DB (DAO, Entities)
  ├── remote/    → Supabase Client + DTOs
  └── repository/→ Repositorio que orquesta local ↔ remoto
sync/            → SyncEngine + SyncWorker (WorkManager)
util/            → Utilidades (fechas, etc.)
```

---

## 🛠 Tech Stack

| Categoría | Tecnología | Versión |
|---|---|---|
| **Lenguaje** | Kotlin | 2.1.0 |
| **UI** | Jetpack Compose (BOM) | 2024.09.00 |
| **Base de datos local** | Room | 2.7.0 |
| **Backend** | Supabase (Postgrest, GoTrue, Storage) | 2.3.0 |
| **HTTP Client** | Ktor | 2.3.9 |
| **Carga de imágenes** | Coil | 2.6.0 |
| **Sincronización** | WorkManager | 2.9.0 |
| **Navegación** | Navigation Compose | 2.7.7 |
| **Serialización** | Kotlinx Serialization JSON | 1.6.3 |
| **Build** | AGP | 9.1.0 |
| **Procesador de anotaciones** | KSP | 2.1.0-1.0.29 |
| **Gradle** | Gradle Wrapper | 9.3.1 |
| **Min SDK** | Android | 24 (Android 7.0) |
| **Target / Compile SDK** | Android | 35 |

---

## ✅ Requisitos previos

- **Android Studio** Meerkat (2024.3+) o superior (compatible con AGP 9.x)
- **JDK 21** (configurado automáticamente vía Gradle Toolchains)
- **Android SDK 35** instalado
- Cuenta en [Supabase](https://supabase.com/) con un proyecto configurado

---

## 🚀 Instalación y configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/<tu-usuario>/SyncEngine.git
cd SyncEngine
```

### 2. Configurar Supabase

Añade tus credenciales de Supabase al archivo `local.properties` (en la raíz del proyecto):

```properties
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_KEY=tu_anon_key_aqui
```

Estas claves se inyectan automáticamente en `BuildConfig` durante la compilación. El archivo `local.properties` **nunca se sube a git** (está en `.gitignore`).

### 3. Abrir en Android Studio

1. Abre Android Studio
2. **File → Open** y selecciona la carpeta `SyncEngine/`
3. Espera a que Gradle sincronice todas las dependencias
4. Conecta un dispositivo o inicia un emulador con **API 24+**
5. Ejecuta la app con **Run ▶**

### 4. Build desde terminal (opcional)

```bash
# En Windows
.\gradlew.bat assembleDebug

# En macOS / Linux
./gradlew assembleDebug
```

---

## 📁 Estructura del proyecto

```
SyncEngine/
├── app/
│   └── src/main/java/com/example/syncengine/
│       ├── MainActivity.kt                    # Activity principal (Compose)
│       ├── SyncEngineApp.kt                   # Application class
│       ├── data/
│       │   ├── local/
│       │   │   ├── AppDatabase.kt             # Room Database
│       │   │   ├── IncidenciaDao.kt           # DAO de incidencias
│       │   │   ├── IncidenciaEntity.kt        # Entidad Room
│       │   │   └── PendingConflictEntity.kt   # Entidad para conflictos
│       │   ├── remote/
│       │   │   ├── IncidenciaDto.kt           # DTO para Supabase
│       │   │   └── SupabaseClient.kt          # Configuración de Supabase
│       │   └── repository/
│       │       └── IncidenciaRepository.kt    # Repositorio (local + remoto)
│       ├── sync/
│       │   ├── SyncEngine.kt                  # Lógica de sincronización
│       │   └── SyncWorker.kt                  # Worker para sync en background
│       ├── ui/
│       │   ├── navigation/
│       │   │   └── AppNavigation.kt           # Grafo de navegación
│       │   ├── screens/
│       │   │   ├── ConflictScreen.kt          # Pantalla de conflictos
│       │   │   ├── FormScreen.kt              # Formulario de incidencias
│       │   │   ├── ListScreen.kt              # Listado de incidencias
│       │   │   └── LoginScreen.kt             # Pantalla de login
│       │   ├── theme/                          # Material Theme
│       │   └── viewmodel/
│       │       └── IncidenciaViewModel.kt     # ViewModel principal
│       └── util/
│           └── DateUtils.kt                   # Utilidades de fechas
├── gradle/
│   ├── libs.versions.toml                     # 📌 Catálogo de versiones centralizado
│   └── wrapper/
├── build.gradle.kts                           # Build raíz
├── settings.gradle.kts                        # Configuración de módulos
├── gradle.properties                          # Propiedades de Gradle
└── .gitignore
```

---

## 📦 Dependencias

Todas las versiones están centralizadas en [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

### Plugins de Gradle

| Plugin | ID | Versión | Nota |
|---|---|---|---|
| Android Application | `com.android.application` | 9.1.0 | |
| Kotlin Android | `org.jetbrains.kotlin.android` | 2.1.0 | Solo en root (AGP 9.x lo integra) |
| Kotlin Compose | `org.jetbrains.kotlin.plugin.compose` | 2.1.0 | |
| Kotlin Serialization | `org.jetbrains.kotlin.plugin.serialization` | 2.1.0 | |
| KSP | `com.google.devtools.ksp` | 2.1.0-1.0.29 | |

### Librerías

| Librería | Versión | Uso |
|---|---|---|
| `androidx.core:core-ktx` | 1.10.1 | Extensiones Kotlin para Android |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.6.1 | Lifecycle-aware components |
| `androidx.activity:activity-compose` | 1.8.0 | Activity para Compose |
| `androidx.compose:compose-bom` | 2024.09.00 | BOM de Compose (UI, Material3, Tooling) |
| `androidx.room:room-*` | 2.7.0 | Base de datos local SQLite |
| `io.github.jan-tennert.supabase:bom` | 2.3.0 | Backend-as-a-Service |
| `io.coil-kt:coil-compose` | 2.6.0 | Carga de imágenes |
| `io.ktor:ktor-client-android` | 2.3.9 | Cliente HTTP |
| `androidx.work:work-runtime-ktx` | 2.9.0 | Tareas en segundo plano |
| `androidx.navigation:navigation-compose` | 2.7.7 | Navegación entre pantallas |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.6.1 | ViewModel en Compose |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.6.3 | Serialización JSON |

### Testing

| Librería | Versión |
|---|---|
| `junit:junit` | 4.13.2 |
| `androidx.test.ext:junit` | 1.1.5 |
| `androidx.test.espresso:espresso-core` | 3.5.1 |
| `androidx.compose.ui:ui-test-junit4` | (BOM) |

---

## 📝 Notas

- El proyecto usa **Gradle Version Catalog** (`libs.versions.toml`) para gestionar todas las versiones de forma centralizada.
- La sincronización se ejecuta periódicamente con **WorkManager** y maneja conflictos offline/online.
- Se requieren permisos de `INTERNET` y `ACCESS_NETWORK_STATE` (ya declarados en `AndroidManifest.xml`).

---

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Consulta el archivo `LICENSE` para más detalles.

