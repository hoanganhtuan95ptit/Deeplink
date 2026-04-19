package com.simple.deeplink

/**
 * Interface đánh dấu một class có khả năng đăng ký các Deeplink handler
 * vào [DeeplinkResolver].
 *
 * [AutoRegisterManager][com.simple.auto.register.AutoRegisterManager] sẽ tự động
 * tìm tất cả implementation của interface này (thông qua annotation `@AutoRegister`)
 * và gọi [register] khi app khởi động.
 *
 * ## Trong thực tế
 * Bạn **không cần** implement interface này thủ công. [DeeplinkProcessor][com.simple.deeplink.processor.DeeplinkProcessor]
 * sẽ tự động sinh ra class `HandlerRegisterImpl` tại compile-time.
 *
 * Chỉ cần annotate handler class của bạn bằng [@Deeplink][Deeplink] là đủ:
 * ```kotlin
 * @Deeplink
 * class HomeDeeplinkHandler : DeeplinkResolver.DeeplinkHandler { ... }
 * ```
 *
 * @see Deeplink
 * @see com.simple.deeplink.processor.DeeplinkProcessor
 */
interface DeeplinkRegister {

    /**
     * Đăng ký tất cả các Deeplink handler vào [DeeplinkResolver].
     *
     * Hàm này được [AutoRegisterManager][com.simple.auto.register.AutoRegisterManager]
     * gọi **tự động** trong quá trình khởi động app — bạn **không cần** gọi thủ công.
     *
     * Mỗi lần gọi sẽ thêm các handler vào danh sách của [DeeplinkResolver].
     * Thứ tự đăng ký ảnh hưởng đến độ ưu tiên khi resolve deeplink.
     */
    fun register()
}
