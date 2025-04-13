package com.tuanha.deeplink.queue

import android.content.ComponentCallbacks
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.tuanha.deeplink.DeeplinkHandler
import com.tuanha.deeplink.utils.exts.awaitResume
import com.tuanha.deeplink.utils.exts.launchCollect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val list: List<DeeplinkHandler> by lazy {

    val className = "com.tuanha.deeplink.DeeplinkProvider"
    val clazz = Class.forName(className)
    val instance = clazz.getField("INSTANCE").get(null)  // <-- lấy singleton instance

    val method = clazz.getMethod("all")
    val result = method.invoke(instance) as List<*>

    result.filterIsInstance<DeeplinkHandler>()
}


abstract class DeeplinkQueueHandler {

    private val flow by lazy {

        MutableSharedFlow<Pair<String, Pair<Bundle?, Map<String, View>?>>>(replay = 1, extraBufferCapacity = Int.MAX_VALUE, onBufferOverflow = BufferOverflow.SUSPEND)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun setupDeepLink(componentCallbacks: ComponentCallbacks) {

        val lifecycleOwner = (componentCallbacks as? ComponentActivity) ?: (componentCallbacks as? Fragment) ?: return

        flow.launchCollect(lifecycleOwner) { pair ->

            lifecycleOwner.awaitResume()

            val deepLink = pair.first

            val extras = pair.second.first
            val sharedElement = pair.second.second

            val navigation = withContext(Dispatchers.IO) {

                list.find { it.acceptDeeplink(deepLink) }
            }

            if (navigation?.navigation(componentCallbacks, deepLink, extras, sharedElement) == true) {

                flow.resetReplayCache()
            }
        }
    }

    fun sendDeeplink(deepLink: String, extras: Bundle? = null, sharedElement: Map<String, View>? = null) = CoroutineScope(Dispatchers.Main.immediate).launch {

        if (!flow.replayCache.toMap().containsKey(deepLink)) {

            flow.emit(deepLink to (extras to sharedElement))
        }
    }
}