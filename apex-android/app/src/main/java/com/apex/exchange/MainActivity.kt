package com.apex.exchange

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF0B0E11)
private val SurfaceDark = Color(0xFF181A20)
private val Yellow = Color(0xFFF0B90B)
private val Green = Color(0xFF0ECB81)
private val Red = Color(0xFFF6465D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ApexHome()
            }
        }
    }
}

@Composable
fun ApexHome() {
    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = { Icon(Icons.Default.ShowChart, null) },
                    label = { Text("Markets") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = { Icon(Icons.Default.CandlestickChart, null) },
                    label = { Text("Trade") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                    label = { Text("Assets") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APEX", color = Yellow, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Row {
                        Icon(Icons.Default.Notifications, null, tint = Color.White)
                        Spacer(Modifier.width(18.dp))
                        Icon(Icons.Default.Person, null, tint = Color.White)
                    }
                }
            }

            item {
                Text("Total Balance", color = Color.LightGray, fontSize = 14.sp)
                Text("$0.00", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeAction("Deposit", Icons.Default.ArrowDownward)
                    HomeAction("Withdraw", Icons.Default.ArrowUpward)
                    HomeAction("Transfer", Icons.Default.SwapHoriz)
                }
                Spacer(Modifier.height(28.dp))
            }

            item {
                Text("Markets", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }

            item { MarketRow("BTC / USDT", "Bitcoin", "$0.00", "+0.00%", true) }
            item { MarketRow("ETH / USDT", "Ethereum", "$0.00", "+0.00%", true) }
            item { MarketRow("BNB / USDT", "BNB", "$0.00", "-0.00%", false) }
            item { MarketRow("SOL / USDT", "Solana", "$0.00", "+0.00%", true) }
            item { MarketRow("XRP / USDT", "XRP", "$0.00", "-0.00%", false) }

            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Native APEX interface • Testnet connection pending",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
fun HomeAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Yellow)
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun MarketRow(
    pair: String,
    name: String,
    price: String,
    change: String,
    positive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(pair, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(name, color = Color.Gray, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(price, color = Color.White)
            Text(change, color = if (positive) Green else Red, fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = SurfaceDark)
}
