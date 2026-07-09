# Skill: Deeplink Navigation Implementation

## 1. Overview
This skill provides instructions on how to implement and use the **Deeplink** library in Android projects. This library simplifies deeplink routing and navigation by leveraging **KSP (Kotlin Symbol Processing)** and **AutoRegister** to automatically discover and register deeplink handlers at compile-time without boilerplate code.

---

## 2. Architecture & Workflow
When you are asked to implement a deeplink or navigation routing, understand this internal flow:
1. **Annotation `@Deeplink`**: You annotate a concrete class that implements `DeeplinkHandler`.
2. **KSP Processor**: At compile-time, KSP scans for `@Deeplink` and generates a `{ModuleName}DeeplinkRegister` class.
3. **AutoRegister**: Discovers the generated register class when the app starts.
4. **DeeplinkResolver**: Loads all registered handler classes via reflection.
5. **DeeplinkCoordinator**: Intercepts `sendDeeplink()` calls, finds the matching handler, and executes its `navigate()` method.

---

## 3. Installation

The library can be consumed from two sources. **Always check which source the target project uses before writing dependency declarations.**

### Option A — JitPack (remote, public release)

> JitPack automatically appends the repository name to the group, so the group ID becomes `com.github.hoanganhtuan95ptit.Deeplink`.

**In `settings.gradle`:**
```groovy
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

**In module `build.gradle`:**
```groovy
plugins {
    alias(libs.plugins.ksp) // Ensure KSP plugin is applied
}

dependencies {
    implementation 'com.github.hoanganhtuan95ptit.Deeplink:deeplink:<latest_version>'
    ksp 'com.github.hoanganhtuan95ptit.Deeplink:deeplink-processor:<latest_version>'
}
```
*(Always check for the correct `latest_version` if specified in the project).*

---

### Option B — Maven Local (local build, for development/testing)

> When published via `./gradlew publishLocal`, the group ID is taken directly from `build.gradle` root: `com.github.hoanganhtuan95ptit` — **without** the repository name suffix.

**Step 1 — Publish to Maven Local** (run once in the library project):
```bash
./gradlew publishLocal
```

**Step 2 — In `settings.gradle` of the consuming project:**
```groovy
dependencyResolutionManagement {
    repositories {
        mavenLocal() // Must come before mavenCentral/jitpack so local takes priority
        mavenCentral()
    }
}
```

**Step 3 — In module `build.gradle`:**
```groovy
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    implementation 'com.github.hoanganhtuan95ptit:deeplink:1.0.0'
    ksp 'com.github.hoanganhtuan95ptit:deeplink-processor:1.0.0'
}
```

> **Key difference vs JitPack:**
> | Source | Group ID |
> |---|---|
> | JitPack | `com.github.hoanganhtuan95ptit.Deeplink` |
> | Maven Local | `com.github.hoanganhtuan95ptit` |

---

## 4. Implementation Steps for AI

When a user asks you to "create a deeplink handler for screen X" or "navigate to Y using deeplink", follow these steps:

### Step 1: Create a `DeeplinkHandler`
Create a class that implements `DeeplinkHandler` and annotate it with `@Deeplink`.

**Rule 1:** The class **must be a concrete class** (not `abstract` or `sealed`).
**Rule 2:** Choose the matching strategy based on the URL type:

* **Strategy A: Exact Match (Fixed URL)**
  If the URL is static, override the `deeplink` property.
  ```kotlin
  import com.simple.deeplink.Deeplink
  import com.simple.deeplink.DeeplinkHandler
  
  @Deeplink
  class ADeeplinkHandler : DeeplinkHandler {
      override val deeplink: String = "app://a"
  
      override suspend fun navigate(
          fragmentActivity: FragmentActivity,
          deeplink: String,
          extras: Map<String, Any?>?,
          sharedElement: Map<String, View>?
      ): Boolean {
          // TODO: Execute your Fragment transaction or Intent here
          return true // Return true if handled successfully
      }
  }
  ```

* **Strategy B: Dynamic Match (Pattern/Prefix)**
  If the URL has query parameters, paths, or variables, override the `canHandle()` method.
  ```kotlin
  import com.simple.deeplink.Deeplink
  import com.simple.deeplink.DeeplinkHandler
  
  @Deeplink
  class BDeeplinkHandler : DeeplinkHandler {
      override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
          return deeplink.startsWith("app://b", ignoreCase = true)
      }
  
      override suspend fun navigate(
          fragment: Fragment,
          deeplink: String,
          extras: Map<String, Any?>?,
          sharedElement: Map<String, View>?
      ): Boolean {
          // TODO: Parse the deeplink string, get extras, and navigate
          return true
      }
  }
  ```

### Step 2: Trigger the Deeplink
To dispatch a deeplink from the UI, invoke the `sendDeeplink()` top-level function.

```kotlin
import com.simple.deeplink.sendDeeplink

// Inside a Fragment or Activity:
sendDeeplink("app://a")

// With parameters (extras) that cannot be encoded in the URL string:
sendDeeplink("app://b?create", extras = mapOf("userId" to 1, "isNew" to true))
```

---

## 5. API Reference cheat sheet

### `DeeplinkHandler` Properties / Methods
*   `val deeplink: String`: Define the exact URL this handler matches.
*   `val queueName: String`: (Default `"default_queue"`). Handlers with the same queue name execute sequentially.
*   `fun canHandle(lifecycleOwner, deeplink): Boolean`: Defaults to exact match on `deeplink`. Override for pattern matching.
*   `suspend fun navigate(...)`: Execute the actual view navigation. You can override the variant for `Fragment` or `FragmentActivity`. Return `true` if consumed.

### `sendDeeplink`
*   **Signature:** `fun sendDeeplink(deepLink: String, extras: Map<String, Any?>? = null, sharedElement: Map<String, View>? = null)`
*   **Usage:** Fire-and-forget method to pass a deeplink URL along with optional Kotlin maps for complex data (`extras`) or UI transitions (`sharedElement`) to the coordinator.

---

## 6. Important Notes & Edge Cases
*   **Do NOT write manual registry code:** The AI should not create lists or registries of handlers. Just apply the `@Deeplink` annotation and the KSP plugin handles the rest.
*   **Case Sensitivity:** By default, URL matching using the `deeplink` property is case-insensitive.
*   **Thread Safety:** The `navigate` function is a `suspend` function, meaning you can safely make network requests, database lookups, or check authentication directly inside `navigate` before committing a Fragment transaction.
