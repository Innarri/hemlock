/*
 * Copyright (c) 2019 Kenneth H. Cox
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.evergreen_ils.net

import android.net.Uri
import android.text.TextUtils
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import org.evergreen_ils.Api
import org.evergreen_ils.android.Log
import org.opensrf.net.http.HttpConnection
import org.opensrf.util.GatewayResult
import org.opensrf.util.JSONWriter
import org.opensrf.util.OSRFObject
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

const val TAG = "Gateway"
private const val INITIAL_URL_SIZE = 128

// Notes on caching.  We add 2 parameters to every request to ensure a coherent cache:
// clientCacheKey (the app versionCode), and serverCacheKey (the server ils-version).
// In this way we can force cache misses by either upgrading the server or the client.
// Server upgrades sometimes involve incompatible IDL which can cause OSRF decode crashes.
object Gateway {
    lateinit var baseUrl: String
    lateinit var clientCacheKey: String

    var actor: ActorService = GatewayActor
    var auth: AuthService = GatewayAuth
    var circ: CircService = GatewayCirc
    var fielder: FielderService = GatewayFielder
    var pcrud: PCRUDService = GatewayPCRUD
    var search: SearchService = GatewaySearch

    val conn: HttpConnection by lazy { HttpConnection(baseUrl.plus("/osrf-gateway-v1")) }
    private var _serverCacheKey: String? = null
    private val startTime = System.currentTimeMillis()
    var serverCacheKey: String
        get() = _serverCacheKey ?: startTime.toString()
        set(value) { _serverCacheKey = value }

    var randomErrorPercentage = 0
    var defaultTimeoutMs = 30_000
    var searchTimeoutMs = 60_000

    fun buildQuery(service: String?, method: String?, params: Array<Any?>, addCacheArgs: Boolean = true): String {
        val sb = StringBuilder(INITIAL_URL_SIZE)
        sb.append("service=").append(service)
        sb.append("&method=").append(method)
        for (param in params) {
            sb.append("&param=")
            sb.append(Uri.encode(JSONWriter(param).write(), "UTF-8"))
        }

        if (addCacheArgs) {
            sb.append("&_ck=").append(clientCacheKey)
            sb.append("&_sk=").append(serverCacheKey)
        }

        return sb.toString()
    }

    @JvmOverloads
    fun buildUrl(service: String, method: String, args: Array<Any?>, addCacheArgs: Boolean = true): String {
        return baseUrl.plus("/osrf-gateway-v1?").plus(
                buildQuery(service, method, args, addCacheArgs))
    }

    fun getUrl(relativeUrl: String): String {
        return baseUrl.plus(relativeUrl)
    }

    fun getIDLUrl(shouldCache: Boolean = true): String {
        val params = mutableListOf<String>()
        for (className in TextUtils.split(Api.IDL_CLASSES_USED, ",")) {
            params.add("class=$className")
        }
        if (shouldCache) {
            params.add("_ck=$clientCacheKey")
            params.add("_sk=$serverCacheKey")
        }
        return baseUrl.plus("/reports/fm_IDL.xml?")
                .plus(TextUtils.join("&", params))
    }

    // Make an OSRF Gateway request from inside a CoroutineScope.  `block` is expected to return T or throw
    suspend fun <T> fetch(service: String, method: String, args: Array<Any?>, options: RequestOptions, block: (GatewayResult) -> T) =
            fetchImpl(service, method, args, options, block)
    suspend fun <T> fetch(service: String, method: String, args: Array<Any?>, shouldCache: Boolean, block: (GatewayResult) -> T) =
            fetchImpl(service, method, args, RequestOptions(defaultTimeoutMs, shouldCache, true), block)

    private suspend fun <T> fetchImpl(service: String, method: String, args: Array<Any?>, options: RequestOptions, block: (GatewayResult) -> T) = suspendCoroutine<T> { cont ->
        maybeInjectRandomError()
        val url = buildUrl(service, method, args, options.shouldCache)
        val r = GatewayJsonObjectRequest(
                url,
                Request.Priority.NORMAL,
                Response.Listener { response ->
                    try {
                        val res = block(response)
                        cont.resumeWith(Result.success(res))
                    } catch (ex: Exception) {
                        cont.resumeWithException(ex)
                    }
                },
                Response.ErrorListener { error ->
                    cont.resumeWithException(error)
                })
        enqueueRequest(r, options)
    }

    // fetchObject - make gateway request and expect json payload of OSRFObject
    suspend fun fetchObject(service: String, method: String, args: Array<Any?>, shouldCache: Boolean) = fetch(service, method, args, shouldCache) { result ->
        result.asObject()
    }

    // fetchOptionalObject - expect OSRFObject or empty
    suspend fun fetchOptionalObject(service: String, method: String, args: Array<Any?>, shouldCache: Boolean) = fetch(service, method, args, shouldCache) { result ->
        result.asOptionalObject()
    }

    // fetchObjectArray - make gateway request and expect json payload of [OSRFObject]
    suspend fun fetchObjectArray(service: String, method: String, args: Array<Any?>, shouldCache: Boolean) = fetch(service, method, args, shouldCache) { result ->
        result.asObjectArray()
    }

    // fetchStringPayload - make gateway request and expect json payload of String
    suspend fun fetchObjectString(service: String, method: String, args: Array<Any?>, shouldCache: Boolean) = fetch(service, method, args, shouldCache) { result ->
        result.asString()
    }

    // fetchString - fetch url and expect a string response
    suspend fun fetchString(url: String, shouldCache: Boolean = true) = suspendCoroutine<String> { cont ->
        val r = StringRequest(Request.Method.GET,
                url,
                Response.Listener { response ->
                    cont.resumeWith(Result.success(response))
                },
                Response.ErrorListener { error ->
                    cont.resumeWithException(error)
                })
        enqueueRequest(r, RequestOptions(defaultTimeoutMs, shouldCache, true))
    }

    private fun enqueueRequest(r: Request<*>, options: RequestOptions) {
        r.setShouldCache(options.shouldCache)
        r.retryPolicy = DefaultRetryPolicy(
                options.timeoutMs,
                if (options.shouldRetry) 1 else 0,
                0.0f)//do not increase timeout on retry
        Volley.getInstance().addToRequestQueue(r)
    }

    /** for testing, inject an error randomly */
    private fun maybeInjectRandomError() {
        if (randomErrorPercentage <= 0) return
        val r = Random.nextInt(100)
        Log.d(TAG, "[kcxxx] Random error if $r < $randomErrorPercentage")
        if (r < randomErrorPercentage) throw GatewayError("Random error $r < $randomErrorPercentage")
    }
}
