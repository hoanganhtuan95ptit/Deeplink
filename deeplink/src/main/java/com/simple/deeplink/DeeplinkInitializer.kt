package com.simple.deeplink

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.startup.Initializer
import com.simple.auto.register.AutoRegisterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

/**
 * Khởi tạo toàn bộ hệ thống Deeplink tự động khi app start.
 *
 * Class này implement [Initializer] từ thư viện **AndroidX App Startup**,
 * cho phép khởi tạo mà không cần gọi thủ công trong `Application.onCreate()`.
 * Chỉ cần khai báo trong `AndroidManifest.xml` là đủ.
 *
 * ## Hai nhiệm vụ chính
 * 1. **Đăng ký handler:** Subscribe [AutoRegisterManager] để nhận tất cả
 *    [DeeplinkRegister] implementation và gọi `register()` → nạp handler vào [DeeplinkResolver].
 * 2. **Attach lifecycle:** Lắng nghe vòng đời Activity/Fragment để [DeeplinkCoordinator]
 *    biết khi nào UI sẵn sàng nhận deeplink.
 *
 * ## Luồng khởi tạo
 * ```
 * App start → AndroidX Startup → DeeplinkInitializer.create()
 *   ├─ subscribeHandlerRegistrations()  → nạp tất cả @Deeplink handler
 *   └─ registerActivityLifecycleCallbacks()
 *        └─ mỗi Activity onCreate
 *             ├─ DeeplinkCoordinator.attach(activity)
 *             └─ (nếu FragmentActivity) lắng nghe Fragment attach
 *                  └─ DeeplinkCoordinator.attach(fragment)
 * ```
 *
 * @see DeeplinkCoordinator
 * @see DeeplinkRegister
 */
class DeeplinkInitializer : Initializer<Unit> {

    /**
     * Điểm khởi tạo được AndroidX Startup gọi một lần duy nhất khi app start.
     *
     * @param context Application context — dùng để đăng ký Activity lifecycle callback.
     */
    override fun create(context: Context) {
        subscribeHandlerRegistrations()
        registerActivityLifecycleCallbacks(context)
    }

    /**
     * Khai báo danh sách [Initializer] khác phải chạy **trước** class này.
     *
     * Trả về danh sách rỗng vì Deeplink không phụ thuộc vào Initializer nào khác.
     */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    // ─── Handler Registration ─────────────────────────────────────────────────

    /**
     * Subscribe [AutoRegisterManager] để nhận tất cả class implement [DeeplinkRegister].
     *
     * Khi [AutoRegisterManager] emit ra danh sách implementation mới
     * (bao gồm class `HandlerRegisterImpl` được sinh ra bởi KSP),
     * hàm sẽ gọi [DeeplinkRegister.register] trên từng item để nạp
     * handler vào [DeeplinkResolver].
     *
     * Dùng [CoroutineScope] riêng với [Dispatchers.Main] để đảm bảo
     * không block Main thread, đồng thời vẫn chạy trên đúng dispatcher.
     */
    private fun subscribeHandlerRegistrations() {
        CoroutineScope(Dispatchers.Main).launch {
            AutoRegisterManager.subscribe(DeeplinkRegister::class.java).collect { registers ->
                registers.forEach { register ->
                    register.register()
                }
            }
        }
    }

    // ─── Activity Lifecycle ───────────────────────────────────────────────────

    /**
     * Đăng ký [Application.ActivityLifecycleCallbacks] để theo dõi vòng đời Activity.
     *
     * Chỉ quan tâm đến sự kiện `onActivityCreated` — đây là thời điểm sớm nhất
     * để attach [DeeplinkCoordinator] mà vẫn đảm bảo Activity đã có lifecycle scope.
     *
     * Chỉ xử lý [ComponentActivity] (bao gồm `AppCompatActivity`, `FragmentActivity`)
     * vì cần `lifecycleScope` và `repeatOnLifecycle`.
     *
     * @param context Context để cast sang [Application] và gọi [Application.registerActivityLifecycleCallbacks].
     */
    private fun registerActivityLifecycleCallbacks(context: Context) {
        val application = context as? Application ?: return

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Chỉ xử lý ComponentActivity — cần lifecycleScope và repeatOnLifecycle
                if (activity !is ComponentActivity) return
                setupActivity(activity)
            }

            // Các callback còn lại không cần xử lý
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    // ─── Activity Setup ───────────────────────────────────────────────────────

    /**
     * Cài đặt Deeplink cho một Activity cụ thể:
     * 1. Attach [DeeplinkCoordinator] để Activity có thể nhận và xử lý deeplink.
     * 2. Nếu là [FragmentActivity], đăng ký thêm callback để attach coordinator
     *    cho từng Fragment khi chúng được gắn vào Activity.
     *
     * @param activity Activity vừa được tạo, đã đảm bảo là [ComponentActivity].
     */
    private fun setupActivity(activity: ComponentActivity) {
        // Attach để Activity nhận deeplink khi đang ở foreground
        DeeplinkCoordinator.attach(activity)

        // Fragment cũng cần nhận deeplink độc lập với Activity
        if (activity is FragmentActivity) {
            activity.observeFragmentAttachments(object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentAttached(fm: FragmentManager, fragment: Fragment, context: Context) {
                    DeeplinkCoordinator.attach(fragment)
                }
            })
        }
    }

    // ─── Fragment Lifecycle Extension ─────────────────────────────────────────

    /**
     * Extension function để quan sát sự kiện attach/detach Fragment trong [FragmentActivity].
     *
     * Dùng [channelFlow] để wrap callback-based API thành Flow, đảm bảo
     * `unregisterFragmentLifecycleCallbacks` được gọi đúng lúc khi lifecycle kết thúc
     * (thông qua `awaitClose`).
     *
     * Tham số `recursive = true` trong [FragmentManager.registerFragmentLifecycleCallbacks]
     * đảm bảo nhận callback từ **tất cả** Fragment con, kể cả Fragment lồng nhau.
     *
     * @param callbacks Callback xử lý sự kiện Fragment lifecycle.
     */
    private fun FragmentActivity.observeFragmentAttachments(
        callbacks: FragmentManager.FragmentLifecycleCallbacks,
    ) = channelFlow<Unit> {
        // Đăng ký — recursive = true để nhận cả Fragment lồng nhau
        supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)

        // Tự động hủy đăng ký khi lifecycle của Activity kết thúc
        awaitClose {
            supportFragmentManager.unregisterFragmentLifecycleCallbacks(callbacks)
        }
    }.launchIn(this.lifecycleScope)
}
