package com.tuanha.adapter

import android.util.Log
import com.google.auto.service.AutoService
import com.tuanha.deeplink.C

@AutoService(C::class)
class B : C {
    override fun run() {
        Log.d("tuanha", "B running")
    }
}