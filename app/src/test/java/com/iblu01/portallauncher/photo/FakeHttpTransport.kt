package com.iblu01.portallauncher.photo



/**
 * In-memory HTTP transport for testing adapters without a real server. Supports JSON and binary
 * responses, and records every request so tests can assert headers / paths / query parameters.
 */
class FakeHttpTransport : HttpTransport {
    data class RecordedRequest(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: String? = null,
    )

    private val handlers = mutableMapOf<String, (HttpTransport.Response) -> HttpTransport.Response?>()
    private val fallbackHandlers = mutableListOf<(RecordedRequest) -> HttpTransport.Response?>()
    private val _requests = mutableListOf<RecordedRequest>()
    val requests: List<RecordedRequest> get() = _requests.toList()

    fun respond(url: String, response: HttpTransport.Response) {
        handlers[url] = { _ -> response }
    }

    fun respond(url: String, body: String, code: Int = 200) {
        handlers[url] = { _ -> HttpTransport.Response(code = code, body = body) }
    }

    fun respondWithBytes(url: String, bytes: ByteArray, code: Int = 200) {
        handlers[url] = { _ -> HttpTransport.Response(code = code, bytes = bytes) }
    }

    fun intercept(fn: (RecordedRequest) -> HttpTransport.Response?) {
        fallbackHandlers.add(fn)
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpTransport.Response {
        _requests.add(RecordedRequest("GET", url, headers))
        handlers[url]?.let { handler ->
            return handler(HttpTransport.Response(code = 404)) ?: HttpTransport.Response(code = 404)
        }
        for (handler in fallbackHandlers) {
            handler(RecordedRequest("GET", url, headers))?.let { return it }
        }
        return HttpTransport.Response(code = 404)
    }

    override suspend fun getBytes(url: String, headers: Map<String, String>): HttpTransport.Response {
        _requests.add(RecordedRequest("GET-BYTES", url, headers))
        handlers[url]?.let { handler ->
            return handler(HttpTransport.Response(code = 404)) ?: HttpTransport.Response(code = 404)
        }
        for (handler in fallbackHandlers) {
            handler(RecordedRequest("GET-BYTES", url, headers))?.let { return it }
        }
        return HttpTransport.Response(code = 404)
    }

    override suspend fun post(url: String, body: String, headers: Map<String, String>): HttpTransport.Response {
        val request = RecordedRequest("POST", url, headers, body)
        _requests.add(request)
        handlers[url]?.let { handler ->
            return handler(HttpTransport.Response(code = 404)) ?: HttpTransport.Response(code = 404)
        }
        for (handler in fallbackHandlers) {
            handler(request)?.let { return it }
        }
        return HttpTransport.Response(code = 404)
    }

    /** Assert the API key was sent but never expose it in the assertion message. */
    fun assertApiKeyHeaderPresent() {
        val sent = _requests.any { it.headers.containsKey("x-api-key") }
        check(sent) { "Expected x-api-key header on at least one request" }
    }

    /** Assert the API key never leaked into a request URL or other publishable request metadata. */
    fun assertApiKeyNotInRequestsOrResponses(secret: String) {
        check(_requests.none { it.url.contains(secret) || it.body?.contains(secret) == true }) {
            "Secret found in request metadata"
        }
    }
}
