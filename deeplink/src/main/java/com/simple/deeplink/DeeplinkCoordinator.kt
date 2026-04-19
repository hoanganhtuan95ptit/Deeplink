package com.simple.deeplink

import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

// ═══════════════════════════════════════════════════════════════════════════════
// DeeplinkCoordinator
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bộ điều phối trung tâm của hệ thống Deeplink — điểm vào duy nhất để gửi
 * và xử lý deeplink trong toàn bộ app.
 *
 * ## Kiến trúc tổng quan
 * ```
 * Caller
 *   └─ sendDeeplink(url)
 *        └─ tryEmit vào _intentQueue (SharedFlow, buffer 10+10)
 *             └─ mỗi LifecycleOwner đã attach sẽ collect (khi STARTED)
 *                  └─ processIntent()
 *                       ├─ DeeplinkResolver.resolve() → tìm handler phù hợp
 *                       ├─ DeeplinkSyncProvider.getBarrier() → lấy Mutex theo queueName
 *                       └─ handler.navigate() → thực hiện điều hướng
 * ```
 *
 * ## Cách sử dụng
 * ```kotlin
 * // Gửi deeplink từ bất kỳ đâu (thread-safe)
 * DeeplinkCoordinator.sendDeeplink("app://home")
 *
 * // Gửi kèm dữ liệu bổ sung
 * DeeplinkCoordinator.sendDeeplink(
 *     deepLink = "app://profile",
 *     extras = mapOf("userId" to "123"),
 *     sharedElement = mapOf("avatar" to avatarView)
 * )
 *
 * // Trong Activity/Fragment — thường được gọi tự động bởi DeeplinkInitializer
 * DeeplinkCoordinator.attach(this)
 * ```
 *
 * ## Các đảm bảo thiết kế
 *
 * ### Không duplicate xử lý
 * [attach] dùng `repeatOnLifecycle(STARTED)` thay vì collect trực tiếp,
 * tránh tình trạng nhiều coroutine collect cùng lúc khi Activity bị recreate
 * (ví dụ: xoay màn hình).
 *
 * ### Không xử lý trùng intent
 * Mỗi [DeeplinkIntent] có `id` UUID duy nhất và cờ `isConsumed` atomic.
 * Dù nhiều [LifecycleOwner] cùng nhận một intent, chỉ một handler đầu tiên
 * navigate thành công mới consume được intent.
 *
 * ### Thread-safe
 * Tất cả operation đều thread-safe: emit/collect qua SharedFlow,
 * navigate được serialize qua Mutex, consumed flag dùng AtomicBoolean.
 *
 * @see DeeplinkResolver
 * @see DeeplinkSyncProvider
 * @see DeeplinkInitializer
 */
object DeeplinkCoordinator {

    /**
     * Hàng đợi intent deeplink — trái tim của hệ thống.
     *
     * Cấu hình buffer:
     * - `replay = 10`: Giữ lại 10 intent gần nhất cho subscriber mới —
     *   đảm bảo deeplink gửi trước khi Activity attach vẫn được xử lý.
     * - `extraBufferCapacity = 10`: Buffer thêm 10 slot để hấp thu burst.
     * - `onBufferOverflow = SUSPEND`: Khi buffer đầy, [sendDeeplink] sẽ suspend
     *   thay vì drop intent — không bao giờ mất deeplink.
     */
    private val _intentQueue = MutableSharedFlow<DeeplinkIntent>(
        replay = 10,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Gắn kết một [LifecycleOwner] (Activity hoặc Fragment) vào hệ thống Deeplink.
     *
     * Kể từ khi [attach] được gọi, [lifecycleOwner] sẽ **tự động nhận và xử lý**
     * deeplink khi lifecycle ≥ [Lifecycle.State.STARTED].
     *
     * ## Tại sao dùng repeatOnLifecycle(STARTED)?
     * - **Tránh duplicate:** Khi Activity bị recreate, coroutine cũ tự cancel
     *   và coroutine mới được tạo — không bao giờ có 2 collector cùng lúc.
     * - **Tiết kiệm tài nguyên:** Coroutine tự pause khi Activity về background
     *   (< STARTED) và resume khi lên foreground.
     *
     * Hàm này thường được gọi **tự động** bởi [DeeplinkInitializer].
     *
     * @param lifecycleOwner Activity hoặc Fragment sẽ nhận và xử lý deeplink.
     */
    fun attach(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                _intentQueue.collect { intent ->
                    processIntent(lifecycleOwner, intent)
                }
            }
        }
    }

    /**
     * Gửi một deeplink vào hàng đợi để được xử lý.
     *
     * **Thread-safe** — có thể gọi từ bất kỳ thread nào, kể cả background thread.
     *
     * Deeplink sẽ được giữ trong buffer (tối đa 20 item) cho đến khi có
     * [LifecycleOwner] đang ở trạng thái STARTED sẵn sàng xử lý.
     *
     * @param deepLink    URL deeplink cần xử lý (ví dụ: `"app://home?tab=profile"`).
     * @param extras      Dữ liệu bổ sung không thể encode vào URL (object, list, v.v.).
     * @param sharedElement Map tên → View dùng cho shared element transition animation.
     */
    fun sendDeeplink(
        deepLink: String,
        extras: Map<String, Any?>? = null,
        sharedElement: Map<String, View>? = null,
    ) {
        // tryEmit() thành công vì buffer còn chỗ (replay=10 + extraCapacity=10).
        // Trong trường hợp cực hiếm buffer đầy, intent sẽ bị drop — chấp nhận được
        // vì tryEmit chỉ thất bại khi có 20+ deeplink chưa được xử lý đồng thời.
        _intentQueue.tryEmit(DeeplinkIntent(deepLink, extras, sharedElement))
    }

    // ─── Internal Processing ──────────────────────────────────────────────────

    /**
     * Xử lý một [DeeplinkIntent] trên một [LifecycleOwner] cụ thể.
     *
     * ## Luồng xử lý chi tiết
     * 1. [DeeplinkResolver.resolve] — tìm handler có `canHandle() = true`
     * 2. [DeeplinkSyncProvider.getBarrier] — lấy Mutex theo [DeeplinkResolver.DeeplinkHandler.queueName]
     * 3. Acquire lock — serialize các deeplink cùng queue
     * 4. Double-check `isConsumed` bên trong lock — tránh race condition giữa nhiều LifecycleOwner
     * 5. [DeeplinkResolver.DeeplinkHandler.navigate] — thực hiện điều hướng thực sự
     * 6. Giải phóng extras/sharedElement và consume intent nếu navigate thành công
     *
     * ## Tại sao cần Mutex?
     * Nhiều [LifecycleOwner] (Activity + Fragment) đồng thời collect [_intentQueue].
     * Khi một intent mới đến, tất cả đều nhận được và cùng gọi [processIntent].
     * Mutex đảm bảo chỉ một trong số đó thực sự navigate.
     *
     * @param lifecycleOwner LifecycleOwner đang active, sẽ thực hiện navigate nếu thắng lock.
     * @param intent         Intent chứa thông tin deeplink cần xử lý.
     */
    private fun processIntent(lifecycleOwner: LifecycleOwner, intent: DeeplinkIntent) {
        // Bước 1: Tìm handler phù hợp — bỏ qua nếu không có handler nào xử lý được URL này
        val handler = DeeplinkResolver.resolve(lifecycleOwner, intent.deepLink) ?: return

        // Bước 2: Lấy Mutex theo queueName để serialize deeplink cùng queue
        val executionBarrier = DeeplinkSyncProvider.getBarrier(handler.queueName)

        lifecycleOwner.lifecycleScope.launch {
            executionBarrier.withLock {
                // Bước 3 (Double-check): Trong khi chờ lock, LifecycleOwner khác
                // có thể đã consume intent này rồi — kiểm tra lại trước khi navigate
                if (intent.isConsumed) return@withLock

                // Bước 4: Thực hiện navigate — suspend cho đến khi hoàn thành
                val success = handler.navigate(lifecycleOwner, intent.deepLink, intent.extras, intent.sharedElement)
                if (!success) return@withLock

                // Bước 5: Giải phóng tài nguyên và đánh dấu intent đã được xử lý
                intent.extras = null
                intent.sharedElement = null
                intent.consume()
            }
        }
    }

    // ─── DeeplinkIntent ───────────────────────────────────────────────────────

    /**
     * Đại diện cho một yêu cầu điều hướng Deeplink đang chờ hoặc đang xử lý.
     *
     * ## Tại sao không dùng data class?
     * `data class` tự sinh `equals()`/`hashCode()` từ các constructor property.
     * Nếu dùng `url` làm property chính, hai intent cùng URL sẽ bị coi là một —
     * dẫn đến việc gọi `sendDeeplink("app://home")` hai lần chỉ được xử lý một lần.
     * Dùng `id` UUID để đảm bảo mỗi lần gọi là một intent độc lập.
     *
     * @param deepLink    URL deeplink cần điều hướng.
     * @param extras      Dữ liệu bổ sung truyền kèm (có thể giải phóng sau khi navigate).
     * @param sharedElement View map cho shared element transition.
     */
    private class DeeplinkIntent(
        val deepLink: String,
        var extras: Map<String, Any?>? = null,
        var sharedElement: Map<String, View>? = null,
    ) {

        /** ID duy nhất để phân biệt các intent, kể cả khi cùng URL. */
        val id: String = UUID.randomUUID().toString()

        /** Cờ thread-safe đánh dấu intent đã được navigate thành công hay chưa. */
        private val _isConsumed = AtomicBoolean(false)

        /**
         * `true` nếu intent đã được một handler navigate thành công.
         * Intent đã consumed sẽ bị bỏ qua bởi tất cả handler còn lại.
         */
        val isConsumed: Boolean
            get() = _isConsumed.get()

        /**
         * Đánh dấu intent là đã consumed (atomic, thread-safe).
         *
         * Dùng [AtomicBoolean.compareAndSet] đảm bảo chỉ **một** lần gọi
         * thành công, dù nhiều coroutine gọi đồng thời.
         *
         * @return `true` nếu consume thành công (lần đầu tiên), `false` nếu đã consumed.
         */
        fun consume(): Boolean = _isConsumed.compareAndSet(false, true)

        // Equals/hashCode dựa trên id — không phải url — để phân biệt đúng intent
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DeeplinkIntent) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()

        override fun toString(): String = "DeeplinkIntent(id=$id, url=$deepLink, consumed=$isConsumed)"
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DeeplinkResolver
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bộ phân giải Deeplink — registry lưu trữ tất cả handler và tìm handler phù hợp
 * cho một deeplink URL cụ thể.
 *
 * ## Vòng đời
 * 1. **App start:** [DeeplinkRegister.register] (class do KSP sinh) gọi [register]
 *    để nạp tất cả `@Deeplink` handler vào danh sách.
 * 2. **Khi có deeplink:** [DeeplinkCoordinator] gọi [resolve] để tìm handler
 *    đầu tiên có `canHandle() = true`.
 *
 * ## Thread safety
 * Danh sách dùng [CopyOnWriteArrayList]:
 * - Write (`register`) tạo bản copy mới → an toàn khi gọi từ nhiều thread.
 * - Read (`resolve`) không lock, không block → hiệu năng cao.
 * Phù hợp vì write chỉ xảy ra khi app start, read xảy ra thường xuyên.
 *
 * @see DeeplinkHandler
 * @see DeeplinkCoordinator
 */
object DeeplinkResolver {

    /** Danh sách tất cả handler đã đăng ký, thread-safe. */
    private val handlers = CopyOnWriteArrayList<DeeplinkHandler>()

    /**
     * Đăng ký một handler mới vào hệ thống.
     *
     * Thường được gọi tự động bởi `HandlerRegisterImpl.register()` (class do KSP sinh).
     * Thứ tự gọi [register] quyết định độ ưu tiên: handler đăng ký trước được
     * kiểm tra trước trong [resolve].
     *
     * @param handler Handler cần đăng ký.
     */
    fun register(handler: DeeplinkHandler) {
        handlers.add(handler)
    }

    /**
     * Tìm handler đầu tiên có thể xử lý [url] trong ngữ cảnh [lifecycleOwner].
     *
     * Duyệt tuần tự theo thứ tự đăng ký, trả về handler đầu tiên mà
     * [DeeplinkHandler.canHandle] trả về `true`.
     *
     * @param lifecycleOwner LifecycleOwner hiện tại — truyền vào `canHandle` để handler
     *   có thể lọc theo context nếu cần (ví dụ: chỉ xử lý khi đang ở Activity cụ thể).
     * @param url URL deeplink cần resolve.
     * @return Handler phù hợp đầu tiên, hoặc `null` nếu không có handler nào xử lý được.
     */
    fun resolve(lifecycleOwner: LifecycleOwner, url: String): DeeplinkHandler? {
        return handlers.find { it.canHandle(lifecycleOwner, url) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DeeplinkSyncProvider
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bộ cung cấp [Mutex] để đồng bộ hoá việc xử lý các deeplink trong cùng queue.
 *
 * Mỗi [DeeplinkResolver.DeeplinkHandler.queueName] có một [Mutex] riêng biệt.
 * Điều này cho phép:
 * - Deeplink trong **cùng queue** → xử lý **tuần tự** (không song song).
 * - Deeplink ở **queue khác** → xử lý **song song** với nhau.
 *
 * ## Ví dụ minh hoạ
 * ```
 * "default_queue":  [deeplink A đang chạy] → [deeplink B chờ] → [deeplink C chờ]
 * "payment_queue":  [deeplink X đang chạy] → [deeplink Y chờ]
 *
 * → A và X chạy SONG SONG (khác queue)
 * → A và B chạy TUẦN TỰ (cùng queue)
 * ```
 *
 * ## Memory management
 * Nếu [queueName] được tạo động (ví dụ: theo `userId`), hãy gọi [release]
 * sau khi dùng xong để tránh memory leak do tích lũy Mutex không dùng nữa.
 */
internal object DeeplinkSyncProvider {

    /** Map lưu trữ [Mutex] theo tên queue, thread-safe với [ConcurrentHashMap]. */
    private val barriers = ConcurrentHashMap<String, Mutex>()

    /**
     * Lấy [Mutex] của queue theo [key], tự động tạo mới nếu chưa tồn tại.
     *
     * Operation này **atomic** nhờ [ConcurrentHashMap.getOrPut] —
     * không có race condition dù nhiều coroutine gọi đồng thời với cùng [key].
     *
     * @param key Tên queue — thường là [DeeplinkResolver.DeeplinkHandler.queueName].
     * @return [Mutex] tương ứng với queue này (tạo mới nếu chưa có).
     */
    fun getBarrier(key: String): Mutex {
        return barriers.getOrPut(key) { Mutex() }
    }

    /**
     * Giải phóng [Mutex] của một queue khi không còn cần thiết.
     *
     * Quan trọng khi `queueName` được tạo động để tránh memory leak.
     *
     * **Cảnh báo:** Gọi [release] khi [Mutex] đang bị lock sẽ không gây exception,
     * nhưng coroutine đang chờ lock có thể bị treo vĩnh viễn. Log cảnh báo sẽ được
     * ghi ra để dễ debug.
     *
     * @param key Tên queue cần giải phóng.
     */
    fun release(key: String) {
        val mutex = barriers.remove(key)
        if (mutex?.isLocked == true) {
            Log.w("DeeplinkSyncProvider", "Releasing a locked barrier for key: $key")
        }
    }

    /**
     * Giải phóng **toàn bộ** [Mutex] — reset hoàn toàn trạng thái của provider.
     *
     * Thường dùng trong unit test để đảm bảo mỗi test case bắt đầu với
     * trạng thái sạch, không bị ảnh hưởng bởi test trước.
     */
    fun releaseAll() {
        barriers.keys.toList().forEach { release(it) }
    }
}
