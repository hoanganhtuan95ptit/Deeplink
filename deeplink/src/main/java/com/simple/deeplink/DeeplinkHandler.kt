package com.simple.deeplink

import android.view.View
import androidx.lifecycle.LifecycleOwner

/**
 * Interface định nghĩa một đơn vị xử lý Deeplink.
 *
 * Mỗi implementation đảm nhận **một URL cụ thể** hoặc **một nhóm URL**
 * (thường tương ứng với một màn hình hoặc một tính năng trong app).
 *
 * ## Cách tạo handler (cách đơn giản — dùng deeplink property)
 * ```kotlin
 * @Deeplink
 * class HomeDeeplinkHandler : DeeplinkHandler {
 *
 *     // Khai báo URL cần xử lý — canHandle() mặc định sẽ so sánh URL này
 *     override val deeplink: String = "app://home"
 *
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
 * ## Cách tạo handler (cách nâng cao — override canHandle)
 * ```kotlin
 * @Deeplink
 * class ProductDeeplinkHandler : DeeplinkHandler {
 *
 *     // Override canHandle để xử lý nhiều URL hoặc dùng pattern matching
 *     override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
 *         return deeplink.startsWith("app://product/")
 *     }
 *
 *     override suspend fun navigate(
 *         lifecycleOwner: LifecycleOwner,
 *         deeplink: String,
 *         extras: Map<String, Any?>?,
 *         sharedElement: Map<String, View>?
 *     ): Boolean {
 *         val productId = Uri.parse(deeplink).lastPathSegment ?: return false
 *         // TODO: mở màn hình Product với productId
 *         return true
 *     }
 * }
 * ```
 *
 * @see Deeplink
 * @see DeeplinkResolver
 * @see DeeplinkCoordinator
 */
interface DeeplinkHandler {

    /**
     * URL deeplink cụ thể mà handler này xử lý.
     *
     * Đây là cách khai báo nhanh cho handler xử lý **đúng một URL**.
     * [canHandle] mặc định sẽ so sánh URL đến với giá trị này (case-insensitive).
     *
     * Nếu cần xử lý nhiều URL hoặc dùng pattern matching, hãy
     * override [canHandle] thay vì property này.
     */
    val deeplink: String
        get() = ""

    /**
     * Tên hàng đợi để nhóm các deeplink cần thực thi **tuần tự**.
     *
     * Các handler cùng [queueName] sẽ không navigate song song —
     * mỗi navigate phải hoàn thành trước khi cái tiếp theo bắt đầu.
     * Các queue khác nhau vẫn có thể chạy song song.
     *
     * Mặc định toàn bộ deeplink dùng chung `"default_queue"`.
     * Override để tạo queue riêng nếu muốn nhóm deeplink của bạn
     * độc lập với phần còn lại.
     */
    val queueName: String
        get() = "default_queue"

    /**
     * Kiểm tra handler này có xử lý được [deeplink] URL không.
     *
     * **Mặc định:** So sánh [deeplink] parameter với property [DeeplinkHandler.deeplink]
     * (case-insensitive). Override nếu cần logic phức tạp hơn.
     *
     * Hàm này chạy trên Main thread — phải **nhanh và không blocking**.
     *
     * @param lifecycleOwner LifecycleOwner hiện tại đang request xử lý.
     * @param deeplink       URL deeplink cần kiểm tra.
     * @return `true` nếu handler này có thể xử lý URL này.
     */
    fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
        return this.deeplink.equals(deeplink, ignoreCase = true)
    }

    /**
     * Thực hiện điều hướng cho deeplink URL — hàm xử lý chính của handler.
     *
     * Là `suspend` function — có thể thực hiện các tác vụ async như gọi API,
     * load dữ liệu, kiểm tra auth trước khi mở màn hình.
     *
     * ## Giá trị trả về
     * - `true`: Navigate thành công → intent bị consumed, không handler nào khác nhận.
     * - `false`: Navigate thất bại → intent **không** bị consumed, có thể retry.
     *
     * @param lifecycleOwner LifecycleOwner để thực hiện navigate (Activity/Fragment).
     * @param deeplink       URL deeplink cần xử lý.
     * @param extras         Dữ liệu bổ sung không encode được vào URL (nullable).
     * @param sharedElement  Map tên → View cho shared element transition (nullable).
     * @return `true` nếu navigate thành công, `false` nếu thất bại hoặc không phù hợp.
     */
    suspend fun navigate(
        lifecycleOwner: LifecycleOwner,
        deeplink: String,
        extras: Map<String, Any?>? = null,
        sharedElement: Map<String, View>? = null,
    ): Boolean
}
