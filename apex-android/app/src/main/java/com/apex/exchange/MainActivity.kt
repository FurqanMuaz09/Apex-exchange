package com.apex.exchange

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder
import java.net.URL
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val APEX_API_BASE_URL = BuildConfig.APEX_API_BASE_URL

private suspend fun apexApiRequest(path: String, method: String = "GET", body: JSONObject? = null): JSONObject =
    withContext(Dispatchers.IO) {
        require(APEX_API_BASE_URL.isNotBlank()) { "APEX backend URL is not configured. Hosting is required first." }
        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Please login first.")
        val token = user.getIdToken(false).await().token ?: throw IllegalStateException("Could not get login token.")
        val connection = (URL(APEX_API_BASE_URL.trimEnd('/') + path).openConnection() as java.net.HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (code !in 200..299) throw IllegalStateException(result.optString("error", "API request failed ($code)."))
            result
        } finally { connection.disconnect() }
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
    var tab by remember { mutableStateOf("Home") }
    var detail by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = detail != null) { detail = null }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg, surface = Panel, primary = Yellow,
            onBackground = White, onSurface = White
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = White) {
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
                            onClick = { tab = route; detail = null },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
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
        ) { padding ->
            Column(
                Modifier.fillMaxSize().background(Bg).padding(padding)
            ) {
                if (detail != null) {
                    NativeDetail(detail!!) { detail = null }
                } else {
                    when (tab) {
                        "Home" -> HomeScreen(
                            onMarkets = { tab = "Markets" },
                            onTrade = { tab = "Trade" },
                            onDetail = { detail = it }
                        )
                        "Markets" -> MarketsScreen { detail = it }
                        "Trade" -> TradeScreen { detail = it }
                        "Futures" -> NativeDetail("Futures Trading · Coming Soon") { detail = null }
                        "Assets" -> AssetsScreen { detail = it }
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

@Composable
private fun HomeScreen(onMarkets: () -> Unit, onTrade: () -> Unit, onDetail: (String) -> Unit) {
    var filter by remember { mutableStateOf("Favorite") }
    var search by remember { mutableStateOf("") }
    val visibleMarkets = sampleMarkets.filter {
        (it.symbol.contains(search, true) || it.name.contains(search, true)) &&
        (filter == "All" || filter == "Favorite" || filter == "Hot" ||
         (filter == "Gainer" && it.up) || (filter == "Loser" && !it.up))
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(30.dp).background(Yellow, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("A", color = Bg, fontWeight = FontWeight.Black, fontSize = 19.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text("APEX", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.weight(1f).height(50.dp),
                    placeholder = { Text("Search BTC/USDT", fontSize = 11.sp, color = Muted) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Yellow, unfocusedBorderColor = Panel,
                        focusedTextColor = White, unfocusedTextColor = White
                    )
                )
                IconButton(onClick = { onDetail("Help & Support") }) {
                    Icon(Icons.Filled.HeadsetMic, "Help and Support", tint = White)
                }
                IconButton(onClick = { onDetail("Notifications") }) {
                    Icon(Icons.Filled.NotificationsNone, "Notifications", tint = White)
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(15.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Est. Total Value (USD)", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onDetail("Account Login") }, contentPadding = PaddingValues(0.dp)) {
                            Text("◉ Hide", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Connect account", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(
                            onClick = { onDetail("Add Funds") },
                            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Bg),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                        ) { Text("Add Funds", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF292D35))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's PNL", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("—", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF20242B)),
                shape = RoundedCornerShape(13.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("APEX EXCHANGE", color = Yellow, fontSize = 10.sp, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(7.dp))
                        Text("Trade Smarter with APEX", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Explore the crypto market", color = Muted, fontSize = 11.sp)
                    }
                    Text("◆", color = Yellow, fontSize = 37.sp)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("Favorite", "Hot", "Gainer", "Loser", "All").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF383019),
                            selectedLabelColor = Yellow,
                            labelColor = Muted
                        )
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("Name", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("Last Price", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.width(28.dp))
                Text("24h chg%", color = Muted, fontSize = 11.sp)
            }
        }
        items(visibleMarkets) { MarketRow(it) { onDetail("Trade ${it.symbol}") } }
        item {
            TextButton(onClick = onMarkets, modifier = Modifier.fillMaxWidth()) {
                Text("View More ↓", color = Yellow, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Spot Trading", Icons.Filled.SwapHoriz, Modifier.weight(1f)) { onTrade() }
                QuickTile("My Profile", Icons.Filled.Person, Modifier.weight(1f)) { onDetail("Profile") }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
private data class Coin(val symbol: String, val name: String, val price: String, val change: String, val up: Boolean)

private data class Candle(val open: Float, val high: Float, val low: Float, val close: Float)

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
    val depth = org.json.JSONObject(request("/api/v3/depth?symbol=$pair&limit=5"))
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
private data class PairMarketData(val price: String, val candles: List<Candle>)

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
    val ticker = org.json.JSONObject(getJson("/api/v3/ticker/price?symbol=$pair"))
    val klines = JSONArray(getJson("/api/v3/klines?symbol=$pair&interval=$interval&limit=48"))
    val candles = (0 until klines.length()).mapNotNull { index ->
        val row = klines.getJSONArray(index)
        val open = row.getString(1).toFloatOrNull() ?: return@mapNotNull null
        val high = row.getString(2).toFloatOrNull() ?: return@mapNotNull null
        val low = row.getString(3).toFloatOrNull() ?: return@mapNotNull null
        val close = row.getString(4).toFloatOrNull() ?: return@mapNotNull null
        Candle(open, high, low, close)
    }
    PairMarketData(ticker.getString("price"), candles)
}

private suspend fun fetchLiveMarkets(): List<Coin> = withContext(Dispatchers.IO) {
    val symbols = listOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "DOGEUSDT", "ADAUSDT")
    val encoded = URLEncoder.encode(JSONArray(symbols).toString(), "UTF-8")
    val connection = (URL("https://api.binance.com/api/v3/ticker/24hr?symbols=$encoded").openConnection() as java.net.HttpURLConnection)
    try {
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        if (connection.responseCode !in 200..299) throw IllegalStateException("Market API HTTP ${connection.responseCode}")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val rows = JSONArray(body)
        val names = mapOf(
            "BTC" to "Bitcoin", "ETH" to "Ethereum", "BNB" to "BNB",
            "SOL" to "Solana", "XRP" to "XRP", "DOGE" to "Dogecoin", "ADA" to "Cardano"
        )
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            val symbol = row.getString("symbol").removeSuffix("USDT")
            val price = row.getString("lastPrice").toDoubleOrNull() ?: return@mapNotNull null
            val change = row.getString("priceChangePercent").toDoubleOrNull() ?: 0.0
            Coin(
                symbol = symbol,
                name = names[symbol] ?: symbol,
                price = String.format(java.util.Locale.US, "%,.6f", price).trimEnd('0').trimEnd('.'),
                change = String.format(java.util.Locale.US, "%+.2f%%", change),
                up = change >= 0
            )
        }
    } finally {
        connection.disconnect()
    }
}
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
private fun MarketsScreen(onCoin: (String) -> Unit) {
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var liveMarkets by remember { mutableStateOf(sampleMarkets) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val favorites = remember { mutableStateListOf("BTC", "ETH") }
    val hotCoins = setOf("BTC", "ETH", "BNB", "SOL")

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val latest = fetchLiveMarkets()
                if (latest.isNotEmpty()) {
                    liveMarkets = latest
                    error = null
                }
            } catch (e: Exception) {
                error = e.message ?: "Market data unavailable"
            } finally {
                loading = false
            }
            delay(10000)
        }
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
            Text("Last price", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.width(24.dp))
            Text("24h", color = Muted, fontSize = 11.sp)
        }
        HorizontalDivider(color = Panel)
        if (loading && liveMarkets.all { it.price == "—" }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Yellow)
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SearchOff, null, tint = Muted, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
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
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(coin.symbol, color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("/USDT", color = Muted, fontSize = 10.sp)
                            }
                            Text(coin.name, color = Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(coin.price, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("USDT", color = Muted, fontSize = 9.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(coin.change, color = if (coin.up) Green else Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
private fun MarketRow(coin: Coin, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("${coin.symbol}/USDT", color = White, fontWeight = FontWeight.SemiBold)
            Text(coin.name, color = Muted, fontSize = 11.sp)
        }
        Text(coin.price, color = White, fontSize = 13.sp)
        Spacer(Modifier.width(24.dp))
        Text(coin.change, color = if (coin.up) Green else Red, fontSize = 12.sp)
    }
    HorizontalDivider(color = Panel, thickness = 1.dp)
}

@Composable
private fun TradeScreen(onDetail: (String) -> Unit) {
    var side by remember { mutableStateOf("Buy") }
    var orderType by remember { mutableStateOf("Limit") }
    var pair by remember { mutableStateOf("BTC/USDT") }
    var pairMenu by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var limitPrice by remember { mutableStateOf("") }
    var percent by remember { mutableFloatStateOf(0f) }
    var chartInterval by remember { mutableStateOf("15m") }
    var chartExpanded by remember { mutableStateOf(true) }
    var selectedOrdersTab by remember { mutableStateOf("Open Orders") }
    val base = pair.substringBefore("/")
    val quote = pair.substringAfter("/")
    var marketPrice by remember { mutableStateOf("—") }
    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val livePriceValue = marketPrice.replace(",", "").toDoubleOrNull() ?: 0.0
    val effectivePrice = if (orderType == "Limit") limitPrice.toDoubleOrNull() ?: livePriceValue else livePriceValue
    val estimatedTotal = if (side == "Buy") amountValue * effectivePrice else amountValue
    var candles by remember { mutableStateOf(emptyList<Candle>()) }
    var marketError by remember { mutableStateOf<String?>(null) }
    var bids by remember { mutableStateOf(emptyList<BookLevel>()) }
    var asks by remember { mutableStateOf(emptyList<BookLevel>()) }
    var depthError by remember { mutableStateOf<String?>(null) }
    val orderScope = rememberCoroutineScope()
    var orderBusy by remember { mutableStateOf(false) }
    var orderMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pair, chartInterval) {
        while (true) {
            try {
                val data = fetchPairMarketData(pair, chartInterval.lowercase())
                marketPrice = data.price
                candles = data.candles
                marketError = null
            } catch (e: Exception) { marketError = e.message ?: "Market data unavailable" }
            delay(5000)
        }
    }
    LaunchedEffect(pair) {
        while (true) {
            try {
                val data = fetchDepthAndTrades(pair)
                bids = data.first
                asks = data.second
                depthError = null
            } catch (e: Exception) { depthError = e.message ?: "Order book unavailable" }
            delay(3000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(32.dp).background(Yellow, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("A", color = Bg, fontSize = 21.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("APEX", color = White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Exchange", color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDetail("Notifications") }) {
                    Icon(Icons.Filled.NotificationsNone, "Notifications", tint = White)
                }
                IconButton(onClick = { onDetail("Profile") }) {
                    Icon(Icons.Filled.AccountCircle, "Profile", tint = White)
                }
            }
            HorizontalDivider(color = Color(0xFF252A32))
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { pairMenu = true }.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pair, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.ArrowDropDown, null, tint = White)
                    }
                    Text("▲  +1.20%     " + marketPrice, color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = pairMenu, onDismissRequest = { pairMenu = false }) {
                    listOf("BTC/USDT", "ETH/USDT", "BNB/USDT", "SOL/USDT").forEach { option ->
                        DropdownMenuItem(text = { Text(option, color = White) }, onClick = {
                            pair = option; pairMenu = false; amount = ""; percent = 0f
                        })
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.CandlestickChart, null, tint = White, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(15.dp))
                Icon(Icons.Filled.MoreHoriz, null, tint = White)
            }
            HorizontalDivider(color = Color(0xFF252A32))
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    Modifier.weight(1.18f).padding(start = 8.dp, end = 5.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth().height(38.dp).background(Panel, RoundedCornerShape(9.dp)).padding(3.dp)) {
                        listOf("Buy", "Sell").forEach { label ->
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if (side == label) (if (label == "Buy") Green else Red) else Color.Transparent, RoundedCornerShape(7.dp))
                                    .clickable { side = label },
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = if (side == label) Bg else Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(
                        Modifier.fillMaxWidth().height(34.dp).background(Panel, RoundedCornerShape(7.dp))
                            .clickable { orderType = if (orderType == "Limit") "Market" else "Limit" }
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(orderType, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, null, tint = White)
                    }
                    if (orderType == "Limit") {
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(
                            value = limitPrice,
                            onValueChange = { limitPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            modifier = Modifier.fillMaxWidth().height(62.dp),
                            label = { Text("Price ($quote)", fontSize = 10.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Yellow, unfocusedBorderColor = Panel, focusedTextColor = White, unfocusedTextColor = White)
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' }; percent = 0f },
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        label = { Text("Amount ($base)", fontSize = 10.sp) },
                        placeholder = { Text("Enter amount", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Yellow, unfocusedBorderColor = Panel, focusedTextColor = White, unfocusedTextColor = White)
                    )
                    Slider(
                        value = percent,
                        onValueChange = {
                            percent = it
                            amount = if (it == 0f) "" else String.format(java.util.Locale.US, "%.4f", it * 0.01)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.height(30.dp),
                        colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("25%", "50%", "75%", "100%").forEachIndexed { index, label ->
                            Text(label, color = Muted, fontSize = 9.sp, modifier = Modifier.clickable {
                                percent = (index + 1) / 4f
                            })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(8.dp)) {
                        Text("Total ($quote)", color = Muted, fontSize = 10.sp)
                        Text(if (amountValue > 0 && effectivePrice > 0) String.format(java.util.Locale.US, "%.2f", estimatedTotal) else "0.00", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Available: — $quote", color = Muted, fontSize = 10.sp)
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
                                        amount = ""; percent = 0f
                                    } catch (e: Exception) { orderMessage = e.localizedMessage ?: "Order failed." }
                                    finally { orderBusy = false }
                                }
                            }
                        },
                        Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (side == "Buy") Green else Red, contentColor = Bg),
                        enabled = !orderBusy
                    ) {
                        if (orderBusy) CircularProgressIndicator(Modifier.size(17.dp), color = Bg, strokeWidth = 2.dp)
                        else Text("$side $base", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (orderMessage != null) Text(orderMessage!!, color = if (orderMessage!!.startsWith("Testnet order")) Green else Red, fontSize = 9.sp)
                }

                Column(
                    Modifier.weight(0.92f).fillMaxHeight().background(Panel).padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text("Order Book", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Price", color = Muted, fontSize = 9.sp)
                        Text("Amount", color = Muted, fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    asks.asReversed().take(5).forEach { level ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(level.price, color = Red, fontSize = 10.sp, maxLines = 1)
                            Text(level.quantity, color = White, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF30343B))
                    Spacer(Modifier.height(7.dp))
                    Text(marketPrice, color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("≈ $quote", color = Muted, fontSize = 9.sp)
                    Spacer(Modifier.height(7.dp))
                    bids.take(5).forEach { level ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(level.price, color = Green, fontSize = 10.sp, maxLines = 1)
                            Text(level.quantity, color = White, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    if (depthError != null) Text("Reconnecting…", color = Muted, fontSize = 9.sp)
                }
            }
            HorizontalDivider(color = Color(0xFF252A32))
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Open Orders", "Holdings", "Order History").forEach { tab ->
                    Column(
                        Modifier.clickable { selectedOrdersTab = tab; onDetail(tab) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (tab == "Open Orders") "Open Orders (0)" else tab, color = if (selectedOrdersTab == tab) White else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width(62.dp).height(2.dp).background(if (selectedOrdersTab == tab) Yellow else Color.Transparent, RoundedCornerShape(2.dp)))
                    }
                }
                Icon(Icons.Filled.Menu, null, tint = Muted, modifier = Modifier.clickable { onDetail("Order History") })
            }
            HorizontalDivider(color = Color(0xFF252A32))
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { chartExpanded = !chartExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$pair Chart", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Icon(if (chartExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null, tint = White)
                    Spacer(Modifier.weight(1f))
                    if (chartExpanded) {
                        listOf("1m", "5m", "15m", "1h", "1D").forEach { interval ->
                            Text(
                                interval,
                                color = if (chartInterval == interval) Bg else Muted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp).background(if (chartInterval == interval) Yellow else Color.Transparent, RoundedCornerShape(10.dp)).clickable { chartInterval = interval }.padding(horizontal = 5.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (chartExpanded) {
                    Box(
                        Modifier.fillMaxWidth().height(145.dp).padding(top = 8.dp).background(Panel, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (candles.size > 1) {
                            Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                                val minPrice = candles.minOf { it.low }
                                val maxPrice = candles.maxOf { it.high }
                                val range = (maxPrice - minPrice).coerceAtLeast(0.000001f)
                                val slot = size.width / candles.size
                                candles.forEachIndexed { index, candle ->
                                    val x = slot * index + slot / 2f
                                    val yHigh = size.height - ((candle.high - minPrice) / range) * size.height
                                    val yLow = size.height - ((candle.low - minPrice) / range) * size.height
                                    val yOpen = size.height - ((candle.open - minPrice) / range) * size.height
                                    val yClose = size.height - ((candle.close - minPrice) / range) * size.height
                                    val color = if (candle.close >= candle.open) Green else Red
                                    drawLine(color, Offset(x, yHigh), Offset(x, yLow), strokeWidth = 1.4f)
                                    drawLine(color, Offset(x, yOpen), Offset(x, yClose), strokeWidth = (slot * 0.58f).coerceAtLeast(2f))
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Yellow, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(marketError ?: "Loading chart…", color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
                    Text("Live Binance market data", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}


@Composable
private fun AssetsScreen(onDetail: (String) -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    val scope = rememberCoroutineScope()
    var balances by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Login to load your Binance Spot Testnet wallet.") }
    var totalUsdt by remember { mutableStateOf(0.0) }

    fun refreshWallet() {
        if (user == null) { message = "Login to load your Testnet wallet."; return }
        busy = true
        scope.launch {
            try {
                val response = apexApiRequest("/api/account")
                val list = response.optJSONArray("balances") ?: JSONArray()
                val found = mutableListOf<Pair<String, String>>()
                var usdt = 0.0
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val asset = item.optString("asset")
                    val free = item.optString("free", "0").toDoubleOrNull() ?: 0.0
                    val locked = item.optString("locked", "0").toDoubleOrNull() ?: 0.0
                    if (free + locked > 0) found.add(asset to String.format(java.util.Locale.US, "%.8f", free + locked))
                    if (asset == "USDT") usdt = free + locked
                }
                balances = found
                totalUsdt = usdt
                message = "Binance Spot Testnet · Live account data"
            } catch (e: Exception) { message = e.localizedMessage ?: "Wallet could not be loaded." }
            finally { busy = false }
        }
    }
    LaunchedEffect(user?.uid) { refreshWallet() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Assets")
        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(18.dp)) {
                Text("Estimated total value", color = Muted, fontSize = 13.sp)
                Text(String.format(java.util.Locale.US, "%.2f USDT", totalUsdt), color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("USDT balance only · other assets not converted", color = Muted, fontSize = 10.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Refresh", Modifier.weight(1f)) { refreshWallet() }
            ActionButton("Deposit", Modifier.weight(1f)) { onDetail("Deposit") }
            ActionButton("Withdraw", Modifier.weight(1f)) { onDetail("Withdraw") }
        }
        Text(message, color = if (message.startsWith("Binance")) Green else Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        if (busy) CircularProgressIndicator(color = Yellow, modifier = Modifier.padding(18.dp))
        SectionTitle("My Testnet Assets")
        if (balances.isEmpty() && !busy) Text("No funded assets returned.", color = Muted, modifier = Modifier.padding(18.dp))
        balances.forEach { coin ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(coin.first, color = White, modifier = Modifier.weight(1f))
                Text(coin.second, color = White)
            }
            HorizontalDivider(color = Panel)
        }
        Text("Testnet funds have no real monetary value. Deposits and withdrawals are not enabled here.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(18.dp))
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
    if (title == "Login / Sign up" || title == "Account Login") {
        AuthScreen(onBack)
    } else if (title == "Profile Details") {
        ProfileDetailsScreen(onBack)
    } else if (title == "Identity Verification (KYC)") {
        KycScreen(onBack)
    } else if (title == "Notifications") {
        NotificationsScreen(onBack)
    } else if (title == "Security") {
        SecurityScreen(onBack)
    } else if (title == "Order History" || title == "Open Orders") {
        OrderHistoryScreen(title, onBack)
    } else if (title == "Help & Support") {
        HelpScreen(onBack)
    } else {
        Column(Modifier.fillMaxSize()) {
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
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
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
        Text("Secure sign in with Firebase Authentication", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Panel)
            Text("  or use email  ", color = Muted, fontSize = 11.sp)
            HorizontalDivider(Modifier.weight(1f), color = Panel)
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it.trim(); error = null },
            modifier = Modifier.fillMaxWidth(), label = { Text("Email address") },
            singleLine = true, colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Yellow, unfocusedBorderColor = Panel,
                focusedTextColor = White, unfocusedTextColor = White, cursorColor = Yellow
            )
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(), label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password", tint = Muted)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Yellow, unfocusedBorderColor = Panel,
                focusedTextColor = White, unfocusedTextColor = White, cursorColor = Yellow
            )
        )
        if (isSignUp) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it; error = null },
                modifier = Modifier.fillMaxWidth(), label = { Text("Confirm password") },
                singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Yellow, unfocusedBorderColor = Panel,
                    focusedTextColor = White, unfocusedTextColor = White, cursorColor = Yellow
                )
            )
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = Red, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.height(14.dp))
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
                Canvas(Modifier.size(22.dp)) {
                    val sw = 4.dp.toPx()
                    val inset = sw / 2f
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                    val origin = Offset(inset, inset)
                    drawArc(Color(0xFF4285F4), -45f, 90f, false, origin, arcSize, style = Stroke(sw))
                    drawArc(Color(0xFF34A853), 45f, 90f, false, origin, arcSize, style = Stroke(sw))
                    drawArc(Color(0xFFFBBC05), 135f, 90f, false, origin, arcSize, style = Stroke(sw))
                    drawArc(Color(0xFFEA4335), 225f, 90f, false, origin, arcSize, style = Stroke(sw))
                    drawLine(Color(0xFF4285F4), Offset(size.width * 0.52f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), sw)
                }
                Spacer(Modifier.width(12.dp))
                Text("Continue with Google", color = Color(0xFF202124), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(12.dp))
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
