package com


import android.content.ComponentCallbacks
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.tuanha.app.R
import com.tuanha.deeplink.DeeplinkHandler
import com.tuanha.deeplink.annotation.Deeplink

class HomeFragment : Fragment(R.layout.item_test2) {

}

@Deeplink(queue = "Confirm")
class HomeDeeplink : DeeplinkHandler {

    override fun getDeeplink(): String = "app://home"

    override suspend fun navigation(componentCallbacks: ComponentCallbacks, deepLink: String, extras: Map<String, Any?>?, sharedElement: Map<String, View>?): Boolean {

        if (componentCallbacks !is FragmentActivity) return false

        componentCallbacks.supportFragmentManager.beginTransaction()
            .add(R.id.main, HomeFragment(), "")
            .commitAllowingStateLoss()

        return true
    }
}