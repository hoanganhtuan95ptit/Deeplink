# Deeplink

Thư viện Android giúp quản lý và xử lý điều hướng thông qua deeplink một cách gọn gàng — sử dụng **KSP** để tự động đăng ký handler, không cần viết boilerplate.

---

## Cách hoạt động

```
Annotation @Deeplink
      ↓
KSP sinh ra {TênModule}DeeplinkRegister lúc compile
      ↓
AutoRegister tự phát hiện register khi app khởi động
      ↓
DeeplinkResolver nạp các handler class thông qua reflection
      ↓
DeeplinkCoordinator điều phối và thực thi điều hướng cho từng URL
```

---

## Cài đặt

Thư viện có thể được sử dụng từ hai nguồn. Group ID sẽ **khác nhau** tuỳ theo nguồn.

### Cách 1 — JitPack (bản release công khai)

JitPack tự động ghép tên repository vào group, nên group ID sẽ là `com.github.hoanganhtuan95ptit.Deeplink`.

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

```groovy
// build.gradle (module)
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    implementation 'com.github.hoanganhtuan95ptit.Deeplink:deeplink:x.y.z'
    ksp          'com.github.hoanganhtuan95ptit.Deeplink:deeplink-processor:x.y.z'
}
```

---

### Cách 2 — Maven Local (build nội bộ, dùng để dev/test)

Khi publish bằng `./gradlew publishLocal`, group ID lấy trực tiếp từ `build.gradle` gốc là `com.github.hoanganhtuan95ptit` — **không** có hậu tố tên repository.

**Bước 1 — Publish lên Maven Local** (chạy một lần trong project thư viện):

```bash
./gradlew publishLocal
```

**Bước 2 — `settings.gradle` của project tiêu thụ:**

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenLocal() // Phải đặt trước mavenCentral để ưu tiên bản local
        mavenCentral()
    }
}
```

**Bước 3 — `build.gradle` (module):**

```groovy
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    implementation 'com.github.hoanganhtuan95ptit:deeplink:1.0.0'
    ksp          'com.github.hoanganhtuan95ptit:deeplink-processor:1.0.0'
}
```

> **Tóm tắt sự khác biệt về Group ID:**
>
> | Nguồn | Group ID |
> |---|---|
> | JitPack | `com.github.hoanganhtuan95ptit.Deeplink` |
> | Maven Local | `com.github.hoanganhtuan95ptit` |

---

## Cách dùng

### Bước 1 — Tạo DeeplinkHandler

Đánh dấu class bằng `@Deeplink`. KSP sẽ tự động đăng ký lúc compile, không cần khai báo thêm gì. Class của bạn cần implement `DeeplinkHandler`.

**Cách 1 — Dùng trực tiếp với URL cố định:**

```kotlin
@Deeplink
class ADeeplinkHandler : DeeplinkHandler {

    // Khai báo URL cần xử lý — canHandle() mặc định sẽ so sánh URL này
    override val deeplink: String by lazy {
        "app://a"
    }

    override suspend fun navigate(
        fragmentActivity: FragmentActivity,
        deeplink: String,
        extras: Map<String, Any?>?,
        sharedElement: Map<String, View>?
    ): Boolean {
        // todo: mở màn hình AFragment
        return true
    }
}
```

**Cách 2 — Override canHandle để dùng pattern matching:**

```kotlin
@Deeplink
class BDeeplinkHandler : DeeplinkHandler {

    // Override canHandle để xử lý nhiều URL hoặc dùng pattern matching
    override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
        return deeplink.startsWith("app://b", true)
    }

    override suspend fun navigate(
        fragment: Fragment,
        deeplink: String,
        extras: Map<String, Any?>?,
        sharedElement: Map<String, View>?
    ): Boolean {
        // todo: mở màn hình BFragment
        return true
    }
}
```

### Bước 2 — Điều hướng

Bạn có thể gọi trực tiếp hàm `sendDeeplink` từ Fragment hoặc Activity để thực thi deeplink.

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ...

        // Gửi deeplink đơn giản
        sendDeeplink("app://a")

        // Gửi deeplink kèm theo dữ liệu (extras)
        sendDeeplink("app://b?create", extras = mapOf("userId" to 1))
    }
}
```

---

## Tài liệu API

### `DeeplinkHandler`

| Hàm | Mục đích |
|---|---|
| `deeplink: String` | Khai báo URL cố định mà handler xử lý. |
| `queueName: String` | Tên hàng đợi để nhóm các deeplink thực thi tuần tự (mặc định `"default_queue"`). |
| `canHandle(...)` | Kiểm tra handler có xử lý được deeplink không (mặc định so sánh với `deeplink`). |
| `navigate(...)` | Hàm `suspend` thực hiện điều hướng, trả về `true` nếu thành công. Hỗ trợ cho `Fragment` hoặc `FragmentActivity`. |

### `sendDeeplink`

```kotlin
fun sendDeeplink(deepLink: String, extras: Map<String, Any?>? = null, sharedElement: Map<String, View>? = null)
```

Top-level function hỗ trợ gọi nhanh qua `DeeplinkCoordinator.sendDeeplink()`. Có thể truyền `extras` để gửi dữ liệu bổ sung không encode được vào URL và `sharedElement` cho transition.

---

## Code được sinh tự động

Khi build, KSP quét toàn bộ class có `@Deeplink` và sinh ra một class đăng ký cho mỗi module:

```kotlin
// Generated by DeeplinkProcessor. DO NOT EDIT.
@AutoRegister(apis = [DeeplinkRegister::class])
public class AppDeeplinkRegister : DeeplinkRegister {
    override fun register() {
        DeeplinkResolver.register(ADeeplinkHandler())
        DeeplinkResolver.register(BDeeplinkHandler())
    }
}
```

Tên class được suy ra từ tên Gradle module — ví dụ module `app` sẽ tạo ra `AppDeeplinkRegister`. Không cần cấu hình gì thêm.

---

## Giấy phép

```
Copyright 2024 hoanganhtuan95ptit

Licensed under the Apache License, Version 2.0
```
