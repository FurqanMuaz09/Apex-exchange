package com.apex.exchange

import android.os.Bundle
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
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
                        Triple("Assets", Icons.Filled.AccountBalanceWallet, "Assets"),
                        Triple("Profile", Icons.Filled.Person, "Profile")
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
    var orderType by remember { mutableStateOf("Market") }
    var pair by remember { mutableStateOf("BTC/USDT") }
    var pairMenu by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var limitPrice by remember { mutableStateOf("") }
    var percent by remember { mutableFloatStateOf(0f) }
    var chartInterval by remember { mutableStateOf("1H") }
    val base = pair.substringBefore("/")
    val quote = pair.substringAfter("/")
    val amountValue = amount.toDoubleOrNull() ?: 0.0
    var marketPrice by remember { mutableStateOf("—") }
    var candles by remember { mutableStateOf(emptyList<Candle>()) }
    var marketError by remember { mutableStateOf<String?>(null) }
    var bids by remember { mutableStateOf(emptyList<BookLevel>()) }
    var asks by remember { mutableStateOf(emptyList<BookLevel>()) }
    var recentTrades by remember { mutableStateOf(emptyList<RecentTrade>()) }
    var depthError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pair, chartInterval) {
        while (true) {
            try {
                val data = fetchPairMarketData(pair, chartInterval.lowercase())
                marketPrice = data.price
                candles = data.candles
                marketError = null
            } catch (e: Exception) {
                marketError = e.message ?: "Unable to load market data"
            }
            delay(5000)
        }
    }
    LaunchedEffect(pair) {
        while (true) {
            try {
                val data = fetchDepthAndTrades(pair)
                bids = data.first
                asks = data.second
                recentTrades = data.third
                depthError = null
            } catch (e: Exception) {
                depthError = e.message ?: "Order book unavailable"
            }
            delay(3000)
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopBar("Spot Trading") { onDetail("Notifications") }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { pairMenu = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pair, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.ArrowDropDown, null, tint = Yellow)
                    }
                    Text("Spot · Binance Testnet", color = Muted, fontSize = 10.sp)
                }
                DropdownMenu(expanded = pairMenu, onDismissRequest = { pairMenu = false }) {
                    listOf("BTC/USDT", "ETH/USDT", "BNB/USDT", "SOL/USDT").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = White) },
                            onClick = { pair = option; pairMenu = false; amount = ""; percent = 0f }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("LIVE PRICE", color = Muted, fontSize = 9.sp)
                    Text(marketPrice, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Price chart", color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onDetail("Professional Chart · $pair") }, contentPadding = PaddingValues(0.dp)) {
                            Text("Expand ↗", color = Yellow, fontSize = 11.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("15m", "1H", "4H", "1D", "1W").forEach { interval ->
                            FilterChip(
                                selected = chartInterval == interval,
                                onClick = { chartInterval = interval },
                                label = { Text(interval, fontSize = 9.sp) }
                            )
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(165.dp).padding(top = 8.dp)
                            .background(Bg, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (candles.size > 1) {
                            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
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
                                    drawLine(color, Offset(x, yHigh), Offset(x, yLow), strokeWidth = 1.5f)
                                    drawLine(color, Offset(x, yOpen), Offset(x, yClose), strokeWidth = (slot * 0.58f).coerceAtLeast(2f))
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Yellow, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text(marketError ?: "Loading live candles…", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Live Binance public data · 5 sec refresh", color = Muted, fontSize = 9.sp)
                        Text(marketError?.let { "Reconnecting" } ?: "${candles.size} candles", color = if (marketError == null) Green else Red, fontSize = 9.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Buy", "Sell").forEach { label ->
                    Button(
                        onClick = { side = label },
                        modifier = Modifier.weight(1f).height(43.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (side == label) (if (label == "Buy") Green else Red) else Panel,
                            contentColor = if (side == label) Bg else Muted
                        )
                    ) { Text(label, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Market", "Limit").forEach { type ->
                    FilterChip(
                        selected = orderType == type,
                        onClick = { orderType = type },
                        label = { Text(type, fontSize = 11.sp) }
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("Advanced", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
                if (orderType == "Limit") {
                    OutlinedTextField(
                        value = limitPrice, onValueChange = { limitPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Limit price ($quote)") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Yellow, unfocusedBorderColor = Panel,
                            focusedTextColor = White, unfocusedTextColor = White
                        )
                    )
                    Spacer(Modifier.height(9.dp))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter { ch -> ch.isDigit() || ch == '.' }
                        percent = 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount ($quote)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    trailingIcon = { Text(quote, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(end = 12.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Yellow, unfocusedBorderColor = Panel,
                        focusedTextColor = White, unfocusedTextColor = White
                    )
                )
                Spacer(Modifier.height(7.dp))
                Slider(
                    value = percent, onValueChange = {
                        percent = it
                        amount = if (it == 0f) "" else String.format(java.util.Locale.US, "%.2f", it * 100.0)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = Yellow, activeTrackColor = Yellow)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("0%", "25%", "50%", "75%", "100%").forEach { Text(it, color = Muted, fontSize = 9.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Available balance", color = Muted, fontSize = 11.sp)
                    Text("— $quote", color = White, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimated total", color = Muted, fontSize = 11.sp)
                    Text(if (amountValue > 0) String.format(java.util.Locale.US, "%.2f %s", amountValue, quote) else "— $quote", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onDetail("Login required · Testnet order") },
                    modifier = Modifier.fillMaxWidth().height(47.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (side == "Buy") Green else Red, contentColor = Bg)
                ) { Text("$side $base", fontWeight = FontWeight.Bold) }
                Text("Orders are restricted to authenticated Binance Spot Testnet accounts.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(14.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Order Book", color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text("$base / $quote", color = Muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Price ($quote)", color = Muted, fontSize = 10.sp)
                        Text("Amount ($base)", color = Muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    asks.asReversed().forEach { level ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(level.price, color = Red, fontSize = 11.sp)
                            Text(level.quantity, color = White, fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF292D35))
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Market price", color = Muted, fontSize = 10.sp)
                        Text(marketPrice, color = Yellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    bids.forEach { level ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(level.price, color = Green, fontSize = 11.sp)
                            Text(level.quantity, color = White, fontSize = 11.sp)
                        }
                    }
                    if (depthError != null) Text("Order book reconnecting…", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 5.dp))
                    HorizontalDivider(color = Color(0xFF292D35))
                    Spacer(Modifier.height(8.dp))
                    Text("Recent Trades", color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Price", color = Muted, fontSize = 10.sp)
                        Text("Amount", color = Muted, fontSize = 10.sp)
                        Text("Time", color = Muted, fontSize = 10.sp)
                    }
                    recentTrades.forEach { trade ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(trade.price, color = if (trade.buyerIsMaker) Red else Green, fontSize = 10.sp)
                            Text(trade.quantity, color = White, fontSize = 10.sp)
                            Text(trade.time, color = Muted, fontSize = 10.sp)
                        }
                    }
                    if (recentTrades.isEmpty()) Text("Loading recent trades…", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 10.dp))
                    Text("Public market feed · refreshes every 3 seconds", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            SectionTitle("Orders", "View history") { onDetail("Order History") }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile("Open Orders", Icons.Filled.List, Modifier.weight(1f)) { onDetail("Open Orders") }
                QuickTile("Order History", Icons.Filled.History, Modifier.weight(1f)) { onDetail("Order History") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AssetsScreen(onDetail: (String) -> Unit) {
    Column {
        TopBar("Assets")
        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(18.dp)) {
                Text("Estimated total value", color = Muted, fontSize = 13.sp)
                Text("0.00 USDT", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("≈ $0.00", color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Deposit", Modifier.weight(1f)) { onDetail("Deposit") }
            ActionButton("Withdraw", Modifier.weight(1f)) { onDetail("Withdraw") }
            ActionButton("Transfer", Modifier.weight(1f)) { onDetail("Transfer") }
        }
        SectionTitle("My Assets")
        sampleMarkets.forEach { coin ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(coin.symbol, color = White, modifier = Modifier.weight(1f))
                Text("0.00", color = White)
            }
            HorizontalDivider(color = Panel)
        }
    }
}

@Composable
private fun ProfileScreen(onDetail: (String) -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    Column {
        TopBar("Profile") { onDetail("Notifications") }
        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, null, tint = Yellow, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(user?.displayName ?: if (user == null) "Welcome to APEX" else "APEX Member", color = White, fontWeight = FontWeight.Bold)
                    Text(user?.email ?: "Login or create your account", color = Muted, fontSize = 12.sp)
                    if (user != null) Text("UID: ${user.uid}", color = Muted, fontSize = 9.sp, maxLines = 1)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Muted)
            }
        }
        if (user == null) {
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
private fun AuthScreen(onSuccess: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSuccess) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text("APEX Account", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Text(if (isSignUp) "Create your account" else "Welcome back", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Secure sign in with Firebase Authentication", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
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
            singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
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
        TextButton(onClick = { isSignUp = !isSignUp; error = null; confirmPassword = "" }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSignUp) "Already have an account? Login" else "New to APEX? Create account", color = Yellow)
        }
        Spacer(Modifier.height(10.dp))
        Text("Email/password sign-in. Google sign-in will be added in the next auth step.", color = Muted, fontSize = 10.sp)
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
