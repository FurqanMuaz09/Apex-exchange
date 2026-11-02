package com.apex.exchange

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    LazyColumn {
        item {
            TopBar("APEX Exchange") { onDetail("Notifications") }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Estimated Balance", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("0.00 USDT", color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        Text("≈ $0.00", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionButton("Add Funds", Modifier.weight(1f)) { onDetail("Add Funds") }
                            ActionButton("Trade", Modifier.weight(1f)) { onTrade() }
                            ActionButton("Assets", Modifier.weight(1f)) { onDetail("Assets") }
                        }
                    }
                }
            }
        }
        item { SectionTitle("Markets", "View all", onMarkets) }
        items(sampleMarkets.take(5)) { MarketRow(it) { onDetail("Trade ${it.symbol}") } }
        item {
            SectionTitle("Explore APEX")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Spot Trading", Icons.Filled.SwapHoriz, Modifier.weight(1f)) { onTrade() }
                QuickTile("My Profile", Icons.Filled.Person, Modifier.weight(1f)) { onDetail("Profile") }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private data class Coin(val symbol: String, val name: String, val price: String, val change: String, val up: Boolean)
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
    Column {
        TopBar("Markets")
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search coins", color = Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Yellow, unfocusedBorderColor = Panel,
                focusedTextColor = White, unfocusedTextColor = White
            )
        )
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Favorite", "Hot", "Gainer", "Loser").forEach {
                FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text("Pair", color = Muted, modifier = Modifier.weight(1f))
            Text("Last price", color = Muted)
            Spacer(Modifier.width(30.dp))
            Text("24h", color = Muted)
        }
        LazyColumn {
            items(sampleMarkets.filter { it.symbol.contains(query, true) || it.name.contains(query, true) }) {
                MarketRow(it) { onCoin(it.symbol) }
            }
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
    var amount by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Spot Trading") { onDetail("Trading pairs") }
        Row(Modifier.fillMaxWidth().clickable { onDetail("Trading pairs") }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pair, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.ArrowDropDown, null, tint = Yellow)
            Spacer(Modifier.weight(1f))
            Text("Price: —", color = Muted)
        }
        Card(Modifier.fillMaxWidth().padding(14.dp).height(170.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ShowChart, null, tint = Yellow, modifier = Modifier.size(32.dp))
                    Text("Market chart", color = White, fontWeight = FontWeight.SemiBold)
                    Text("Live chart connection pending", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Buy", "Sell").forEach { label ->
                Button(
                    onClick = { side = label }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (side == label) (if (label == "Buy") Green else Red) else Panel)
                ) { Text(label, color = White) }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Market", "Limit", "Stop-limit").forEach {
                FilterChip(selected = orderType == it, onClick = { orderType = it }, label = { Text(it) })
            }
        }
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text("Amount (USDT)") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Yellow, focusedTextColor = White, unfocusedTextColor = White)
        )
        Text("Available balance: —", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(18.dp))
        Button(
            onClick = { onDetail("Testnet connection required") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (side == "Buy") Green else Red)
        ) { Text("$side ${pair.substringBefore('/')}") }
        Text("Order book and recent trades", color = White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(18.dp))
        Text("Connect secure Testnet data to load live depth and trades.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
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
    Column {
        TopBar("Profile") { onDetail("Notifications") }
        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, null, tint = Yellow, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Welcome to APEX", color = White, fontWeight = FontWeight.Bold)
                    Text("Login or create your account", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null, tint = Muted)
            }
        }
        listOf("Login / Sign up", "Identity Verification (KYC)", "Security", "Payment Methods", "Notifications", "Help & Support", "About APEX").forEach { item ->
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = White) }
            Text(title, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Native screen foundation is ready. Secure service integration is next.", color = Muted, fontSize = 13.sp)
            }
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
