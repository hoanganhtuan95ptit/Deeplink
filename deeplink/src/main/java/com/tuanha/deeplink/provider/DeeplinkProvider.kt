package com.tuanha.deeplink.provider

import com.tuanha.deeplink.DeeplinkHandler

abstract class DeeplinkProvider {
    abstract fun provider(): List<Pair<String, DeeplinkHandler>>
}
