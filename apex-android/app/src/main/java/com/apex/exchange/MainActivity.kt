package com.apex.exchange

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import java.net.URL
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

private val Bg=Color(0xFF0B0E11)
private val Panel=Color(0xFF181A20)
private val Panel2=Color(0xFF20242B)
private val Txt=Color(0xFFEAECEF)
private val Muted=Color(0xFF848E9C)
private val Yellow=Color(0xFFF0B90B)
private val Green=Color(0xFF0ECB81)
private val Red=Color(0xFFF6465D)

private data class Asset(val pair:String,val name:String) {
 val symbol:String get()=pair.replace("/","")
}
private val assets=listOf(Asset("BTC/USDT","Bitcoin"),Asset("ETH/USDT","Ethereum"),Asset("BNB/USDT","BNB"),Asset("SOL/USDT","Solana"),Asset("XRP/USDT","XRP"))
private data class Ticker(val price:Double,val change:Double)
private data class BookLevel(val price:Double,val quantity:Double)
private data class TradeTick(val price:Double,val quantity:Double,val buyerMaker:Boolean)
private object NativeMarket {
 val tickers = mutableStateMapOf<String,Ticker>()
 val bids = mutableStateMapOf<String,List<BookLevel>>()
 val asks = mutableStateMapOf<String,List<BookLevel>>()
 val recent = mutableStateMapOf<String,List<TradeTick>>()
 val candles = mutableStateMapOf<String,List<Float>>()
 var error by mutableStateOf("")
 suspend fun refreshPair(symbol:String) = withContext(Dispatchers.IO) {
  try {
   val depth=JSONObject(URL("https://api.binance.com/api/v3/depth?symbol="+symbol+"&limit=8").readText())
   val bidRows=depth.getJSONArray("bids"); val askRows=depth.getJSONArray("asks")
   val bidList=(0 until bidRows.length()).map{val a=bidRows.getJSONArray(it);BookLevel(a.getString(0).toDouble(),a.getString(1).toDouble())}
   val askList=(0 until askRows.length()).map{val a=askRows.getJSONArray(it);BookLevel(a.getString(0).toDouble(),a.getString(1).toDouble())}
   val klineRows=JSONArray(URL("https://api.binance.com/api/v3/klines?symbol="+symbol+"&interval=1m&limit=60").readText())
   val closes=(0 until klineRows.length()).map{ klineRows.getJSONArray(it).getString(4).toFloat() }
   val tradeRows=JSONArray(URL("https://api.binance.com/api/v3/trades?symbol="+symbol+"&limit=12").readText())
   val tradeList=(0 until tradeRows.length()).map{val t=tradeRows.getJSONObject(it);TradeTick(t.getString("price").toDouble(),t.getString("qty").toDouble(),t.getBoolean("isBuyerMaker"))}
   withContext(Dispatchers.Main){bids[symbol]=bidList;asks[symbol]=askList;recent[symbol]=tradeList;candles[symbol]=closes}
  } catch(_:Exception) { }
 }
 suspend fun refresh() = withContext(Dispatchers.IO) {
  try {
   val symbols=assets.joinToString(prefix="[",postfix="]") { "\"" + it.symbol + "\"" }
   val raw=URL("https://api.binance.com/api/v3/ticker/24hr?symbols="+java.net.URLEncoder.encode(symbols,"UTF-8")).readText()
   val rows=JSONArray(raw)
   val result=mutableMapOf<String,Ticker>()
   for(i in 0 until rows.length()) {
    val row=rows.getJSONObject(i)
    result[row.getString("symbol")]=Ticker(row.getString("lastPrice").toDouble(),row.getString("priceChangePercent").toDouble())
   }
   withContext(Dispatchers.Main) { tickers.putAll(result); error="" }
  } catch(e:Exception) { withContext(Dispatchers.Main) { error="Live market connection unavailable" } }
 }
}
private fun priceText(value:Double):String = if(value>=1) "%,.2f".format(java.util.Locale.US,value) else "%,.6f".format(java.util.Locale.US,value)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=android.graphics.Color.rgb(11,14,17)
  window.navigationBarColor=android.graphics.Color.rgb(11,14,17)
  setContent { APEXNativeApp() }
 }
}

@Composable private fun APEXNativeApp(){
 var tab by remember { mutableStateOf("Home") }
 LaunchedEffect(Unit) { while(true) { NativeMarket.refresh(); delay(10_000) } }
 Scaffold(containerColor=Bg,bottomBar={
  NavigationBar(containerColor=Panel){
   listOf("Home","Markets","Trade","Assets","Profile").forEach { item ->
    val icon=when(item){"Home"->Icons.Default.Home;"Markets"->Icons.Default.ShowChart;"Trade"->Icons.Default.SwapHoriz;"Assets"->Icons.Default.AccountBalanceWallet;else->Icons.Default.Person}
    NavigationBarItem(selected=tab==item,onClick={tab=item},icon={Icon(icon,null)},label={Text(item,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Yellow,selectedTextColor=Yellow,indicatorColor=Panel2,unselectedIconColor=Muted,unselectedTextColor=Muted))
   }
  }
 }){padding->
  when(tab){
   "Home"->HomeScreen(Modifier.padding(padding),{tab="Trade"})
   "Markets"->MarketsScreen(Modifier.padding(padding),{tab="Trade"})
   "Trade"->TradeScreen(Modifier.padding(padding))
   "Assets"->AssetsScreen(Modifier.padding(padding))
   else->ProfileScreen(Modifier.padding(padding))
  }
 }
}

@Composable private fun Header(title:String="APEX",search:Boolean=false){
 Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically){
  Text(title,color=Yellow,fontSize=23.sp,fontWeight=FontWeight.ExtraBold)
  Spacer(Modifier.weight(1f))
  if(search) Icon(Icons.Default.Search,null,tint=Txt)
  Spacer(Modifier.width(18.dp));Icon(Icons.Default.SupportAgent,null,tint=Txt)
  Spacer(Modifier.width(18.dp));Icon(Icons.Default.NotificationsNone,null,tint=Txt)
 }
}
@Composable private fun HomeScreen(modifier:Modifier,onTrade:()->Unit){
 var filter by remember{mutableStateOf("Favorite")}
 var hidden by remember{mutableStateOf(false)}
 LazyColumn(modifier.fillMaxSize().background(Bg)){
  item{Header(search=true)}
  item{
   Column(Modifier.padding(horizontal=16.dp).fillMaxWidth().background(Panel,RoundedCornerShape(14.dp)).padding(16.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Text("Est. Total Value (USD)",color=Muted,fontSize=12.sp);Spacer(Modifier.width(8.dp));Icon(Icons.Default.Visibility,null,tint=Muted,modifier=Modifier.clickable{hidden=!hidden})}
    Row(verticalAlignment=Alignment.CenterVertically){Text(if(hidden)"******" else "Connect account",color=Txt,fontSize=26.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Button(onClick=onTrade,colors=ButtonDefaults.buttonColors(containerColor=Yellow,contentColor=Bg)){Text("Add Funds")}}
    Text("Today's PNL       —",color=Muted,fontSize=12.sp)
   }
  }
  item{Row(Modifier.padding(16.dp).fillMaxWidth().background(Panel2,RoundedCornerShape(12.dp)).padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("APEX EXCHANGE",color=Yellow,fontSize=10.sp);Text("Trade Smarter with APEX",color=Txt,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("Explore the crypto market",color=Muted,fontSize=12.sp)};Text("◆",color=Yellow,fontSize=30.sp)}}
  item{Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Favorite","Hot","Gainer","Loser","New").forEach{AssistChip(onClick={filter=it},label={Text(it)},colors=AssistChipDefaults.assistChipColors(containerColor=if(filter==it)Yellow else Panel,labelColor=if(filter==it)Bg else Muted))}}}
  item{MarketHeader()}
  val visible=when(filter) {
   "Gainer"->assets.filter{(NativeMarket.tickers[it.symbol]?.change ?: 0.0)>0}.sortedByDescending{NativeMarket.tickers[it.symbol]?.change ?: 0.0}
   "Loser"->assets.filter{(NativeMarket.tickers[it.symbol]?.change ?: 0.0)<0}.sortedBy{NativeMarket.tickers[it.symbol]?.change ?: 0.0}
   "Hot"->assets.sortedByDescending{kotlin.math.abs(NativeMarket.tickers[it.symbol]?.change ?: 0.0)}
   "New"->emptyList()
   else->assets
  }
  items(visible){AssetRow(it,onTrade)}
  item{Text("Market prices will appear when the native market data service is connected.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(18.dp))}
 }
}
@Composable private fun MarketHeader(){Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Name",color=Muted,fontSize=11.sp);Text("Last Price        24h chg%",color=Muted,fontSize=11.sp)}}
@Composable private fun AssetRow(asset:Asset,onClick:()->Unit){
 Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
  Column(Modifier.weight(1f)){Text(asset.pair,color=Txt,fontWeight=FontWeight.SemiBold);Text(asset.name,color=Muted,fontSize=11.sp)}
  val ticker=NativeMarket.tickers[asset.symbol]
  Text(ticker?.let{priceText(it.price)} ?: "—",color=Txt,modifier=Modifier.width(90.dp),textAlign=TextAlign.End)
  Text(ticker?.let{(if(it.change>=0) "+" else "")+"%.2f%%".format(java.util.Locale.US,it.change)} ?: "—",color=ticker?.let{if(it.change>=0)Green else Red} ?: Muted,modifier=Modifier.width(75.dp),textAlign=TextAlign.End,fontSize=12.sp)
 }
 HorizontalDivider(color=Panel2)
}
@Composable private fun SearchField(text:String,onText:(String)->Unit,hint:String){
 Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).background(Panel,RoundedCornerShape(10.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
  Icon(Icons.Default.Search,null,tint=Muted);Spacer(Modifier.width(8.dp))
  BasicTextField(text,onText,modifier=Modifier.weight(1f),singleLine=true,textStyle=LocalTextStyle.current.copy(color=Txt),decorationBox={inner->if(text.isEmpty())Text(hint,color=Muted);inner()})
 }
}
@Composable private fun MarketsScreen(modifier:Modifier,onTrade:()->Unit){
 var query by remember{mutableStateOf("")};var filter by remember{mutableStateOf("All")}
 Column(modifier.fillMaxSize().background(Bg)){Header("Markets");SearchField(query,{query=it},"Search coins")
  Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("All","Gainers","Losers").forEach{FilterChip(selected=filter==it,onClick={filter=it},label={Text(it)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Yellow,selectedLabelColor=Bg))}}
  MarketHeader();val filtered=assets.filter{it.pair.contains(query,true)||it.name.contains(query,true)}.filter{when(filter){"Gainers"->(NativeMarket.tickers[it.symbol]?.change ?: 0.0)>0;"Losers"->(NativeMarket.tickers[it.symbol]?.change ?: 0.0)<0;else->true}}
  LazyColumn{items(filtered){AssetRow(it,onTrade)}}
 }
}
@Composable private fun TradeScreen(modifier:Modifier){
 var pair by remember{mutableStateOf("BTC/USDT")}
 LaunchedEffect(pair){while(true){NativeMarket.refreshPair(pair.replace("/",""));delay(3000)}}
 var side by remember{mutableStateOf("Buy")};var order by remember{mutableStateOf("Market")};var amount by remember{mutableStateOf("")};var price by remember{mutableStateOf("")};var orderMessage by remember{mutableStateOf("")}
 Column(modifier.fillMaxSize().background(Bg)){Header("Spot Trading");Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){var expanded by remember{mutableStateOf(false)};Box{Text(pair+"  ▾",color=Txt,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{expanded=true});DropdownMenu(expanded,{expanded=false}){listOf("BTC/USDT","ETH/USDT","BNB/USDT").forEach{DropdownMenuItem(text={Text(it)},onClick={pair=it;expanded=false})}}};Spacer(Modifier.weight(1f));Text(NativeMarket.tickers[pair.replace("/","")]?.let{priceText(it.price)} ?: "—",color=Green,fontWeight=FontWeight.Bold)}
  NativePriceChart(pair)
  Text("1m   5m   15m   1H   4H   1D",color=Muted,fontSize=11.sp,modifier=Modifier.padding(vertical=6.dp))
  Row(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(10.dp)).padding(4.dp)){listOf("Buy","Sell").forEach{Button(onClick={side=it},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=if(side==it){if(it=="Buy")Green else Red}else Panel,contentColor=Bg)){Text(it)}}}
  Spacer(Modifier.height(12.dp));Text("Order type",color=Muted);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Market","Limit","Stop-limit").forEach{FilterChip(selected=order==it,onClick={order=it},label={Text(it)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Yellow,selectedLabelColor=Bg))}}
  if(order!="Market")InputField("Price (USDT)",price,{price=it})
  InputField("Amount (USDT)",amount,{amount=it})
  Text("Estimated total     "+(amount.ifBlank{"0.00"})+" USDT",color=Txt,modifier=Modifier.fillMaxWidth().padding(top=10.dp).background(Panel,RoundedCornerShape(10.dp)).padding(14.dp))
  Button(onClick={val qty=amount.toDoubleOrNull();val p=price.toDoubleOrNull();orderMessage=when{qty==null||qty<=0->"Valid amount enter karein.";order!="Market"&&(p==null||p<=0)->"Valid price enter karein.";else->"Order submit nahi hua: secure Testnet backend aur Firebase login abhi connect nahi hain."}},modifier=Modifier.fillMaxWidth().padding(top=14.dp),colors=ButtonDefaults.buttonColors(containerColor=if(side=="Buy")Green else Red,contentColor=Bg)){Text(side+" "+pair)}
  if(orderMessage.isNotBlank()) Text(orderMessage,color=Yellow,fontSize=12.sp,modifier=Modifier.padding(10.dp))
  Text("Binance Spot Testnet only. Orders require login and a funded Testnet account.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(12.dp))
  Text("Order book",color=Txt,fontSize=18.sp,fontWeight=FontWeight.Bold)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Price (USDT)",color=Muted,fontSize=11.sp);Text("Amount",color=Muted,fontSize=11.sp)}
  NativeMarket.asks[pair.replace("/","")].orEmpty().asReversed().forEach{level->Row(Modifier.fillMaxWidth().padding(vertical=3.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(priceText(level.price),color=Red,fontSize=12.sp);Text("%.5f".format(level.quantity),color=Txt,fontSize=12.sp)}}
  NativeMarket.bids[pair.replace("/","")].orEmpty().forEach{level->Row(Modifier.fillMaxWidth().padding(vertical=3.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(priceText(level.price),color=Green,fontSize=12.sp);Text("%.5f".format(level.quantity),color=Txt,fontSize=12.sp)}}
  Text("Recent trades",color=Txt,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Price",color=Muted,fontSize=11.sp);Text("Amount",color=Muted,fontSize=11.sp)}
  NativeMarket.recent[pair.replace("/","")].orEmpty().forEach{trade->Row(Modifier.fillMaxWidth().padding(vertical=3.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(priceText(trade.price),color=if(trade.buyerMaker)Red else Green,fontSize=12.sp);Text("%.5f".format(trade.quantity),color=Txt,fontSize=12.sp)}}
  Text("Orders",color=Txt,fontSize=18.sp,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(18.dp)){Text("Holdings",color=Muted);Text("Open Orders",color=Yellow);Text("History",color=Muted)};Text("Log in to view Testnet orders.",color=Muted,modifier=Modifier.padding(18.dp))
 }}
}
@Composable private fun InputField(label:String,value:String,onValue:(String)->Unit){
 Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
 BasicTextField(value,onValue,modifier=Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(9.dp)).padding(14.dp),singleLine=true,textStyle=LocalTextStyle.current.copy(color=Txt),decorationBox={inner->if(value.isEmpty())Text("Enter "+label,color=Muted);inner()})
}
@Composable private fun AssetsScreen(modifier:Modifier){
 Column(modifier.fillMaxSize().background(Bg)){Header("Assets");Column(Modifier.padding(16.dp).fillMaxWidth().background(Panel,RoundedCornerShape(14.dp)).padding(18.dp)){Text("Total estimated value",color=Muted);Text("Log in to view",color=Txt,fontSize=26.sp,fontWeight=FontWeight.Bold);Text("Binance Spot Testnet account balances.",color=Muted,fontSize=12.sp);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("↓ Deposit","↑ Withdraw","⇄ Transfer").forEach{Text(it,color=Yellow,modifier=Modifier.clickable{}.padding(10.dp))}}}
  Text("My assets",color=Txt,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(16.dp));Text("Log in to view Testnet balances.",color=Muted,modifier=Modifier.padding(16.dp));Text("Testnet only. This is a shared backend Testnet account.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(16.dp))
 }
}
@Composable private fun ProfileScreen(modifier:Modifier){
 Column(modifier.fillMaxSize().background(Bg)){Header("Profile");Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AccountCircle,null,tint=Yellow,modifier=Modifier.size(52.dp));Column(Modifier.padding(start=14.dp)){Text("Welcome to APEX",color=Txt,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Sign in to manage your account",color=Muted,fontSize=12.sp)}}
  Button(onClick={},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),colors=ButtonDefaults.buttonColors(containerColor=Yellow,contentColor=Bg)){Text("Log in / Sign up")}
  listOf("Identity verification","Security","Payment methods","Notifications","Help & Support","About APEX").forEach{Row(Modifier.fillMaxWidth().clickable{}.padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(it,color=Txt);Text("›",color=Muted)};HorizontalDivider(color=Panel2)}
  Text("Authentication and KYC need native Firebase integration.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(16.dp))
 }
}

@Composable private fun NativePriceChart(pair:String) { val points=NativeMarket.candles[pair.replace("/","")].orEmpty(); Column(Modifier.fillMaxWidth().padding(vertical=12.dp).background(Panel,RoundedCornerShape(12.dp)).padding(10.dp)) { Text(pair+" · 1 minute",color=Muted,fontSize=11.sp); if(points.size<2) Box(Modifier.fillMaxWidth().height(170.dp),contentAlignment=Alignment.Center){Text("Loading chart data…",color=Muted)} else Canvas(Modifier.fillMaxWidth().height(170.dp)){ val low=points.minOrNull()?:0f;val high=points.maxOrNull()?:1f;val range=(high-low).takeIf{it>0f}?:1f;val path=Path();points.forEachIndexed{index,value->val x=size.width*index/(points.size-1).toFloat();val y=size.height-(value-low)/range*size.height;if(index==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,color=Yellow,style=Stroke(width=2.5f));for(i in 1..3)drawLine(Panel2,androidx.compose.ui.geometry.Offset(0f,size.height*i/4),androidx.compose.ui.geometry.Offset(size.width,size.height*i/4),1f) } } }
