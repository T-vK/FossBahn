package de.openbahn.api

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * Cronet + Bahn mobile API header quirks (must run before CronetInterceptor):
 * - Cronet substitutes `application/octet-stream` when the OkHttp body has no media type → 405.
 * - OkHttp/Ktor often append `; charset=utf-8`; the mobile edge rejects that → 405.
 * - Ktor ContentNegotiation appends `Accept: application/json` beside the vendor type → 405.
 */
internal class PreserveContentTypeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val accept = request.header("Accept")
        if (accept != null && ',' in accept) {
            val preferred = accept.split(',')
                .map { it.trim() }
                .firstOrNull { "x.db.vendo" in it }
            if (preferred != null) {
                request = request.newBuilder().header("Accept", preferred).build()
            }
        }

        val body = request.body ?: return chain.proceed(request)
        val rawCt = body.contentType()?.toString()
            ?: request.header("Content-Type")
            ?: return chain.proceed(request)
        val cleaned = rawCt.substringBefore(';').trim()
        val current = body.contentType()?.toString()
        if (current != null && current == cleaned) {
            return chain.proceed(request)
        }
        val mediaType = cleaned.toMediaTypeOrNull() ?: return chain.proceed(request)
        val buffer = Buffer()
        body.writeTo(buffer)
        val newBody = buffer.readByteArray().toRequestBody(mediaType)
        val newRequest = request.newBuilder()
            .header("Content-Type", cleaned)
            .method(request.method, newBody)
            .build()
        return chain.proceed(newRequest)
    }
}
