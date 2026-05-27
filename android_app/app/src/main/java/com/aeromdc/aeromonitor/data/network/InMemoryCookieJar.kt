package com.aeromdc.aeromonitor.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Simple in-memory cookie jar for OkHttp — maintains the session cookie
 * across login and dashboard requests, just like httpx's built-in cookie jar.
 */
class InMemoryCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existing = cookieStore.getOrPut(host) { mutableListOf() }
        cookies.forEach { newCookie ->
            existing.removeAll { it.name == newCookie.name }
            existing.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val matching = mutableListOf<Cookie>()
        cookieStore.entries.forEach { (host, cookies) ->
            if (url.host.endsWith(host) || host.endsWith(url.host)) {
                matching.addAll(cookies.filter { it.matches(url) })
            }
        }
        return matching
    }

    fun clear() = cookieStore.clear()
}
