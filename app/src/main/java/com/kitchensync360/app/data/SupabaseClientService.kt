package com.kitchensync360.app.data

import com.kitchensync360.app.model.CreateOrderItemPayload
import com.kitchensync360.app.model.OrderDto
import com.kitchensync360.app.model.OrderItemDto
import com.kitchensync360.app.model.UserProfileDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class AuthSessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: AuthUserDto
)

@Serializable
data class AuthUserDto(
    val id: String,
    val email: String = ""
)

@Serializable
private data class SupabaseErrorPayload(
    val message: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val details: String? = null,
    val hint: String? = null
)

class SupabaseClientService(
    private val scope: CoroutineScope,
    private val onSessionUpdated: (String, String?, Long) -> Unit = { _, _, _ -> }
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var baseUrl: String = ""

    @Volatile
    private var publishableKey: String = ""

    @Volatile
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var expiresAtMillis: Long = 0
    private val sessionLock = Any()

    fun restoreSession(token: String?, refresh: String?, expiresAt: Long) = synchronized(sessionLock) {
        accessToken = token
        refreshToken = refresh
        expiresAtMillis = expiresAt
    }

    private fun saveSession(session: AuthSessionResponse) {
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        expiresAtMillis = System.currentTimeMillis() + session.expiresIn * 1000
        onSessionUpdated(session.accessToken, session.refreshToken, expiresAtMillis)
        webSocket?.send(buildJsonObject {
            put("topic", "realtime:public:kitchensync")
            put("event", "access_token")
            put("payload", buildJsonObject { put("access_token", session.accessToken) })
            put("ref", refCounter.getAndIncrement().toString())
        }.toString())
    }

    private fun freshToken(): String? = synchronized(sessionLock) {
        if (accessToken != null && System.currentTimeMillis() >= expiresAtMillis - 60_000) {
            val refresh = refreshToken ?: return@synchronized accessToken
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                .header("apikey", publishableKey)
                .post(buildJsonObject { put("refresh_token", refresh) }.toString().toRequestBody(jsonMediaType))
                .build()
            saveSession(executeJson<AuthSessionResponse>(request))
        }
        accessToken
    }

    @Volatile
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val refCounter = AtomicInteger(1)

    fun configure(url: String, key: String) {
        baseUrl = url.trim().trimEnd('/')
        publishableKey = key.trim()
    }

    fun isConfigured(): Boolean = baseUrl.startsWith("http") && publishableKey.isNotBlank()

    suspend fun signInWithPassword(email: String, password: String): Result<Pair<AuthSessionResponse, UserProfileDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(isConfigured()) { "Supabase URL and publishable key are not configured." }
                val payload = buildJsonObject {
                    put("email", email)
                    put("password", password)
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/auth/v1/token?grant_type=password")
                    .header("apikey", publishableKey)
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                val session = executeJson<AuthSessionResponse>(request)
                synchronized(sessionLock) { saveSession(session) }

                val profileRequest = Request.Builder()
                    .url("$baseUrl/rest/v1/profiles?id=eq.${session.user.id}&select=*")
                    .header("apikey", publishableKey)
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val profiles = executeJson<List<UserProfileDto>>(profileRequest)
                val profile = profiles.firstOrNull()
                    ?: throw IllegalStateException("No profile record found in public.profiles for ${session.user.email}")

                session to profile
            }
        }

    suspend fun fetchSnapshot(): Result<Pair<List<OrderDto>, List<OrderItemDto>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken ?: throw IllegalStateException("Not authenticated with Supabase")
                val ordersRequest = Request.Builder()
                    .url("$baseUrl/rest/v1/orders?select=*&order=created_at.desc")
                    .header("apikey", publishableKey)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val itemsRequest = Request.Builder()
                    .url("$baseUrl/rest/v1/order_items?select=*&order=created_at.asc")
                    .header("apikey", publishableKey)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val orders = executeJson<List<OrderDto>>(ordersRequest)
                val items = executeJson<List<OrderItemDto>>(itemsRequest)
                orders to items
            }
        }

    suspend fun createOrder(tableNumber: Int, items: List<CreateOrderItemPayload>, requestId: String): Result<OrderDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("p_table_number", tableNumber)
                    put("p_request_id", requestId)
                    put("p_items", json.encodeToJsonElement(items))
                }
                callRpc<OrderDto>("create_order", body)
            }
        }

    suspend fun acceptStationOrder(orderId: String, stationId: String): Result<List<OrderItemDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("p_order_id", orderId)
                    put("p_station", stationId)
                }
                callRpc<List<OrderItemDto>>("accept_station_order", body)
            }
        }

    suspend fun markItemReady(itemId: String): Result<OrderDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("p_item_id", itemId)
                }
                callRpc<OrderDto>("mark_item_ready", body)
            }
        }

    suspend fun acknowledgeOrder(orderId: String): Result<OrderDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("p_order_id", orderId)
                }
                callRpc<OrderDto>("acknowledge_order", body)
            }
        }

    suspend fun markOrderPickedUp(orderId: String): Result<OrderDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("p_order_id", orderId)
                }
                callRpc<OrderDto>("mark_order_picked_up", body)
            }
        }

    suspend fun cancelOrder(orderId: String): Result<OrderDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("p_order_id", orderId)
                }
                callRpc<OrderDto>("cancel_order", body)
            }
        }

    fun connectRealtime(
        onConnected: () -> Unit,
        onReconnecting: () -> Unit,
        onChangeNotification: () -> Unit
    ) {
        disconnectRealtime()
        if (!isConfigured() || accessToken == null) return

        val wsScheme = if (baseUrl.startsWith("https")) "wss" else "ws"
        val hostPart = baseUrl.removePrefix("https://").removePrefix("http://")
        val wsUrl = "$wsScheme://$hostPart/realtime/v1/websocket?apikey=$publishableKey&vsn=1.0.0"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            private var joinRef: String? = null
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val token = accessToken ?: publishableKey
                joinRef = refCounter.getAndIncrement().toString()
                val joinPayload = """
                    {
                      "topic": "realtime:public:kitchensync",
                      "event": "phx_join",
                      "payload": {
                        "config": {
                          "broadcast": { "self": false },
                          "presence": { "key": "" },
                          "postgres_changes": [
                            { "event": "*", "schema": "public", "table": "orders" },
                            { "event": "*", "schema": "public", "table": "order_items" }
                          ]
                        },
                        "access_token": "$token"
                      },
                      "ref": "$joinRef"
                    }
                """.trimIndent()
                webSocket.send(joinPayload)
                startHeartbeat(webSocket)
            }

            // Callbacks from a socket that has already been replaced must not tear down
            // the current healthy connection or trigger extra reconnects.
            private fun isCurrent(socket: WebSocket) = socket === this@SupabaseClientService.webSocket

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(webSocket)) return
                val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                val event = message["event"]?.jsonPrimitive?.content
                val payload = message["payload"] as? JsonObject
                when {
                    event == "phx_reply" && message["ref"]?.jsonPrimitive?.content == joinRef -> {
                        if (payload?.get("status")?.jsonPrimitive?.content == "ok") {
                            onConnected()
                            onChangeNotification()
                        } else {
                            onReconnecting()
                            scheduleReconnect(onConnected, onReconnecting, onChangeNotification)
                        }
                    }
                    event == "phx_error" || (event == "system" && payload?.get("status")?.jsonPrimitive?.content == "error") -> {
                        onReconnecting()
                        scheduleReconnect(onConnected, onReconnecting, onChangeNotification)
                    }
                    event == "postgres_changes" -> onChangeNotification()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent(webSocket)) return
                onReconnecting()
                scheduleReconnect(onConnected, onReconnecting, onChangeNotification)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isCurrent(webSocket) && code != 1000) {
                    onReconnecting()
                    scheduleReconnect(onConnected, onReconnecting, onChangeNotification)
                }
            }
        })
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(20_000L)
                val ref = refCounter.getAndIncrement().toString()
                val hb = """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"$ref"}"""
                ws.send(hb)
            }
        }
    }

    private fun scheduleReconnect(
        onConnected: () -> Unit,
        onReconnecting: () -> Unit,
        onChangeNotification: () -> Unit
    ) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(3_000L)
            if (accessToken != null && isConfigured()) {
                connectRealtime(
                    onConnected = {
                        onConnected()
                        // Fresh snapshot after reconnection as required by implementation plan
                        onChangeNotification()
                    },
                    onReconnecting = onReconnecting,
                    onChangeNotification = onChangeNotification
                )
            }
        }
    }

    fun disconnectRealtime() {
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }

    private inline fun <reified T> callRpc(functionName: String, body: JsonObject): T {
        val token = accessToken ?: throw IllegalStateException("Not authenticated")
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$functionName")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        return executeJson(request)
    }

    private inline fun <reified T> executeJson(request: Request): T {
        val authenticatedRequest = if (request.header("Authorization") != null) {
            request.newBuilder().header("Authorization", "Bearer ${freshToken()}").build()
        } else request
        httpClient.newCall(authenticatedRequest).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedError = runCatching {
                    json.decodeFromString<SupabaseErrorPayload>(rawBody)
                }.getOrNull()
                val message = parsedError?.message
                    ?: parsedError?.errorDescription
                    ?: parsedError?.error
                    ?: rawBody.ifBlank { "HTTP ${response.code}" }
                throw IOException(message)
            }
            return json.decodeFromString(rawBody)
        }
    }
}
