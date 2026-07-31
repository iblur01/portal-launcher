package com.iblu01.portallauncher.photo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class OkHttpTransportSecurityTest {
    @Test fun `redirects are not followed and credential never reaches target`() = runBlocking {
        val targetRequests = AtomicInteger()
        val target = ServerSocket(0).apply { soTimeout = 1_000 }
        val targetThread = thread(start = true, name = "redirect-target") {
            try {
                target.accept().use { targetRequests.incrementAndGet() }
            } catch (_: SocketTimeoutException) {
                // Expected: redirect following is disabled.
            }
        }

        val origin = ServerSocket(0).apply { soTimeout = 2_000 }
        val originThread = thread(start = true, name = "redirect-origin") {
            origin.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (reader.readLine()?.isNotEmpty() == true) Unit
                val response = buildString {
                    append("HTTP/1.1 302 Found\r\n")
                    append("Location: http://127.0.0.1:${target.localPort}/target\r\n")
                    append("Content-Length: 0\r\n")
                    append("Connection: close\r\n\r\n")
                }
                socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
            }
        }

        try {
            val response = OkHttpTransport().get(
                "http://127.0.0.1:${origin.localPort}/start",
                mapOf("x-api-key" to "must-not-forward"),
            )
            originThread.join(2_000)
            targetThread.join(2_000)
            assertEquals(302, response.code)
            assertEquals(0, targetRequests.get())
        } finally {
            origin.close()
            target.close()
        }
    }
}
