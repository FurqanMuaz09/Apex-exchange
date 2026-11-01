package com.apex.exchange

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
private val Panel2 = Color(0xFF20242B)
private val Txt = Color(0xFFEAECEF)
private val Muted = Color(0xFF848E9C)
private val Yellow = Color(0xFFF0B90B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                APEXApp()
            }
        }
    }
}

@Composable
fun APEXApp() {
    var selectedTab by remember { mutableStateOf("Home") }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                val tabs = listOf(
                    "Home", "Markets", "Trade", "Assets"
                )
                val icons = listOf(
                    Icons.Default.Home,
                    Icons.Default.ShowChart,
                    Icons.Default.CandlestickChart,
                    Icons.Default.AccountBalanceWallet
                )

                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icons[index], contentDescription = tab) },
                        label = { Text(tab) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Yellow,
                            selectedTextColor = Yellow,
                            indicatorColor = Panel2,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            "Home" -> HomeScreen(Modifier.padding(padding))
            else -> ComingSoonScreen(selectedTab, Modifier.padding(padding))
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf("All") }
    var balanceHidden by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "APEX",
                    color = Yellow,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Search, null, tint = Txt)
                Spacer(Modifier.width(18.dp))
                Icon(Icons.Default.SupportAgent, null, tint = Txt)
                Spacer(Modifier.width(18.dp))
                Icon(Icons.Default.NotificationsNone, null, tint = Txt)
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Panel, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Est. Total Value (USD)", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (balanceHidden) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = "Hide balance",
                        tint = Muted,
                        modifier = Modifier.clickable {
                            balanceHidden = !balanceHidden
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    if (balanceHidden) "******" else "Connect account",
                    color = Txt,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))
                Text("Today's PNL     --", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Add Funds", Icons.Default.Add)
                    ActionButton("Withdraw", Icons.Default.ArrowUpward)
                    ActionButton("Transfer", Icons.Default.SwapHoriz)
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth()
                    .background(Panel2, RoundedCornerShape(14.dp))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Trade Smarter with APEX",
                        color = Txt,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Explore the crypto markets", color = Muted, fontSize = 12.sp)
                }
                Text("APEX", color = Yellow, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text("Markets", color = Txt, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("All", "Favorite", "Hot", "Gainer", "Loser").forEach { option ->
                    Text(
                        option,
                        color = if (filter == option) Yellow else Muted,
                        fontSize = 13.sp,
                        fontWeight = if (filter == option) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { filter = option }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Trading Pair", color = Muted, fontSize = 12.sp)
                Text("Last Price / 24h Change", color = Muted, fontSize = 12.sp)
            }
        }

        items(5) { index ->
            val coins = listOf(
                "BTC/USDT" to "Bitcoin",
                "ETH/USDT" to "Ethereum",
                "BNB/USDT" to "BNB",
                "SOL/USDT" to "Solana",
                "XRP/USDT" to "XRP"
            )
            val coin = coins[index]

            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(coin.first, color = Txt, fontWeight = FontWeight.SemiBold)
                    Text(coin.second, color = Muted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("--", color = Txt)
                    Text("--", color = Muted, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = Panel2)
        }

        item {
            Text(
                "Live market data connection pending.",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        }
    }
}

@Composable
fun RowScope.ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        Modifier.weight(1f)
            .background(Panel2, RoundedCornerShape(10.dp))
            .clickable { }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Yellow)
        Spacer(Modifier.height(6.dp))
        Text(label, color = Txt, fontSize = 11.sp)
    }
}

@Composable
fun ComingSoonScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Txt, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Native screen is being built.", color = Muted)
    }
}