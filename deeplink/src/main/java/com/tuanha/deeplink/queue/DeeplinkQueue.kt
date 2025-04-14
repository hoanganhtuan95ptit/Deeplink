package com.tuanha.deeplink.queue

import android.content.ComponentCallbacks
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.tuanha.deeplink.flow
import com.tuanha.deeplink.groupDeeplink
import com.tuanha.deeplink.utils.exts.awaitResume
import com.tuanha.deeplink.utils.exts.launchCollect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

abstract class DeeplinkQueue {

    abstract fun getQueue(): String

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun setupDeepLink(componentCallbacks: ComponentCallbacks) {

        val lifecycleOwner = (componentCallbacks as? ComponentActivity) ?: (componentCallbacks as? Fragment) ?: return

        flow.launchCollect(lifecycleOwner) { pair ->

            val deepLink = pair.first

            val extras = pair.second.first
            val sharedElement = pair.second.second

            val navigation = withContext(Dispatchers.IO) {

                groupDeeplink[getQueue()]?.find {

                    it.acceptDeeplink(deepLink)
                }
            }

            lifecycleOwner.awaitResume()

            if (navigation?.navigation(componentCallbacks, deepLink, extras, sharedElement) == true) {

                flow.resetReplayCache()
            }
        }
    }
}
