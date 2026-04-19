package com.simple.deeplink.app

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.simple.deeplink.Deeplink
import com.simple.deeplink.DeeplinkCoordinator
import com.simple.deeplink.DeeplinkHandler
import com.simple.deeplink.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DeeplinkCoordinator.sendDeeplink("app://b")
    }
}

@Deeplink
class BDeeplinkHandler : DeeplinkHandler {

    override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
        return deeplink == "app://b"
    }

    override suspend fun navigate(lifecycleOwner: LifecycleOwner, deeplink: String, extras: Map<String, Any?>?, sharedElement: Map<String, View>?): Boolean {
        Log.d("tuanha", "navigate: B")
        return true
    }
}

@Deeplink
class ADeeplinkHandler : DeeplinkHandler {

    override fun canHandle(lifecycleOwner: LifecycleOwner, deeplink: String): Boolean {
        return deeplink == "app://a"
    }

    override suspend fun navigate(lifecycleOwner: LifecycleOwner, deeplink: String, extras: Map<String, Any?>?, sharedElement: Map<String, View>?): Boolean {
        Log.d("tuanha", "navigate: A")
        return true
    }
}