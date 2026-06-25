# Repository Guidelines

## Project Overview

Seina Chan is a native Android chat client for [Hermes Agent](https://github.com/NousResearch/hermes-agent), communicating with the Hermes Gateway over **WebSocket JSON-RPC 2.0**. The app reimplements the Claude.com design system (cream/coral/dark-navy palette) in Jetpack Compose + Material3.

The project lives in `apps/android/` — the root directory has no build system. `hermes-agent/` is a git submodule included for protocol reference only (gitignored from the root).

---

## Architecture & Data Flow

**MVI (Model-View-Intent)** with strict unidirectional data flow:

```
Compose UI  ──action──>  @HiltViewModel  ──delegate──>  Repository  ──RPC──>  Hermes Gateway
     ▲                       │                                 │
     │                  StateFlow<UiState>                Local (Room/DataStore)
     │                       │                                 │
     └──collectAsStateWithLifecycle──<──StateFlow/SharedFlow──<──┘
```

### Layer Breakdown

| Layer | Location | Role |
|-------|----------|------|
| **UI** | `ui/screens/` | Compose screens per route; observe `StateFlow<UiState>`, call ViewModel methods directly |
| **ViewModel** | `ui/screens/*/` | `@HiltViewModel`; owns `MutableStateFlow<UiState>` + `MutableSharedFlow` for one-shot events |
| **Repository** | `data/repository/` | Single source of truth coordinator; bridges remote (WS) and local (Room, DataStore) |
| **Remote** | `data/remote/` | `HermesWsClient` singleton — Ktor CIO WebSocket, 5-state machine, auto-reconnect, JSON-RPC |
| **Local** | `data/local/` | Room DB (3 entities, 3 migrations) + DataStore Preferences for settings |
| **DI** | `di/AppModule.kt` | Hilt `@Singleton` providers for all singletons |
| **Service** | `service/` | Foreground service with `PARTIAL_WAKE_LOCK` to keep WebSocket alive in background |

### WebSocket Client (`HermesWsClient`)

- **State machine**: `Idle → Connecting → Open → Closed | Error`
- **Reconnect**: Exponential backoff (1s→2s→4s→…→5min cap, 20 attempts max)
- **Heartbeat**: Watchdog checks `lastFrameTime` every 15s; timeout 180s (normal) / 600s (long-running e.g. file upload)
- **JSON-RPC**: Request/response via `ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>`; per-method timeouts (15s session.list, 300s prompt.submit)
- **Events**: `SharedFlow<GatewayEvent>` (256 buffer); polymorphic deserialization via `type` field → `GatewayEventSerializer`
- **Network-aware**: `NetworkMonitor` debounced flow → cancel reconnect on loss, immediate retry on recovery

### Session Lifecycle

```
ConnectScreen → ensureSession() → session.create (new) or session.resume (existing)
ChatScreen → sendMessage → prompt.submit → streaming events → message.complete
           → stopGenerating → session.interrupt
           → branchFromMessage → message.complete(branch_at=msgId)
```

Auth: TOKEN mode (`?token=` in WS URL) or OAUTH mode (`password-login → mint-ws-ticket → WS`).

### Event Streaming Flow

1. `HermesWsClient` parses WS frame → JSON-RPC `{method:"event", params:{type, payload}}`
2. Reshapes params, deserializes via `GatewayEventSerializer` → sealed `GatewayEvent` subclass
3. Emits to `SharedFlow<GatewayEvent>`
4. `ChatRepository` collects: `MessageStart` → create streaming message, `MessageDelta` → append content, `ToolStart/Progress/Complete` → track tool calls, `ApprovalRequest`/etc → set dialog state
5. `ChatViewModel` collects repository flows → `ChatUiState` → UI recomposes

---

## Key Directories

```
apps/android/                              # All development happens here
├── app/src/main/java/com/seina/chan/
│   ├── SeinaChanApplication.kt            # @HiltAndroidApp, FileLogger init
│   ├── MainActivity.kt                    # @AndroidEntryPoint, binds service, theme
│   ├── di/AppModule.kt                    # Hilt @Singleton providers
│   ├── data/
│   │   ├── remote/                        # HermesWsClient, GatewayEvent, JsonRpcProtocol, HermesMethods
│   │   ├── local/                         # AppDatabase, DAOs, entities
│   │   ├── repository/                    # ChatRepository, SessionRepository, ConnectionRepository, AuthRepository, SettingsRepository
│   │   └── model/                         # ChatMessage, Session, ConnectionConfig, ConnectionProfile, ThemeConfig
│   ├── service/                           # HermesConnectionService (foreground), ServiceSessionTracker
│   ├── ui/
│   │   ├── theme/                         # Color.kt, Type.kt, Shape.kt, Spacing.kt, Theme.kt
│   │   ├── components/                    # SeinaButton, SeinaTextField, MarkdownText, ConnectionStatusBar, etc.
│   │   ├── screens/connect/               # ConnectScreen + ConnectViewModel
│   │   ├── screens/chat/                  # ChatScreen + ChatViewModel + ChatUiState
│   │   ├── screens/sessions/              # SessionListScreen + SessionListViewModel
│   │   ├── screens/settings/              # SettingsScreen + SettingsViewModel
│   │   └── navigation/SeinaNavHost.kt     # 3-route NavHost
│   ├── util/
│   │   ├── FileLogger.kt                  # Async file logger with rotation
│   │   ├── NetworkMonitor.kt              # ConnectivityManager StateFlow
│   │   └── UncaughtExceptionHandler.kt    # Crash logger
│   └── AppForegroundTracker.kt            # MutableStateFlow<Boolean> foreground tracker
├── gradle/libs.versions.toml              # Single source of truth for all versions
├── app/build.gradle.kts                   # App module config
├── build.gradle.kts                       # Root (plugin declarations only)
├── settings.gradle.kts                    # Aliyun mirror logic, single :app module
└── gradle.properties                      # JVM args, parallel, caching
```

---

## Development Commands

```bash
# Always run from apps/android/
cd apps/android

# Build
./gradlew assembleDebug

# Install on device/emulator
./gradlew installDebug

# Lint
./gradlew lint

# Unit tests
./gradlew test

# Instrumentation tests (requires device)
./gradlew connectedAndroidTest

# Check dependencies
./gradlew app:dependencies
```

**SDK targets**: `compileSdk=35`, `minSdk=26`, `targetSdk=35`

---

## Code Conventions & Common Patterns

### Naming & Style
- **Kotlin code style**: `official` (no wildcard imports)
- **Compose composables**: PascalCase, return `Unit`, use `@Composable` annotation
- **ViewModels**: Suffix with `ViewModel`; state class suffix with `UiState`; `@HiltViewModel` + `@Inject constructor`
- **Repositories**: Suffix with `Repository`, Hilt `@Singleton`, `@Inject constructor`
- **DAOs**: Suffix with `Dao`; entities suffix with `Entity`
- **Package**: All under `com.seina.chan`; no multi-module split

### MVI Pattern

```kotlin
// ViewModel
@HiltViewModel
class FooViewModel @Inject constructor(
    private val fooRepository: FooRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FooUiState())
    val uiState: StateFlow<FooUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FooEvent>()
    val events: SharedFlow<FooEvent> = _events.asSharedFlow()

    fun onAction(param: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                fooRepository.doThing(param)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Action failed", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

// Screen
@Composable
fun FooScreen(viewModel: FooViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Observe events: LaunchedEffect / viewModel.events.collect { ... }
}
```

### Asynchronous Patterns
- **Coroutine scopes**: `viewModelScope` for ViewModels, `SupervisorJob() + Dispatchers.IO` for `HermesWsClient`, `SupervisorJob() + Dispatchers.Default` for `ChatRepository`
- **Serialized writes**: `Mutex.withLock` in `ChatRepository` to prevent Room write races
- **Flows**: `StateFlow` for persistent UI state, `SharedFlow` for one-shot events; `combine()/stateIn()` for derived state; `collectAsStateWithLifecycle()` in Compose
- **RPC responses**: `CompletableDeferred` with timeout for request/response matching

### Error Handling
- All coroutine RPC calls wrapped in `try/catch` → log via `FileLogger`, update UI state
- `ConnectionRepository.connect()` and `testConnection()` return `Result<T>`
- `ConnectUiState.testStatus: TestStatus` enum (None/Testing/Success/Error) for typed status
- `ChatUiState.error: String?` for transient errors
- Heartbeat watchdog force-closes stale connections → reconnect engine takes over

### Persistence
- **Room**: Always add migrations (never `fallbackToDestructiveMigration()`); current version 4
- **DataStore**: Typed preferences via `SettingsRepository`; keys in `SettingsKeys.kt`
- **Mutex serialization**: DB writes in ChatRepository use `dbMutex.withLock`

### Dependency Injection (Hilt)
- All singletons in `AppModule.kt` via `@Provides @Singleton`
- Two named `HttpClient` beans: `@Named("ws")` (with `WebSockets` + long socket timeout), `@Named("api")` (with cookie storage for auth)
- Uses **kapt** for Hilt and Room annotation processors — NOT KSP

---

## Important Files

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | App config: SDK versions, signing, dependencies |
| `gradle/libs.versions.toml` | Version catalog — all dependency versions |
| `di/AppModule.kt` | Hilt DI wiring for all singletons |
| `data/remote/HermesWsClient.kt` | WebSocket client core — state machine, reconnect, JSON-RPC |
| `data/remote/GatewayEvent.kt` | Sealed class hierarchy of all gateway events + serializer |
| `data/remote/HermesMethods.kt` | All JSON-RPC method names and event type constants |
| `data/repository/ChatRepository.kt` | Central chat logic: streaming, tool calls, persistence |
| `data/repository/ConnectionRepository.kt` | Auth-aware connect, config persistence |
| `data/repository/SettingsRepository.kt` | All DataStore-backed settings |
| `data/local/AppDatabase.kt` | Room DB with all entities, DAOs, and migrations |
| `ui/theme/Theme.kt` | Compose theme entry point |
| `ui/screens/chat/ChatViewModel.kt` | Main ViewModel: session lifecycle, messaging, streaming |
| `ui/screens/chat/ChatScreen.kt` | Chat UI with drawer, dialogs, message list |
| `ui/components/MarkdownText.kt` | Custom Markdown renderer (~715 lines) |
| `service/HermesConnectionService.kt` | Foreground service with WakeLock |
| `MainActivity.kt` | Entry activity: binds service, manages theme, lifecycle intents |
| `util/FileLogger.kt` | Async file logger with rotation |

---

## Runtime/Tooling Preferences

- **Language**: Kotlin 2.0.21, Java 17 source/target
- **Build system**: Gradle 8.9, AGP 8.7.3
- **Compose compiler**: Kotlin Compose plugin (Compose BOM 2024.12.01)
- **Package manager**: Gradle version catalog (`libs.versions.toml`) — never hardcode versions in build files
- **DI**: Hilt 2.54 with kapt (NOT KSP)
- **Persistence**: Room 2.6.1 with kapt
- **Network**: Ktor 3.0.3 (CIO engine) — all Ktor artifacts at same version
- **Image loading**: Coil 2.6.0
- **Navigation**: Navigation Compose 2.8.5
- **Serialization**: Kotlinx Serialization 1.7.3 (plugin applied alongside library)
- **Gradle daemon**: `-Xmx4096m`, parallel builds, caching enabled
- **Repositories**: Aliyun mirrors for non-CI builds (local China); CI uses official repos only
- **Signing**: CI reads from env vars; local from `keystore.properties`
- **Logging**: Custom `FileLogger` (file-based, rotated at 5MB, 7-day retention) — NOT Logcat

---

## Testing & QA

- **Unit tests**: JUnit 4.13.2 (`./gradlew test` from `apps/android/`)
- **Instrumentation tests**: Espresso 3.6.1 (`./gradlew connectedAndroidTest`)
- **Test runner**: `AndroidJUnitRunner`
- **No test source directories exist** — Gradle tasks exist but produce "no tests" results
- **Lint**: `./gradlew lint` for Kotlin/Compose diagnostics
- **ProGuard**: Release builds minified; custom rules keep serializers, Hilt/Dagger, Ktor, Room entities

### Common Pitfalls

- **DO NOT** run Gradle from root — always `cd apps/android` first
- **DO NOT** hardcode versions — always use the version catalog
- **DO NOT** mix Ktor versions — all at `3.0.3` from catalog
- **DO NOT** switch to KSP — Hilt and Room both use kapt
- **DO NOT** use `fallbackToDestructiveMigration()` — always add proper migrations
- **DO NOT** use Android Logcat — use `FileLogger` from `util/`
- **DO NOT** edit `hermes-agent/` submodule as part of Seina Chan development
- **DO NOT** add split-pane/tablet layouts — app is phone-only
- **DO NOT** commit without explicit request
