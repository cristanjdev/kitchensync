package com.kitchensync360.app

import com.kitchensync360.app.data.SupabaseClientService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Minimal HTTP/1.1 stub built on java.net only. com.sun.net.httpserver and MockWebServer are not
 * available on the Android unit-test compile classpath used by this project.
 */
private class StubHttpServer(
    private val handler: (path: String, headers: Map<String, String>, body: String) -> Pair<Int, String>
) : AutoCloseable {
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val baseUrl: String get() = "http://127.0.0.1:${serverSocket.localPort}"

    init {
        thread(isDaemon = true, name = "stub-http-accept") {
            while (!serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                thread(isDaemon = true, name = "stub-http-conn") { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket): Unit = socket.use {
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val path = requestLine.split(" ").getOrNull(1)?.substringBefore('?') ?: "/"
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyBytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bodyBytes, read, length - read)
            if (n < 0) break
            read += n
        }
        val (status, responseBody) = handler(path, headers, String(bodyBytes, Charsets.UTF_8))
        val bytes = responseBody.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${if (status in 200..299) "OK" else "Error"}\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        val output = socket.getOutputStream()
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString("US-ASCII")
            if (b == '\n'.code) return buffer.toString("US-ASCII").trimEnd('\r')
            buffer.write(b)
        }
    }

    override fun close() {
        serverSocket.close()
    }
}

class CloudSessionTest {
    @Test
    fun `concurrent snapshots rotate expired session once and use new credentials`() = runBlocking {
        val refreshes = AtomicInteger()
        val staleRequests = AtomicInteger()
        var savedRefresh: String? = null
        StubHttpServer { path, headers, body ->
            if (path == "/auth/v1/token") {
                refreshes.incrementAndGet()
                assertTrue(body.contains("old-refresh"))
                200 to """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,"user":{"id":"waiter"}}"""
            } else {
                if (headers["authorization"] != "Bearer new-access") staleRequests.incrementAndGet()
                200 to "[]"
            }
        }.use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val client = SupabaseClientService(scope) { _, refresh, _ -> savedRefresh = refresh }
                client.configure(server.baseUrl, "publishable-test")
                client.restoreSession("expired-access", "old-refresh", 0)
                val results = (1..4).map { async { client.fetchSnapshot() } }.awaitAll()
                assertTrue(results.all { it.isSuccess })
                assertEquals(1, refreshes.get())
                assertEquals(0, staleRequests.get())
                assertEquals("new-refresh", savedRefresh)
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `failed refresh is reported without sending writes using expired credentials`() = runBlocking {
        val writes = AtomicInteger()
        StubHttpServer { path, _, _ ->
            if (path.startsWith("/rest")) writes.incrementAndGet()
            400 to """{"message":"Refresh token expired"}"""
        }.use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val client = SupabaseClientService(scope)
                client.configure(server.baseUrl, "publishable-test")
                client.restoreSession("expired", "expired-refresh", 0)
                assertTrue(client.acknowledgeOrder("order-1").isFailure)
                assertEquals(0, writes.get())
            } finally {
                scope.cancel()
            }
        }
    }
}
