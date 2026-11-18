package com.kitchensync360.app.data

import android.content.Context
import com.kitchensync360.app.BuildConfig
import com.kitchensync360.app.model.AppRole
import com.kitchensync360.app.model.ConnectionMode
import com.kitchensync360.app.model.CreateOrderItemPayload
import com.kitchensync360.app.model.DEMO_RESTAURANT_ID
import com.kitchensync360.app.model.ItemStatus
import com.kitchensync360.app.model.OrderDto
import com.kitchensync360.app.model.OrderItemDto
import com.kitchensync360.app.model.OrderStatus
import com.kitchensync360.app.model.OrderWithItems
import com.kitchensync360.app.model.PresetAccounts
import com.kitchensync360.app.model.PresetDemoAccount
import com.kitchensync360.app.model.StationType
import com.kitchensync360.app.model.SyncStatus
import com.kitchensync360.app.model.UserProfileDto
import com.kitchensync360.app.notification.WearOrderNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID

@Serializable
private data class PersistedLocalStore(
    val nextOrderNumber: Int = 28,
    val orders: List<OrderDto> = emptyList(),
    val items: List<OrderItemDto> = emptyList()
)

data class RepositorySnapshot(
    val currentUser: UserProfileDto? = null,
    val isCreatingOrder: Boolean = false,
    val isManualRefreshing: Boolean = false,
    val selectedStation: StationType = StationType.GRILL,
    val connectionMode: ConnectionMode = ConnectionMode.LOCAL_DEMO,
    val syncStatus: SyncStatus = SyncStatus.CONNECTED,
    val supabaseUrl: String = "",
    val supabaseKeyConfigured: Boolean = false,
    val orders: List<OrderWithItems> = emptyList(),
    val pendingOrderIds: Set<String> = emptySet(),
    val pendingItemIds: Set<String> = emptySet(),
    val failedWriteByOrderId: Map<String, String> = emptyMap(),
    val bannerMessage: String? = null,
    val bannerIsError: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val notificationsEnabledOnDevice: Boolean = true,
    val phase1WatchStatus: String? = null,
    val lastSyncedAtEpochMillis: Long = System.currentTimeMillis()
)

class KitchenSyncRepository private constructor(
    private val appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val localStoreFile = File(appContext.filesDir, LOCAL_STORE_FILENAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val supabaseClient = SupabaseClientService(scope) { token, refresh, expiresAt ->
        prefs.edit().putString(KEY_SAVED_ACCESS_TOKEN, token)
            .putString("saved_refresh_token", refresh).putLong("session_expires_at", expiresAt).commit()
    }
    private val creatingOrder = java.util.concurrent.atomic.AtomicBoolean(false)
    val notificationManager = WearOrderNotificationManager(appContext)
    private val localMutex = Mutex()
    private var cloudPollJob: Job? = null
    private val snapshotRequestSeq = java.util.concurrent.atomic.AtomicLong(0)
    private val snapshotApplyLock = Any()
    private var lastAppliedSnapshotSeq = 0L

    private val _state = MutableStateFlow(RepositorySnapshot())
    val state: StateFlow<RepositorySnapshot> = _state.asStateFlow()

    init {
        initializeConfigurationAndRestoreState()
    }

    private fun initializeConfigurationAndRestoreState() {
        // There is no in-app Settings screen: always use the backend built into the app (local.properties),
        // and the cloud whenever it is configured. Old values saved by the removed Settings dialog are ignored.
        supabaseClient.configure(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        val mode = if (supabaseClient.isConfigured()) {
            ConnectionMode.SUPABASE_CLOUD
        } else {
            ConnectionMode.LOCAL_DEMO
        }

        // Ensure local demo store is initialized or loaded from disk
        if (!localStoreFile.exists()) {
            val seeded = createInitialSeedStore()
            saveLocalStore(seeded)
        }
        val localStore = loadLocalStore()
        val combined = combineOrdersWithItems(localStore.orders, localStore.items)

        val savedEmail = prefs.getString(KEY_SAVED_USER_EMAIL, null)
        val savedRole = prefs.getString(KEY_SAVED_USER_ROLE, null)
        val savedUserId = prefs.getString(KEY_SAVED_USER_ID, null)
        val savedDisplayName = prefs.getString(KEY_SAVED_USER_NAME, null)
        val savedToken = prefs.getString(KEY_SAVED_ACCESS_TOKEN, null)

        val restoredUser = if (savedEmail != null && savedRole != null && savedUserId != null) {
            UserProfileDto(
                id = savedUserId,
                email = savedEmail,
                restaurantId = prefs.getString("saved_restaurant_id", DEMO_RESTAURANT_ID) ?: DEMO_RESTAURANT_ID,
                role = savedRole,
                displayName = savedDisplayName ?: savedEmail
            )
        } else {
            null
        }

        val savedStation = StationType.fromId(
            prefs.getString(KEY_SELECTED_STATION, StationType.GRILL.id) ?: StationType.GRILL.id
        )

        val permissionOk = notificationManager.isNotificationPermissionGranted()

        _state.update {
            it.copy(
                currentUser = restoredUser,
                selectedStation = savedStation,
                connectionMode = mode,
                syncStatus = SyncStatus.CONNECTED,
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKeyConfigured = BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank(),
                orders = if (mode == ConnectionMode.LOCAL_DEMO) combined else emptyList(),
                notificationPermissionGranted = permissionOk
            )
        }

        if (mode == ConnectionMode.SUPABASE_CLOUD && restoredUser != null && !savedToken.isNullOrBlank()) {
            supabaseClient.restoreSession(savedToken, prefs.getString("saved_refresh_token", null), prefs.getLong("session_expires_at", 0))
            startCloudSync()
        } else if (mode == ConnectionMode.LOCAL_DEMO) {
            reconcileNotificationsIfNeeded(combined, restoredUser)
        }
    }

    fun refreshNotificationPermissionState() {
        val granted = notificationManager.isNotificationPermissionGranted()
        _state.update { it.copy(notificationPermissionGranted = granted) }
        if (granted) {
            reconcileNotificationsIfNeeded(_state.value.orders, _state.value.currentUser)
        }
    }

    fun setNotificationsEnabledOnDevice(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabledOnDevice = enabled) }
        if (enabled) {
            reconcileNotificationsIfNeeded(_state.value.orders, _state.value.currentUser)
        } else {
            notificationManager.clearAllOrderNotifications()
        }
    }

    fun selectStation(station: StationType) {
        prefs.edit().putString(KEY_SELECTED_STATION, station.id).apply()
        _state.update { it.copy(selectedStation = station, bannerMessage = null) }
    }

    fun clearBanner() {
        _state.update { it.copy(bannerMessage = null, bannerIsError = false) }
    }

    fun updateBackendConfiguration(
        supabaseUrl: String,
        supabasePublishableKey: String,
        useCloudMode: Boolean
    ) {
        val cleanUrl = supabaseUrl.trim()
        // The setup dialog never pre-fills the key; a blank entry means "keep the existing key".
        val cleanKey = supabasePublishableKey.trim().ifBlank {
            prefs.getString(KEY_SUPABASE_KEY, null)?.takeIf { it.isNotBlank() }
                ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY
        }

        prefs.edit()
            .putString(KEY_SUPABASE_URL, cleanUrl)
            .putString(KEY_SUPABASE_KEY, cleanKey)
            .putBoolean(KEY_PREFER_CLOUD, useCloudMode)
            .apply()

        supabaseClient.disconnectRealtime()
        cloudPollJob?.cancel()
        supabaseClient.configure(cleanUrl, cleanKey)

        val effectiveMode = if (useCloudMode && supabaseClient.isConfigured()) {
            ConnectionMode.SUPABASE_CLOUD
        } else {
            ConnectionMode.LOCAL_DEMO
        }

        if (effectiveMode == ConnectionMode.LOCAL_DEMO) {
            val localStore = loadLocalStore()
            val combined = combineOrdersWithItems(localStore.orders, localStore.items)
            _state.update {
                it.copy(
                    connectionMode = ConnectionMode.LOCAL_DEMO,
                    syncStatus = SyncStatus.CONNECTED,
                    supabaseUrl = cleanUrl,
                    supabaseKeyConfigured = cleanKey.isNotBlank(),
                    orders = combined,
                    bannerMessage = if (useCloudMode && !supabaseClient.isConfigured()) {
                        "Supabase URL or key missing; using On-Device Demo Engine."
                    } else {
                        "Switched to On-Device Demo Engine."
                    },
                    bannerIsError = false
                )
            }
            reconcileNotificationsIfNeeded(combined, _state.value.currentUser)
        } else {
            // Re-authenticate current preset user against Supabase Cloud if signed in
            val currentEmail = _state.value.currentUser?.email
            val preset = PresetAccounts.accounts.firstOrNull { it.email == currentEmail }
            _state.update {
                it.copy(
                    connectionMode = ConnectionMode.SUPABASE_CLOUD,
                    syncStatus = SyncStatus.SYNCING,
                    supabaseUrl = cleanUrl,
                    supabaseKeyConfigured = cleanKey.isNotBlank(),
                    bannerMessage = "Connecting to Supabase Cloud...",
                    bannerIsError = false
                )
            }
            if (preset != null) {
                signInWithCredentials(preset.email, preset.password)
            } else {
                signOut()
            }
        }
    }

    fun signInWithPreset(account: PresetDemoAccount) {
        // One-tap preset sign-in is a debug-only demo tool; release builds carry no demo password.
        if (!BuildConfig.DEMO_TOOLS || account.password.isBlank()) return
        signInWithCredentials(account.email, account.password)
    }

    fun signInWithCredentials(email: String, password: String) {
        val cleanEmail = email.trim().lowercase()
        scope.launch {
            _state.update { it.copy(syncStatus = SyncStatus.SYNCING, bannerMessage = null) }

            if (_state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD && supabaseClient.isConfigured()) {
                val result = supabaseClient.signInWithPassword(cleanEmail, password)
                result.onSuccess { (session, profile) ->
                    persistUserSession(profile, session.accessToken)
                    _state.update {
                        it.copy(
                            currentUser = profile,
                            syncStatus = SyncStatus.CONNECTED,
                            bannerMessage = null,
                            bannerIsError = false
                        )
                    }
                    startCloudSync()
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            syncStatus = SyncStatus.DISCONNECTED,
                            bannerMessage = "Supabase Sign-In Failed: ${error.message}",
                            bannerIsError = true
                        )
                    }
                }
            } else {
                if (!BuildConfig.DEMO_TOOLS) {
                    // Release builds have no offline demo engine: without a backend nobody can sign in.
                    _state.update {
                        it.copy(
                            syncStatus = SyncStatus.DISCONNECTED,
                            bannerMessage = "Can't reach the server. Check the internet connection and try again.",
                            bannerIsError = true
                        )
                    }
                    return@launch
                }
                // Local Demo Engine authentication against preset accounts
                val matched = PresetAccounts.accounts.firstOrNull {
                    it.email.equals(cleanEmail, ignoreCase = true) && it.password == password
                }
                if (matched == null) {
                    _state.update {
                        it.copy(
                            syncStatus = SyncStatus.CONNECTED,
                            bannerMessage = "Invalid demo credentials. Use one of the preset demo accounts.",
                            bannerIsError = true
                        )
                    }
                    return@launch
                }

                val profile = UserProfileDto(
                    id = matched.userId,
                    email = matched.email,
                    restaurantId = DEMO_RESTAURANT_ID,
                    role = matched.role.id,
                    displayName = matched.displayName
                )
                persistUserSession(profile, null)
                val localStore = localMutex.withLock { loadLocalStore() }
                val combined = combineOrdersWithItems(localStore.orders, localStore.items)

                _state.update {
                    it.copy(
                        currentUser = profile,
                        syncStatus = SyncStatus.CONNECTED,
                        orders = combined,
                        bannerMessage = null,
                        bannerIsError = false,
                        lastSyncedAtEpochMillis = System.currentTimeMillis()
                    )
                }
                reconcileNotificationsIfNeeded(combined, profile)
            }
        }
    }

    fun signOut() {
        supabaseClient.disconnectRealtime()
        supabaseClient.restoreSession(null, null, 0)
        notificationManager.clearAllOrderNotifications()
        cloudPollJob?.cancel()
        prefs.edit()
            .remove("saved_refresh_token")
            .remove("session_expires_at")
            .remove("saved_restaurant_id")
            .remove(KEY_SAVED_USER_ID)
            .remove(KEY_SAVED_USER_EMAIL)
            .remove(KEY_SAVED_USER_ROLE)
            .remove(KEY_SAVED_USER_NAME)
            .remove(KEY_SAVED_ACCESS_TOKEN)
            .apply()
        _state.update {
            it.copy(
                currentUser = null,
                bannerMessage = null,
                bannerIsError = false
            )
        }
    }

    private fun startCloudSync() {
        supabaseClient.disconnectRealtime()
        cloudPollJob?.cancel()

        // 1. Initial snapshot fetch
        scope.launch {
            fetchCloudSnapshotInternal()
        }

        // 2. Realtime subscription + reconnect recovery
        supabaseClient.connectRealtime(
            onConnected = {
                _state.update { it.copy(syncStatus = SyncStatus.CONNECTED) }
            },
            onReconnecting = {
                _state.update { it.copy(syncStatus = SyncStatus.RECONNECTING) }
            },
            onChangeNotification = {
                scope.launch {
                    fetchCloudSnapshotInternal()
                }
            }
        )

        // 3. Lightweight fallback sync loop to guarantee <5s updates and recovery
        cloudPollJob = scope.launch {
            while (isActive && _state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD) {
                delay(3_000L)
                fetchCloudSnapshotInternal()
            }
        }
    }

    fun manualReconnectAndRefresh() {
        if (_state.value.isManualRefreshing) return
        scope.launch {
            val startedAt = System.currentTimeMillis()
            _state.update { it.copy(isManualRefreshing = true) }
            try {
                if (_state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD) {
                    _state.update { it.copy(syncStatus = SyncStatus.SYNCING, bannerMessage = null) }
                    fetchCloudSnapshotInternal()
                    startCloudSync()
                } else {
                    val store = localMutex.withLock { loadLocalStore() }
                    val combined = combineOrdersWithItems(store.orders, store.items)
                    _state.update {
                        it.copy(
                            orders = combined,
                            syncStatus = SyncStatus.CONNECTED,
                            lastSyncedAtEpochMillis = System.currentTimeMillis(),
                            bannerMessage = null
                        )
                    }
                    reconcileNotificationsIfNeeded(combined, _state.value.currentUser)
                }
            } finally {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < 380L) {
                    delay(380L - elapsed)
                }
                _state.update { it.copy(isManualRefreshing = false) }
            }
        }
    }

    private suspend fun fetchCloudSnapshotInternal() {
        // Polls, realtime events and post-write refreshes overlap. Apply a snapshot only if
        // no later-started request has already been applied, so stale responses cannot
        // briefly roll the UI or notifications back to an older state.
        val requestSeq = snapshotRequestSeq.incrementAndGet()
        val result = supabaseClient.fetchSnapshot()
        result.onSuccess { (orders, items) ->
            val isNewest = synchronized(snapshotApplyLock) {
                if (requestSeq > lastAppliedSnapshotSeq) {
                    lastAppliedSnapshotSeq = requestSeq
                    true
                } else {
                    false
                }
            }
            if (!isNewest) return
            val combined = combineOrdersWithItems(orders, items)
            _state.update {
                it.copy(
                    orders = combined,
                    syncStatus = SyncStatus.CONNECTED,
                    lastSyncedAtEpochMillis = System.currentTimeMillis()
                )
            }
            reconcileNotificationsIfNeeded(combined, _state.value.currentUser)
        }.onFailure { error ->
            val raw = error.message.orEmpty()
            val friendlyMessage = when {
                raw.contains("JWT", ignoreCase = true) ||
                    raw.contains("expired", ignoreCase = true) ||
                    raw.contains("401") ->
                    "Session expired — tap Sync or sign in again to resume live updates."
                else ->
                    "Connection interrupted — retrying automatically."
            }
            _state.update {
                it.copy(
                    syncStatus = SyncStatus.RECONNECTING,
                    bannerMessage = friendlyMessage,
                    bannerIsError = true
                )
            }
        }
    }

    // ========================================================================
    // ORDER OPERATIONS (Strict Role & State Machine Enforcement)
    // ========================================================================

    fun createOrder(
        tableNumber: Int,
        draftItems: List<CreateOrderItemPayload>,
        onSuccess: () -> Unit = {}
    ) {
        if (!creatingOrder.compareAndSet(false, true)) return
        _state.update { it.copy(isCreatingOrder = true) }
        scope.launch {
            try {
                val user = _state.value.currentUser
                if (user == null || user.appRole != AppRole.CONTROLLER) {
                    postError("Forbidden: Only the Controller role can create orders.")
                    return@launch
                }
                if (tableNumber <= 0) {
                    postError("Enter a valid table number greater than 0.")
                    return@launch
                }
                if (draftItems.isEmpty() || draftItems.any { it.quantity <= 0 }) {
                    postError("Select at least one menu item with quantity >= 1.")
                    return@launch
                }

                if (_state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD) {
                    // Keep the request identity across uncertain failures and process restarts.
                    val fingerprint = user.id + ":" + tableNumber + ":" + json.encodeToString(draftItems)
                    val requestId = if (prefs.getString("create_fingerprint", null) == fingerprint) {
                        prefs.getString("create_request_id", null) ?: UUID.randomUUID().toString()
                    } else UUID.randomUUID().toString()
                    prefs.edit().putString("create_fingerprint", fingerprint)
                        .putString("create_request_id", requestId).commit()
                    val res = supabaseClient.createOrder(tableNumber, draftItems, requestId)
                    res.onSuccess {
                        prefs.edit().remove("create_fingerprint").remove("create_request_id").commit()
                        fetchCloudSnapshotInternal()
                        onSuccess()
                    }.onFailure { err ->
                        postError("Failed to Create Order: ${err.message}")
                    }
                } else {
                    localMutex.withLock {
                        val store = loadLocalStore()
                        val nowIso = Instant.now().toString()
                        val newOrderId = UUID.randomUUID().toString()
                        val waiterId = PresetAccounts.accounts.first { it.role == AppRole.WAITER }.userId
                        val newOrder = OrderDto(
                            id = newOrderId,
                            orderNumber = store.nextOrderNumber,
                            restaurantId = user.restaurantId,
                            tableNumber = tableNumber,
                            assignedWaiterId = waiterId,
                            status = OrderStatus.PREPARING.id,
                            createdAt = nowIso,
                            updatedAt = nowIso
                        )
                        val newItems = draftItems.map { payload ->
                            OrderItemDto(
                                id = UUID.randomUUID().toString(),
                                orderId = newOrderId,
                                restaurantId = user.restaurantId,
                                menuItemId = payload.menuItemId,
                                menuLabel = payload.menuLabel,
                                quantity = payload.quantity,
                                station = payload.station,
                                status = ItemStatus.PENDING.id,
                                createdAt = nowIso,
                                updatedAt = nowIso
                            )
                        }
                        val updatedStore = store.copy(
                            nextOrderNumber = store.nextOrderNumber + 1,
                            orders = listOf(newOrder) + store.orders,
                            items = store.items + newItems
                        )
                        saveLocalStore(updatedStore)
                        publishLocalStore(updatedStore)
                    }
                    onSuccess()
                }
            } catch (error: Exception) {
                postError("Failed to Create Order: ${error.message}")
            } finally {
                creatingOrder.set(false)
                _state.update { it.copy(isCreatingOrder = false) }
            }
        }
    }

    fun cancelOrder(orderId: String) {
        scope.launch {
            val user = _state.value.currentUser
            if (user == null || user.appRole != AppRole.CONTROLLER) {
                postError("Forbidden: Only the Controller role can cancel orders.")
                return@launch
            }
            markOrderPending(orderId, true)
            try {
                if (_state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD) {
                    val res = supabaseClient.cancelOrder(orderId)
                    res.onSuccess {
                        fetchCloudSnapshotInternal()
                    }.onFailure { err ->
                        postError("Failed to Cancel Order: ${err.message}")
                    }
                } else {
                    localMutex.withLock {
                        val store = loadLocalStore()
                        val target = store.orders.firstOrNull { it.id == orderId }
                            ?: throw IllegalStateException("Order not found.")
                        if (target.orderStatus == OrderStatus.CANCELED) {
                            return@withLock
                        }
                        if (target.orderStatus == OrderStatus.PICKED_UP) {
                            throw IllegalStateException("Cannot cancel an order that has already been picked up.")
                        }
                        val nowIso = Instant.now().toString()
                        val updatedOrders = store.orders.map {
                            if (it.id == orderId) {
                                it.copy(
                                    status = OrderStatus.CANCELED.id,
                                    canceledAt = it.canceledAt ?: nowIso,
                                    updatedAt = nowIso
                                )
                            } else {
                                it
                            }
                        }
                        val updatedStore = store.copy(orders = updatedOrders)
                        saveLocalStore(updatedStore)
                        publishLocalStore(updatedStore)
                    }
                }
            } catch (e: Exception) {
                postError(e.message ?: "Could not cancel order.")
            } finally {
                markOrderPending(orderId, false)
            }
        }
    }

    fun acceptStationOrder(orderId: String, station: StationType) {
        scope.launch {
            val user = _state.value.currentUser
            if (user == null || user.appRole != AppRole.KITCHEN) {
                postError("Forbidden: Only the Kitchen role can accept station orders.")
                return@launch
            }
            markOrderPending(orderId, true)
            try {
                if (_state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD) {
                    val res = supabaseClient.acceptStationOrder(orderId, station.id)
                    res.onSuccess {
                        fetchCloudSnapshotInternal()
                    }.onFailure { err ->
                        postError("Failed to Accept Station Order: ${err.message}")
                    }
                } else {
                    localMutex.withLock {
                        val store = loadLocalStore()
                        val order = store.orders.firstOrNull { it.id == orderId }
                            ?: throw IllegalStateException("Order not found.")
                        if (order.orderStatus == OrderStatus.CANCELED) {
                            throw IllegalStateException("Cannot accept a canceled order.")
                        }
                        if (order.orderStatus != OrderStatus.PREPARING) {
                            return@withLock
                        }
                        val nowIso = Instant.now().toString()
                        val updatedItems = store.items.map { item ->
                            if (item.orderId == orderId &&
                                item.stationType == station &&
                                item.itemStatus == ItemStatus.PENDING
                            ) {
                                item.copy(status = ItemStatus.PREPARING.id, updatedAt = nowIso)
                            } else {
                                item
                            }
                        }
                        val updatedStore = store.copy(items = updatedItems)
                        saveLocalStore(updatedStore)
                        publishLocalStore(updatedStore)
                    }
                }
            } catch (e: Exception) {
                postError(e.message ?: "Failed to accept order.")
            } finally {
                markOrderPending(orderId, false)
            }
        }
    }

    fun markItemReady(itemId: String) {
        scope.launch {
            val user = _state.value.currentUser
            if (user == null || user.appRole != AppRole.KITCHEN) {
                postError("Forbidden: Only the Kitchen role can mark items ready.")
                return@launch
            }
            markItemPending(itemId, true)
            try {
                if (_state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD) {
                    val res = supabaseClient.markItemReady(itemId)
                    res.onSuccess {
                        fetchCloudSnapshotInternal()
                    }.onFailure { err ->
                        postError("Failed to Mark Item Ready: ${err.message}")
                    }
                } else {
                    // Atomic completion check under mutex (matches PostgreSQL FOR UPDATE semantics)
                    localMutex.withLock {
                        val store = loadLocalStore()
                        val targetItem = store.items.firstOrNull { it.id == itemId }
                            ?: throw IllegalStateException("Order item not found.")
                        val parentOrder = store.orders.firstOrNull { it.id == targetItem.orderId }
                            ?: throw IllegalStateException("Parent order not found.")

                        if (parentOrder.orderStatus == OrderStatus.CANCELED) {
                            throw IllegalStateException("Cannot mark item ready on a canceled order.")
                        }

                        check(targetItem.itemStatus != ItemStatus.PENDING) { "Accept the station order before marking items ready." }
                        val nowIso = Instant.now().toString()
                        val updatedItems = store.items.map { item ->
                            if (item.id == itemId && item.itemStatus != ItemStatus.READY) {
                                item.copy(
                                    status = ItemStatus.READY.id,
                                    readyAt = item.readyAt ?: nowIso,
                                    updatedAt = nowIso
                                )
                            } else {
                                item
                            }
                        }

                        val orderItemsAfter = updatedItems.filter { it.orderId == parentOrder.id }
                        val allReady = orderItemsAfter.isNotEmpty() &&
                            orderItemsAfter.all { it.itemStatus == ItemStatus.READY }

                        val updatedOrders = store.orders.map { order ->
                            if (order.id == parentOrder.id) {
                                if (allReady && order.orderStatus == OrderStatus.PREPARING) {
                                    order.copy(
                                        status = OrderStatus.READY.id,
                                        readyAt = order.readyAt ?: nowIso,
                                        updatedAt = nowIso
                                    )
                                } else {
                                    order.copy(updatedAt = nowIso)
                                }
                            } else {
                                order
                            }
                        }

                        val updatedStore = store.copy(
                            orders = updatedOrders,
                            items = updatedItems
                        )
                        saveLocalStore(updatedStore)
                        publishLocalStore(updatedStore)
                    }
                }
            } catch (e: Exception) {
                postError(e.message ?: "Failed to mark item ready.")
            } finally {
                markItemPending(itemId, false)
            }
        }
    }

    fun acknowledgeOrder(orderId: String) {
        scope.launch {
            val user = _state.value.currentUser
            if (user == null || user.appRole != AppRole.WAITER) {
                postError("Forbidden: Only the assigned Waiter role can acknowledge ready orders.")
                return@launch
            }
            executeAcknowledgeInternal(orderId = orderId, callerWaiterId = user.id)
        }
    }

    /**
     * Invoked when the waiter taps Acknowledge on the Wear OS watch or phone notification.
     * Validates state and updates notification only after backend confirmation.
     */
    suspend fun handleWatchOrNotificationAcknowledge(
        orderId: String,
        orderNumber: Int,
        tableNumber: Int
    ) {
        if (orderId == WearOrderNotificationManager.PHASE1_TEST_ORDER_ID) {
            notificationManager.postAcknowledgedNotification(
                orderId = orderId,
                orderNumber = orderNumber,
                tableNumber = tableNumber
            )
            _state.update {
                it.copy(
                    phase1WatchStatus = "Verified: Watch Acknowledge received by phone (${
                        java.time.LocalTime.now().withNano(0)
                    })",
                    bannerMessage = "Phase 1 Watch Action received and verified on phone.",
                    bannerIsError = false
                )
            }
            return
        }

        // Cloud mode: the backend identifies the caller from the session token.
        // Local single-device rehearsal: the notification always acts as the demo waiter,
        // even while the device is showing the Dashboard or Station view.
        val currentUser = _state.value.currentUser
        val callerWaiterId = if (_state.value.connectionMode == ConnectionMode.LOCAL_DEMO ||
            currentUser?.appRole != AppRole.WAITER
        ) {
            PresetAccounts.accounts.first { it.role == AppRole.WAITER }.userId
        } else {
            currentUser.id
        }
        val result = executeAcknowledgeInternal(
            orderId = orderId,
            callerWaiterId = callerWaiterId
        )
        result.onSuccess { updatedOrder ->
            notificationManager.postAcknowledgedNotification(
                orderId = updatedOrder.id,
                orderNumber = updatedOrder.orderNumber,
                tableNumber = updatedOrder.tableNumber
            )
        }.onFailure { err ->
            val currentOrder = _state.value.orders.firstOrNull { it.order.id == orderId }?.order
            if (currentOrder == null || currentOrder.orderStatus == OrderStatus.CANCELED ||
                currentOrder.orderStatus == OrderStatus.PICKED_UP
            ) {
                notificationManager.cancelOrderNotification(orderId)
            } else {
                // Keep retry action on notification and do not falsely confirm success
                notificationManager.postReadyNotification(
                    order = currentOrder,
                    onlyAlertOnce = true,
                    retryErrorText = "Sync Failed, Tap Acknowledge to Retry"
                )
            }
        }
    }

    private suspend fun executeAcknowledgeInternal(
        orderId: String,
        callerWaiterId: String
    ): Result<OrderDto> {
        markOrderPending(orderId, true)
        clearOrderWriteFailure(orderId)
        return try {
            if (_state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD) {
                val res = supabaseClient.acknowledgeOrder(orderId)
                res.onSuccess {
                    fetchCloudSnapshotInternal()
                }.onFailure { err ->
                    recordOrderWriteFailure(orderId, "Acknowledge Failed: ${err.message}")
                }
                res
            } else {
                localMutex.withLock {
                    runCatching {
                        val store = loadLocalStore()
                        val target = store.orders.firstOrNull { it.id == orderId }
                            ?: throw IllegalStateException("Order not found.")

                        if (target.assignedWaiterId != null && target.assignedWaiterId != callerWaiterId) {
                            throw IllegalStateException("Forbidden: Order is assigned to another waiter.")
                        }
                        if (target.orderStatus == OrderStatus.CANCELED) {
                            throw IllegalStateException("Order was canceled; stale acknowledgment rejected.")
                        }
                        // Idempotent: repeating an already accepted acknowledgment returns the existing order
                        if (target.orderStatus == OrderStatus.ACKNOWLEDGED) {
                            return@runCatching target
                        }
                        if (target.orderStatus != OrderStatus.READY) {
                            throw IllegalStateException("Order is ${target.orderStatus.displayName} and cannot be acknowledged.")
                        }

                        val nowIso = Instant.now().toString()
                        val updatedOrder = target.copy(
                            status = OrderStatus.ACKNOWLEDGED.id,
                            acknowledgedAt = target.acknowledgedAt ?: nowIso,
                            updatedAt = nowIso
                        )
                        val updatedOrders = store.orders.map { if (it.id == orderId) updatedOrder else it }
                        val updatedStore = store.copy(orders = updatedOrders)
                        saveLocalStore(updatedStore)
                        publishLocalStore(updatedStore)
                        updatedOrder
                    }.onFailure { err ->
                        recordOrderWriteFailure(orderId, err.message ?: "Acknowledge Failed")
                    }
                }
            }
        } finally {
            markOrderPending(orderId, false)
        }
    }

    fun markOrderPickedUp(orderId: String) {
        scope.launch {
            val user = _state.value.currentUser
            if (user == null || user.appRole != AppRole.WAITER) {
                postError("Forbidden: Only the assigned Waiter role can confirm order pickup.")
                return@launch
            }
            markOrderPending(orderId, true)
            clearOrderWriteFailure(orderId)
            try {
                if (_state.value.connectionMode == ConnectionMode.SUPABASE_CLOUD) {
                    val res = supabaseClient.markOrderPickedUp(orderId)
                    res.onSuccess {
                        notificationManager.cancelOrderNotification(orderId)
                        fetchCloudSnapshotInternal()
                    }.onFailure { err ->
                        recordOrderWriteFailure(orderId, "Pickup Failed: ${err.message}")
                    }
                } else {
                    localMutex.withLock {
                        val store = loadLocalStore()
                        val target = store.orders.firstOrNull { it.id == orderId }
                            ?: throw IllegalStateException("Order not found.")
                        if (target.assignedWaiterId != null && target.assignedWaiterId != user.id) {
                            throw IllegalStateException("Forbidden: Order is assigned to another waiter.")
                        }
                        if (target.orderStatus == OrderStatus.CANCELED) {
                            throw IllegalStateException("Cannot pick up a canceled order.")
                        }
                        if (target.orderStatus == OrderStatus.PICKED_UP) {
                            return@withLock
                        }
                        if (target.orderStatus != OrderStatus.ACKNOWLEDGED) {
                            throw IllegalStateException("Acknowledge the order before marking Picked Up.")
                        }

                        val nowIso = Instant.now().toString()
                        val updatedOrder = target.copy(
                            status = OrderStatus.PICKED_UP.id,
                            pickedUpAt = target.pickedUpAt ?: nowIso,
                            updatedAt = nowIso
                        )
                        val updatedOrders = store.orders.map { if (it.id == orderId) updatedOrder else it }
                        val updatedStore = store.copy(orders = updatedOrders)
                        saveLocalStore(updatedStore)
                        notificationManager.cancelOrderNotification(orderId)
                        publishLocalStore(updatedStore)
                    }
                }
            } catch (e: Exception) {
                recordOrderWriteFailure(orderId, e.message ?: "Pickup Failed")
            } finally {
                markOrderPending(orderId, false)
            }
        }
    }

    fun triggerPhase1WatchTest() {
        val sent = notificationManager.sendPhase1WatchVerificationAlert()
        if (sent) {
            _state.update {
                it.copy(
                    phase1WatchStatus = "Alert sent to phone & paired watch. Tap 'Acknowledge' on watch to verify.",
                    bannerMessage = "Phase 1 test notification sent. Check phone & paired Wear OS watch.",
                    bannerIsError = false
                )
            }
        } else {
            _state.update {
                it.copy(
                    phase1WatchStatus = "Notification permission denied. Grant permission first.",
                    bannerMessage = "Notification permission is denied. Enable notifications in settings.",
                    bannerIsError = true,
                    notificationPermissionGranted = false
                )
            }
        }
    }

    fun resetDemoData() {
        scope.launch {
            localMutex.withLock {
                notificationManager.clearAllOrderNotifications()
                val seeded = createInitialSeedStore()
                saveLocalStore(seeded)
                publishLocalStore(seeded)
                _state.update {
                    it.copy(
                        failedWriteByOrderId = emptyMap(),
                        bannerMessage = "Demo orders reset to initial presentation state.",
                        bannerIsError = false
                    )
                }
            }
        }
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    private fun publishLocalStore(store: PersistedLocalStore) {
        val combined = combineOrdersWithItems(store.orders, store.items)
        _state.update {
            it.copy(
                orders = combined,
                syncStatus = SyncStatus.CONNECTED,
                lastSyncedAtEpochMillis = System.currentTimeMillis()
            )
        }
        reconcileNotificationsIfNeeded(combined, _state.value.currentUser)
    }

    private fun reconcileNotificationsIfNeeded(
        orders: List<OrderWithItems>,
        user: UserProfileDto?
    ) {
        val permissionOk = notificationManager.isNotificationPermissionGranted()
        _state.update { it.copy(notificationPermissionGranted = permissionOk) }
        if (!_state.value.notificationsEnabledOnDevice) return

        // Always reconcile when in Waiter mode or when running local multi-role rehearsal on device
        val shouldBridge = user?.appRole == AppRole.WAITER ||
            _state.value.connectionMode == ConnectionMode.LOCAL_DEMO
        if (shouldBridge) {
            // Use the signed-in waiter's real identity so accounts created in the Supabase
            // dashboard (with their own UUIDs) still receive alerts. Local demo data is
            // assigned to the preset waiter.
            val waiterId = if (user?.appRole == AppRole.WAITER) {
                user.id
            } else {
                PresetAccounts.accounts.first { it.role == AppRole.WAITER }.userId
            }
            notificationManager.reconcileOrdersForWaiter(
                orders = orders,
                assignedWaiterId = waiterId
            )
        }
    }

    private fun combineOrdersWithItems(
        orders: List<OrderDto>,
        items: List<OrderItemDto>
    ): List<OrderWithItems> {
        val currentRestaurant = _state.value.currentUser?.restaurantId ?: DEMO_RESTAURANT_ID
        val itemsByOrder = items
            .filter { it.restaurantId == currentRestaurant }
            .groupBy { it.orderId }
        return orders
            .filter { it.restaurantId == currentRestaurant }
            .sortedByDescending { it.orderNumber }
            .map { order ->
                OrderWithItems(
                    order = order,
                    items = itemsByOrder[order.id].orEmpty()
                )
            }
    }

    private fun markOrderPending(orderId: String, pending: Boolean) {
        _state.update {
            val next = if (pending) it.pendingOrderIds + orderId else it.pendingOrderIds - orderId
            it.copy(pendingOrderIds = next)
        }
    }

    private fun markItemPending(itemId: String, pending: Boolean) {
        _state.update {
            val next = if (pending) it.pendingItemIds + itemId else it.pendingItemIds - itemId
            it.copy(pendingItemIds = next)
        }
    }

    private fun recordOrderWriteFailure(orderId: String, message: String) {
        _state.update {
            it.copy(
                failedWriteByOrderId = it.failedWriteByOrderId + (orderId to message),
                bannerMessage = message,
                bannerIsError = true
            )
        }
    }

    private fun clearOrderWriteFailure(orderId: String) {
        _state.update {
            it.copy(failedWriteByOrderId = it.failedWriteByOrderId - orderId)
        }
    }

    private fun postError(message: String) {
        _state.update {
            it.copy(
                bannerMessage = message,
                bannerIsError = true
            )
        }
    }

    private fun persistUserSession(profile: UserProfileDto, accessToken: String?) {
        prefs.edit()
            .putString(KEY_SAVED_USER_ID, profile.id)
            .putString(KEY_SAVED_USER_EMAIL, profile.email)
            .putString(KEY_SAVED_USER_ROLE, profile.role)
            .putString(KEY_SAVED_USER_NAME, profile.displayName)
            .putString("saved_restaurant_id", profile.restaurantId)
            .putString(KEY_SAVED_ACCESS_TOKEN, accessToken)
            .apply()
    }

    private fun loadLocalStore(): PersistedLocalStore {
        return runCatching {
            if (!localStoreFile.exists()) {
                val seeded = createInitialSeedStore()
                saveLocalStore(seeded)
                seeded
            } else {
                json.decodeFromString<PersistedLocalStore>(localStoreFile.readText())
            }
        }.getOrElse {
            val seeded = createInitialSeedStore()
            saveLocalStore(seeded)
            seeded
        }
    }

    private fun saveLocalStore(store: PersistedLocalStore) {
        runCatching {
            localStoreFile.writeText(json.encodeToString(PersistedLocalStore.serializer(), store))
        }
    }

    private fun createInitialSeedStore(): PersistedLocalStore {
        val now = Instant.now()
        val waiterId = PresetAccounts.accounts.first { it.role == AppRole.WAITER }.userId

        val order25Id = "aaaa0025-0000-0000-0000-000000000025"
        val order26Id = "aaaa0026-0000-0000-0000-000000000026"
        val order27Id = "aaaa0027-0000-0000-0000-000000000027"

        val orders = listOf(
            // Order #025: Table 12 - Preparing across Grill, Fryer, Pastry
            OrderDto(
                id = order25Id,
                orderNumber = 25,
                restaurantId = DEMO_RESTAURANT_ID,
                tableNumber = 12,
                assignedWaiterId = waiterId,
                status = OrderStatus.PREPARING.id,
                createdAt = now.minusSeconds(210).toString(),
                updatedAt = now.minusSeconds(30).toString()
            ),
            // Order #026: Table 8 - Ready (all 3 stations Ready)
            OrderDto(
                id = order26Id,
                orderNumber = 26,
                restaurantId = DEMO_RESTAURANT_ID,
                tableNumber = 8,
                assignedWaiterId = waiterId,
                status = OrderStatus.READY.id,
                createdAt = now.minusSeconds(320).toString(),
                readyAt = now.minusSeconds(8).toString(),
                updatedAt = now.minusSeconds(8).toString()
            ),
            // Order #027: Table 15 - Acknowledged (Grill + Fryer only, Pastry Not required)
            OrderDto(
                id = order27Id,
                orderNumber = 27,
                restaurantId = DEMO_RESTAURANT_ID,
                tableNumber = 15,
                assignedWaiterId = waiterId,
                status = OrderStatus.ACKNOWLEDGED.id,
                createdAt = now.minusSeconds(480).toString(),
                readyAt = now.minusSeconds(110).toString(),
                acknowledgedAt = now.minusSeconds(65).toString(),
                updatedAt = now.minusSeconds(65).toString()
            )
        )

        val items = listOf(
            // Items for Order #025 (Table 12)
            OrderItemDto(
                id = "item-25-grill",
                orderId = order25Id,
                restaurantId = DEMO_RESTAURANT_ID,
                menuItemId = "grill_chicken",
                menuLabel = "Grilled Chicken",
                quantity = 1,
                station = StationType.GRILL.id,
                status = ItemStatus.PREPARING.id,
                createdAt = now.minusSeconds(210).toString(),
                updatedAt = now.minusSeconds(90).toString()
            ),
            OrderItemDto(
                id = "item-25-fryer",
                orderId = order25Id,
                restaurantId = DEMO_RESTAURANT_ID,
                menuItemId = "fryer_fries",
                menuLabel = "French Fries",
                quantity = 2,
                station = StationType.FRYER.id,
                status = ItemStatus.READY.id,
                createdAt = now.minusSeconds(210).toString(),
                readyAt = now.minusSeconds(45).toString(),
                updatedAt = now.minusSeconds(45).toString()
            ),
            OrderItemDto(
                id = "item-25-pastry",
                orderId = order25Id,
                restaurantId = DEMO_RESTAURANT_ID,
                menuItemId = "pastry_choco_cake",
                menuLabel = "Chocolate Cake",
                quantity = 1,
                station = StationType.PASTRY.id,
                status = ItemStatus.PENDING.id,
                createdAt = now.minusSeconds(210).toString(),
                updatedAt = now.minusSeconds(210).toString()
            ),
            // Items for Order #026 (Table 8)
            OrderItemDto(
                id = "item-26-grill",
                orderId = order26Id,
                restaurantId = DEMO_RESTAURANT_ID,
                menuItemId = "grill_salmon",
                menuLabel = "Pan-Seared Salmon",
                quantity = 1,
                station = StationType.GRILL.id,
                status = ItemStatus.READY.id,
                createdAt = now.minusSeconds(320).toString(),
                readyAt = now.minusSeconds(25).toString(),
                updatedAt = now.minusSeconds(25).toString()
            ),
            OrderItemDto(
                id = "item-26-fryer",
                orderId = order26Id,
                restaurantId = DEMO_RESTAURANT_ID,
                menuItemId = "fryer_croquettes",
                menuLabel = "Potato Croquettes",
                quantity = 1,
                station = StationType.FRYER.id,
                status = ItemStatus.READY.id,
                createdAt = now.minusSeconds(320).toString(),
                readyAt = now.minusSeconds(15).toString(),
                updatedAt = now.minusSeconds(15).toString()
            ),
            OrderItemDto(
                id = "item-26-pastry",
                orderId = order26Id,
                restaurantId = DEMO_RESTAURANT_ID,
                menuItemId = "pastry_cheesecake",
                menuLabel = "New York Cheesecake",
                quantity = 1,
                station = StationType.PASTRY.id,
                status = ItemStatus.READY.id,
                createdAt = now.minusSeconds(320).toString(),
                readyAt = now.minusSeconds(8).toString(),
                updatedAt = now.minusSeconds(8).toString()
            ),
            // Items for Order #027 (Table 15)
            OrderItemDto(
                id = "item-27-grill",
                orderId = order27Id,
                restaurantId = DEMO_RESTAURANT_ID,
                menuItemId = "grill_burger",
                menuLabel = "Beef Burger",
                quantity = 2,
                station = StationType.GRILL.id,
                status = ItemStatus.READY.id,
                createdAt = now.minusSeconds(480).toString(),
                readyAt = now.minusSeconds(140).toString(),
                updatedAt = now.minusSeconds(140).toString()
            ),
            OrderItemDto(
                id = "item-27-fryer",
                orderId = order27Id,
                restaurantId = DEMO_RESTAURANT_ID,
                menuItemId = "fryer_onion_rings",
                menuLabel = "Onion Rings",
                quantity = 2,
                station = StationType.FRYER.id,
                status = ItemStatus.READY.id,
                createdAt = now.minusSeconds(480).toString(),
                readyAt = now.minusSeconds(110).toString(),
                updatedAt = now.minusSeconds(110).toString()
            )
        )

        return PersistedLocalStore(
            nextOrderNumber = 28,
            orders = orders,
            items = items
        )
    }

    companion object {
        private const val PREFS_NAME = "kitchensync360_prefs"
        private const val LOCAL_STORE_FILENAME = "kitchensync360_local_store_v1.json"

        private const val KEY_SUPABASE_URL = "supabase_url"
        private const val KEY_SUPABASE_KEY = "supabase_key"
        private const val KEY_PREFER_CLOUD = "prefer_cloud"
        private const val KEY_SELECTED_STATION = "selected_station"

        private const val KEY_SAVED_USER_ID = "saved_user_id"
        private const val KEY_SAVED_USER_EMAIL = "saved_user_email"
        private const val KEY_SAVED_USER_ROLE = "saved_user_role"
        private const val KEY_SAVED_USER_NAME = "saved_user_name"
        private const val KEY_SAVED_ACCESS_TOKEN = "saved_access_token"

        @Volatile
        private var instance: KitchenSyncRepository? = null

        fun getInstance(context: Context): KitchenSyncRepository {
            return instance ?: synchronized(this) {
                instance ?: KitchenSyncRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
