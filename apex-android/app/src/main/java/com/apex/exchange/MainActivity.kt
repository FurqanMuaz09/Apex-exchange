package com.apex.exchange

// Chart gesture and live-price guide behavior is isolated to the chart composable.

import android.os.Bundle
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.debounce
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder
import java.net.URL
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.disk.DiskCache
import coil.request.CachePolicy
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale

private object ApexCoinImageLoader {
    @Volatile private var shared: coil.ImageLoader? = null

    fun get(context: android.content.Context): coil.ImageLoader {
        return shared ?: synchronized(this) {
            shared ?: coil.ImageLoader.Builder(context.applicationContext)
                .components { add(coil.decode.SvgDecoder.Factory()) }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.applicationContext.filesDir, "apex_coin_logo_cache"))
                        .maxSizeBytes(128L * 1024L * 1024L)
                        .build()
                }
                .build()
                .also { shared = it }
        }
    }
}

@Composable
private fun MarketCoinIcon(symbol: String, size: androidx.compose.ui.unit.Dp) {
    val ticker = symbol.uppercase().trim()
    val context = LocalContext.current
    val bundledSymbols = remember {
        setOf("BTC", "ETH", "BNB", "SOL", "XRP", "DOGE", "ADA", "TRX", "USDC", "USDT", "LINK", "LTC", "BCH", "AVAX", "DOT", "ZEC", "UNI", "MATIC", "ATOM", "ETC", "FIL")
    }
    val localAssetSymbol = remember(ticker) {
        when (ticker) {
            "XBT" -> "btc"
            "POL" -> "matic"
            else -> ticker.lowercase()
        }
    }
    val hasLocalAsset = ticker in bundledSymbols || ticker == "XBT" || ticker == "POL"
    var useLocalAsset by remember(ticker) { mutableStateOf(hasLocalAsset) }
    var sourceIndex by remember(ticker) { mutableIntStateOf(0) }
    val svgImageLoader = remember(context) { ApexCoinImageLoader.get(context) }

    val tickerCandidates = remember(ticker) {
        val aliases = mapOf(
            "XBT" to listOf("BTC"),
            "BCC" to listOf("BCH"),
            "BCHABC" to listOf("BCH"),
            "MIOTA" to listOf("IOTA"),
            "XRB" to listOf("NANO"),
            "LUNA2" to listOf("LUNA"),
            "USTC" to listOf("UST"),
            "POL" to listOf("MATIC"),
            "RNDR" to listOf("RENDER"),
            "BTTOLD" to listOf("BTT"),
            "DOG" to listOf("DOGE"),
            "XNO" to listOf("NANO"),
            "STR" to listOf("XLM"),
            "BCCOIN" to listOf("BCC")
        )
        (listOf(ticker) + aliases[ticker].orEmpty()).distinct()
    }
    val trustWalletChains = remember {
        mapOf(
            "BTC" to "bitcoin", "ETH" to "ethereum", "BNB" to "smartchain",
            "SOL" to "solana", "XRP" to "ripple", "DOGE" to "dogecoin",
            "ADA" to "cardano", "TRX" to "tron", "LTC" to "litecoin",
            "BCH" to "bitcoincash", "AVAX" to "avalanchec", "DOT" to "polkadot",
            "ATOM" to "cosmos", "XLM" to "stellar", "TON" to "ton"
        )
    }
    val sources = remember(ticker) {
        val nativeTrustWallet = trustWalletChains[ticker]?.let { chain ->
            "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/$chain/info/logo.png"
        }
        tickerCandidates.flatMap { candidate ->
            val key = candidate.lowercase()
            listOfNotNull(
                if (candidate == ticker) nativeTrustWallet else null,
                "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/$key.png",
                "https://assets.coincap.io/assets/icons/${key}@2x.png",
                "https://coinicons-api.vercel.app/api/icon/$key",
                "https://cryptoicons.org/api/icon/$key/200",
                "https://cdn.jsdelivr.net/gh/atomiclabs/cryptocurrency-icons@master/128/color/$key.png",
                "https://cdn.jsdelivr.net/gh/spothq/cryptocurrency-icons@master/128/color/$key.png",
                "https://raw.githubusercontent.com/ErikThiart/cryptocurrency-icons/master/128/color/$key.png",
                "https://cdn.jsdelivr.net/gh/ErikThiart/cryptocurrency-icons@master/128/color/$key.png",
                "https://raw.githack.com/spothq/cryptocurrency-icons/master/128/color/$key.png",
                "https://raw.githack.com/atomiclabs/cryptocurrency-icons/master/128/color/$key.png",
                "https://cdn.statically.io/gh/spothq/cryptocurrency-icons/master/128/color/$key.png",
                "https://cdn.statically.io/gh/ErikThiart/cryptocurrency-icons/master/128/color/$key.png"
            )
        }.distinct()
    }

    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color(0xFF252A33)),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = if (useLocalAsset) {
                "file:///android_asset/coin-logos/$localAssetSymbol.svg"
            } else {
                ImageRequest.Builder(context)
                    .data(sources.getOrNull(sourceIndex))
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            },
            imageLoader = svgImageLoader,
            contentDescription = "$ticker coin logo",
            modifier = Modifier.size(size - 6.dp).clip(CircleShape),
            contentScale = ContentScale.Fit,
            onError = {
                if (useLocalAsset) {
                    useLocalAsset = false
                } else if (sourceIndex < sources.lastIndex) {
                    sourceIndex += 1
                }
            },
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(ticker.take(1), color = Yellow, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
                }
            },
            error = {
                Box(Modifier.fillMaxSize().background(Color(0xFF303846), CircleShape), contentAlignment = Alignment.Center) {
                    Text(ticker.take(1), color = Yellow, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
                }
            },
            success = { SubcomposeAsyncImageContent() }
        )
    }
}

private const val APEX_API_BASE_URL = BuildConfig.APEX_API_BASE_URL

// Shared HTTP connection pool: reuse TLS connections and keep API calls snappy.
private val APEX_HTTP_CLIENT: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .writeTimeout(8, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
    .build()

private suspend fun apexApiRequest(path: String, method: String = "GET", body: JSONObject? = null): JSONObject =
    withContext(Dispatchers.IO) {
        require(APEX_API_BASE_URL.isNotBlank()) { "APEX backend URL is not configured. Hosting is required first." }
        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Please login first.")
        val token = user.getIdToken(false).await().token ?: throw IllegalStateException("Could not get login token.")
        val requestBuilder = Request.Builder()
            .url(APEX_API_BASE_URL.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .method(method, if (body != null) {
                body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            } else if (method == "GET" || method == "DELETE") null else {
                ByteArray(0).toRequestBody(null)
            })
        APEX_HTTP_CLIENT.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (!response.isSuccessful) {
                throw IllegalStateException(result.optString("error", "API request failed (${response.code})."))
            }
            result
        }
    }

private val Bg = Color(0xFF0B0E11)
private val Panel = Color(0xFF181A20)
private val Muted = Color(0xFF848E9C)
private val Green = Color(0xFF0ECB81)
private val Red = Color(0xFFF6465D)
private val Yellow = Color(0xFFF0B90B)
private val White = Color(0xFFEAECEF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 14, 17)
        window.navigationBarColor = android.graphics.Color.rgb(11, 14, 17)
        setContent { ApexNativeApp() }
    }
}

@Composable
private fun ApexNativeApp() {
    val context = LocalContext.current
    val favorites = remember {
        mutableStateListOf<String>().apply {
            addAll(context.getSharedPreferences("apex_prefs", android.content.Context.MODE_PRIVATE)
                .getStringSet("favorite_coins", emptySet()) ?: emptySet())
        }
    }
    LaunchedEffect(favorites.toList()) {
        context.getSharedPreferences("apex_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putStringSet("favorite_coins", favorites.toSet()).apply()
    }
    var tab by remember { mutableStateOf("Home") }
    var detail by remember { mutableStateOf<String?>(null) }
    var selectedTradingPair by remember { mutableStateOf("BTC/USDT") }
    var selectedTradingSide by remember { mutableStateOf("Buy") }
    var chartPair by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    BackHandler(enabled = detail != null || chartPair != null) {
        if (detail != null) detail = null else chartPair = null
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg, surface = Panel, primary = Yellow,
            onBackground = White, onSurface = White
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                if (chartPair != null && detail == null) {
                    Row(Modifier.fillMaxWidth().height(88.dp).background(Bg).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { selectedTradingPair = chartPair!!; selectedTradingSide = "Buy"; chartPair = null; tab = "Trade" }, modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)) { Text("Buy", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                        Button(onClick = { selectedTradingPair = chartPair!!; selectedTradingSide = "Sell"; chartPair = null; tab = "Trade" }, modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = White)) { Text("Sell", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                    }
                } else if (chartPair == null) {
                    NavigationBar(modifier = Modifier.fillMaxWidth(), containerColor = Panel, contentColor = White) {
                    val destinations = listOf(
                        Triple("Home", Icons.Filled.Home, "Home"),
                        Triple("Markets", Icons.Filled.ShowChart, "Markets"),
                        Triple("Trade", Icons.Filled.SwapHoriz, "Trade"),
                        Triple("Futures", Icons.Filled.ShowChart, "Futures"),
                        Triple("Assets", Icons.Filled.AccountBalanceWallet, "Assets")
                    )
                    destinations.forEach { (label, icon, route) ->
                        NavigationBarItem(
                            selected = tab == route && detail == null,
                            onClick = { tab = route; detail = null; chartPair = null },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                            label = { Text(label, fontSize = 9.sp, lineHeight = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Yellow,
                                selectedTextColor = Yellow,
                                indicatorColor = Color(0xFF34302A),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
                }
            }
        ) { padding ->
            var pullDistance by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier.fillMaxSize().background(Bg).padding(padding)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0f) {
                                    pullDistance += dragAmount
                                    if (pullDistance > 110f) {
                                        refreshKey++
                                        pullDistance = -10000f
                                    }
                                } else if (pullDistance < 0f) {
                                    pullDistance = 0f
                                }
                                change.consume()
                            },
                            onDragEnd = { pullDistance = 0f },
                            onDragCancel = { pullDistance = 0f }
                        )
                    }
            ) {
                if (detail != null) {
                    NativeDetail(detail!!) { detail = null }
                } else if (chartPair != null) {
                    TradeScreen(
                        favorites = favorites,
                        initialPair = chartPair!!,
                        chartOnly = true,
                        onOpenOrderEntry = { pair ->
                            selectedTradingPair = pair
                            chartPair = null
                            tab = "Trade"
                        },
                        onDetail = { detail = it },
                        onTradeUpdate = { refreshKey++ }
                    )
                } else {
                    when (tab) {
                        "Home" -> HomeScreen(
                            favorites = favorites,
                            refreshKey = refreshKey,
                            onMarkets = { tab = "Markets" },
                            onTrade = { tab = "Trade" },
                            onDetail = { value ->
                                if (value.startsWith("Trade ")) {
                                    chartPair = value.removePrefix("Trade ") + "/USDT"
                                } else detail = value
                            }
                        )
                        "Markets" -> MarketsScreen(favorites, refreshKey) { symbol ->
                            chartPair = "$symbol/USDT"
                            detail = null
                        }
                        "Trade" -> TradeScreen(
                            favorites = favorites,
                            initialPair = selectedTradingPair,
                            chartOnly = false,
                            initialSide = selectedTradingSide,
                            onOpenOrderEntry = { selectedTradingPair = it; chartPair = null },
                            onDetail = { detail = it },
                            onTradeUpdate = { refreshKey++ }
                        )
                        "Futures" -> NativeDetail("Futures Trading · Coming Soon") { detail = null }
                        "Assets" -> if (FirebaseAuth.getInstance().currentUser == null) NativeDetail("Login / Sign up") { detail = null } else AssetsScreen(refreshKey = refreshKey) { detail = it }
                        else -> ProfileScreen { detail = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (onAction != null) {
            IconButton(onClick = onAction) {
                Icon(Icons.Filled.NotificationsNone, "Notifications", tint = White)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (action != null) Text(
            action, color = Muted, fontSize = 12.sp,
            modifier = Modifier.clickable { onClick?.invoke() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(favorites: MutableList<String>, refreshKey: Int, onMarkets: () -> Unit, onTrade: () -> Unit, onDetail: (String) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var signedInUser by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            signedInUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    var walletBalance by remember { mutableStateOf<String?>(null) }
    var homeAccountBalances by remember { mutableStateOf(JSONObject()) }
    var todayPnl by remember { mutableStateOf<Double?>(null) }
    var balanceHidden by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("Hot") }
    var search by remember { mutableStateOf("") }
    var liveMarkets by remember { mutableStateOf(cachedLiveMarkets?.takeIf { it.isNotEmpty() } ?: sampleMarkets) }
    LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { sharedLiveMarketsState.value }.debounce(350).collect { if (it.isNotEmpty()) liveMarkets = it } }
    var loading by remember { mutableStateOf(!hasUsableMarketPrices(cachedLiveMarkets.orEmpty())) }
    var sheetSearch by remember { mutableStateOf("") }
    var sheetFilter by remember { mutableStateOf("All") }
    var showAllPairs by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        try {
            val latest = fetchLiveMarkets()
            if (latest.isNotEmpty()) {
                liveMarkets = latest
                loading = false
            }
        } catch (_: Exception) {
            // Keep skeleton placeholders visible until a valid price snapshot arrives.
        }

        val client = OkHttpClient()
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val request = Request.Builder()
                .url("wss://stream.binance.com:9443/ws/!ticker@arr")
                .build()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val updated = parseTickerWebSocketMarkets(text)
                        if (updated.isNotEmpty() && updated != cachedLiveMarkets) {
                            liveMarkets = updated
                            cachedLiveMarkets = updated
                            sharedLiveMarketsState.value = updated
                            cachedLiveMarketsAt = System.currentTimeMillis()
                            loading = false
                        }
                    } catch (_: Exception) { }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed.complete(Unit)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }
            })
            kotlinx.coroutines.withTimeoutOrNull(25000) { closed.await() }
            socket.cancel()
            // REST snapshot only after stream disconnect; reconnect quickly.
            try {
                val latest = fetchLiveMarkets(forceRefresh = true)
                if (latest.isNotEmpty()) { liveMarkets = latest; loading = false }
            } catch (_: Exception) { }
            delay(750)
        }
    }

    DisposableEffect(signedInUser?.uid, refreshKey) {
        val uid = signedInUser?.uid
        if (uid == null) {
            walletBalance = null
            homeAccountBalances = JSONObject()
            todayPnl = null
            onDispose { }
        } else {
            val ordersListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("orders")
                .addSnapshotListener { _, error ->
                    if (error == null) scope.launch {
                        try {
                            val response = apexApiRequest("/api/pnl/today")
                            if (response.has("pnl") && !response.isNull("pnl")) todayPnl = response.optDouble("pnl")
                        } catch (_: Exception) { }
                        try {
                            val account = apexApiRequest("/api/account")
                            val (testnetAssets, amounts) = parseBinanceTestnetBalances(account)
                            homeAccountBalances = amounts
                            cachedAssetBalances = testnetAssets
                            cachedAssetTotalUsdt = calculateWalletTotalUsdt(amounts, liveMarkets)
                            cachedAssetBalancesAt = System.currentTimeMillis()
                        } catch (_: Exception) { }
                    }
                }
            scope.launch {
                try {
                    val response = apexApiRequest("/api/pnl/today")
                    if (response.has("pnl") && !response.isNull("pnl")) todayPnl = response.optDouble("pnl")
                } catch (_: Exception) { }
                try {
                    val account = apexApiRequest("/api/account")
                    val (testnetAssets, balances) = parseBinanceTestnetBalances(account)
                    homeAccountBalances = balances
                    cachedAssetBalances = testnetAssets
                    cachedAssetTotalUsdt = calculateWalletTotalUsdt(balances, liveMarkets)
                    cachedAssetBalancesAt = System.currentTimeMillis()
                } catch (_: Exception) { }
            }
            onDispose { ordersListener.remove() }
        }
    }

    LaunchedEffect(liveMarkets, homeAccountBalances) {
        if (signedInUser != null && homeAccountBalances.length() > 0) {
            val total = calculateWalletTotalUsdt(homeAccountBalances, liveMarkets)
            walletBalance = String.format(java.util.Locale.US, "%,.2f USDT", total)
            cachedAssetTotalUsdt = total
        }
    }

    val filtered = liveMarkets.filter { coin ->
        (coin.symbol.contains(search, true) || coin.name.contains(search, true)) &&
        when (filter) {
            "Favorite" -> coin.symbol in favorites
            "Hot" -> coin.symbol in setOf("BTC", "ETH", "BNB", "SOL", "XRP", "DOGE")
            "Gainer" -> coin.up
            "Loser" -> !coin.up
            else -> true
        }
    }.take(6)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onDetail("Profile") }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.weight(1f).height(42.dp),
                    placeholder = { Text("Search BTC/USDT", fontSize = 11.sp, color = Muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Yellow, unfocusedBorderColor = Color(0xFF292D35),
                        focusedTextColor = White, unfocusedTextColor = White,
                        unfocusedContainerColor = Panel, focusedContainerColor = Panel
                    )
                )
                IconButton(onClick = { onDetail("Notifications") }) {
                    Icon(Icons.Filled.NotificationsNone, "Notifications", tint = White)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Est. Total Value (USD)", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { balanceHidden = !balanceHidden },
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (balanceHidden) "◎ Show" else "◉ Hide", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val balanceLabel = when {
                            signedInUser == null -> "Connect account"
                            balanceHidden -> "••••••••"
                            else -> walletBalance ?: "—"
                        }
                        val balanceParts = balanceLabel.removeSuffix(" USDT")
                        val displayedBalance = if (signedInUser != null && !balanceHidden && walletBalance != null) "\$" + balanceParts else balanceParts
                        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            val fittedFontSize = if (displayedBalance.isNotEmpty()) {
                                (maxWidth.value / displayedBalance.length.coerceAtLeast(1) * 1.95f)
                                    .coerceIn(22f, 41f).sp
                            } else 32.sp
                            Text(
                                displayedBalance,
                                modifier = Modifier.fillMaxWidth(),
                                color = White,
                                fontSize = fittedFontSize,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                            )
                        }
                        Button(
                            onClick = {
                                if (signedInUser == null) onDetail("Account Login")
                                else onDetail("Add Funds")
                            },
                            modifier = Modifier.width(82.dp).height(29.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (signedInUser == null) "Sign Up / Log In" else "Add Funds",
                                fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                maxLines = 1, softWrap = false
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's PNL", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            todayPnl?.let { String.format(java.util.Locale.US, "%+.2f USDT", it) } ?: "—",
                            color = when {
                                todayPnl == null -> Muted
                                todayPnl!! > 0 -> Green
                                todayPnl!! < 0 -> Color(0xFFF6465D)
                                else -> White
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Favorites", "Hot", "Gainer", "Loser", "New").forEach { label ->
                            val option = if (label == "Favorites") "Favorite" else label
                            Text(
                                label,
                                color = if (filter == option) White else Muted,
                                fontSize = 14.sp,
                                fontWeight = if (filter == option) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.clickable { filter = option }.padding(vertical = 9.dp)
                            )
                        }
                        Text(
                            "☰", color = Muted,
                            modifier = Modifier.clickable { showAllPairs = true }.padding(vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
                        Text("Name", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("Last Price", color = Muted, fontSize = 11.sp, modifier = Modifier.width(100.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Spacer(Modifier.width(8.dp))
                        Text("24h chg%", color = Muted, fontSize = 11.sp, modifier = Modifier.width(82.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    if (loading) {
                        repeat(6) { MarketRowSkeleton() }
                    } else {
                        filtered.take(6).forEach { coin ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onDetail("Trade ${coin.symbol}") }
                                    .padding(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MarketCoinIcon(coin.symbol, 34.dp)
                                Spacer(Modifier.width(8.dp))
                                CompactMarketPairName("${coin.symbol}/USDT", coin.name, pairSize = 14.sp, modifier = Modifier.weight(1f))
                                CompactMarketPrice(coin.price, Modifier.width(100.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
                                    MarketChangeBadge(coin.change)
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { showAllPairs = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 0.dp)
                    ) { Text("View More", color = Muted, fontSize = 14.sp, lineHeight = 17.sp) }
                }
            }
        }
    }

    if (showAllPairs) {
        ModalBottomSheet(
            onDismissRequest = { showAllPairs = false; sheetSearch = ""; sheetFilter = "All" },
            modifier = Modifier.fillMaxHeight(0.96f),
            sheetState = sheetState,
            containerColor = Panel,
            contentColor = White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Muted) }
        ) {
            Column(Modifier.fillMaxWidth().fillMaxSize()) {
                Text("All Market Pairs", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                OutlinedTextField(
                    value = sheetSearch, onValueChange = { sheetSearch = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(50.dp),
                    placeholder = { Text("Search coin or pair", color = Muted, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Yellow, unfocusedBorderColor = Color(0xFF292D35),
                        focusedTextColor = White, unfocusedTextColor = White,
                        unfocusedContainerColor = Color(0xFF111419),
                        focusedContainerColor = Color(0xFF111419)
                    )
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    listOf("All", "Favorite", "Hot", "Gainer", "Loser").forEach { category ->
                        Text(
                            category,
                            color = if (sheetFilter == category) Yellow else Muted,
                            fontSize = 14.sp,
                            fontWeight = if (sheetFilter == category) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clickable { sheetFilter = category }.padding(vertical = 7.dp)
                        )
                    }
                }
                val allPairResults = liveMarkets
                    .filter { coin ->
                        (coin.symbol.contains(sheetSearch, true) || coin.name.contains(sheetSearch, true)) &&
                            when (sheetFilter) {
                                "Favorite" -> coin.symbol in favorites
                                "Hot" -> coin.symbol in setOf("BTC", "ETH", "BNB", "SOL", "XRP", "DOGE")
                                "Gainer" -> (coin.change.removeSuffix("%").toDoubleOrNull() ?: 0.0) > 0.0
                                "Loser" -> (coin.change.removeSuffix("%").toDoubleOrNull() ?: 0.0) < 0.0
                                else -> true
                            }
                    }
                    .let { coins ->
                        when (sheetFilter) {
                            "Gainer" -> coins.sortedByDescending { it.change.removeSuffix("%").toDoubleOrNull() ?: 0.0 }
                            "Loser" -> coins.sortedBy { it.change.removeSuffix("%").toDoubleOrNull() ?: 0.0 }
                            else -> coins
                        }
                    }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Name", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("Last Price", color = Muted, fontSize = 11.sp, modifier = Modifier.width(100.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Spacer(Modifier.width(8.dp))
                            Text("24h chg%", color = Muted, fontSize = 11.sp, modifier = Modifier.width(82.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.width(34.dp))
                        }
                    }
                    items(allPairResults) { coin ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                showAllPairs = false
                                onDetail("Trade ${coin.symbol}")
                            }.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MarketCoinIcon(coin.symbol, 34.dp)
                            Spacer(Modifier.width(8.dp))
                            CompactMarketPairName("${coin.symbol}/USDT", coin.name, modifier = Modifier.weight(1f))
                            CompactMarketPrice(coin.price, Modifier.width(100.dp))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
                                MarketChangeBadge(coin.change)
                            }
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    if (coin.symbol in favorites) favorites.remove(coin.symbol)
                                    else favorites.add(coin.symbol)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (coin.symbol in favorites) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = if (coin.symbol in favorites) "Remove from favorites" else "Add to favorites",
                                    tint = if (coin.symbol in favorites) Yellow else Muted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun CompactMarketPairName(
    pair: String,
    coinName: String,
    pairSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            pair,
            color = White,
            fontWeight = FontWeight.SemiBold,
            fontSize = pairSize,
            lineHeight = (pairSize.value + 1f).sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            coinName,
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactMarketPrice(
    price: String,
    modifier: Modifier = Modifier,
    secondLineSize: androidx.compose.ui.unit.TextUnit = 10.sp
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            price,
            color = White,
            fontSize = 15.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            softWrap = false
        )
        Text(
            coinUsdPriceLabel(price),
            color = Muted,
            fontSize = secondLineSize,
            lineHeight = (secondLineSize.value + 1f).sp,
            fontWeight = FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun coinUsdPriceLabel(price: String): String {
    val value = price.replace(",", "").replace("$", "").trim().toDoubleOrNull()
        ?: return "≈ $—"
    return "≈ $" + when {
        value >= 1.0 -> String.format(java.util.Locale.US, "%,.2f", value)
        value >= 0.01 -> String.format(java.util.Locale.US, "%.4f", value)
        else -> String.format(java.util.Locale.US, "%.6f", value)
    }
}

private data class Coin(val symbol: String, val name: String, val price: String, val change: String, val up: Boolean)

private fun marketChangeColor(change: String): Color = when {
    change.trim().startsWith("+") -> Green
    change.trim().startsWith("-") -> Red
    else -> Muted
}

@Composable
private fun MarketChangeBadge(change: String, fontSize: androidx.compose.ui.unit.TextUnit = 11.sp) {
    val positive = change.trim().startsWith("+")
    val negative = change.trim().startsWith("-")
    val tint = when {
        positive -> Green
        negative -> Red
        else -> Muted
    }
    Box(
        modifier = Modifier
            .width(82.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Text(
            change,
            color = White,
            fontSize = maxOf(fontSize.value, 11f).sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

private data class Candle(val open: Float, val high: Float, val low: Float, val close: Float, val openTime: Long = 0L, val volume: Float = 0f, val quoteVolume: Float = 0f)

private data class BookLevel(val price: String, val quantity: String)
private data class RecentTrade(val price: String, val quantity: String, val time: String, val buyerIsMaker: Boolean)
private suspend fun fetchDepthAndTrades(symbol: String): Triple<List<BookLevel>, List<BookLevel>, List<RecentTrade>> = withContext(Dispatchers.IO) {
    val pair = symbol.replace("/", "")
    fun request(path: String): String {
        val connection = (URL("https://api.binance.com$path").openConnection() as java.net.HttpURLConnection)
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            if (connection.responseCode !in 200..299) throw IllegalStateException("Market API HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
    val depth = org.json.JSONObject(request("/api/v3/depth?symbol=$pair&limit=20"))
    fun levels(key: String): List<BookLevel> {
        val array = depth.getJSONArray(key)
        return (0 until array.length()).map { i ->
            val row = array.getJSONArray(i)
            BookLevel(row.getString(0), row.getString(1))
        }
    }
    val rows = JSONArray(request("/api/v3/trades?symbol=$pair&limit=8"))
    val trades = (0 until rows.length()).map { i ->
        val row = rows.getJSONObject(i)
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(row.getLong("time")))
        RecentTrade(row.getString("price"), row.getString("qty"), time, row.getBoolean("isBuyerMaker"))
    }
    Triple(levels("bids"), levels("asks"), trades)
}
private data class PairMarketData(val price: String, val changePercent: String, val candles: List<Candle>)

private suspend fun fetchPairMarketData(symbol: String, interval: String): PairMarketData = withContext(Dispatchers.IO) {
    val pair = symbol.replace("/", "")
    fun getJson(path: String) : String {
        val connection = (URL("https://api.binance.com$path").openConnection() as java.net.HttpURLConnection)
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) throw IllegalStateException("Market API HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
    val ticker = org.json.JSONObject(getJson("/api/v3/ticker/24hr?symbol=$pair"))
    val klines = JSONArray(getJson("/api/v3/klines?symbol=$pair&interval=$interval&limit=120"))
    val candles = (0 until klines.length()).mapNotNull { index ->
        val row = klines.getJSONArray(index)
        val open = row.getString(1).toFloatOrNull() ?: return@mapNotNull null
        val high = row.getString(2).toFloatOrNull() ?: return@mapNotNull null
        val low = row.getString(3).toFloatOrNull() ?: return@mapNotNull null
        val close = row.getString(4).toFloatOrNull() ?: return@mapNotNull null
        Candle(open, high, low, close, row.optLong(0, 0L), row.optString(5).toFloatOrNull() ?: 0f, row.optString(7).toFloatOrNull() ?: 0f)
    }
    PairMarketData(ticker.getString("lastPrice"), ticker.optString("priceChangePercent", "0"), candles)
}

@Volatile
private var cachedUsdtSpotSymbols: Set<String>? = null
@Volatile
private var pinnedAssetSymbol: String? = null
private var cachedAssetBalances: List<Pair<String, String>> = emptyList()
private var cachedAssetTotalUsdt: Double = 0.0
private var cachedAssetBalancesAt: Long = 0L

private var cachedLiveMarkets: List<Coin>? = null
private val sharedLiveMarketsState = androidx.compose.runtime.mutableStateOf<List<Coin>>(emptyList())
@Volatile
private var cachedLiveMarketsAt: Long = 0L

// Convert every credited wallet asset into a USDT portfolio value using current spot prices.
private fun parseBinanceTestnetBalances(response: JSONObject): Pair<List<Pair<String, String>>, JSONObject> {
    val rows = response.optJSONArray("balances") ?: JSONArray()
    val amounts = JSONObject()
    val found = mutableListOf<Pair<String, String>>()
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val asset = row.optString("asset").uppercase()
        val free = row.optDouble("free", 0.0)
        val locked = row.optDouble("locked", 0.0)
        val total = free + locked
        if (asset.isNotBlank() && total > 0.0 && total.isFinite()) {
            amounts.put(asset, total)
            found.add(asset to String.format(java.util.Locale.US, "%.8f", total))
        }
    }
    return found to amounts
}

private fun calculateWalletTotalUsdt(
    balances: JSONObject,
    markets: List<Coin> = cachedLiveMarkets.orEmpty()
): Double {
    var total = 0.0
    val keys = balances.keys()
    while (keys.hasNext()) {
        val asset = keys.next().uppercase()
        val amount = balances.optDouble(asset, 0.0)
        if (!amount.isFinite() || amount <= 0.0) continue
        val price = when (asset) {
            "USDT", "USDC", "FDUSD", "BUSD" -> 1.0
            else -> markets.firstOrNull { it.symbol.equals(asset, ignoreCase = true) }
                ?.price?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        }
        if (price.isFinite() && price > 0.0) total += amount * price
    }
    return total.takeIf { it.isFinite() } ?: 0.0
}

private suspend fun fetchLiveMarkets(forceRefresh: Boolean = false): List<Coin> = withContext(Dispatchers.IO) {
    val cached = cachedLiveMarkets
    if (!forceRefresh && !cached.isNullOrEmpty() && System.currentTimeMillis() - cachedLiveMarketsAt < 3000L) {
        return@withContext cached
    }
    require(APEX_API_BASE_URL.isNotBlank()) { "APEX backend URL is not configured." }
    val request = Request.Builder()
        .url(APEX_API_BASE_URL.trimEnd('/') + "/api/markets")
        .header("Accept", "application/json")
        .build()
    APEX_HTTP_CLIENT.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException("APEX market API HTTP ${response.code}")
        val payload = JSONObject(raw)
        val rows = payload.optJSONArray("markets") ?: JSONArray()
        val result = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val symbol = row.optString("symbol")
            val priceValue = row.optString("price").toDoubleOrNull() ?: return@mapNotNull null
            if (symbol.isBlank() || !priceValue.isFinite() || priceValue <= 0.0) return@mapNotNull null
            val change = row.optDouble("change", 0.0)
            Coin(
                symbol = symbol,
                name = row.optString("name", symbol),
                price = String.format(java.util.Locale.US, "%,.8f", priceValue).trimEnd('0').trimEnd('.'),
                change = String.format(java.util.Locale.US, "%+.2f%%", change),
                up = row.optBoolean("up", change >= 0.0)
            )
        }
        if (result.isEmpty()) throw IllegalStateException("No live USDT market pairs returned.")
        cachedUsdtSpotSymbols = result.map { it.symbol + "USDT" }.toSet()
        cachedLiveMarkets = result
        sharedLiveMarketsState.value = result
        cachedLiveMarketsAt = System.currentTimeMillis()
        result
    }
}

private fun parseTickerWebSocketMarkets(payload: String): List<Coin> {
    val rows = JSONArray(payload)
    val names = mapOf(
        "BTC" to "Bitcoin", "ETH" to "Ethereum", "BNB" to "BNB",
        "SOL" to "Solana", "XRP" to "XRP", "DOGE" to "Dogecoin",
        "ADA" to "Cardano", "TRX" to "TRON", "TON" to "Toncoin",
        "LTC" to "Litecoin", "BCH" to "Bitcoin Cash", "LINK" to "Chainlink",
        "DOT" to "Polkadot", "AVAX" to "Avalanche", "POL" to "POL",
        "SHIB" to "Shiba Inu", "ATOM" to "Cosmos", "ETC" to "Ethereum Classic",
        "UNI" to "Uniswap", "NEAR" to "NEAR Protocol"
    )
    val allowed = cachedUsdtSpotSymbols.orEmpty()
    val quoteVolumes = HashMap<String, Double>(rows.length())
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        quoteVolumes[row.optString("s")] = row.optString("q").toDoubleOrNull() ?: 0.0
    }
    return (0 until rows.length()).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        val full = row.optString("s")
        if (!full.endsWith("USDT") || (allowed.isNotEmpty() && full !in allowed)) return@mapNotNull null
        val base = full.removeSuffix("USDT")
        val price = row.optString("c").toDoubleOrNull() ?: return@mapNotNull null
        if (!price.isFinite() || price <= 0.0) return@mapNotNull null
        val change = row.optString("P").toDoubleOrNull() ?: 0.0
        Coin(
            symbol = base,
            name = names[base] ?: base,
            price = String.format(java.util.Locale.US, "%,.8f", price).trimEnd('0').trimEnd('.'),
            change = String.format(java.util.Locale.US, "%+.2f%%", change),
            up = change >= 0.0
        )
    }.sortedByDescending { coin -> quoteVolumes[coin.symbol + "USDT"] ?: 0.0 }
}

private fun hasUsableMarketPrices(markets: List<Coin>): Boolean = markets.any { it.price != "—" && (it.price.toDoubleOrNull() ?: 0.0) > 0.0 }

private val sampleMarkets = listOf(
    Coin("BTC", "Bitcoin", "—", "—", true),
    Coin("ETH", "Ethereum", "—", "—", true),
    Coin("BNB", "BNB", "—", "—", false),
    Coin("SOL", "Solana", "—", "—", true),
    Coin("XRP", "XRP", "—", "—", false),
    Coin("DOGE", "Dogecoin", "—", "—", true),
    Coin("ADA", "Cardano", "—", "—", false)
)

@Composable
private fun MarketsScreen(favorites: MutableList<String>, refreshKey: Int, onCoin: (String) -> Unit) {
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var liveMarkets by remember { mutableStateOf(sharedLiveMarketsState.value.ifEmpty { sampleMarkets }) }
    LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { sharedLiveMarketsState.value }.collect { if (it.isNotEmpty()) liveMarkets = it } }
    var loading by remember { mutableStateOf(!hasUsableMarketPrices(sharedLiveMarketsState.value.ifEmpty { cachedLiveMarkets.orEmpty() })) }
    var error by remember { mutableStateOf<String?>(null) }
    val hotCoins = setOf("BTC", "ETH", "BNB", "SOL")

    LaunchedEffect(refreshKey) {
        try {
            val latest = fetchLiveMarkets()
            if (latest.isNotEmpty()) {
                liveMarkets = latest
                error = null
            }
        } catch (e: Exception) {
            error = e.message ?: "Market data unavailable"
        } finally {
            if (hasUsableMarketPrices(liveMarkets)) loading = false
        }
        val client = OkHttpClient()
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val request = Request.Builder().url("wss://stream.binance.com:9443/ws/!ticker@arr").build()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val updated = parseTickerWebSocketMarkets(text)
                        if (updated.isNotEmpty()) {
                            liveMarkets = updated
                            cachedLiveMarkets = updated
                            sharedLiveMarketsState.value = updated
                            cachedLiveMarketsAt = System.currentTimeMillis()
                            loading = false
                            error = null
                        }
                    } catch (_: Exception) { }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed.complete(Unit)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }
            })
            try {
                kotlinx.coroutines.withTimeoutOrNull(25000) { closed.await() }
            } finally {
                socket.cancel()
            }
            // Reconnect if the stream closes or becomes silent; HTTP is only a recovery path.
            try {
                val latest = fetchLiveMarkets(forceRefresh = true)
                if (latest.isNotEmpty()) {
                    liveMarkets = latest
                    cachedLiveMarkets = latest
                    sharedLiveMarketsState.value = latest
                    cachedLiveMarketsAt = System.currentTimeMillis()
                    loading = false
                    error = null
                }
            } catch (e: Exception) {
                if (liveMarkets.isEmpty()) error = e.message ?: "Market data reconnecting"
            }
            delay(1000)
        }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    val filtered = liveMarkets.filter { coin ->
        val matchesSearch = coin.symbol.contains(query, true) || coin.name.contains(query, true)
        val matchesFilter = when (filter) {
            "Favorite" -> coin.symbol in favorites
            "Hot" -> coin.symbol in hotCoins
            "Gainer" -> coin.up
            "Loser" -> !coin.up
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Markets")
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
            placeholder = { Text("Search coins or trading pairs", color = Muted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Filled.Close, "Clear search", tint = Muted)
                }
            },
            singleLine = true, shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Yellow, unfocusedBorderColor = Panel,
                focusedTextColor = White, unfocusedTextColor = White, cursorColor = Yellow
            )
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf("All", "Favorite", "Hot", "Gainer", "Loser").forEach { option ->
                FilterChip(
                    selected = filter == option, onClick = { filter = option },
                    label = { Text(option, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF383019),
                        selectedLabelColor = Yellow, labelColor = Muted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = filter == option,
                        borderColor = Panel, selectedBorderColor = Color(0xFF66521B)
                    )
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Asset / Pair", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("Last price", color = Muted, fontSize = 11.sp, modifier = Modifier.width(100.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Spacer(Modifier.width(8.dp))
            Text("24h", color = Muted, fontSize = 11.sp, modifier = Modifier.width(82.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(34.dp))
        }
        HorizontalDivider(color = Panel)
        if (loading && !hasUsableMarketPrices(liveMarkets)) {
            LazyColumn(Modifier.weight(1f)) {
                items(10) { MarketRowSkeleton() }
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SearchOff, null, tint = Muted, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(if (filter == "Favorite") "No favorite coins yet" else "No coins found", color = Muted)
                    Text("Try another filter or search", color = Muted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered) { coin ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onCoin(coin.symbol) }
                            .padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactMarketPairName("${coin.symbol}/USDT", coin.name, pairSize = 14.sp, modifier = Modifier.weight(1f))
                        CompactMarketPrice(coin.price, modifier = Modifier.width(100.dp), secondLineSize = 9.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
                            MarketChangeBadge(coin.change)
                        }
                        IconButton(
                            onClick = {
                                if (coin.symbol in favorites) favorites.remove(coin.symbol)
                                else favorites.add(coin.symbol)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                if (coin.symbol in favorites) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Toggle favorite",
                                tint = if (coin.symbol in favorites) Yellow else Muted,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Panel, thickness = 1.dp)
                }
            }
        }
        if (error != null) {
            Text("Live prices temporarily unavailable · retrying", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
        } else {
            Text("Public market data · refreshes every 10 seconds", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun MarketRowSkeleton() {
    val skeleton = Color(0xFF252A32)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).background(skeleton, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(82.dp).height(12.dp).background(skeleton, RoundedCornerShape(6.dp)))
            Box(Modifier.width(52.dp).height(9.dp).background(skeleton, RoundedCornerShape(5.dp)))
        }
        Box(Modifier.width(54.dp).height(11.dp).background(skeleton, RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(42.dp))
        Box(Modifier.width(50.dp).height(28.dp).background(skeleton, RoundedCornerShape(8.dp)))
    }
}

@Composable
private fun MarketRow(coin: Coin, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactMarketPairName("${coin.symbol}/USDT", coin.name, modifier = Modifier.weight(1f))
        CompactMarketPrice(coin.price, modifier = Modifier.width(100.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
            MarketChangeBadge(coin.change)
        }
    }
    HorizontalDivider(color = Panel, thickness = 1.dp)
}

@Composable
private fun TradingMarketHeader(pair: String, favorite: Boolean, onFavorite: () -> Unit, onPairPicker: () -> Unit, compactPrice: String, compactChange: String, showCompactPrice: Boolean) {
    Column(Modifier.fillMaxWidth().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = White, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(15.dp))
                Text(pair, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onPairPicker() })
                Icon(Icons.Filled.ArrowDropDown, "Select pair", tint = White, modifier = Modifier.size(28.dp).clickable { onPairPicker() })
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.AutoAwesome, "Tools", tint = Yellow, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(15.dp))
                Icon(if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorite", tint = Yellow, modifier = Modifier.size(27.dp).clickable { onFavorite() })
                Spacer(Modifier.width(15.dp))
                Icon(Icons.Filled.NotificationsNone, "Notifications", tint = White, modifier = Modifier.size(26.dp))
            }
            Row(Modifier.fillMaxWidth().height(39.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(25.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Price", "Info", "Data", "Square").forEachIndexed { index, label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = if (index == 0) White else Muted, fontSize = 17.sp, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium)
                        if (index == 0) Box(Modifier.padding(top = 4.dp).width(42.dp).height(3.dp).background(Yellow, RoundedCornerShape(3.dp)))
                    }
                }
            }
            if (showCompactPrice) {
                val compactPercent = compactChange.toDoubleOrNull() ?: 0.0
                Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(compactPrice, color = if (compactPercent >= 0) Green else Red, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                    Text(String.format(java.util.Locale.US, "%+.2f%%", compactPercent), color = if (compactPercent >= 0) Green else Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = Color(0xFF292D35))
    }
}

@Composable
private fun TradingMarketOverview(pair: String, price: String, change: String, candles: List<Candle>, interval: String, onInterval: (String) -> Unit, favorite: Boolean, onFavorite: () -> Unit, onPairPicker: () -> Unit, showHeader: Boolean = true) {
    val p = price.replace(",", "").toDoubleOrNull() ?: 0.0
    // Match the meaningful decimal precision supplied by the live ticker.
    val normalizedLivePrice = price.replace(",", "").trim()
    val priceDecimals = if (normalizedLivePrice.contains(".")) normalizedLivePrice.substringAfter(".").trimEnd('0').length.coerceIn(0, 8) else 0
    val axisPriceFormat = "%,.${priceDecimals}f"
    val changeValue = change.toDoubleOrNull() ?: 0.0
    val closes = candles.map { it.close }
    fun movingAverage(period: Int): Float? = closes.takeLast(period).takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val high = candles.maxOfOrNull { it.high }
    val low = candles.minOfOrNull { it.low }
    var selectedCandleIndex by remember(pair, interval) { mutableStateOf<Int?>(null) }
    var crosshairX by remember(pair, interval) { mutableFloatStateOf(0f) }
    var crosshairY by remember(pair, interval) { mutableFloatStateOf(0f) }
    var hasCrosshair by remember(pair, interval) { mutableStateOf(false) }
    var draggedPriceOffset by remember(pair, interval) { mutableFloatStateOf(0f) }
    var selectedPriceY by remember(pair, interval) { mutableFloatStateOf(-1f) }
    var isPriceDragging by remember(pair, interval) { mutableStateOf(false) }
    var candleScrollOffset by remember(pair, interval) { mutableIntStateOf(0) }
    var chartEdgeOffsetPx by remember(pair, interval) { mutableFloatStateOf(0f) }
    var visibleCandleCount by remember(pair, interval) { mutableIntStateOf(minOf(40, candles.size).coerceAtLeast(1)) }
    var verticalPanFraction by remember(pair, interval) { mutableFloatStateOf(0f) }
    LaunchedEffect(candles.size) {
        visibleCandleCount = if (visibleCandleCount <= 1 && candles.size > 1) minOf(40, candles.size)
            else visibleCandleCount.coerceIn(1, candles.size.coerceAtLeast(1))
        candleScrollOffset = candleScrollOffset.coerceIn(0, (candles.size - visibleCandleCount).coerceAtLeast(0))
    }
    val maxCandleScroll = (candles.size - visibleCandleCount).coerceAtLeast(0)
    val visibleStart = (candles.size - visibleCandleCount - candleScrollOffset).coerceIn(0, maxCandleScroll)
    // The chart screen opens before its first REST candle snapshot arrives.
    // Never subList an empty candle list: that previously crashed the whole app.
    val chartCandles = remember(candles, visibleStart, visibleCandleCount) {
        if (candles.isNotEmpty() && visibleCandleCount > 0) {
            val safeStart = visibleStart.coerceIn(0, candles.lastIndex)
            val safeEnd = (safeStart + visibleCandleCount).coerceIn(safeStart + 1, candles.size)
            candles.subList(safeStart, safeEnd)
        } else emptyList()
    }
    val selectedCandle = selectedCandleIndex?.let { chartCandles.getOrNull(it) }
    fun candleTime(timestamp: Long): String = if (timestamp > 0L) {
        java.text.SimpleDateFormat("MMM d, yyyy  HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    } else "Time unavailable"
    fun compactVolume(value: Float): String = when {
        value >= 1_000_000_000f -> String.format(java.util.Locale.US, "%.2fB", value / 1_000_000_000f)
        value >= 1_000_000f -> String.format(java.util.Locale.US, "%.2fM", value / 1_000_000f)
        value >= 1_000f -> String.format(java.util.Locale.US, "%.2fK", value / 1_000f)
        else -> String.format(java.util.Locale.US, "%.2f", value)
    }
    fun candlePrice(value: Float): String = String.format(java.util.Locale.US, "%,.${priceDecimals}f", value)
    Column(Modifier.fillMaxWidth().background(Color.Black)) {
        if (showHeader) {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = White, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(15.dp))
                Text(pair, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onPairPicker() })
                Icon(Icons.Filled.ArrowDropDown, "Select pair", tint = White, modifier = Modifier.size(28.dp).clickable { onPairPicker() })
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.AutoAwesome, "Tools", tint = Yellow, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(15.dp))
                Icon(if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorite", tint = Yellow, modifier = Modifier.size(27.dp).clickable { onFavorite() })
                Spacer(Modifier.width(15.dp))
                Icon(Icons.Filled.NotificationsNone, "Notifications", tint = White, modifier = Modifier.size(26.dp))
            }
            Row(Modifier.fillMaxWidth().height(43.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(25.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Price", "Info", "Data", "Square").forEachIndexed { index, label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = if (index == 0) White else Muted, fontSize = 17.sp, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium)
                        if (index == 0) Box(Modifier.padding(top = 4.dp).width(42.dp).height(3.dp).background(Yellow, RoundedCornerShape(3.dp)))
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF292D35))
    
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.1f)) {
                Text(if (p > 0) String.format(java.util.Locale.US, axisPriceFormat, p) else "—", color = if (changeValue >= 0) Green else Red, fontSize = 31.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (p > 0) "$" + String.format(java.util.Locale.US, axisPriceFormat, p) else "—", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    Text(String.format(java.util.Locale.US, "%+.2f%%", changeValue), color = if (changeValue >= 0) Green else Red, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(2.dp))
                Text("Payments  ›   Vol  ›   Prv  ›", color = Muted, fontSize = 14.sp)
            }
            Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("24h High" to (high?.let { String.format(java.util.Locale.US, axisPriceFormat, it) } ?: "—"), "24h Low" to (low?.let { String.format(java.util.Locale.US, axisPriceFormat, it) } ?: "—"), "24h Vol(BTC)" to "—", "24h Vol(USDT)" to "—").forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1); Text(value, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF292D35))
        Row(Modifier.fillMaxWidth().height(45.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            Text("Time", color = Muted, fontSize = 14.sp)
            listOf("15m", "1h", "4h", "1D").forEach { value -> Text(value, color = if (interval == value) White else Muted, fontSize = 15.sp, fontWeight = if (interval == value) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable { onInterval(value) }) }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ShowChart, null, tint = White, modifier = Modifier.size(22.dp))
            Icon(Icons.Filled.Settings, null, tint = White, modifier = Modifier.size(22.dp))
            Icon(Icons.Filled.GridView, null, tint = White, modifier = Modifier.size(22.dp))
        }
        HorizontalDivider(color = Color(0xFF292D35))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("MA(7): ${movingAverage(7)?.let { candlePrice(it) } ?: "—"}", color = Yellow, fontSize = 11.sp)
            Text("MA(25): ${movingAverage(25)?.let { candlePrice(it) } ?: "—"}", color = Color(0xFFE92BA8), fontSize = 11.sp)
            Text("MA(99): ${movingAverage(99)?.let { candlePrice(it) } ?: "—"}", color = Color(0xFF5796F5), fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(270.dp)) {
            Canvas(Modifier.fillMaxSize().offset { IntOffset(chartEdgeOffsetPx.roundToInt(), 0) }.padding(start = 2.dp).pointerInput(pair, interval, candles.size, visibleCandleCount) {
                // A single gesture state machine prevents pan, crosshair and pinch
                // detectors from consuming each other's pointer events.
                var panRemainder = 0f
                var pinchDistance = 0f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis
                    var moved = false
                    var crosshairMode = false
                    var wasMultiTouch = false
                    var previousPosition = down.position
                    var tapPosition = down.position
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val active: List<PointerInputChange> = event.changes.filter { change -> change.pressed }
                        if (active.size >= 2) {
                            wasMultiTouch = true
                            moved = true
                            val first = active[0]
                            val second = active[1]
                            val dx = first.position.x - second.position.x
                            val dy = first.position.y - second.position.y
                            val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
                            if (pinchDistance > 0f && candles.isNotEmpty()) {
                                val zoom = distance / pinchDistance
                                if (kotlin.math.abs(zoom - 1f) > 0.002f) {
                                    val nextCount = (visibleCandleCount / zoom).roundToInt()
                                        .coerceIn(5.coerceAtMost(candles.size), candles.size)
                                    visibleCandleCount = nextCount
                                    candleScrollOffset = candleScrollOffset.coerceIn(0, (candles.size - nextCount).coerceAtLeast(0))
                                    chartEdgeOffsetPx = 0f
                                    selectedCandleIndex = null
                                    hasCrosshair = false
                                    selectedPriceY = -1f
                                    panRemainder = 0f
                                }
                            }
                            pinchDistance = distance
                        } else if (active.size == 1 && !wasMultiTouch) {
                            val change = active.first()
                            val delta = change.position - previousPosition
                            val elapsed = change.uptimeMillis - downTime
                            if (!crosshairMode && elapsed >= viewConfiguration.longPressTimeoutMillis && !moved) {
                                crosshairMode = true
                                if (chartCandles.isNotEmpty()) {
                                    crosshairX = change.position.x.coerceIn(0f, size.width.toFloat())
                                    crosshairY = change.position.y.coerceIn(0f, size.height.toFloat())
                                    selectedPriceY = crosshairY
                                    val slot = size.width.toFloat() / chartCandles.size.coerceAtLeast(1)
                                    selectedCandleIndex = (crosshairX / slot).toInt().coerceIn(0, chartCandles.lastIndex)
                                    hasCrosshair = true
                                }
                            }
                            if (crosshairMode) {
                                moved = true
                                crosshairX = change.position.x.coerceIn(0f, size.width.toFloat())
                                crosshairY = change.position.y.coerceIn(0f, size.height.toFloat())
                                selectedPriceY = crosshairY
                                val slot = size.width.toFloat() / chartCandles.size.coerceAtLeast(1)
                                selectedCandleIndex = (crosshairX / slot).toInt().coerceIn(0, chartCandles.lastIndex)
                                hasCrosshair = true
                            } else if (kotlin.math.abs(delta.x) > viewConfiguration.touchSlop || moved) {
                                moved = true
                                // Horizontal history drag only; discard excess at the ends
                                // so reversing direction responds immediately instead of sticking.
                                val slotWidth = (size.width.toFloat() / visibleCandleCount.coerceAtLeast(1)).coerceAtLeast(1f)
                                panRemainder += delta.x
                                val steps = (panRemainder / slotWidth).toInt()
                                if (steps != 0) {
                                    val maxOffset = (candles.size - visibleCandleCount).coerceAtLeast(0)
                                    val oldOffset = candleScrollOffset
                                    val nextOffset = (oldOffset + steps).coerceIn(0, maxOffset)
                                    val applied = nextOffset - oldOffset
                                    candleScrollOffset = nextOffset
                                    panRemainder -= applied * slotWidth
                                    if (applied == 0) panRemainder = 0f
                                    selectedCandleIndex = null
                                    hasCrosshair = false
                                    selectedPriceY = -1f
                                }
                                chartEdgeOffsetPx = 0f
                            }
                            previousPosition = change.position
                            tapPosition = change.position
                        } else if (active.isEmpty()) {
                            pinchDistance = 0f
                        }
                        event.changes.forEach { it.consume() }
                    } while (active.isNotEmpty())
                    if (!moved && !wasMultiTouch && down.uptimeMillis > 0L) {
                        if (selectedCandleIndex != null) {
                            selectedCandleIndex = null
                            hasCrosshair = false
                            selectedPriceY = -1f
                        }
                    }
                    pinchDistance = 0f
                }
            }) {
                if (chartCandles.isNotEmpty()) {
                    // White plot surface applies only behind the candles; the surrounding
                    // app and right-hand price scale keep their existing dark theme.
                    drawRect(Color.White)
                    // Keep the visible candle window as the price-scale source. The live
                    // price guide may sit beyond this range while browsing older candles.
                    val rawMin = chartCandles.minOf { it.low }
                    val rawMax = chartCandles.maxOf { it.high }
                    val padding = ((rawMax - rawMin) * 0.04f).coerceAtLeast(kotlin.math.abs(p.toFloat()) * 0.0001f)
                    val baseRange = (rawMax - rawMin + padding * 2f).coerceAtLeast(0.000001f)
                    val minP = rawMin - padding + baseRange * verticalPanFraction
                    val maxP = rawMax + padding + baseRange * verticalPanFraction
                    val range = (maxP - minP).coerceAtLeast(0.000001f)
                    val slot = size.width / chartCandles.size.coerceAtLeast(1)
                    // Removed the short horizontal tick lines on the right price scale.
                    if (p > 0.0) {
                        val liveY = if (hasCrosshair && selectedPriceY >= 0f) selectedPriceY.coerceIn(0f, size.height) else (size.height - ((p.toFloat() - minP) / range) * size.height).coerceIn(0f, size.height.toFloat())
                        drawLine(Color(0xFF252A32).copy(alpha = 0.85f), Offset(0f, liveY), Offset(size.width, liveY), 1.2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 5f)))
                    }
                    chartCandles.forEachIndexed { i, candle ->
                        val x = slot * i + slot / 2f
                        val yH = size.height - ((candle.high - minP) / range) * size.height
                        val yL = size.height - ((candle.low - minP) / range) * size.height
                        val yO = size.height - ((candle.open - minP) / range) * size.height
                        val yC = size.height - ((candle.close - minP) / range) * size.height
                        val c = if (candle.close >= candle.open) Green else Red
                        drawLine(c, Offset(x, yH), Offset(x, yL), 1.3f); drawLine(c, Offset(x, yO), Offset(x, yC), (slot * 0.58f).coerceAtLeast(2f))
                    }
                    // Small labels for the highest and lowest visible candle prices.
                    val highIndex = chartCandles.indices.maxByOrNull { chartCandles[it].high }
                    val lowIndex = chartCandles.indices.minByOrNull { chartCandles[it].low }
                    val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        textSize = 10f * density
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                    highIndex?.let { index ->
                        val candle = chartCandles[index]
                        val x = slot * index + slot / 2f
                        val y = size.height - ((candle.high - minP) / range) * size.height
                        val label = String.format(java.util.Locale.US, "%,.${priceDecimals}f", candle.high)
                        val labelX = if (x > size.width * 0.72f) x - labelPaint.measureText(label) - 7f else x + 7f
                        val lineEnd = if (labelX < x) labelX + labelPaint.measureText(label) else labelX
                        drawLine(Color(0xFF333333), Offset(x, y), Offset(lineEnd, y), 1f)
                        drawContext.canvas.nativeCanvas.drawText(label, labelX, (y - 5f).coerceAtLeast(12f), labelPaint)
                    }
                    lowIndex?.let { index ->
                        val candle = chartCandles[index]
                        val x = slot * index + slot / 2f
                        val y = size.height - ((candle.low - minP) / range) * size.height
                        val label = String.format(java.util.Locale.US, "%,.${priceDecimals}f", candle.low)
                        val labelX = if (x > size.width * 0.72f) x - labelPaint.measureText(label) - 7f else x + 7f
                        val lineEnd = if (labelX < x) labelX + labelPaint.measureText(label) else labelX
                        drawLine(Color.White, Offset(x, y), Offset(lineEnd, y), 1f)
                        drawContext.canvas.nativeCanvas.drawText(label, labelX, (y + 13f).coerceAtMost(size.height - 2f), labelPaint)
                    }
                    if (hasCrosshair) {
                        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 5f))
                        drawLine(Color(0xFF6B7280), Offset(crosshairX, 0f), Offset(crosshairX, size.height), 1.2f, pathEffect = dash)
                        drawLine(Color(0xFF6B7280), Offset(0f, crosshairY), Offset(size.width, crosshairY), 1.2f, pathEffect = dash)
                        drawCircle(Color(0xFF111827), 4f, Offset(crosshairX, crosshairY))
                    }
                }
            }
            BoxWithConstraints(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(66.dp)
                    .pointerInput(pair, interval, p) {
                        detectDragGestures(
                            onDragStart = { position ->
                                selectedCandleIndex = null
                                hasCrosshair = true
                                selectedPriceY = position.y.coerceIn(0f, size.height.toFloat())
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                hasCrosshair = true
                                selectedPriceY = (selectedPriceY + dragAmount.y).coerceIn(0f, size.height.toFloat())
                            }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // Price scale grid shares the chart's horizontal levels; no divider line beside the badge.
                    // Removed the short horizontal tick lines on the right price scale.
                    if (high != null && low != null && p > 0.0) {
                        val range = (high - low).coerceAtLeast(0.000001f)
                        val rawMin = candles.minOfOrNull { it.low } ?: p.toFloat()
                        val rawMax = candles.maxOfOrNull { it.high } ?: p.toFloat()
                        val pad = ((rawMax - rawMin) * 0.04f).coerceAtLeast(kotlin.math.abs(p.toFloat()) * 0.0001f)
                        val plotMin = rawMin - pad
                        val plotMax = rawMax + pad
                        val plotRange = (plotMax - plotMin).coerceAtLeast(0.000001f)
                        val currentFraction = ((plotMax - p.toFloat()) / plotRange).coerceIn(0f, 1f)
                        val badgeFraction = if (hasCrosshair && selectedPriceY >= 0f) (selectedPriceY / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f) else currentFraction
                        val badgeY = badgeFraction * size.height
                        drawLine(Color(0xFFEAECEF), Offset(0f, badgeY), Offset(size.width, badgeY), 1.2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 5f)))
                    }
                }
                val axisHeight = maxHeight
                val density = androidx.compose.ui.platform.LocalDensity.current
                // Axis follows the visible historical candles, not the live ticker.
                // This lets the live-price guide stay pinned to the nearest chart edge
                // while the user browses history outside the current price range.
                val rawMin = chartCandles.minOfOrNull { it.low } ?: (low ?: p.toFloat())
                val rawMax = chartCandles.maxOfOrNull { it.high } ?: (high ?: p.toFloat())
                val plotPadding = ((rawMax - rawMin) * 0.04f).coerceAtLeast(kotlin.math.abs(p.toFloat()) * 0.0001f)
                val basePlotRange = (rawMax - rawMin + plotPadding * 2f).coerceAtLeast(0.000001f)
                val plotMin = rawMin - plotPadding + basePlotRange * verticalPanFraction
                val plotMax = rawMax + plotPadding + basePlotRange * verticalPanFraction
                val plotRange = (plotMax - plotMin).coerceAtLeast(0.000001f)
                Column(Modifier.fillMaxSize().padding(start = 5.dp, end = 3.dp)) {
                    (0..4).forEach { i ->
                        Text(
                            String.format(java.util.Locale.US, axisPriceFormat, plotMax - (plotRange * i / 4f)),
                            color = Muted, fontSize = 9.sp, maxLines = 1, softWrap = false,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.fillMaxWidth().offset(y = axisHeight * i / 4f - 6.dp)
                        )
                    }
                }
                if (p > 0.0) {
                    val currentFraction = ((plotMax - p.toFloat()) / plotRange).coerceIn(0f, 1f)
                    val badgeFraction = if (hasCrosshair && selectedPriceY >= 0f) (selectedPriceY / with(density) { maxHeight.toPx() }.coerceAtLeast(1f)).coerceIn(0f, 1f) else currentFraction
                    val badgePrice = if (hasCrosshair && selectedPriceY >= 0f) plotMax - (plotRange * badgeFraction) else p.toFloat()
                    val badgeChange = ((badgePrice - p.toFloat()) / p.toFloat()) * 100f
                    Column(
                        Modifier.align(Alignment.TopEnd)
                            .offset { IntOffset(0, (maxHeight * badgeFraction - if (hasCrosshair && selectedPriceY >= 0f) 16.dp else 10.dp).roundToPx().coerceIn(0, (maxHeight - if (hasCrosshair && selectedPriceY >= 0f) 38.dp else 20.dp).roundToPx().coerceAtLeast(0))) }
                            // No filled background behind the price badge; keep its text and outline.
                            .border(1.dp, Color.White, RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = if (hasCrosshair && selectedPriceY >= 0f) 2.dp else 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(String.format(java.util.Locale.US, axisPriceFormat, badgePrice), color = Color.White, fontSize = if (hasCrosshair && selectedPriceY >= 0f) 9.sp else 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        if (hasCrosshair && selectedPriceY >= 0f) {
                            Text(String.format(java.util.Locale.US, "%+.2f%%", badgeChange), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            if (selectedCandle != null) {
                val candleChange = selectedCandle.close - selectedCandle.open
                val candleChangePercent = if (selectedCandle.open != 0f) candleChange / selectedCandle.open * 100f else 0f
                val fluctuation = if (selectedCandle.open != 0f) (selectedCandle.high - selectedCandle.low) / selectedCandle.open * 100f else 0f
                val detailColor = if (candleChange >= 0f) Green else Red
                val historyAlignment = if ((selectedCandleIndex ?: 0) >= chartCandles.size / 2) Alignment.TopStart else Alignment.TopEnd
                Column(
                    Modifier.align(historyAlignment).padding(horizontal = 5.dp, vertical = 5.dp)
                        .width(112.dp)
                        .background(Color(0xEE111418), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF596273), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$pair · $interval", color = White, fontSize = 6.5.sp, fontWeight = FontWeight.Bold)
                            Text(candleTime(selectedCandle.openTime), color = Muted, fontSize = 6.5.sp)
                        }
                        Text("×", color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { selectedCandleIndex = null }.padding(start = 5.dp))
                    }
                    HorizontalDivider(color = Color(0xFF424956), thickness = 0.5.dp)
                    @Composable fun detailRow(label: String, value: String, valueColor: Color = White) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = Muted, fontSize = 6.2.sp, maxLines = 1, softWrap = false)
                            Text(value, color = valueColor, fontSize = 6.2.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                        }
                    }
                    detailRow("Open", candlePrice(selectedCandle.open))
                    detailRow("Close", candlePrice(selectedCandle.close))
                    detailRow("High", candlePrice(selectedCandle.high))
                    detailRow("Low", candlePrice(selectedCandle.low))
                    HorizontalDivider(color = Color(0xFF424956), thickness = 0.5.dp)
                    detailRow("% Change", String.format(java.util.Locale.US, "%+.2f%%", candleChangePercent), detailColor)
                    detailRow("Change", String.format(java.util.Locale.US, "%+,.2f", candleChange), detailColor)
                    detailRow("Fluctuation", String.format(java.util.Locale.US, "%.2f%%", fluctuation))
                    detailRow("Volume", compactVolume(selectedCandle.volume))
                    detailRow("Trading vol", compactVolume(selectedCandle.quoteVolume))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Vol: —", color = Muted, fontSize = 12.sp); Text(interval, color = Muted, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(19.dp)) {
            listOf("MA", "EMA", "BOLL", "SAR", "AVL", "SUPER", "VWAP", "VOL").forEach { Text(it, color = if (it == "MA") White else Muted, fontSize = 14.sp) }
        }
        HorizontalDivider(color = Color(0xFF292D35))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Today", "7 Days", "30 Days", "90 Days").forEach { label -> Column { Text(label, color = Muted, fontSize = 12.sp); Text("—", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold) } }
        }
        HorizontalDivider(color = Color(0xFF292D35))
    }
}
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TradeScreen(favorites: MutableList<String>, initialPair: String, chartOnly: Boolean, initialSide: String = "Buy", onOpenOrderEntry: (String) -> Unit, onDetail: (String) -> Unit, onTradeUpdate: () -> Unit) {
    var side by remember(initialPair, chartOnly) { mutableStateOf(initialSide) }
    var orderType by remember { mutableStateOf("Limit") }
    var pair by remember(initialPair) { mutableStateOf(initialPair) }
    var showPairSheet by remember { mutableStateOf(false) }
    var pairSearch by remember { mutableStateOf("") }
    var pairSheetFilter by remember { mutableStateOf("All") }
    var allPairMarkets by remember { mutableStateOf(sharedLiveMarketsState.value.ifEmpty { cachedLiveMarkets ?: emptyList() }) }
    LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { sharedLiveMarketsState.value }.collect { if (it.isNotEmpty()) allPairMarkets = it } }
    var pairLoading by remember { mutableStateOf(false) }
    var pairLoadError by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var limitPrice by remember { mutableStateOf("") }
    var limitPriceAutoFilled by remember { mutableStateOf(false) }
    var totalInput by remember { mutableStateOf("") }
    var percent by remember { mutableFloatStateOf(0f) }
    var chartInterval by remember { mutableStateOf("15m") }
    val chartScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val overviewScrollOffset = chartScrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 1 }?.offset ?: 0
    val showCompactHeaderPrice = overviewScrollOffset < -95 || chartScrollState.firstVisibleItemIndex > 1
    var chartExpanded by remember { mutableStateOf(true) }
    var selectedOrdersTab by remember { mutableStateOf("Open Orders") }
    val base = pair.substringBefore("/")
    val quote = pair.substringAfter("/")
    var marketPrice by remember { mutableStateOf("—") }
    var marketChangePercent by remember { mutableStateOf("0.00") }
    var marketPriceColor by remember { mutableStateOf(White) }
    var bookDecimalsOverride by remember { mutableStateOf<Int?>(null) }
    val liveBookPrice = marketPrice.replace(",", "").toDoubleOrNull() ?: 0.0
    val autoBookDecimals = if (liveBookPrice > 0.0 && liveBookPrice < 1.0) 4 else 3
    val bookDecimals = bookDecimalsOverride ?: autoBookDecimals
    var showDecimalSheet by remember { mutableStateOf(false) }
    var availableUsdt by remember { mutableStateOf("0.00") }
    var availableUsdc by remember { mutableStateOf("0.00") }
    var availableBase by remember { mutableStateOf("0.00") }
    var holdingCostBasis by remember { mutableStateOf<Double?>(null) }
    var holdingTrackedQuantity by remember { mutableStateOf(0.0) }
    var holdingRealizedPnl by remember { mutableStateOf(0.0) }
    var holdingPnlPercent by remember { mutableStateOf<Double?>(null) }
    var holdingBreakeven by remember { mutableStateOf<Double?>(null) }
    var holdingMetricsError by remember { mutableStateOf<String?>(null) }
    var openOrders by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var openOrdersLoading by remember { mutableStateOf(false) }
    var openOrdersError by remember { mutableStateOf<String?>(null) }
    var cancellingOrderId by remember { mutableStateOf<String?>(null) }
    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val livePriceValue = marketPrice.replace(",", "").toDoubleOrNull() ?: 0.0
    val effectivePrice = if (orderType == "Limit") limitPrice.toDoubleOrNull() ?: livePriceValue else livePriceValue
    val estimatedTotal = if (side == "Buy") amountValue * effectivePrice else amountValue * effectivePrice
    val sliderAvailable = if (side == "Buy") availableUsdt.toDoubleOrNull() ?: 0.0
        else availableBase.toDoubleOrNull() ?: 0.0
    fun applyTradePercent(value: Float) {
        percent = value
        val price = effectivePrice
        if (value <= 0f || (side == "Buy" && price <= 0.0)) {
            amount = ""
            totalInput = ""
        } else {
            val selected = sliderAvailable * value
            val quantity = if (side == "Buy") selected / price else selected
            amount = String.format(java.util.Locale.US, "%.8f", quantity).trimEnd('0').trimEnd('.')
            totalInput = String.format(java.util.Locale.US, "%.2f", quantity * price)
        }
    }
    var candles by remember { mutableStateOf(emptyList<Candle>()) }
    var marketError by remember { mutableStateOf<String?>(null) }
    var bids by remember { mutableStateOf(emptyList<BookLevel>()) }
    var asks by remember { mutableStateOf(emptyList<BookLevel>()) }
    var depthError by remember { mutableStateOf<String?>(null) }
    val orderScope = rememberCoroutineScope()
    var orderBusy by remember { mutableStateOf(false) }
    var orderMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pair) {
        // Reset the price when the trading pair changes; the new pair's live
        // ticker will populate the Limit price field as soon as it arrives.
        limitPrice = ""
        limitPriceAutoFilled = false
        marketPrice = "—"
    }
    LaunchedEffect(marketPrice, orderType) {
        if (orderType == "Limit" && !limitPriceAutoFilled) {
            val current = marketPrice.replace(",", "").toDoubleOrNull()
            if (current != null && current > 0.0) {
                limitPrice = java.math.BigDecimal.valueOf(current).stripTrailingZeros().toPlainString()
                limitPriceAutoFilled = true
            }
        }
    }
    LaunchedEffect(selectedOrdersTab, chartOnly) {
        // Chart-only navigation must not start the private open-orders stream.
        if (chartOnly) return@LaunchedEffect
        if (selectedOrdersTab == "Open Orders") {
            if (FirebaseAuth.getInstance().currentUser == null) {
                openOrders = emptyList()
                openOrdersError = "Login to view your open orders."
            } else {
                val client = OkHttpClient()
                var socket: WebSocket? = null
                try {
                    openOrdersLoading = openOrders.isEmpty()
                    val response = apexApiRequest("/api/orders")
                    val rows = response.optJSONArray("orders") ?: JSONArray()
                    openOrders = (0 until rows.length()).mapNotNull { index ->
                        rows.optJSONObject(index)?.takeIf {
                            it.optString("status") in listOf("NEW", "PARTIALLY_FILLED")
                        }
                    }
                    openOrdersError = null
                    openOrdersLoading = false

                    while (true) {
                        try {
                            val stream = apexApiRequest("/api/account/listen-key")
                            val listenKey = stream.getString("listenKey")
                            val connected = kotlinx.coroutines.CompletableDeferred<Unit>()
                            val request = Request.Builder()
                                .url("wss://stream.testnet.binance.vision/ws/$listenKey")
                                .build()
                            socket = client.newWebSocket(request, object : WebSocketListener() {
                                override fun onOpen(webSocket: WebSocket, response: Response) {
                                    connected.complete(Unit)
                                }
                                override fun onMessage(webSocket: WebSocket, text: String) {
                                    try {
                                        val event = JSONObject(text)
                                        if (event.optString("e") != "executionReport") return
                                        val symbol = event.optString("s")
                                        val id = event.optString("i")
                                        val status = event.optString("X")
                                        val executed = event.optString("z", "0")
                                        val quoteExecuted = event.optString("Z", "0")
                                        val eventOrder = JSONObject().apply {
                                            put("symbol", symbol)
                                            put("orderId", id)
                                            put("side", event.optString("S"))
                                            put("type", event.optString("o"))
                                            put("price", event.optString("p"))
                                            put("origQty", event.optString("q"))
                                            put("executedQty", executed)
                                            put("cummulativeQuoteQty", quoteExecuted)
                                            put("status", status)
                                        }
                                        orderScope.launch {
                                            val active = status == "NEW" || status == "PARTIALLY_FILLED"
                                            openOrders = if (active) {
                                                val index = openOrders.indexOfFirst {
                                                    it.optString("symbol") == symbol && it.optString("orderId") == id
                                                }
                                                if (index >= 0) openOrders.toMutableList().also { it[index] = eventOrder }
                                                else listOf(eventOrder) + openOrders
                                            } else {
                                                openOrders.filterNot {
                                                    it.optString("symbol") == symbol && it.optString("orderId") == id
                                                }
                                            }
                                            openOrdersError = null
                                        }
                                    } catch (_: Exception) { }
                                }
                                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                    if (!connected.isCompleted) connected.complete(Unit)
                                }
                                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                    if (!connected.isCompleted) connected.complete(Unit)
                                }
                            })
                            connected.await()
                            awaitCancellation()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            openOrdersError = e.message ?: "Live order stream reconnecting…"
                            delay(1000)
                        } finally {
                            socket?.close(1000, "Open orders screen closed")
                            socket = null
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    openOrdersError = e.message ?: "Could not load open orders."
                    openOrdersLoading = false
                } finally {
                    socket?.close(1000, "Open orders screen closed")
                    client.dispatcher.executorService.shutdown()
                }
            }
        }
    }
    LaunchedEffect(pair, selectedOrdersTab, chartOnly) {
        if (chartOnly) return@LaunchedEffect
        if (selectedOrdersTab == "Holdings" && FirebaseAuth.getInstance().currentUser != null) {
            holdingCostBasis = null
            holdingTrackedQuantity = 0.0
            holdingRealizedPnl = 0.0
            holdingPnlPercent = null
            holdingBreakeven = null
            holdingMetricsError = null
            try {
                val metrics = apexApiRequest("/api/holdings/metrics?symbol=${pair.replace("/", "")}")
                holdingCostBasis = metrics.optDouble("costBasis", 0.0)
                holdingTrackedQuantity = metrics.optDouble("trackedQuantity", 0.0)
                holdingRealizedPnl = metrics.optDouble("realizedPnl", 0.0)
                holdingPnlPercent = metrics.optDouble("pnlPercent", 0.0)
                holdingBreakeven = metrics.optDouble("averageEntryPrice", 0.0).takeIf { it > 0.0 }
            } catch (e: Exception) {
                holdingMetricsError = e.message ?: "Trade history unavailable"
            }
        }
    }
    LaunchedEffect(pair, chartInterval) {
        // One REST snapshot for chart history, then ticker + kline updates arrive over WebSocket.
        try {
            val data = fetchPairMarketData(pair, chartInterval.lowercase())
            marketPrice = data.price
            marketChangePercent = data.changePercent
            candles = data.candles
            marketError = null
        } catch (e: Exception) { marketError = e.message ?: "Market data unavailable" }

        val streamSymbol = pair.replace("/", "").lowercase(java.util.Locale.US)
        val interval = chartInterval.lowercase(java.util.Locale.US)
        val client = OkHttpClient()
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val streams = "$streamSymbol@ticker/$streamSymbol@kline_$interval"
            val request = Request.Builder()
                .url("wss://stream.binance.com:9443/stream?streams=$streams")
                .build()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val envelope = JSONObject(text)
                        val event = envelope.optJSONObject("data") ?: envelope
                        when (event.optString("e")) {
                            "24hrTicker" -> {
                                val nextPrice = event.optString("c")
                                val nextValue = nextPrice.toDoubleOrNull()
                                if (nextValue != null && nextValue > 0.0) {
                                    val previous = marketPrice.replace(",", "").toDoubleOrNull()
                                    marketPriceColor = when {
                                        previous == null || nextValue == previous -> White
                                        nextValue > previous -> Green
                                        else -> Red
                                    }
                                    marketPrice = nextPrice
                                    marketChangePercent = event.optString("P", marketChangePercent)
                                    marketError = null
                                }
                            }
                            "kline" -> {
                                val kline = event.optJSONObject("k") ?: return
                                val candle = Candle(
                                    kline.optString("o").toFloatOrNull() ?: return,
                                    kline.optString("h").toFloatOrNull() ?: return,
                                    kline.optString("l").toFloatOrNull() ?: return,
                                    kline.optString("c").toFloatOrNull() ?: return,
                                    kline.optLong("t", 0L),
                                    kline.optString("v").toFloatOrNull() ?: 0f,
                                    kline.optString("q").toFloatOrNull() ?: 0f
                                )
                                candles = if (candles.isEmpty()) listOf(candle) else candles.dropLast(1) + candle
                                marketError = null
                            }
                        }
                    } catch (_: Exception) { }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed.complete(Unit)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }
            })
            try {
                // Binance sends ticker/kline updates continuously; reconnect if the stream goes silent.
                kotlinx.coroutines.withTimeoutOrNull(70000) { closed.await() }
            } finally {
                socket.cancel()
            }
            try {
                val snapshot = fetchPairMarketData(pair, interval)
                marketPrice = snapshot.price
                marketChangePercent = snapshot.changePercent
                candles = snapshot.candles
                marketError = null
            } catch (e: Exception) {
                marketError = e.message ?: "Market stream reconnecting…"
            }
            delay(1000)
        }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
    LaunchedEffect(base, chartOnly) {
        // Chart view only needs public ticker/kline data; avoid another private account socket.
        if (chartOnly) return@LaunchedEffect
        if (FirebaseAuth.getInstance().currentUser == null) {
            availableUsdt = "0"
            availableUsdc = "0"
            availableBase = "0"
            return@LaunchedEffect
        }
        suspend fun applyAccountBalances(rows: JSONArray) {
            var usdtFree = 0.0
            var usdcFree = 0.0
            var baseFree = 0.0
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                // REST snapshots use asset/free; Binance account-stream events use a/f.
                val asset = row.optString("asset", row.optString("a"))
                val free = row.optString("free", row.optString("f", "0")).toDoubleOrNull() ?: 0.0
                if (asset == "USDT") usdtFree = free
                if (asset == "USDC") usdcFree = free
                if (asset == base) baseFree = free
            }
            availableUsdt = String.format(java.util.Locale.US, "%.8f", usdtFree).trimEnd('0').trimEnd('.').ifBlank { "0" }
            availableUsdc = String.format(java.util.Locale.US, "%.8f", usdcFree).trimEnd('0').trimEnd('.').ifBlank { "0" }
            availableBase = String.format(java.util.Locale.US, "%.8f", baseFree).trimEnd('0').trimEnd('.').ifBlank { "0" }
        }

        // Initial account snapshot, followed by Binance Testnet account WebSocket events.
        try {
            val snapshot = apexApiRequest("/api/account")
            applyAccountBalances(snapshot.optJSONArray("balances") ?: JSONArray())
        } catch (_: Exception) { }

        val client = OkHttpClient()
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            var socket: WebSocket? = null
            try {
                val listenKeyResponse = apexApiRequest("/api/account/listen-key")
                val listenKey = listenKeyResponse.getString("listenKey")
                val connected = kotlinx.coroutines.CompletableDeferred<Unit>()
                val streamClosed = kotlinx.coroutines.CompletableDeferred<Unit>()
                val request = Request.Builder()
                    .url("wss://stream.testnet.binance.vision/ws/$listenKey")
                    .build()
                socket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        connected.complete(Unit)
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val event = JSONObject(text)
                            when (event.optString("e")) {
                                "outboundAccountPosition", "outboundAccountInfo" -> {
                                    val rows = event.optJSONArray("B") ?: event.optJSONArray("balances") ?: JSONArray()
                                    orderScope.launch { applyAccountBalances(rows) }
                                }
                                "executionReport" -> {
                                    orderScope.launch {
                                        try {
                                            val snapshot = apexApiRequest("/api/account")
                                            applyAccountBalances(snapshot.optJSONArray("balances") ?: JSONArray())
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        connected.complete(Unit)
                        streamClosed.complete(Unit)
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        connected.complete(Unit)
                        streamClosed.complete(Unit)
                    }
                })
                connected.await()
                // Keep the account stream alive; rotate listen keys well before expiry.
                kotlinx.coroutines.withTimeoutOrNull(25 * 60 * 1000L) { streamClosed.await() }
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException()
            } catch (_: Exception) {
                delay(1500)
            } finally {
                socket?.close(1000, "Account stream reconnect")
            }
            delay(1000)
        }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    LaunchedEffect(pair, chartOnly) {
        // Chart-only mode renders candles and ticker data; skip the extra 100ms order-book socket.
        if (chartOnly) return@LaunchedEffect
        // Load an initial REST snapshot, then keep the order book/depth ratio live
        // from Binance's 100ms partial-depth WebSocket stream.
        try {
            val initial = fetchDepthAndTrades(pair)
            bids = initial.first
            asks = initial.second
            depthError = null
        } catch (e: Exception) {
            depthError = e.message ?: "Order book unavailable"
        }

        val client = OkHttpClient()
        while (true) {
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val streamSymbol = pair.replace("/", "").lowercase(java.util.Locale.US)
            val request = Request.Builder()
                .url("wss://stream.binance.com:9443/ws/${streamSymbol}@depth20@100ms")
                .build()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val payload = JSONObject(text)
                        fun parseLevels(key: String): List<BookLevel> {
                            val rows = payload.optJSONArray(key) ?: JSONArray()
                            return (0 until rows.length()).mapNotNull { index ->
                                val row = rows.optJSONArray(index) ?: return@mapNotNull null
                                if (row.length() < 2) return@mapNotNull null
                                BookLevel(row.optString(0), row.optString(1))
                            }
                        }
                        val nextBids = parseLevels("bids")
                        val nextAsks = parseLevels("asks")
                        if (nextBids.isNotEmpty() && nextAsks.isNotEmpty()) {
                            orderScope.launch {
                                bids = nextBids
                                asks = nextAsks
                                depthError = null
                            }
                        }
                    } catch (_: Exception) { }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    orderScope.launch {
                        depthError = t.message ?: "Order book reconnecting"
                        closed.complete(Unit)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }
            })
            try {
                closed.await()
            } finally {
                socket.cancel()
            }
            delay(1000)
        }
    }

    LazyColumn(
        state = chartScrollState,
        modifier = Modifier.fillMaxSize().background(if (chartOnly) Color.Black else Bg),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        if (chartOnly) {
            stickyHeader {
                TradingMarketHeader(
                    pair = pair,
                    favorite = base in favorites,
                    onFavorite = { if (base in favorites) favorites.remove(base) else favorites.add(base) },
                    onPairPicker = { pairSearch = ""; showPairSheet = true },
                    compactPrice = marketPrice,
                    compactChange = marketChangePercent,
                    showCompactPrice = showCompactHeaderPrice
                )
            }
            item {
                TradingMarketOverview(pair, marketPrice, marketChangePercent, candles, chartInterval, { chartInterval = it }, base in favorites, { if (base in favorites) favorites.remove(base) else favorites.add(base) }, { pairSearch = ""; showPairSheet = true }, showHeader = false)
            }
        } else {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(pair, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { pairSearch = ""; showPairSheet = true })
                Icon(Icons.Filled.ArrowDropDown, "Select pair", tint = White,
                    modifier = Modifier.size(28.dp).clickable { pairSearch = ""; showPairSheet = true })
                Spacer(Modifier.weight(1f))
                Text("Spot", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    Modifier.weight(1.18f).padding(start = 12.dp, end = 0.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth().height(29.dp).background(Panel, RoundedCornerShape(9.dp))) {
                        listOf("Buy", "Sell").forEach { label ->
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if (side == label) (if (label == "Buy") Green else Red) else Color.Transparent, RoundedCornerShape(7.dp))
                                    .clickable { side = label; applyTradePercent(percent) },
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = if (side == label) Bg else Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().height(29.dp).background(Panel, RoundedCornerShape(7.dp))
                            .clickable { orderType = if (orderType == "Limit") "Market" else "Limit" }
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(orderType, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, null, tint = White)
                    }
                    if (orderType == "Limit") {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).height(38.dp).background(Panel, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Price ($quote)", color = Muted, fontSize = 9.sp, lineHeight = 10.sp)
                                androidx.compose.foundation.text.BasicTextField(
                                    value = limitPrice,
                                    onValueChange = { value ->
                                        val clean = value.filter { ch -> ch.isDigit() || ch == '.' }
                                        limitPrice = clean
                                        limitPriceAutoFilled = true
                                        val p = clean.toDoubleOrNull()
                                        val q = amount.toDoubleOrNull()
                                        if (p != null && q != null) totalInput = String.format(java.util.Locale.US, "%.2f", p * q)
                                    },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(White),
                                    textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(
                                Modifier.width(54.dp).height(38.dp).background(Panel, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(quote, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.fillMaxWidth().height(38.dp).background(Panel, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Amount ($base)", color = Muted, fontSize = 9.sp, lineHeight = 10.sp)
                        androidx.compose.foundation.text.BasicTextField(
                            value = amount,
                            onValueChange = { value ->
                                val clean = value.filter { ch -> ch.isDigit() || ch == '.' }
                                amount = clean
                                percent = 0f
                                val p = effectivePrice
                                val q = clean.toDoubleOrNull()
                                totalInput = if (q != null && p > 0.0) String.format(java.util.Locale.US, "%.2f", q * p) else ""
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(White),
                            textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Slider(
                        value = percent,
                        onValueChange = { applyTradePercent(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                        colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("25%", "50%", "75%", "100%").forEachIndexed { index, label ->
                            Text(label, color = Muted, fontSize = 9.sp, modifier = Modifier.clickable {
                                applyTradePercent((index + 1) / 4f)
                            })
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth().height(48.dp).background(Panel, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL ($quote)", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        androidx.compose.foundation.text.BasicTextField(
                            value = totalInput.ifBlank {
                                if (amountValue > 0.0 && effectivePrice > 0.0) String.format(java.util.Locale.US, "%.2f", estimatedTotal) else ""
                            },
                            onValueChange = { value ->
                                val clean = value.filter { ch -> ch.isDigit() || ch == '.' }
                                totalInput = clean
                                val total = clean.toDoubleOrNull()
                                if (total != null && effectivePrice > 0.0) {
                                    amount = String.format(java.util.Locale.US, "%.8f", total / effectivePrice).trimEnd('0').trimEnd('.')
                                    percent = 0f
                                } else if (clean.isEmpty()) {
                                    amount = ""
                                    percent = 0f
                                }
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(White),
                            textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                            modifier = Modifier.width(125.dp)
                        )
                    }
                    Text("Est fee: ${String.format(java.util.Locale.US, "%.4f", estimatedTotal * 0.001)} $quote", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("AVAL  ${if (side == "Buy") availableUsdt else availableBase} ${if (side == "Buy") quote else base}   ⊕", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val priceValue = if (orderType == "Limit") limitPrice.toDoubleOrNull() else livePriceValue
                            val enteredAmount = amount.toDoubleOrNull()
                            if (FirebaseAuth.getInstance().currentUser == null) orderMessage = "Login first to place a Testnet order."
                            else if (enteredAmount == null || enteredAmount <= 0 || priceValue == null || priceValue <= 0) orderMessage = "Enter a valid amount and price."
                            else {
                                orderBusy = true
                                orderMessage = null
                                orderScope.launch {
                                    try {
                                        val payload = JSONObject().put("symbol", pair.replace("/", "")).put("side", side.uppercase()).put("type", orderType.uppercase()).put("quantity", enteredAmount)
                                        if (orderType == "Limit") payload.put("price", priceValue)
                                        val result = apexApiRequest("/api/order", "POST", payload)
                                        orderMessage = "Testnet order: " + (result.optJSONObject("order")?.optString("status", "accepted") ?: "accepted")
                                        onTradeUpdate()
                                        amount = ""; percent = 0f
                                    } catch (e: Exception) { orderMessage = e.localizedMessage ?: "Order failed." }
                                    finally { orderBusy = false }
                                }
                            }
                        },
                        Modifier.fillMaxWidth().height(42.dp),
                        contentPadding = PaddingValues(vertical = 0.dp, horizontal = 10.dp),
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (side == "Buy") Green else Red, contentColor = Bg),
                        enabled = !orderBusy
                    ) {
                        if (orderBusy) CircularProgressIndicator(Modifier.size(17.dp), color = Bg, strokeWidth = 2.dp)
                        else Text("$side $base", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (orderMessage != null) Text(orderMessage!!, color = if (orderMessage!!.startsWith("Testnet order")) Green else Red, fontSize = 9.sp)
                }

                Column(
                    Modifier.weight(0.82f).height(440.dp).background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text("ORDER BOOK", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Price\n($quote)", color = Muted, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.weight(0.58f))
                        Text("Quantity\n($base)", color = Muted, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.weight(0.42f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                    if (asks.isEmpty()) {
                        repeat(9) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Text("···", color = Red, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.weight(0.58f))
                                Text("···", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(0.42f))
                            }
                        }
                    } else asks.asReversed().take(9).forEach { level ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(String.format(java.util.Locale.US, "%,.${bookDecimals}f", level.price.replace(",", "").toDoubleOrNull() ?: 0.0), color = Red, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 1, modifier = Modifier.weight(0.58f))
                            Text(String.format(java.util.Locale.US, "%,.4f", level.quantity.replace(",", "").toDoubleOrNull() ?: 0.0), color = White, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(0.42f))
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                        Text(if (livePriceValue > 0.0) String.format(java.util.Locale.US, "%,.${bookDecimals}f", livePriceValue) else "—", color = marketPriceColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    if (bids.isEmpty()) {
                        repeat(9) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Text("···", color = Green, fontSize = 10.sp, lineHeight = 17.sp, modifier = Modifier.weight(0.58f))
                                Text("···", color = Muted, fontSize = 10.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(0.42f))
                            }
                        }
                    } else bids.take(9).forEach { level ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(String.format(java.util.Locale.US, "%,.${bookDecimals}f", level.price.replace(",", "").toDoubleOrNull() ?: 0.0), color = Green, fontSize = 10.sp, lineHeight = 17.sp, maxLines = 1, modifier = Modifier.weight(0.58f))
                            Text(String.format(java.util.Locale.US, "%,.4f", level.quantity.replace(",", "").toDoubleOrNull() ?: 0.0), color = White, fontSize = 10.sp, lineHeight = 17.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(0.42f))
                        }
                    }
                    IconButton(onClick = { showDecimalSheet = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Tune, contentDescription = "Decimal precision", tint = Muted, modifier = Modifier.size(15.dp)) }
                    // Live buy/sell depth is calculated from the quote value (price × quantity)
                    // of the visible order-book levels, and refreshes with bids/asks every 3 seconds.
                    val buyDepth = bids.sumOf {
                        (it.price.replace(",", "").toDoubleOrNull() ?: 0.0) *
                            (it.quantity.replace(",", "").toDoubleOrNull() ?: 0.0)
                    }
                    val sellDepth = asks.sumOf {
                        (it.price.replace(",", "").toDoubleOrNull() ?: 0.0) *
                            (it.quantity.replace(",", "").toDoubleOrNull() ?: 0.0)
                    }
                    val totalDepth = buyDepth + sellDepth
                    val buyRatio = if (totalDepth > 0.0) (buyDepth / totalDepth).coerceIn(0.0, 1.0) else 0.5
                    val buyPercent = (buyRatio * 100.0).toInt().coerceIn(0, 100)
                    val sellPercent = 100 - buyPercent
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("B $buyPercent%", color = Color(0xFF0ECB81), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(5.dp))
                        Row(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp))) {
                            if (buyRatio > 0.0) Box(Modifier.weight(buyRatio.toFloat()).fillMaxHeight().background(Color(0xFF0ECB81)))
                            if (buyRatio < 1.0) Box(Modifier.weight((1.0 - buyRatio).toFloat()).fillMaxHeight().background(Color(0xFFEA4778)))
                        }
                        Spacer(Modifier.width(5.dp))
                        Text("$sellPercent% S", color = Color(0xFFEA4778), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 18.dp, top = 4.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Open Orders", "Holdings").forEach { tab ->
                        Column(
                            Modifier.clickable { selectedOrdersTab = tab }.padding(top = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(if (tab == "Open Orders") "Open Orders (${openOrders.size})" else "Holdings", color = if (selectedOrdersTab == tab) White else Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(92.dp).height(3.dp).background(if (selectedOrdersTab == tab) Yellow else Color.Transparent, RoundedCornerShape(2.dp)))
                        }
                    }
                }
                IconButton(onClick = { selectedOrdersTab = "Order History"; onDetail("Order History") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.History, contentDescription = "Order History", tint = White, modifier = Modifier.size(28.dp))
                }
            }
            HorizontalDivider(color = Color(0xFF252A32), thickness = 1.dp)
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (selectedOrdersTab == "Open Orders") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Current pair: $pair", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Refresh",
                            color = Yellow,
                            fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Panel)
                                .clickable {
                                    orderScope.launch {
                                        openOrdersLoading = true
                                        try {
                                            val response = apexApiRequest("/api/orders")
                                            val rows = response.optJSONArray("orders") ?: JSONArray()
                                            openOrders = (0 until rows.length()).mapNotNull { index ->
                                                rows.optJSONObject(index)?.takeIf { it.optString("status") in listOf("NEW", "PARTIALLY_FILLED") }
                                            }
                                            openOrdersError = null
                                        } catch (e: Exception) {
                                            openOrdersError = e.message ?: "Could not load open orders."
                                        }
                                        openOrdersLoading = false
                                    }
                                }.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    when {
                        openOrdersLoading && openOrders.isEmpty() -> {
                            Column(Modifier.fillMaxWidth().height(150.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(color = Yellow, modifier = Modifier.size(25.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.height(10.dp))
                                Text("Loading open orders…", color = Muted, fontSize = 12.sp)
                            }
                        }
                        openOrders.isEmpty() -> {
                            Column(Modifier.fillMaxWidth().height(150.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = Muted, modifier = Modifier.size(38.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(if (openOrdersError != null) openOrdersError!! else "No open orders", color = Muted, fontSize = 13.sp)
                            }
                        }
                        else -> {
                            openOrders.forEach { order ->
                                val orderSymbol = order.optString("symbol", pair.replace("/", ""))
                                val orderSide = order.optString("side", "—")
                                val orderStatus = order.optString("status", "—")
                                val orderTypeLabel = order.optString("type", "—")
                                val orderPrice = order.optString("price", "0")
                                val orderQty = order.optString("origQty", "0")
                                val filledQty = order.optString("executedQty", "0")
                                val quoteFilled = order.optString("cummulativeQuoteQty", "0")
                                val orderId = order.optString("orderId", "")
                                val sideColor = if (orderSide == "BUY") Green else Red
                                val quantityNumber = orderQty.toDoubleOrNull() ?: 0.0
                                val filledNumber = filledQty.toDoubleOrNull() ?: 0.0
                                val fillPercent = if (quantityNumber > 0.0) (filledNumber / quantityNumber * 100.0).coerceIn(0.0, 100.0) else 0.0
                                Column(
                                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp))
                                        .background(Panel).padding(13.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(orderSymbol.chunked(3).joinToString("/"), color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(orderSide, color = sideColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(sideColor.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 4.dp))
                                        Spacer(Modifier.weight(1f))
                                        Text(orderStatus.replace("_", " "), color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(13.dp))
                                    @Composable
                                    fun OrderMetric(label: String, value: String, alignEnd: Boolean = false) {
                                        Column(Modifier.weight(1f), horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
                                            Text(label, color = Muted, fontSize = 10.sp)
                                            Spacer(Modifier.height(3.dp))
                                            Text(value, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        OrderMetric("Order type", orderTypeLabel)
                                        OrderMetric("Limit price", if (orderPrice.toDoubleOrNull() == 0.0) "Market" else "$orderPrice USDT", true)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        OrderMetric("Amount", "$orderQty ${orderSymbol.removeSuffix("USDT")}")
                                        OrderMetric("Filled", "$filledQty (${String.format(java.util.Locale.US, "%.1f", fillPercent)}%)", true)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        OrderMetric("Executed value", "$quoteFilled USDT")
                                        OrderMetric("Order ID", orderId, true)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Fill progress", color = Muted, fontSize = 10.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(Color(0xFF343943))) {
                                            Box(Modifier.fillMaxWidth((fillPercent / 100.0).toFloat()).fillMaxHeight().background(Green))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(String.format(java.util.Locale.US, "%.1f%%", fillPercent), color = White, fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Text(
                                            if (cancellingOrderId == orderId) "Cancelling…" else "Cancel order",
                                            color = Red,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Red.copy(alpha = 0.10f))
                                                .clickable(enabled = cancellingOrderId == null) {
                                                    cancellingOrderId = orderId
                                                    orderScope.launch {
                                                        try {
                                                            val cancelled = apexApiRequest("/api/order/$orderSymbol/$orderId", "DELETE")
                                                            val finalStatus = cancelled.optJSONObject("order")?.optString("status") ?: "CANCELED"
                                                            if (finalStatus !in listOf("NEW", "PARTIALLY_FILLED")) {
                                                                openOrders = openOrders.filterNot {
                                                                    it.optString("symbol") == orderSymbol && it.optString("orderId") == orderId
                                                                }
                                                            } else {
                                                                openOrders = openOrders.map { existing ->
                                                                    if (existing.optString("symbol") == orderSymbol && existing.optString("orderId") == orderId) {
                                                                        existing.put("status", finalStatus)
                                                                        existing
                                                                    } else existing
                                                                }
                                                            }
                                                            openOrdersError = null
                                                        } catch (e: Exception) {
                                                            openOrdersError = e.message ?: "Could not cancel order."
                                                        } finally {
                                                            cancellingOrderId = null
                                                        }
                                                    }
                                                }.padding(horizontal = 13.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val baseQuantity = availableBase.toDoubleOrNull() ?: 0.0
                    val stableUsdt = availableUsdt.toDoubleOrNull() ?: 0.0
                    val stableUsdc = availableUsdc.toDoubleOrNull() ?: 0.0
                    val stableAsset = when {
                        stableUsdt > 0.0 -> "USDT"
                        stableUsdc > 0.0 -> "USDC"
                        quote == "USDC" -> "USDC"
                        else -> "USDT"
                    }
                    val stableQuantity = if (stableAsset == "USDC") stableUsdc else stableUsdt
                    val basePrice = livePriceValue
                    val baseEquity = baseQuantity * basePrice
                    val stableEquity = stableQuantity
                    val baseDisplay = String.format(java.util.Locale.US, "%.8f", baseQuantity).trimEnd('0').trimEnd('.').ifBlank { "0" }
                    val stableDisplay = String.format(java.util.Locale.US, "%.8f", stableQuantity).trimEnd('0').trimEnd('.').ifBlank { "0" }

                    @Composable
                    fun HoldingAssetCard(asset: String, quantity: String, equity: Double, isStable: Boolean) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MarketCoinIcon(asset, 34.dp)
                                Spacer(Modifier.width(9.dp))
                                Text(asset, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(17.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Spot PnL ($stableAsset)", color = Muted, fontSize = 12.sp)
                                Text("Spot PnL%", color = Muted, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val unrealizedNow = (livePriceValue * holdingTrackedQuantity) - (holdingCostBasis ?: 0.0)
                                val pnlValue = if (isStable || holdingCostBasis == null) null else holdingRealizedPnl + unrealizedNow
                                val pnlPct = if (isStable || holdingCostBasis == null || holdingCostBasis!! <= 0.0) null
                                    else ((holdingRealizedPnl + unrealizedNow) / holdingCostBasis!!) * 100.0
                                val pnlColor = if ((pnlValue ?: 0.0) >= 0.0) Color(0xFF0ECB81) else Color(0xFFEA4778)
                                Text(
                                    pnlValue?.let { String.format(java.util.Locale.US, "%+.4f", it) } ?: if (holdingMetricsError != null && !isStable) "Unavailable" else "—",
                                    color = if (pnlValue == null) Muted else pnlColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    pnlPct?.let { String.format(java.util.Locale.US, "%+.2f%%", it) } ?: "—",
                                    color = if (pnlPct == null) Muted else if (pnlPct >= 0.0) Color(0xFF0ECB81) else Color(0xFFEA4778),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(15.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Equity", color = Muted, fontSize = 12.sp)
                                    Text("$quantity  $ " + String.format(java.util.Locale.US, "%.2f", equity), color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Balance", color = Muted, fontSize = 12.sp)
                                    Text(quantity, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (!isStable) {
                                Spacer(Modifier.height(13.dp))
                                Text("Spot breakeven price ($stableAsset)", color = Muted, fontSize = 12.sp)
                                Text(
                                    holdingBreakeven?.let { String.format(java.util.Locale.US, "%.8f", it).trimEnd('0').trimEnd('.') } ?: if (holdingMetricsError != null) "Unavailable" else "—",
                                    color = White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Column(Modifier.fillMaxWidth()) {
                        if (baseQuantity > 0.0 || basePrice > 0.0) {
                            HoldingAssetCard(base, baseDisplay, baseEquity, false)
                            HorizontalDivider(color = Color(0xFF30343B))
                        }
                        if (stableQuantity > 0.0 || (baseQuantity <= 0.0 && basePrice <= 0.0)) {
                            HoldingAssetCard(stableAsset, stableDisplay, stableEquity, true)
                        }
                        if (baseQuantity <= 0.0 && stableQuantity <= 0.0 && basePrice <= 0.0) {
                            Column(Modifier.fillMaxWidth().height(90.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("No holdings yet", color = Muted, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Buy/Sell", "Add funds", "Transfer").forEach { action ->
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(Panel)
                                        .clickable {
                                            when (action) {
                                                "Buy/Sell" -> { side = "Buy"; selectedOrdersTab = "Open Orders" }
                                                "Add funds" -> onDetail("Add Funds")
                                                "Transfer" -> onDetail("Transfer")
                                            }
                                        }.padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(action, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF252A32), thickness = 1.dp)
        }
        }
    }
    if (showPairSheet) {
        LaunchedEffect(showPairSheet) {
            if (showPairSheet) {
                cachedLiveMarkets?.let { if (it.isNotEmpty()) allPairMarkets = it }
                pairLoading = !hasUsableMarketPrices(allPairMarkets)
                pairLoadError = null
                try {
                    val latest = fetchLiveMarkets(forceRefresh = true)
                    if (latest.isNotEmpty()) allPairMarkets = latest
                } catch (e: Exception) {
                    pairLoadError = e.message ?: "Markets unavailable"
                } finally {
                    pairLoading = !hasUsableMarketPrices(allPairMarkets)
                }
                val client = OkHttpClient()
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
                    val request = Request.Builder().url("wss://stream.binance.com:9443/ws/!ticker@arr").build()
                    val socket = client.newWebSocket(request, object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            try {
                                val updated = parseTickerWebSocketMarkets(text)
                                if (updated.isNotEmpty()) {
                                    allPairMarkets = updated
                                    cachedLiveMarkets = updated
                                    sharedLiveMarketsState.value = updated
                                    cachedLiveMarketsAt = System.currentTimeMillis()
                                    pairLoading = false
                                    pairLoadError = null
                                }
                            } catch (_: Exception) { }
                        }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            closed.complete(Unit)
                        }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            closed.complete(Unit)
                        }
                    })
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(25000) { closed.await() }
                    } finally {
                        socket.cancel()
                    }
                    try {
                        val latest = fetchLiveMarkets(forceRefresh = true)
                        if (latest.isNotEmpty()) {
                            allPairMarkets = latest
                            cachedLiveMarkets = latest
                            sharedLiveMarketsState.value = latest
                            cachedLiveMarketsAt = System.currentTimeMillis()
                            pairLoading = false
                        }
                    } catch (e: Exception) {
                        if (allPairMarkets.isEmpty()) pairLoadError = e.message ?: "Market stream reconnecting"
                    }
                    delay(1000)
                }
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showPairSheet = false; pairSearch = ""; pairSheetFilter = "All" },
            modifier = Modifier.fillMaxHeight(0.96f),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Panel,
            contentColor = White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Muted) }
        ) {
            Column(Modifier.fillMaxWidth().fillMaxSize()) {
                Text("All Market Pairs", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                OutlinedTextField(
                    value = pairSearch, onValueChange = { pairSearch = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(50.dp),
                    placeholder = { Text("Search coin or pair", color = Muted, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Yellow, unfocusedBorderColor = Color(0xFF292D35),
                        focusedTextColor = White, unfocusedTextColor = White,
                        unfocusedContainerColor = Color(0xFF111419),
                        focusedContainerColor = Color(0xFF111419)
                    )
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    listOf("All", "Favorite", "Hot", "Gainer", "Loser").forEach { category ->
                        Text(
                            category,
                            color = if (pairSheetFilter == category) Yellow else Muted,
                            fontSize = 14.sp,
                            fontWeight = if (pairSheetFilter == category) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clickable { pairSheetFilter = category }.padding(vertical = 7.dp)
                        )
                    }
                }
                val tradePairResults = allPairMarkets.filter { coin ->
                    (coin.symbol.contains(pairSearch, true) || coin.name.contains(pairSearch, true)) &&
                        when (pairSheetFilter) {
                            "Favorite" -> coin.symbol in favorites
                            "Hot" -> coin.symbol in setOf("BTC", "ETH", "BNB", "SOL", "XRP", "DOGE")
                            "Gainer" -> (coin.change.removeSuffix("%").toDoubleOrNull() ?: 0.0) > 0.0
                            "Loser" -> (coin.change.removeSuffix("%").toDoubleOrNull() ?: 0.0) < 0.0
                            else -> true
                        }
                }.let { coins ->
                    when (pairSheetFilter) {
                        "Gainer" -> coins.sortedByDescending { it.change.removeSuffix("%").toDoubleOrNull() ?: 0.0 }
                        "Loser" -> coins.sortedBy { it.change.removeSuffix("%").toDoubleOrNull() ?: 0.0 }
                        else -> coins
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Name", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("Last Price", color = Muted, fontSize = 11.sp, modifier = Modifier.width(100.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Spacer(Modifier.width(8.dp))
                            Text("24h chg%", color = Muted, fontSize = 11.sp, modifier = Modifier.width(82.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.width(30.dp))
                        }
                    }
                    if (pairLoading && !hasUsableMarketPrices(tradePairResults)) {
                        items(8) { MarketRowSkeleton() }
                    } else {
                        items(tradePairResults) { coin ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier.weight(1f).clickable {
                                    pair = coin.symbol + "/USDT"
                                    amount = ""; limitPrice = ""; totalInput = ""; percent = 0f
                                    showPairSheet = false; pairSearch = ""; pairSheetFilter = "All"
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MarketCoinIcon(coin.symbol, 34.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${coin.symbol}/USDT", color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(coin.name, color = Muted, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            CompactMarketPrice(coin.price, modifier = Modifier.width(100.dp))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
                                MarketChangeBadge(coin.change)
                            }
                            IconButton(
                                onClick = {
                                    if (coin.symbol in favorites) favorites.remove(coin.symbol)
                                    else favorites.add(coin.symbol)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    if (coin.symbol in favorites) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "Toggle favorite",
                                    tint = if (coin.symbol in favorites) Yellow else Muted,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (showDecimalSheet) {
        ModalBottomSheet(onDismissRequest = { showDecimalSheet = false }, containerColor = Panel, contentColor = White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Order Book Decimal Precision", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clickable { bookDecimalsOverride = null; showDecimalSheet = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto ($bookDecimals decimals)", modifier = Modifier.weight(1f), fontSize = 15.sp)
                    if (bookDecimalsOverride == null) Icon(Icons.Filled.Check, null, tint = Yellow)
                }
                HorizontalDivider(color = Color(0xFF30343B))
                (3..4).forEach { digits ->
                    Row(Modifier.fillMaxWidth().clickable { bookDecimalsOverride = digits; showDecimalSheet = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("0." + "0".repeat(digits), modifier = Modifier.weight(1f), fontSize = 15.sp)
                        if (bookDecimalsOverride == digits) Icon(Icons.Filled.Check, null, tint = Yellow)
                    }
                    HorizontalDivider(color = Color(0xFF30343B))
                }
            }
        }
    }

}


@Composable
private fun AssetsScreen(refreshKey: Int, onDetail: (String) -> Unit) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    val scope = rememberCoroutineScope()
    var balances by remember { mutableStateOf(cachedAssetBalances) }
    var busy by remember { mutableStateOf(user != null && cachedAssetBalances.isEmpty()) }
    var message by remember { mutableStateOf(if (cachedAssetBalances.isNotEmpty()) "Binance Spot Testnet · Cached wallet" else "Login to load your Binance Spot Testnet wallet.") }
    var totalUsdt by remember { mutableStateOf(cachedAssetTotalUsdt) }
    var assetMetrics by remember { mutableStateOf<Map<String, org.json.JSONObject>>(emptyMap()) }
    var livePrices by remember { mutableStateOf(sharedLiveMarketsState.value.ifEmpty { cachedLiveMarkets.orEmpty() }) }
    var walletRequestInFlight by remember { mutableStateOf(false) }
    var walletRefreshSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        snapshotFlow { sharedLiveMarketsState.value }.debounce(350).collect { markets: List<Coin> ->
            if (markets.isNotEmpty()) livePrices = markets
        }
    }

    LaunchedEffect(livePrices, balances) {
        val walletAmounts = JSONObject()
        balances.forEach { (asset, amount) -> walletAmounts.put(asset, amount.toDoubleOrNull() ?: 0.0) }
        val value = calculateWalletTotalUsdt(walletAmounts, livePrices)
        if (value.isFinite()) {
            totalUsdt = value
            cachedAssetTotalUsdt = value
        }
    }

    // Wallet prices keep streaming while the Assets tab is open.
    LaunchedEffect(user?.uid) {
        if (user == null) return@LaunchedEffect
        val client = OkHttpClient()
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val request = Request.Builder().url("wss://stream.binance.com:9443/ws/!ticker@arr").build()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val updated = parseTickerWebSocketMarkets(text)
                        if (updated.isNotEmpty()) {
                            cachedLiveMarkets = updated
                            sharedLiveMarketsState.value = updated
                            cachedLiveMarketsAt = System.currentTimeMillis()
                        }
                    } catch (_: Exception) { }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(Unit) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
            })
            try { kotlinx.coroutines.withTimeoutOrNull(25000L) { closed.await() } }
            finally { socket.cancel() }
            try {
                val latest = fetchLiveMarkets(forceRefresh = true)
                if (latest.isNotEmpty()) {
                    livePrices = latest
                    sharedLiveMarketsState.value = latest
                }
            } catch (_: Exception) { }
            delay(1000)
        }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // Binance Testnet account events update wallet quantities immediately after fills.
    LaunchedEffect(user?.uid) {
        if (user == null) return@LaunchedEffect
        val client = OkHttpClient()
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            var socket: WebSocket? = null
            try {
                val keyResponse = apexApiRequest("/api/account/listen-key")
                val listenKey = keyResponse.getString("listenKey")
                val connected = kotlinx.coroutines.CompletableDeferred<Unit>()
                val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
                val request = Request.Builder().url("wss://stream.testnet.binance.vision/ws/$listenKey").build()
                socket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { connected.complete(Unit) }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val event = JSONObject(text)
                            when (event.optString("e")) {
                                "outboundAccountPosition", "outboundAccountInfo", "executionReport" -> {
                                    // Account-stream payloads are partial updates. Fetch a complete signed
                                    // REST snapshot instead of replacing the whole wallet with partial rows.
                                    scope.launch { walletRefreshSignal++ }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        connected.complete(Unit); closed.complete(Unit)
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        connected.complete(Unit); closed.complete(Unit)
                    }
                })
                connected.await()
                kotlinx.coroutines.withTimeoutOrNull(25 * 60 * 1000L) { closed.await() }
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException()
            } catch (_: Exception) {
                delay(1500)
            } finally {
                socket?.close(1000, "Assets account stream reconnect")
            }
            delay(1000)
        }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    fun refreshWallet() {
        if (user == null) { message = "Login to load your APEX wallet."; return }
        if (walletRequestInFlight) return
        walletRequestInFlight = true
        // Keep cached balances visible during every background refresh.
        if (balances.isEmpty()) busy = true
        scope.launch {
            try {
                val response = apexApiRequest("/api/account")
                val (found, walletBalances) = parseBinanceTestnetBalances(response)

                // Render the account balances immediately; market-price refresh must not block the wallet.
                balances = found
                cachedAssetBalances = found
                cachedAssetBalancesAt = System.currentTimeMillis()
                val cachedValue = calculateWalletTotalUsdt(walletBalances, livePrices)
                totalUsdt = cachedValue
                cachedAssetTotalUsdt = cachedValue
                message = "Binance Spot Testnet · Available + Locked"

                // Refresh prices independently, then update the portfolio valuation if successful.
                try {
                    fetchLiveMarkets()
                    val updatedValue = calculateWalletTotalUsdt(walletBalances, livePrices)
                    totalUsdt = updatedValue
                    cachedAssetTotalUsdt = updatedValue
                } catch (_: Exception) {
                    // Keep the balances and cached valuation visible when market data is unavailable.
                }
            } catch (e: Exception) {
                if (balances.isEmpty()) message = e.localizedMessage ?: "Wallet could not be loaded."
            } finally { busy = false; walletRequestInFlight = false }
        }
    }
    LaunchedEffect(walletRefreshSignal) {
        if (walletRefreshSignal > 0) refreshWallet()
    }

    var walletTradeVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(user?.uid, refreshKey) {
        val uid = user?.uid
        if (uid == null) {
            balances = emptyList()
            totalUsdt = 0.0
            onDispose { }
        } else {
            balances = cachedAssetBalances
            totalUsdt = cachedAssetTotalUsdt
            val ordersListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("orders")
                .addSnapshotListener { _, error ->
                    if (error == null) walletTradeVersion++
                }
            scope.launch { refreshWallet() }
            onDispose {
                ordersListener.remove()
            }
        }
    }

    LaunchedEffect(user?.uid, balances.map { it.first }, walletTradeVersion) {
        if (user == null) {
            assetMetrics = emptyMap()
        } else {
            // Fetch only the first 20 non-stable holdings, sequentially. This prevents hundreds
            // of simultaneous API calls from freezing the Assets screen on large testnet accounts.
            val metricAssets = balances.filter { coin ->
                coin.first !in listOf("USDT", "USDC", "FDUSD", "BUSD")
            }.take(8)
            for (coin in metricAssets) {
                val asset = coin.first
                val marketPair = livePrices
                    .firstOrNull { it.symbol.replace("/", "").equals("${asset}USDT", true) }
                    ?.symbol?.replace("/", "") ?: "${asset}USDT"
                try {
                    val metrics = apexApiRequest("/api/holdings/metrics?symbol=$marketPair")
                    assetMetrics = assetMetrics + (asset to metrics)
                } catch (_: Exception) {
                    assetMetrics = assetMetrics + (asset to JSONObject())
                }
            }
        }
    }

    val rankedBalances = balances.sortedWith(
        compareByDescending<Pair<String, String>> { it.first.equals(pinnedAssetSymbol, ignoreCase = true) }
            .thenByDescending { it.first.equals("USDT", ignoreCase = true) }
            .thenByDescending { coin ->
                val amount = coin.second.toDoubleOrNull() ?: 0.0
                val stable = coin.first in listOf("USDT", "USDC", "FDUSD", "BUSD")
                val price = if (stable) 1.0 else livePrices.firstOrNull { it.symbol.equals(coin.first, true) }
                    ?.price?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                amount * price
            }
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item { TopBar("Assets") }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Estimated total value", color = Muted, fontSize = 13.sp)
                    val totalLabel = String.format(java.util.Locale.US, "%,.2f", totalUsdt)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            totalLabel,
                            color = White,
                            fontSize = when {
                                totalLabel.length > 16 -> 18.sp
                                totalLabel.length > 13 -> 21.sp
                                totalLabel.length > 10 -> 24.sp
                                else -> 28.sp
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(1f),
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("USDT", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text("Binance Spot Testnet · Free + Locked", color = Muted, fontSize = 10.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Deposit", Modifier.weight(1f)) { onDetail("Deposit") }
                ActionButton("Withdraw", Modifier.weight(1f)) { onDetail("Withdraw") }
                ActionButton("Transfer", Modifier.weight(1f)) { onDetail("Transfer") }
            }
        }
        item {
            Text(message, color = if (message.startsWith("Binance")) Green else Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            SectionTitle("My Spot Testnet Assets")
        }
        if (busy && balances.isEmpty()) {
            item {
                repeat(6) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF292D35)))
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f).height(13.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF292D35)))
                        Spacer(Modifier.width(18.dp))
                        Box(Modifier.width(88.dp).height(13.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF292D35)))
                    }
                    HorizontalDivider(color = Panel)
                }
            }
        } else if (balances.isEmpty()) {
            item { Text("No credited wallet assets yet.", color = Muted, modifier = Modifier.padding(18.dp)) }
        } else {
            items(rankedBalances, key = { it.first }) { coin ->
                val asset = coin.first
                val quantity = coin.second.toDoubleOrNull() ?: 0.0
                val market = livePrices.firstOrNull { it.symbol.equals(asset, true) }
                val marketPrice = if (asset == "USDT" || asset == "USDC" || asset == "FDUSD" || asset == "BUSD") 1.0
                    else market?.price?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                val equity = quantity * marketPrice
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)
                        .pointerInput(asset) {
                            detectTapGestures(onLongPress = {
                                if (marketPrice > 0.0 && asset !in listOf("USDT", "USDC", "FDUSD", "BUSD")) {
                                    pinnedAssetSymbol = asset
                                    android.widget.Toast.makeText(context, "$asset pinned to the top of Assets", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MarketCoinIcon(asset, 34.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(asset, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(15.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spot PnL (USDT)", color = Muted, fontSize = 12.sp)
                        Text("Spot PnL%", color = Muted, fontSize = 12.sp)
                    }
                    val isStableAsset = asset in listOf("USDT", "USDC", "FDUSD", "BUSD")
                    val metrics = assetMetrics[asset]
                    val hasPnlMetrics = metrics?.has("unrealizedPnl") == true || metrics?.has("realizedPnl") == true
                    val pnl = if (!isStableAsset && hasPnlMetrics) {
                        (marketPrice * metrics!!.optDouble("trackedQuantity", 0.0) - metrics.optDouble("costBasis", 0.0)) + metrics.optDouble("realizedPnl", 0.0)
                    } else null
                    val pnlPercent = if (!isStableAsset && metrics != null && metrics.optDouble("costBasis", 0.0) > 0.0) {
                        val basis = metrics.optDouble("costBasis", 0.0)
                        (((marketPrice * metrics.optDouble("trackedQuantity", 0.0) - basis) + metrics.optDouble("realizedPnl", 0.0)) / basis) * 100.0
                    } else null
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            pnl?.let { String.format(java.util.Locale.US, "%+.4f", it) }
                                ?: if (isStableAsset || metrics != null) "—" else "Loading trade history…",
                            color = when { pnl == null -> Muted; pnl >= 0.0 -> Green; else -> Red },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            pnlPercent?.let { String.format(java.util.Locale.US, "%+.2f%%", it) } ?: "—",
                            color = when { pnlPercent == null -> Muted; pnlPercent >= 0.0 -> Green; else -> Red },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Equity", color = Muted, fontSize = 12.sp)
                            Text(String.format(java.util.Locale.US, "%.8f  $%.2f", quantity, equity), color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Balance", color = Muted, fontSize = 12.sp)
                            Text(coin.second, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (!isStableAsset) {
                        Spacer(Modifier.height(12.dp))
                        Text("Spot breakeven price (USDT)", color = Muted, fontSize = 12.sp)
                        Text(
                            metrics?.optDouble("averageEntryPrice", 0.0)?.takeIf { it > 0.0 }?.let { String.format(java.util.Locale.US, "%.8f", it) } ?: if (metrics == null) "Loading…" else "—",
                            color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(if (pinnedAssetSymbol.equals(asset, true)) "★ Pinned to top" else "Long press to pin this asset to top", color = if (pinnedAssetSymbol.equals(asset, true)) Yellow else Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
                HorizontalDivider(color = Panel)
            }
        }
        item {
            Text("Internal transfers move demo ledger balances between your APEX Spot and Funding wallets only. Binance Spot Testnet remains separate.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(18.dp))
        }
    }
}

@Composable
private fun ProfileScreen(onDetail: (String) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { state -> user = state.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val currentUser = user
    Column {
        TopBar("Profile") { onDetail("Notifications") }
        Card(Modifier.fillMaxWidth().padding(16.dp).clickable { onDetail("Profile Details") }, colors = CardDefaults.cardColors(containerColor = Panel)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, null, tint = Yellow, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(currentUser?.displayName ?: if (currentUser == null) "Welcome to APEX" else "APEX Member", color = White, fontWeight = FontWeight.Bold)
                    Text(currentUser?.email ?: "Login or create your account", color = Muted, fontSize = 12.sp)
                    if (currentUser != null) Text("UID: ${currentUser.uid}", color = Muted, fontSize = 9.sp, maxLines = 1)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Muted)
            }
        }
        if (currentUser == null) {
            Button(
                onClick = { onDetail("Login / Sign up") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg)
            ) { Text("Login / Create Account", fontWeight = FontWeight.Bold) }
        } else {
            OutlinedButton(
                onClick = { FirebaseAuth.getInstance().signOut() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) { Text("Sign Out", color = White) }
        }
        listOf("Identity Verification (KYC)", "Security", "Payment Methods", "Notifications", "Help & Support", "About APEX").forEach { item ->
            Row(Modifier.fillMaxWidth().clickable { onDetail(item) }.padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item, color = White, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null, tint = Muted)
            }
            HorizontalDivider(color = Panel)
        }
    }
}


@Composable
private fun NativeDetail(title: String, onBack: () -> Unit) {
    when (title) {
        "Login / Sign up", "Account Login" -> AuthScreen(onBack)
        "Profile Details" -> ProfileDetailsScreen(onBack)
        "Identity Verification (KYC)" -> KycScreen(onBack)
        "Notifications" -> NotificationsScreen(onBack)
        "Deposit" -> WalletActionScreen("Deposit", onBack)
        "Withdraw" -> WalletActionScreen("Withdraw", onBack)
        "Transfer" -> WalletTransferScreen(onBack)
        "Wallet Transactions" -> WalletTransactionsScreen(onBack)
        "Security" -> SecurityScreen(onBack)
        "Order History", "Open Orders" -> OrderHistoryScreen(title, onBack)
        "Help & Support" -> HelpScreen(onBack)
        else -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
                Text(title, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("This native section will be connected next.", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun WalletActionScreen(title: String, onBack: () -> Unit) {
    val isDeposit = title == "Deposit"
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text(if (isDeposit) "Testnet Demo Deposit" else "Testnet Demo Withdrawal", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(18.dp)) {
                Text("TESTNET SIMULATION ONLY", color = Yellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (isDeposit) "Claim demo balance" else "Simulate a withdrawal",
                    color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isDeposit) "Claim 100 demo USDT once per day. This has no cash value and is not a real deposit."
                    else "The amount is deducted from your internal demo ledger. No crypto is sent to any network.",
                    color = Muted, fontSize = 13.sp
                )
                if (!isDeposit) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (USDT)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = destination, onValueChange = { destination = it },
                        label = { Text("Destination label (demo only)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = if (it.startsWith("Success")) Green else Red, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !busy && (isDeposit || ((amount.toDoubleOrNull() ?: 0.0) > 0.0 && destination.trim().length >= 8)),
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                val payload = JSONObject()
                                if (!isDeposit) {
                                    payload.put("asset", "USDT")
                                    payload.put("amount", amount.toDouble())
                                    payload.put("destination", destination.trim())
                                }
                                val response = apexApiRequest(
                                    if (isDeposit) "/api/wallet/testnet/deposit" else "/api/wallet/testnet/withdraw",
                                    "POST", payload
                                )
                                message = "Success: " + response.optString("message", "Demo transaction recorded.")
                            } catch (e: Exception) {
                                message = e.localizedMessage ?: "Testnet demo transaction failed."
                            } finally { busy = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg)
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Bg, strokeWidth = 2.dp)
                    else Text(if (isDeposit) "Claim 100 Demo USDT" else "Simulate Withdrawal")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("These controls do not generate deposit addresses or send blockchain funds. Real-money custody remains disabled.",
            color = Red, fontSize = 11.sp)
    }
}


@Composable
private fun WalletTransferScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf("spot") }
    var to by remember { mutableStateOf("funding") }
    var asset by remember { mutableStateOf("USDT") }
    var amount by remember { mutableStateOf("") }
    var spotBalance by remember { mutableStateOf(0.0) }
    var fundingBalance by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun loadWallet() {
        val wallet = apexApiRequest("/api/wallet")
        val spot = wallet.optJSONObject("spotBalances") ?: wallet.optJSONObject("balances") ?: JSONObject()
        val funding = wallet.optJSONObject("fundingBalances") ?: JSONObject()
        spotBalance = spot.optDouble(asset, 0.0)
        fundingBalance = funding.optDouble(asset, 0.0)
    }
    LaunchedEffect(asset) {
        try { loadWallet() } catch (e: Exception) { message = e.localizedMessage ?: "Wallet load failed." }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("Internal Transfer", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp)) {
                Text("APEX INTERNAL WALLETS", color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Spot", color = Muted); Text(String.format(java.util.Locale.US, "%.8f", spotBalance), color = White) }
                    Column(horizontalAlignment = Alignment.End) { Text("Funding", color = Muted); Text(String.format(java.util.Locale.US, "%.8f", fundingBalance), color = White) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("From", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("spot" to "Spot", "funding" to "Funding").forEach { option ->
                Button(onClick = { from = option.first; to = if (from == "spot") "funding" else "spot" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (from == option.first) Yellow else Panel,
                        contentColor = if (from == option.first) Bg else White), modifier = Modifier.weight(1f)) { Text(option.second) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.SwapVert, "Transfer direction", tint = Yellow, modifier = Modifier.size(28.dp))
        }
        Text("To: ${if (to == "spot") "Spot" else "Funding"}", color = White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = asset, onValueChange = { asset = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12) },
            label = { Text("Asset (e.g. USDT)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(24) },
            label = { Text("Transfer amount") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Available: ${String.format(java.util.Locale.US, "%.8f", if (from == "spot") spotBalance else fundingBalance)} $asset",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        message?.let { Text(it, color = if (it.startsWith("Success")) Green else Red, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(18.dp))
        Button(enabled = !busy && !loading && asset.matches(Regex("[A-Z0-9]{2,12}")) &&
            (amount.toDoubleOrNull()?.let { it > 0.0 && it <= if (from == "spot") spotBalance else fundingBalance } == true),
            onClick = {
                scope.launch {
                    busy = true; message = null
                    try {
                        val payload = JSONObject().put("asset", asset).put("from", from).put("to", to).put("amount", amount.toDouble())
                        val result = apexApiRequest("/api/wallet/transfer", "POST", payload)
                        message = "Success: " + result.optString("message", "Transfer completed.")
                        amount = ""
                        loadWallet()
                    } catch (e: Exception) { message = e.localizedMessage ?: "Transfer failed." }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg)) {
            if (busy || loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Bg, strokeWidth = 2.dp)
            else Text("Confirm Transfer")
        }
        Spacer(Modifier.height(10.dp))
        Text("Instant internal ledger transfer only. Binance Spot Testnet funds are not moved by this action.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun WalletTransactionsScreen(onBack: () -> Unit) {
    var transactions by remember { mutableStateOf(emptyList<JSONObject>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val user = FirebaseAuth.getInstance().currentUser
    DisposableEffect(user?.uid) {
        val uid = user?.uid
        if (uid == null) {
            loading = false
            error = "Login to view wallet transactions."
            onDispose { }
        } else {
            val listener = FirebaseFirestore.getInstance()
                .collection("wallets").document(uid).collection("transactions")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, failure ->
                    if (failure != null) {
                        error = failure.localizedMessage ?: "Transaction stream unavailable."
                        loading = false
                    } else if (snapshot != null) {
                        transactions = snapshot.documents.map { doc ->
                            val data = doc.data ?: emptyMap()
                            val created = doc.getTimestamp("createdAt")?.toDate()?.toString() ?: "Date unavailable"
                            JSONObject(data).put("id", doc.id).put("createdAt", created)
                        }
                        error = null
                        loading = false
                    }
                }
            onDispose { listener.remove() }
        }
    }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("Wallet Transactions", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        when {
            loading -> CircularProgressIndicator(color = Yellow, modifier = Modifier.padding(24.dp))
            error != null -> Text(error ?: "Could not load transactions.", color = Red, modifier = Modifier.padding(12.dp))
            transactions.isEmpty() -> Text("No wallet transactions yet.", color = Muted, modifier = Modifier.padding(12.dp))
            else -> LazyColumn {
                items(transactions) { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.optString("type", "Transaction").uppercase(), color = White,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(item.optString("status", "PENDING"), color = Yellow, fontSize = 12.sp)
                        }
                        Text("${item.optString("amount", "—")} ${item.optString("asset", "")}", color = White, fontSize = 14.sp)
                        Text(item.optString("createdAt", "Date unavailable"), color = Muted, fontSize = 10.sp)
                    }
                    HorizontalDivider(color = Panel)
                }
            }
        }
    }
}

@Composable
private fun KycScreen(onBack: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    val db = remember { FirebaseFirestore.getInstance() }
    var documentType by remember { mutableStateOf("National ID / CNIC") }
    var legalName by remember { mutableStateOf(user?.displayName.orEmpty()) }
    var status by remember { mutableStateOf("Not submitted") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(user?.uid) {
        if (user != null) db.collection("users").document(user.uid).collection("kyc").document("application").get()
            .addOnSuccessListener { doc ->
                legalName = doc.getString("legalName") ?: legalName
                documentType = doc.getString("documentType") ?: documentType
                status = doc.getString("status") ?: "Not submitted"
            }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("Identity Verification (KYC)", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (user == null) Text("Please login first.", color = Muted, modifier = Modifier.padding(20.dp))
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Current status: $status", color = Yellow, fontWeight = FontWeight.Bold) }
            item { Text("Submit your identity details for review. Do not enter real document details until APEX has a secure document-upload channel.", color = Muted, fontSize = 12.sp) }
            item { OutlinedTextField(value = legalName, onValueChange = { legalName = it }, label = { Text("Full legal name") }, modifier = Modifier.fillMaxWidth()) }
            item {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(documentType, color = White) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("National ID / CNIC", "Passport", "Driving Licence").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { documentType = option; expanded = false })
                        }
                    }
                }
            }
            item {
                Text("Document image upload is not enabled yet because Firebase Storage is not configured. This form does not verify identity.", color = Red, fontSize = 12.sp)
                if (message != null) Text(message!!, color = Green, fontSize = 12.sp)
                Button(onClick = {
                    if (legalName.isBlank()) message = "Please enter your legal name."
                    else {
                        busy = true; message = null
                        db.collection("users").document(user.uid).collection("kyc").document("application").set(
                            mapOf("legalName" to legalName.trim(), "documentType" to documentType, "status" to "Details saved - documents pending", "submittedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                        ).addOnSuccessListener { busy = false; status = "Details saved - documents pending"; message = "Details saved. Identity is NOT verified." }
                            .addOnFailureListener { e -> busy = false; message = e.localizedMessage ?: "Could not save KYC details." }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg)) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Bg) else Text("Save KYC Details")
                }
            }
        }
    }
}

@Composable
private fun NotificationsScreen(onBack: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    var notices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(user?.uid) {
        if (user != null) FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("notifications")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) message = error.localizedMessage
                else notices = snapshot?.documents?.map { doc -> (doc.getString("title") ?: "APEX Update") to (doc.getString("message") ?: "") } ?: emptyList()
            }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("Notifications", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (user == null) Text("Login to view account notifications.", color = Muted, modifier = Modifier.padding(20.dp))
        else if (notices.isEmpty()) Text(message ?: "No notifications yet.", color = Muted, modifier = Modifier.padding(20.dp))
        else LazyColumn { items(notices) { item ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(item.first, color = White, fontWeight = FontWeight.SemiBold)
                Text(item.second, color = Muted, fontSize = 12.sp)
            }
            HorizontalDivider(color = Panel)
        } }
    }
}

@Composable
private fun SecurityScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("Security", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text("Signed in as: ${user?.email ?: "Not signed in"}", color = Muted, fontSize = 13.sp)
        Text("Email verified: ${if (user?.isEmailVerified == true) "Yes" else "No"}", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            if (user?.email.isNullOrBlank()) message = "Login with email first."
            else auth.sendPasswordResetEmail(user!!.email!!).addOnSuccessListener { message = "Password reset email sent." }
                .addOnFailureListener { e -> message = e.localizedMessage ?: "Could not send reset email." }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg)) { Text("Send Password Reset Email") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = {
            user?.sendEmailVerification()?.addOnSuccessListener { message = "Verification email sent." }
                ?.addOnFailureListener { e -> message = e.localizedMessage ?: "Could not send verification." }
                ?: run { message = "Login first." }
        }, modifier = Modifier.fillMaxWidth()) { Text("Verify Email", color = White) }
        if (message != null) Text(message!!, color = Green, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun OrderHistoryScreen(title: String, onBack: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    var rows by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(user?.uid, title) {
        if (user != null) FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("orders")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) message = error.localizedMessage
                else rows = snapshot?.documents?.map { doc ->
                    val d = doc.data ?: emptyMap()
                    "${d["symbol"] ?: "—"} · ${d["side"] ?: "—"} · ${d["status"] ?: "—"} · Qty ${d["executedQty"] ?: "0"}"
                } ?: emptyList()
            }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text(title, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (user == null) Text("Login to view your orders.", color = Muted, modifier = Modifier.padding(20.dp))
        else if (rows.isEmpty()) Text(message ?: "No orders saved yet. Place a Binance Testnet order to see it here.", color = Muted, modifier = Modifier.padding(20.dp))
        else LazyColumn { items(rows) { row ->
            Text(row, color = White, modifier = Modifier.fillMaxWidth().padding(16.dp))
            HorizontalDivider(color = Panel)
        } }
    }
}

@Composable
private fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var subject by remember { mutableStateOf("APEX Exchange Support") }
    var body by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("Help & Support", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("For account, KYC or testnet issues, contact the APEX support team.", color = Muted, fontSize = 13.sp)
        OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Describe your issue") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {                data = android.net.Uri.parse("mailto:")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            try { context.startActivity(intent) } catch (_: Exception) { }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg)) { Text("Open Email App") }
    }
}

@Composable
private fun ProfileDetailsScreen(onBack: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    val db = remember { FirebaseFirestore.getInstance() }
    var fullName by remember { mutableStateOf(user?.displayName.orEmpty()) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user?.uid) {
        if (user == null) {
            loading = false
            message = "Please sign in to manage your profile."
        } else {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    fullName = doc.getString("profileName") ?: fullName
                    phone = doc.getString("profilePhone") ?: ""
                    address = doc.getString("profileAddress") ?: ""
                    country = doc.getString("profileCountry") ?: ""
                    dateOfBirth = doc.getString("profileDob") ?: ""
                    loading = false
                }
                .addOnFailureListener { e ->
                    loading = false
                    message = e.localizedMessage ?: "Could not load profile."
                }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("Profile Details", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (user == null) {
            Text("Login first to save your profile details.", color = Muted, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Account information", color = Yellow, fontWeight = FontWeight.SemiBold)
                    Text("UID: ${user.uid}", color = Muted, fontSize = 11.sp)
                    Text(user.email ?: "", color = Muted, fontSize = 12.sp)
                }
                item {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                item {
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                item {
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
                item {
                    OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                item {
                    OutlinedTextField(value = dateOfBirth, onValueChange = { dateOfBirth = it }, label = { Text("Date of birth (DD-MM-YYYY)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                item {
                    if (message != null) Text(message!!, color = Red, fontSize = 12.sp)
                    Button(
                        onClick = {
                            saving = true
                            message = null
                            val data = hashMapOf(
                                "uid" to user.uid,
                                "email" to (user.email ?: ""),
                                "profileName" to fullName.trim(),
                                "profilePhone" to phone.trim(),
                                "profileAddress" to address.trim(),
                                "profileCountry" to country.trim(),
                                "profileDob" to dateOfBirth.trim(),
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            db.collection("users").document(user.uid).set(data, SetOptions.merge())
                                .addOnSuccessListener { saving = false; message = "Profile saved successfully." }
                                .addOnFailureListener { e -> saving = false; message = e.localizedMessage ?: "Profile save failed." }
                        },
                        enabled = !saving && !loading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg)
                    ) {
                        if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Bg, strokeWidth = 2.dp)
                        else Text("Save Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(onSuccess: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val webClientId = "1067881983186-fcv31ae2ogfmqj5f67ed8qrum18upqtl.apps.googleusercontent.com"
    val googleOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, googleOptions) }
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    busy = false
                    error = "Google ID token missing. Check Firebase OAuth setup."
                } else {
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                        .addOnCompleteListener { task ->
                            busy = false
                            if (task.isSuccessful) onSuccess()
                            else error = task.exception?.localizedMessage ?: "Google sign-in failed."
                        }
                }
            } catch (e: Exception) {
                busy = false
                error = e.localizedMessage ?: "Google sign-in was cancelled or failed."
            }
        } else {
            busy = false
            val status = result.data?.let {
                GoogleSignIn.getSignedInAccountFromIntent(it).exception as? ApiException
            }
            error = when (status?.statusCode) {
                10 -> "Google setup error (DEVELOPER_ERROR). Firebase SHA-1/SHA-256 or OAuth package config is incorrect."
                12500 -> "Google sign-in configuration error. Check Firebase OAuth client and SHA fingerprints."
                12501 -> "Google sign-in was cancelled."
                else -> "Google sign-in failed (resultCode=${result.resultCode}, status=${status?.statusCode ?: "unknown"}). Check Firebase SHA fingerprints."
            }
        }
    }

    var passwordVisible by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSuccess) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("APEX Account", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Text(if (isSignUp) "Create your account" else "Welcome back", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        if (!isSignUp) {
            Spacer(Modifier.height(5.dp))
            Text("Sign in to your APEX account", color = Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it.trim(); error = null },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            placeholder = { Text("Email address or phone number", color = Muted.copy(alpha = 0.72f)) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Yellow, unfocusedBorderColor = Color(0xFF343943),
                focusedTextColor = Muted, unfocusedTextColor = Muted,
                focusedPlaceholderColor = Muted.copy(alpha = 0.72f),
                unfocusedPlaceholderColor = Muted.copy(alpha = 0.72f),
                focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                cursorColor = Yellow
            )
        )
        Spacer(Modifier.height(11.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            placeholder = { Text("Password", color = Muted.copy(alpha = 0.72f)) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password", tint = Muted)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Yellow, unfocusedBorderColor = Color(0xFF343943),
                focusedTextColor = Muted, unfocusedTextColor = Muted,
                focusedPlaceholderColor = Muted.copy(alpha = 0.72f),
                unfocusedPlaceholderColor = Muted.copy(alpha = 0.72f),
                focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                cursorColor = Yellow
            )
        )
        if (isSignUp) {
            Spacer(Modifier.height(11.dp))
            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it; error = null },
                modifier = Modifier.fillMaxWidth().height(58.dp), placeholder = { Text("Confirm password", color = Muted.copy(alpha = 0.72f)) },
                singleLine = true, shape = RoundedCornerShape(13.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Yellow, unfocusedBorderColor = Color(0xFF343943),
                    focusedTextColor = Muted, unfocusedTextColor = Muted,
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel, cursorColor = Yellow
                )
            )
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = Red, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val cleanEmail = email.trim()
                when {
                    cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> error = "Enter a valid email address."
                    password.length < 6 -> error = "Password must be at least 6 characters."
                    isSignUp && password != confirmPassword -> error = "Passwords do not match."
                    else -> {
                        busy = true
                        error = null
                        val task = if (isSignUp) auth.createUserWithEmailAndPassword(cleanEmail, password)
                                   else auth.signInWithEmailAndPassword(cleanEmail, password)
                        task.addOnCompleteListener { result ->
                            busy = false
                            if (result.isSuccessful) onSuccess()
                            else error = result.exception?.localizedMessage ?: "Authentication failed. Try again."
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg)
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Bg, strokeWidth = 2.dp)
            else Text(if (isSignUp) "Create Account" else "Login", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                error = null
                busy = true
                try {
                    googleLauncher.launch(googleClient.signInIntent)
                } catch (e: Exception) {
                    busy = false
                    error = e.localizedMessage ?: "Unable to open Google sign-in."
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF202124)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0)),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(19.dp), color = Color(0xFF4285F4), strokeWidth = 2.dp)
            } else {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.google_g_logo),
                    contentDescription = "Google",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Continue with Google", color = Color(0xFF202124), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (!isSignUp) {
            TextButton(onClick = {
                val cleanEmail = email.trim()
                if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) error = "Enter your email above first."
                else {
                    busy = true
                    auth.sendPasswordResetEmail(cleanEmail).addOnCompleteListener { task ->
                        busy = false
                        error = if (task.isSuccessful) "Password reset email sent." else task.exception?.localizedMessage ?: "Reset email failed."
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Forgot Password?", color = Yellow) }
        }
        TextButton(onClick = { isSignUp = !isSignUp; error = null; confirmPassword = "" }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSignUp) "Already have an account? Login" else "New to APEX? Create account", color = Yellow)
        }
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3139)),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
    ) { Text(label, color = White, fontSize = 11.sp) }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Yellow)
            Spacer(Modifier.width(8.dp))
            Text(label, color = White, fontSize = 12.sp)
        }
    }
}