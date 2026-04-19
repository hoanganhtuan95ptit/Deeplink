package com.simple.deeplink

import android.view.View

/**
 * Đánh dấu một class là Deeplink handler để [DeeplinkProcessor][com.simple.deeplink.processor.DeeplinkProcessor]
 * tự động phát hiện và đăng ký vào [DeeplinkResolver] tại **compile-time**.
 *
 * ## Yêu cầu bắt buộc
 * Class được annotate **phải** thoả mãn đồng thời:
 * 1. Là **concrete class** — không phải `abstract` hoặc `sealed`.
 * 2. **Implement trực tiếp** interface [DeeplinkResolver.DeeplinkHandler].
 *
 * Vi phạm bất kỳ điều kiện nào sẽ gây lỗi **build-time** (không phải runtime).
 *
 * ## Cách sử dụng
 * ```kotlin
 * @Deeplink
 * class HomeDeeplinkHandler : DeeplinkResolver.DeeplinkHandler {
 *
 *     // Trả về true nếu handler này xử lý được deeplink URL này
 *     override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
 *         return deeplink.startsWith("app://home")
 *     }
 *
 *     // Thực hiện điều hướng — trả về true nếu navigate thành công
 *     override suspend fun navigate(
 *         lifecycleOwner: LifecycleOwner,
 *         deeplink: String,
 *         extras: Map<String, Any?>?,
 *         sharedElement: Map<String, View>?
 *     ): Boolean {
 *         // TODO: mở màn hình Home
 *         return true
 *     }
 * }
 * ```
 *
 * ## Kết quả sau compile
 * KSP tự động sinh ra file `HandlerRegisterImpl.kt`:
 * ```kotlin
 * @AutoRegister(apis = [DeeplinkRegister::class])
 * class HandlerRegisterImpl : DeeplinkRegister {
 *     override fun register() {
 *         DeeplinkResolver.register(HomeDeeplinkHandler())
 *         // ... tất cả các handler khác
 *     }
 * }
 * ```
 * File này **không cần và không nên** chỉnh sửa thủ công.
 *
 * @see DeeplinkResolver.DeeplinkHandler
 * @see DeeplinkResolver
 * @see DeeplinkCoordinator
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Deeplink

fun sendDeeplink(deepLink: String) {
    DeeplinkCoordinator.sendDeeplink(deepLink)
}

fun sendDeeplink(deepLink: String, extras: Map<String, Any?>? = null) {
    DeeplinkCoordinator.sendDeeplink(deepLink, extras)
}

fun sendDeeplink(deepLink: String, extras: Map<String, Any?>? = null, sharedElement: Map<String, View>? = null) {
    DeeplinkCoordinator.sendDeeplink(deepLink, extras, sharedElement)
}
