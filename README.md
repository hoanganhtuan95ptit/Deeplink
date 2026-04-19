# Deeplink

[![](https://jitpack.io/v/hoanganhtuan95ptit/Deeplink.svg)](https://jitpack.io/#hoanganhtuan95ptit/Deeplink)

Thư viện xử lý deeplink cho Android, sử dụng **KSP** để tự động đăng ký handler tại compile-time — không cần khai báo thủ công, không reflection lúc runtime.

---

## Tính năng

- **Zero boilerplate** — chỉ cần thêm `@Deeplink`, mọi thứ còn lại tự động.
- **Compile-time safe** — lỗi (sai interface, abstract class) bị bắt ngay lúc build, không phải lúc chạy app.
- **Lifecycle-aware** — deeplink chỉ được xử lý khi Activity/Fragment đang ở foreground (`STARTED`), tự pause/resume theo lifecycle.
- **Không mất deeplink** — buffer 20 intent, deeplink gửi trước khi UI sẵn sàng vẫn được xử lý sau.
- **Thread-safe** — có thể gọi `sendDeeplink` từ bất kỳ thread nào.
- **Hỗ trợ truyền dữ liệu** — gửi kèm `extras` và `sharedElement` cho transition animation.

---

## Cài đặt

### 1. Thêm JitPack vào `settings.gradle`

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. Thêm KSP plugin vào `libs.versions.toml`

```toml
[versions]
ksp = "2.0.21-1.0.25"

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### 3. Thêm dependency vào module cần dùng

```groovy
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)          // Bắt buộc
}

dependencies {
    implementation 'com.github.hoanganhtuan95ptit.Deeplink:deeplink:1.0.0'
    ksp          'com.github.hoanganhtuan95ptit.Deeplink:deeplink-processor:1.0.0'
}
```

> **Lưu ý:** `deeplink` và `deeplink-processor` phải dùng **cùng version**.

### 4. Khởi tạo tự động (không cần làm gì thêm)

Thư viện dùng **AndroidX App Startup** — tự khởi tạo khi app start mà không cần gọi thủ công trong `Application.onCreate()`.

---

## Cách sử dụng

### Bước 1 — Tạo handler

Tạo một class implement `DeeplinkHandler` và annotate bằng `@Deeplink`:

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
        // TODO: mở màn hình B
        return true
    }
}
```

Chỉ vậy thôi. KSP sẽ tự động phát hiện class này khi build và đăng ký vào hệ thống.

### Bước 2 — Gửi deeplink

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...

        // Gửi deeplink — Activity/Fragment sẽ tự nhận khi sẵn sàng
        sendDeeplink("app://b")
    }
}
```

---

## API

### Gửi deeplink

```kotlin
// Chỉ URL
sendDeeplink("app://home")

// Kèm dữ liệu không encode được vào URL
sendDeeplink(
    deepLink = "app://profile",
    extras = mapOf("user" to userObject, "fromTab" to "search")
)

// Kèm shared element cho transition animation
sendDeeplink(
    deepLink = "app://photo/123",
    extras = mapOf("photoId" to "123"),
    sharedElement = mapOf("photo" to photoImageView)
)
```

### DeeplinkHandler interface

| Member | Mô tả |
|--------|-------|
| `val deeplink: String` | URL handler xử lý — `canHandle()` mặc định so sánh với property này (case-insensitive). |
| `val queueName: String` | Tên queue để serialize deeplink. Mặc định `"default_queue"`. |
| `fun canHandle(lifecycleOwner, deeplink): Boolean` | Kiểm tra có xử lý URL này không. Override để dùng pattern matching. |
| `suspend fun navigate(lifecycleOwner, deeplink, extras, sharedElement): Boolean` | Thực hiện điều hướng. Trả về `true` nếu thành công. |

---

## Ví dụ nâng cao

### Dùng `deeplink` property (cách nhanh)

Khi handler chỉ xử lý đúng một URL, khai báo qua property thay vì override `canHandle`:

```kotlin
@Deeplink
class HomeDeeplinkHandler : DeeplinkHandler {

    // Không cần override canHandle() — so sánh tự động
    override val deeplink: String = "app://home"

    override suspend fun navigate(
        lifecycleOwner: LifecycleOwner,
        deeplink: String,
        extras: Map<String, Any?>?,
        sharedElement: Map<String, View>?
    ): Boolean {
        // mở màn hình Home
        return true
    }
}
```

### Pattern matching (nhiều URL)

Override `canHandle` để xử lý nhóm URL:

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
        // mở màn hình Product với productId
        return true
    }
}
```

### Nhận `extras` và `sharedElement`

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
            // mở PhotoFragment với shared element transition
        }
        return true
    }
}
```

### Queue riêng (tránh block deeplink khác)

Mặc định tất cả deeplink dùng chung `"default_queue"` — xử lý tuần tự. Nếu handler của bạn mất nhiều thời gian (gọi API, v.v.), tạo queue riêng để không ảnh hưởng handler khác:

```kotlin
@Deeplink
class PaymentDeeplinkHandler : DeeplinkHandler {

    override val deeplink: String = "app://payment"
    override val queueName: String = "payment_queue"   // Queue riêng

    override suspend fun navigate(...): Boolean {
        // Gọi API kiểm tra auth — không block "default_queue"
        val isAuthenticated = authRepository.checkAuth()
        if (!isAuthenticated) return false
        // mở màn hình Payment
        return true
    }
}
```

---

## Hoạt động bên trong

```
Build
  └─ KSP scan @Deeplink handlers
       └─ Validate (concrete class, implement DeeplinkHandler)
            └─ Sinh ra HandlerRegisterImpl.kt

App start
  └─ AndroidX Startup → DeeplinkInitializer
       ├─ AutoRegisterManager đăng ký HandlerRegisterImpl
       │    └─ DeeplinkResolver.register(AHandler(), BHandler(), ...)
       └─ Activity lifecycle callbacks
            └─ mỗi Activity/Fragment onCreate → DeeplinkCoordinator.attach()

sendDeeplink("app://b")
  └─ emit vào SharedFlow (buffer 20)
       └─ LifecycleOwner STARTED collect
            └─ DeeplinkResolver.resolve() → tìm handler canHandle = true
                 └─ Mutex theo queueName → navigate()
                      └─ success → intent.consume() (atomic, chỉ 1 lần)
```

### File được KSP sinh ra

Sau khi build, KSP tự động tạo file `HandlerRegisterImpl.kt` — **không chỉnh sửa file này**:

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

## Cấu trúc project

```
Deeplink/
├── app/                        # Demo app
│   └── MainActivity.kt         # Ví dụ sử dụng
│
├── deeplink/                   # Module thư viện chính
│   └── src/main/java/com/simple/deeplink/
│       ├── Deeplink.kt         # @Deeplink annotation + sendDeeplink() top-level functions
│       ├── DeeplinkHandler.kt  # Interface handler
│       ├── DeeplinkCoordinator.kt  # Điều phối intent + DeeplinkResolver + DeeplinkSyncProvider
│       ├── DeeplinkInitializer.kt  # Auto-init qua AndroidX Startup
│       └── DeeplinkRegister.kt     # Interface đánh dấu class đăng ký handler
│
└── deeplink-processor/         # KSP processor — sinh code tại compile-time
    └── src/main/java/com/simple/deeplink/processor/
        ├── DeeplinkProcessorProvider.kt  # Factory cho KSP
        └── DeeplinkProcessor.kt          # Logic quét @Deeplink và sinh file
```

---

## Yêu cầu

| | Phiên bản |
|--|--|
| Android | minSdk 21+ |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.25 |
| AGP | 8.8.2 |
