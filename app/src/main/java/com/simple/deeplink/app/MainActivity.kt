package com.simple.deeplink.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.simple.deeplink.Deeplink
import com.simple.deeplink.DeeplinkHandler
import com.simple.deeplink.app.databinding.ActivityMainBinding
import com.simple.deeplink.sendDeeplink

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        sendDeeplink("app://b?create", extras = mapOf("userId" to 1))
        sendDeeplink("app://a")
    }
}

class BFragment : Fragment() {

}

@Deeplink
class BDeeplinkHandler : DeeplinkHandler {

    override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
        return deeplink.startsWith("app://b", true)
    }

    override suspend fun navigate(fragment: Fragment, deeplink: String, extras: Map<String, Any?>?, sharedElement: Map<String, View>?): Boolean {

        // mỏ màn hình BFragment
        return true
    }
}


class AFragment : Fragment() {

}

@Deeplink
class ADeeplinkHandler : DeeplinkHandler {

    override val deeplink: String by lazy {
        "app://a"
    }

    override suspend fun navigate(fragmentActivity: FragmentActivity, deeplink: String, extras: Map<String, Any?>?, sharedElement: Map<String, View>?): Boolean {
        // mở màn hình AFragment
        return true
    }
}