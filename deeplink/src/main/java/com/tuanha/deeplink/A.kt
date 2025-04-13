package com.tuanha.deeplink

import android.util.Log
import com.google.auto.service.AutoService

@AutoService(C::class)
class A : C {
    override fun run() {
        Log.d("tuanha", "A running")
    }
}
