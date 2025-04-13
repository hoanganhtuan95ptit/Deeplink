package com.tuanha.deeplink

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.startup.Initializer
import com.tuanha.deeplink.queue.DeeplinkQueueHandler

class DeeplinkInitializer : Initializer<Unit> {

    override fun create(context: Context) {

        val className = "com.tuanha.deeplink.DeeplinkQueueProvider"
        val clazz = Class.forName(className)
        val instance = clazz.getField("INSTANCE").get(null)  // <-- lấy singleton instance

        val method = clazz.getMethod("all")
        val result = method.invoke(instance) as List<*>
        val handlers = result.filterIsInstance<DeeplinkQueueHandler>()

        (context as? Application)?.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

                handlers.forEach {
                    it.setupDeepLink(activity)
                }

                (activity as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(

                    object : FragmentManager.FragmentLifecycleCallbacks() {

                        override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
                            super.onFragmentAttached(fm, f, context)

                            handlers.forEach {
                                it.setupDeepLink(f)
                            }

                        }

                        override fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
                            super.onFragmentCreated(fm, f, savedInstanceState)
                        }
                    },
                    true // <-- true để theo dõi cả nested fragments
                )
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        })

        return
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}