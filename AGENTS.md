# Seina Chan — Development Guide

**Seina Chan** is a native Android client for [Hermes Agent](https://github.com/NousResearch/hermes-agent). It talks to Hermes Gateway via WebSocket JSON-RPC and reimplements the Claude.com design system (cream/coral/dark-navy) in Jetpack Compose + Material3.

---

## Project Structure

```
SeinaChan/
├── apps/android/               # Android app — the main deliverable
│   └── gradle/libs.versions.toml  # Version catalog (single source of truth)
│
├── hermes-agent/               # Git submodule (gitignored, NOT part of build)
├── .trae/                      # Planning specs from Trae IDE (gitignored)
├── DESIGN.md                   # Claude.com design system reference (gitignored)
└── test_hermes_api.py          # Local Hermes API test script (gitignored)
```

**The root has NO build system.** All development happens in `apps/android/`. `hermes-agent/` is a submodule included for documentation/API reference; it is gitignored from the root and has its own `AGENTS.md` inside.

---

## Build & Run

```bash
# All commands run from apps/android/
cd apps/android

# Build debug APK
./gradlew assembleDebug

# Run on connected device/emulator
./gradlew installDebug

# Check Kotlin/Compose compiler diagnostics
./gradlew lint
```

- **compileSdk = 35**, **minSdk = 26**, **targetSdk = 35**
- **Kotlin 2.0.21**, **AGP 8.7.3**, **Compose BOM 2024.12.01**
- Uses official `google()` and `mavenCentral()` Maven repositories. Aliyun mirrors are conditionally enabled only when `CI` env var is absent (local builds in China); GitHub Actions sets `CI=true` so CI uses only official sources (Aliyun returns 502 on overseas runners).
- `org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8` in `gradle.properties` — Gradle daemon needs sufficient heap.
- `org.gradle.parallel=true` and `org.gradle.caching=true` are enabled.
- `kotlin.code.style=official` (no wildcard imports in official style).

---

## Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| UI | Jetpack Compose + Material3 | `material3` from BOM; adaptive layout + pull-to-refresh |
| Architecture | MVI + ViewModel + Repository | Unidirectional data flow |
| DI | Hilt | `hilt-android:2.54` + `hilt-navigation-compose:1.2.0`; uses **kapt**, not KSP |
| Network | Ktor Client 3.0.3 | CIO engine (pure Kotlin), WebSocket support |
| Serialization | Kotlinx Serialization 1.7.3 | `kotlinx.serialization.json` |
| Async | Kotlin Coroutines + Flow | StateFlow for UI state, SharedFlow for events |
| Image loading | Coil 2.6.0 | Compose-native |
| Local persistence | DataStore Preferences + Room (SQLite) | DataStore for settings/config; Room for messages (`MessageEntity`, `SentImageEntity`) |
| Navigation | Navigation Compose 2.8.5 | Three routes: `connect`, `chat`, `settings` |

---

## Architecture & Key Conventions

### Package Layout (`apps/android/app/src/main/java/com/seina/chan/`)

```
SeinaChanApplication.kt      # @HiltAndroidApp — initializes FileLogger + UncaughtExceptionHandler
MainActivity.kt              # @AndroidEntryPoint — binds HermesConnectionService, applies theme
di/
  AppModule.kt               # Hilt module — provides HttpClient, DataStore, HermesWsClient, AppDatabase, all repositories
data/
  remote/
    HermesWsClient.kt         # WebSocket JSON-RPC client (ConnectionState + SharedFlow of GatewayEvent)
    HermesApiService.kt       # REST API: sessions, messages, status, model info
    HermesMethods.kt          # JSON-RPC method constants + HermesEventTypes object (message.delta, tool.*, approval.*, etc.)
    GatewayEvent.kt           # Data classes for all JSON-RPC event payloads
    JsonRpcProtocol.kt        # Generic JSON-RPC request/response/event data classes
  local/
    AppDatabase.kt            # Room database — version 3, with MIGRATION_1_2 and MIGRATION_2_3
    dao/
      MessageDao.kt           # CRUD for messages table (upsert, query by sessionId, delete)
      SentImageDao.kt         # Lookup local URI from server path for sent images
    entity/
      MessageEntity.kt        # Messages: id, sessionId, role, content, reasoningText, parentId, toolCallsJson, etc.
      SentImageEntity.kt      # serverPath → localUri mapping for image-attach tracking
  repository/
    ConnectionRepository.kt   # Connection URL, token, state management
    SessionRepository.kt      # Session CRUD + upload sent images
    ChatRepository.kt         # Send/receive messages, streaming, interactive events; owns MutableStateFlow<List<ChatMessage>>
    SettingsRepository.kt     # DataStore-backed: theme, pagination, tool display, connection config, hidden tools, custom tools
  model/
    Session.kt
    ChatMessage.kt
    ConnectionConfig.kt
    ConnectionProfile.kt
service/
  HermesConnectionService.kt  # Foreground service — keeps WebSocket alive in background
ui/
  theme/                      # DESIGN.md token system: Color.kt, Type.kt, Shape.kt, Spacing.kt, Theme.kt
  components/                 # Reusable: SeinaButton, SeinaTextField, SnackbarHost, MessageBubble, Composer, ToolCallCard, MarkdownText, ConnectionStatusBar, VerticalScrollbar, GlobalEventHandler, dialogs, etc.
  screens/
    connect/                  # ConnectScreen — enter Hermes URL + token
    chat/                     # ChatScreen — transcript + tool calls + typing indicator
    sessions/                 # SessionListScreen (Drawer in ChatScreen) — session list + delete; SessionListItem is a separate file
    settings/                 # SettingsScreen — theme, tools display, pagination, connection config, hidden/custom tools
  navigation/
    SeinaNavHost.kt           # Three routes: connect / chat / settings; phone-only (no split pane)
util/
  FileLogger.kt               # Custom file-based logger (NOT Android Logcat)
  NetworkMonitor.kt           # ConnectivityManager-based StateFlow<Boolean> for internet availability
  UncaughtExceptionHandler.kt # Logs fatal crashes to FileLogger then delegates to default handler
```

### MVI Flow

```
UI Event → ViewModel.handleAction() → Repository → Ktor WS/HTTP → Hermes Gateway
                                                                ↓
UI ← StateFlow<UiState> ← ViewModel ← Repository ← Flow<GatewayEvent>
```

- Each screen has its own `ViewModel` + `UiState` data class.
- `ChatViewModel` is the most complex — it manages message streaming, tool calls, approval/clarify/secret dialogs, session lifecycle.
- `ChatRepository` owns a `MutableStateFlow<List<ChatMessage>>` that the ViewModel collects.

### WebSocket Client (`HermesWsClient`)

- Wraps Ktor WebSocket session with JSON-RPC protocol.
- Exposes `state: StateFlow<ConnectionState>` (Idle → Connecting → Open → Closed | Error).
- Exposes `events: SharedFlow<GatewayEvent>` for incoming server events.
- `request(method, params)` sends JSON-RPC request and returns a `CompletableDeferred` result.
- Exponential backoff reconnect (max 5 attempts) triggered when `shouldReconnect` is true.
- Uses `ConcurrentHashMap` for pending request tracking by numeric `reqId`.

### Foreground Service

- `HermesConnectionService` keeps the WebSocket alive when the app is backgrounded.
- `MainActivity` binds to it via `ServiceConnection` to share the same connection.
- Notification shown when in background, removed when app returns to foreground.
- Actions sent via `startService()` intents: `ACTION_APP_FOREGROUND`, `ACTION_APP_BACKGROUND`, `ACTION_ENSURE_CONNECTION`.
- Permissions required: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### Room Persistence

- `AppDatabase` (version 3) has two tables:
  - **`messages`**: `MessageEntity` with id, sessionId, role, content, reasoningText, parentId, isReasoning, imageUrl, toolCallsJson, systemEventsJson, isStreaming, createdAt, updatedAt.
  - **`sent_images`**: `SentImageEntity` mapping `serverPath → localUri` for image-attach dedup.
- `ChatRepository` uses `MessageDao` for persisting and restoring chat history.
- `SessionRepository` uses `SentImageDao` for re-attaching previously sent images.
- Migration v1→v2 creates the `messages` table; v2→v3 adds `parentId` column. Always add migrations rather than `fallbackToDestructiveMigration()`.

### SettingsRepository (DataStore)

DataStore preference file: `seina_chan_prefs`. Keys include:
- `pageSize` (int, default 20), `showToolCalls` / `showReasoning` / `showTimestamps` / `autoExpandReasoning` / `autoExpandTools` (bool)
- `themeMode` (string: "system" / "light" / "dark")
- `ip`, `port`, `token` (strings — connection config)
- `hidden_tool_names`, `custom_tools` (string sets — format `"category|tool_name"` for custom tools)
- `connection_profiles` (JSON-serialized `List<ConnectionProfile>` — multi-profile support via `ConnectionProfile.kt` model)

---

## Design System

Token system in `ui/theme/` maps `DESIGN.md` to Compose:

- **Color.kt**: Light + dark color schemes. Key tokens: `Primary` (#CC785C coral), `Canvas` (#FAF9F5 cream), `SurfaceDark` (#181715 navy), `Ink` (#141413 warm black).
- **Type.kt**: Cormorant Garamond (serif display) + Inter (sans body) via Google Fonts. Serif at weight 400 with negative letter-spacing for headlines.
- **Shape.kt**: `xs=4dp, sm=6dp, md=8dp, lg=12dp, xl=16dp, pill=50%`.
- **Spacing.kt**: `xxs=4dp, xs=8dp, sm=12dp, md=16dp, lg=24dp, xl=32dp, xxl=48dp, section=96dp`.
- **Theme.kt**: `SeinaChanTheme` — wraps `MaterialTheme` with `LightColorScheme`/`DarkColorScheme` + custom typography + shapes.

**Important**: The design is deliberately warm (cream + coral), not cool-blue. The serif display font is the brand voice — do not replace with sans.

---

## Common Pitfalls

- **DO NOT run Gradle from root.** There's no `settings.gradle.kts` or `build.gradle.kts` at root. Always `cd apps/android` first.
- **Version catalog is the single source of truth.** `libs.versions.toml` pins every dependency. Do NOT hardcode versions in `app/build.gradle.kts`.
- **All Ktor artifacts use the same `3.0.3` version** from the catalog. Do not mix Ktor versions.
- **Kotlin serialization plugin** (`kotlin.plugin.serialization`) must be applied alongside `kotlinx-serialization-json` library.
- **Hilt uses `kapt`** (not KSP) in this project. The `kotlin("kapt")` plugin is applied in `app/build.gradle.kts`.
- **Room also uses `kapt`** (`androidx.room:room-compiler`). This is already configured — do not switch to KSP.
- **Foreground service** is required for reliable WebSocket persistence. Without it, Android kills the connection when the app is backgrounded.
- **`FileLogger`** is the custom logger — not Logcat. Check `util/FileLogger.kt` for output location. It writes to the app's internal storage. `UncaughtExceptionHandler` also logs crashes there before crashing.
- **No root-level pyproject.toml or package.json.** Python files in the root (`test_hermes_api.py`) are standalone test scripts for local Hermes API debugging.
- **`hermes-agent/` is a git submodule** with its own remote. Do not edit it as part of Seina Chan development — reference it for the Gateway protocol.
- **No network_security_config.xml hardening**: the `@xml/network_security_config` allows cleartext HTTP for local/LAN Hermes instances (`ws://` URLs). Do not ship this to production without review.
- **No split-pane / tablet layout**: The app uses simple NavHost phone navigation. `androidx-adaptive-*` dependencies are declared in `build.gradle.kts` but not imported in any source file — the app is phone-only. Do not add split-pane layouts without explicit request.

---

## Git Conventions

- Single branch (`master`), no remotes configured.
- Commit messages in Chinese using `<type>(<module>): <描述>` format (e.g. `feat(theme): 添加深色模式支持`).
- Types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`.
- Do NOT commit without explicit user request. No automatic `git push`.
- Only `master` branch exists. No release branches, no tags.

---

## Testing

```bash
# From apps/android/
./gradlew test                            # Unit tests
./gradlew connectedAndroidTest            # Instrumentation tests (requires device/emulator)
```

- Unit tests use JUnit 4.13.2.
- Instrumentation tests use Espresso 3.6.1.
- **No test source directories exist** (`src/test/`, `src/androidTest/` are missing). The Gradle tasks exist but will produce "no tests" results.
- No changes to `hermes-agent/` — its test suite (`scripts/run_tests.sh`) is separate.
