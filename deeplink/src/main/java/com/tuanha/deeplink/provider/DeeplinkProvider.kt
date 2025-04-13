package com.tuanha.deeplink.provider

import com.tuanha.deeplink.DeeplinkHandler

interface DeeplinkProvider {
    fun provider(): List<DeeplinkHandler>
}