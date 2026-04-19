# Deeplink

[![](https://jitpack.io/v/hoanganhtuan95ptit/Deeplink.svg)](https://jitpack.io/#hoanganhtuan95ptit/Deeplink)

An Android deeplink library that uses **KSP** to automatically register handlers at compile-time — no manual wiring, no runtime reflection.

---

## Features

- **Zero boilerplate** — just add `@Deeplink`, everything else is automatic.
- **Compile-time safe** — mistakes (wrong interface, abstract class) are caught at build time, not at runtime.
- **Lifecycle-aware** — deeplinks are only processed when the Activity/Fragment is in the foreground (`STARTED`), and automatically pause/resume with the lifecycle.
- **No lost deeplinks** — 20-item buffer ensures deeplinks sent before the UI is ready are still delivered.
- **Thread-safe** — `sendDeeplink` can be called from any thread.
- **Data passing** — send `extras` and `sharedElement` alongside the deeplink for transition animations.

---

## Installation

### 1. Add JitPack to `settings.gradle`

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. Add the KSP plugin to `libs.versions.toml`

```toml
[versions]
ksp = "2.0.21-1.0.25"

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### 3. Add dependencies to your module

```groovy
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)          // Required
}

dependencies {
    implementation 'com.github.hoanganhtuan95ptit.Deeplink:deeplink:1.0.0'
    ksp          'com.github.hoanganhtuan95ptit.Deeplink:deeplink-processor:1.0.0'
}
```

> **Note:** `deeplink` and `deeplink-processor` must use the **same version**.

### 4. Auto-initialization (nothing else needed)

The library uses **AndroidX App Startup** — it initializes automatically when the app starts without any manual call in `Application.onCreate()`.

---

## Usage

### Step 1 — Create a handler

Create a class that implements `DeeplinkHandler` and annotate it with `@Deeplink`:

```kotlin
@Deeplink
class BDeeplinkHandler : DeeplinkHandler {

    override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
        return deeplink == "app://b"
    }

    override suspend fun navigate(
        lifecycleOwner: LifecycleOwner,
        deeplink: String,
        extras: Map<String, Any?>?,
        sharedElement: Map<String, View>?
    ): Boolean {
        // TODO: open screen B
        return true
    }
}
```

That's it. KSP will automatically detect this class at build time and register it into the system.

### Step 2 — Send a deeplink

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...

        // Send a deeplink — the Activity/Fragment will receive it when ready
        sendDeeplink("app://b")
    }
}
```

---

## API

### Sending deeplinks

```kotlin
// URL only
sendDeeplink("app://home")

// With data that can't be encoded into the URL
sendDeeplink(
    deepLink = "app://profile",
    extras = mapOf("user" to userObject, "fromTab" to "search")
)

// With shared elements for transition animation
sendDeeplink(
    deepLink = "app://photo/123",
    extras = mapOf("photoId" to "123"),
    sharedElement = mapOf("photo" to photoImageView)
)
```

### DeeplinkHandler interface

| Member | Description |
|--------|-------------|
| `val deeplink: String` | The URL this handler processes. `canHandle()` compares against this by default (case-insensitive). |
| `val queueName: String` | Queue name for serializing deeplinks. Defaults to `"default_queue"`. |
| `fun canHandle(lifecycleOwner, deeplink): Boolean` | Returns `true` if this handler can process the given URL. Override for pattern matching. |
| `suspend fun navigate(lifecycleOwner, deeplink, extras, sharedElement): Boolean` | Performs the navigation. Returns `true` on success. |

---

## Advanced Usage

### Using the `deeplink` property (shorthand)

When a handler only processes one specific URL, declare it via the property instead of overriding `canHandle`:

```kotlin
@Deeplink
class HomeDeeplinkHandler : DeeplinkHandler {

    // No need to override canHandle() — comparison is automatic
    override val deeplink: String = "app://home"

    override suspend fun navigate(
        lifecycleOwner: LifecycleOwner,
        deeplink: String,
        extras: Map<String, Any?>?,
        sharedElement: Map<String, View>?
    ): Boolean {
        // open Home screen
        return true
    }
}
```

### Pattern matching (multiple URLs)

Override `canHandle` to handle a group of URLs:

```kotlin
@Deeplink
class ProductDeeplinkHandler : DeeplinkHandler {

    override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
        return deeplink.startsWith("app://product/")
    }

    override suspend fun navigate(
        lifecycleOwner: LifecycleOwner,
        deeplink: String,
        extras: Map<String, Any?>?,
        sharedElement: Map<String, View>?
    ): Boolean {
        val productId = Uri.parse(deeplink).lastPathSegment ?: return false
        // open Product screen with productId
        return true
    }
}
```

### Receiving `extras` and `sharedElement`

```kotlin
@Deeplink
class PhotoDeeplinkHandler : DeeplinkHandler {

    override val deeplink: String = "app://photo"

    override suspend fun navigate(
        lifecycleOwner: LifecycleOwner,
        deeplink: String,
        extras: Map<String, Any?>?,
        sharedElement: Map<String, View>?
    ): Boolean {
        val photoId = extras?.get("photoId") as? String ?: return false
        val photoView = sharedElement?.get("photo")

        if (lifecycleOwner is FragmentActivity) {
            // open PhotoFragment with shared element transition
        }
        return true
    }
}
```

### Custom queue (avoid blocking other deeplinks)

By default, all deeplinks share `"default_queue"` and are processed sequentially. If your handler takes a long time (API calls, etc.), use a dedicated queue so it doesn't block other handlers:

```kotlin
@Deeplink
class PaymentDeeplinkHandler : DeeplinkHandler {

    override val deeplink: String = "app://payment"
    override val queueName: String = "payment_queue"   // Dedicated queue

    override suspend fun navigate(...): Boolean {
        // API call to check auth — does not block "default_queue"
        val isAuthenticated = authRepository.checkAuth()
        if (!isAuthenticated) return false
        // open Payment screen
        return true
    }
}
```

---

## How It Works

```
Build
  └─ KSP scans for @Deeplink handlers
       └─ Validates each class (concrete, implements DeeplinkHandler)
            └─ Generates HandlerRegisterImpl.kt

App start
  └─ AndroidX Startup → DeeplinkInitializer
       ├─ AutoRegisterManager registers HandlerRegisterImpl
       │    └─ DeeplinkResolver.register(AHandler(), BHandler(), ...)
       └─ Activity lifecycle callbacks
            └─ each Activity/Fragment onCreate → DeeplinkCoordinator.attach()

sendDeeplink("app://b")
  └─ emit into SharedFlow (buffer 20)
       └─ STARTED LifecycleOwner collects
            └─ DeeplinkResolver.resolve() → finds handler where canHandle = true
                 └─ Mutex per queueName → navigate()
                      └─ success → intent.consume() (atomic, only once)
```

### Generated file

After building, KSP automatically creates `HandlerRegisterImpl.kt` — **do not edit this file**:

```kotlin
// Generated by DeeplinkProcessor. DO NOT EDIT.
@AutoRegister(apis = [DeeplinkRegister::class])
class HandlerRegisterImpl : DeeplinkRegister {
    override fun register() {
        DeeplinkResolver.register(ADeeplinkHandler())
        DeeplinkResolver.register(BDeeplinkHandler())
    }
}
```

---

## Project Structure

```
Deeplink/
├── app/                            # Demo app
│   └── MainActivity.kt             # Usage example
│
├── deeplink/                       # Core library module
│   └── src/main/java/com/simple/deeplink/
│       ├── Deeplink.kt             # @Deeplink annotation + sendDeeplink() top-level functions
│       ├── DeeplinkHandler.kt      # Handler interface
│       ├── DeeplinkCoordinator.kt  # Intent dispatcher + DeeplinkResolver + DeeplinkSyncProvider
│       ├── DeeplinkInitializer.kt  # Auto-init via AndroidX Startup
│       └── DeeplinkRegister.kt     # Interface marking handler registration classes
│
└── deeplink-processor/             # KSP processor — generates code at compile-time
    └── src/main/java/com/simple/deeplink/processor/
        ├── DeeplinkProcessorProvider.kt  # KSP factory
        └── DeeplinkProcessor.kt          # @Deeplink scanning and file generation logic
```

---

## Requirements

| | Version |
|--|--|
| Android | minSdk 21+ |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.25 |
| AGP | 8.8.2 |
