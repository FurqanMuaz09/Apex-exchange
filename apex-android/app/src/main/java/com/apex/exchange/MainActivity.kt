package com.apex.exchange

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bg=Color(0xFF0B0E11); private val panel=Color(0xFF181A20); private val panel2=Color(0xFF20242B)
private val ink=Color(0xFFEAECEF); private val muted=Color(0xFF848E9C); private val yellow=Color(0xFFF0B90B)
private val green=Color(0xFF0ECB81); private val red=Color(0xFFF6465D)
private data class Coin(val symbol:String,val name:String,val price:String,val change:String,val up:Boolean=true)
private val coins=listOf(Coin("BTCUSDT","Bitcoin","67,420.50","+2.41%"),Coin("ETHUSDT","Ethereum","3,520.18","+1.82%"),Coin("BNBUSDT","BNB","612.40","-0.64%",false),Coin("SOLUSDT","Solana","174.22","+4.16%"),Coin("XRPUSDT","XRP","0.6241","-1.20%",false),Coin("ADAUSDT","Cardano","0.4512","+0.72%"),Coin("DOGEUSDT","Dogecoin","0.1642","+3.05%"),Coin("TRXUSDT","TRON","0.1234","+0.31%"),Coin("TONUSDT","Toncoin","6.82","-0.42%",false),Coin("LTCUSDT","Litecoin","84.60","+0.18%"),Coin("DOTUSDT","Polkadot","7.12","+1.12%"),Coin("LINKUSDT","Chainlink","15.80","-0.83%",false),Coin("BCHUSDT","Bitcoin Cash","442.10","+0.51%"),Coin("AVAXUSDT","Avalanche","36.90","+2.02%"),Coin("SHIBUSDT","Shiba Inu","0.0000241","+1.90%"),Coin("UNIUSDT","Uniswap","10.20","-0.34%",false),Coin("XLMUSDT","Stellar","0.1120","+0.44%"),Coin("ATOMUSDT","Cosmos","8.42","+1.03%"),Coin("NEARUSDT","NEAR Protocol","5.91","+2.20%"),Coin("APTUSDT","Aptos","9.12","-0.61%",false),Coin("SUIUSDT","Sui","1.82","+5.14%"),Coin("FILUSDT","Filecoin","5.60","+0.28%"),Coin("ETCUSDT","Ethereum Classic","25.10","-0.74%",false),Coin("USDT","Tether","1.00","+0.01%"))

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(11,14,17);window.navigationBarColor=android.graphics.Color.rgb(11,14,17);setContent{MaterialTheme(colorScheme=darkColorScheme(background=bg,surface=panel,primary=yellow,onBackground=ink,onSurface=ink)){ApexApp()}}}}

@Composable private fun ApexApp(){
 var page by remember{mutableStateOf("Home")}; var filter by remember{mutableStateOf("All")}; var query by remember{mutableStateOf("")}
 var pair by remember{mutableStateOf("BTCUSDT")}; var side by remember{mutableStateOf("BUY")}; var type by remember{mutableStateOf("Market")}
 var amount by remember{mutableStateOf("")}; var price by remember{mutableStateOf("67420.50")}; var hidden by remember{mutableStateOf(false)}
 var favorites by remember{mutableStateOf(setOf("BTCUSDT","ETHUSDT"))}; var search by remember{mutableStateOf(false)}
 var notices by remember{mutableStateOf(false)}; var auth by remember{mutableStateOf(false)}; var signup by remember{mutableStateOf(false)}
 var pairs by remember{mutableStateOf(false)}; var chart by remember{mutableStateOf(false)}; var message by remember{mutableStateOf("")}
 Scaffold(containerColor=bg,bottomBar={NavigationBar(containerColor=panel){listOf("Home","Markets","Trade","Assets","Profile").forEach{p->NavigationBarItem(selected=page==p,onClick={page=p},icon={Icon(when(p){"Home"->Icons.Default.Home;"Markets"->Icons.Default.ShowChart;"Trade"->Icons.Default.CandlestickChart;"Assets"->Icons.Default.AccountBalanceWallet;else->Icons.Default.Person},null)},label={Text(p,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=yellow,selectedTextColor=yellow,indicatorColor=panel2,unselectedIconColor=muted,unselectedTextColor=muted))}}}){pad->
 Column(Modifier.fillMaxSize().background(bg).padding(pad)){
 Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Text("APEX",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=yellow);Spacer(Modifier.weight(1f));IconButton({search=true}){Icon(Icons.Default.Search,null,tint=ink)};IconButton({message="Support & Help"}){Icon(Icons.Default.SupportAgent,null,tint=ink)};IconButton({notices=true}){Icon(Icons.Default.NotificationsNone,null,tint=ink)}}
 when(page){
 "Home"->HomeScreen(hidden,{hidden=!hidden},favorites,{s->favorites=if(s in favorites)favorites-s else favorites+s},{s->pair=s;page="Trade"},{page="Assets"},{page="Markets"})
 "Markets"->MarketsScreen(filter,{filter=it},query,{query=it},favorites,{s->favorites=if(s in favorites)favorites-s else favorites+s},{s->pair=s;page="Trade"})
 "Trade"->TradeScreen(pair,{pairs=true},side,{side=it},type,{type=it},amount,{amount=it},price,{price=it},{chart=true},{message="Sign in required. Testnet order API connection is pending."})
 "Assets"->AssetsScreen({message="Sign in required to load Testnet balances."},{message="Sign in required."})
 "Profile"->ProfileScreen({auth=true},{message=it})
 }
 }}
 if(search)AlertDialog(onDismissRequest={search=false},containerColor=panel,title={Text("Search",color=ink)},text={Column{Field(query,{query=it},"Search coins/features");coins.filter{it.symbol.contains(query,true)||it.name.contains(query,true)}.take(8).forEach{Text(it.symbol+"  "+it.name,Modifier.fillMaxWidth().clickable{pair=it.symbol;page="Trade";search=false}.padding(9.dp),color=ink)}}},confirmButton={TextButton({search=false}){Text("Close",color=yellow)}})
 if(notices)AlertDialog(onDismissRequest={notices=false},containerColor=panel,title={Text("Notifications",color=ink)},text={Text("You’re all caught up.",color=muted)},confirmButton={TextButton({notices=false}){Text("Done",color=yellow)}})
 if(auth)AuthDialog(signup,{signup=it},{auth=false},{message="Native Firebase authentication is not connected yet."})
 if(pairs)PairDialog({pairs=false},{s->pair=s;pairs=false})
 if(chart)AlertDialog(onDismissRequest={chart=false},containerColor=panel,title={Text("$pair Chart",color=ink)},text={Column{Text("Native chart view",color=yellow);Text("Live candlestick data connection pending.",color=muted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("1m","15m","1H","4H","1D").forEach{Text(it,Modifier.background(panel2,RoundedCornerShape(6.dp)).padding(8.dp),color=ink)}}}},confirmButton={TextButton({chart=false}){Text("Close",color=yellow)}})
 if(message.isNotBlank())AlertDialog(onDismissRequest={message=""},containerColor=panel,text={Text(message,color=ink)},confirmButton={TextButton({message=""}){Text("OK",color=yellow)}})
}

@Composable private fun HomeScreen(hidden:Boolean,onHide:()->Unit,favs:Set<String>,onFav:(String)->Unit,onTrade:(String)->Unit,onFunds:()->Unit,onMarkets:()->Unit){
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp)){
 item{CardPanel{Row(verticalAlignment=Alignment.CenterVertically){Text("Est. Total Value (USD)",color=muted);IconButton(onHide){Icon(if(hidden)Icons.Default.VisibilityOff else Icons.Default.Visibility,null,tint=muted)}};Text(if(hidden)"******" else "Connect account",fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Today's PNL     --",color=muted,modifier=Modifier.padding(top=10.dp));Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Add Funds","Withdraw","Transfer").forEach{Action(it){onFunds()}}}}}
 item{CardPanel{Text("Trade Smarter with APEX",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("Explore the crypto markets",color=muted);Text("APEX",color=yellow,fontSize=22.sp,modifier=Modifier.align(Alignment.End))}}
 item{Row(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically){Text("Markets",fontSize=20.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text("View more pairs  ›",modifier=Modifier.clickable{onMarkets()},color=yellow)}}
 item{Row(horizontalArrangement=Arrangement.spacedBy(15.dp)){listOf("All","Favorite","Hot","Gainer","Loser").forEach{Text(it,color=if(it=="All")yellow else muted)}}}
 item{Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Trading Pair",color=muted);Text("Last Price / 24h Change",color=muted)}}
 items(coins.take(8)){CoinItem(it,it.symbol in favs,{onFav(it.symbol)},{onTrade(it.symbol)})}
 item{Text("Displayed prices are sample values until live market API is connected.",color=muted,fontSize=11.sp,modifier=Modifier.padding(vertical=14.dp))}
 }}
@Composable private fun MarketsScreen(filter:String,setFilter:(String)->Unit,q:String,setQ:(String)->Unit,favs:Set<String>,fav:(String)->Unit,trade:(String)->Unit){
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){Text("Markets",fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp));Field(q,setQ,"Search markets");Row(horizontalArrangement=Arrangement.spacedBy(20.dp),modifier=Modifier.padding(vertical=14.dp)){listOf("All","Gainers","Losers").forEach{Text(it,modifier=Modifier.clickable{setFilter(it)},color=if(filter==it)yellow else muted)}}
 LazyColumn{items(coins.filter{(q.isBlank()||it.symbol.contains(q,true)||it.name.contains(q,true))&&(filter=="All"||(filter=="Gainers"&&it.up)||(filter=="Losers"&&!it.up))}){CoinItem(it,it.symbol in favs,{fav(it.symbol)},{trade(it.symbol)})}}
 }}
@Composable private fun TradeScreen(pair:String,openPairs:()->Unit,side:String,setSide:(String)->Unit,type:String,setType:(String)->Unit,amount:String,setAmount:(String)->Unit,price:String,setPrice:(String)->Unit,chart:()->Unit,submit:()->Unit){
 val c=coins.firstOrNull{it.symbol==pair}?:coins[0]
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp)){item{Row(verticalAlignment=Alignment.CenterVertically){Text(pair,modifier=Modifier.clickable{openPairs()},fontSize=20.sp,fontWeight=FontWeight.Bold);IconButton(openPairs){Icon(Icons.Default.ArrowDropDown,null,tint=ink)};Spacer(Modifier.weight(1f));IconButton(chart){Icon(Icons.Default.Fullscreen,null,tint=ink)}};Text(c.price,fontSize=25.sp,fontWeight=FontWeight.Bold);Text(c.change+"  24h",color=if(c.up)green else red)}
 item{CardPanel{Text("Price Chart",color=yellow);Text("Native chart • live candles pending",color=muted);Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf("1m","15m","1H","4H","1D").forEach{Text(it,color=muted)}}}}
 item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("BUY","SELL").forEach{Text(it,Modifier.weight(1f).background(if(side==it)if(it=="BUY")green else red else panel2,RoundedCornerShape(8.dp)).clickable{setSide(it)}.padding(12.dp),color=Color.White)}}}
 item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=12.dp)){listOf("Market","Limit","Stop-limit").forEach{Text(it,Modifier.background(if(type==it)panel2 else bg,RoundedCornerShape(6.dp)).clickable{setType(it)}.padding(8.dp),color=if(type==it)yellow else muted)}}}
 if(type!="Market")item{Field(price,setPrice,"Price (USDT)")}
 item{Field(amount,setAmount,"Amount ("+pair.removeSuffix("USDT")+")")}
 item{Text("Available balance: -- USDT",color=muted,modifier=Modifier.padding(vertical=12.dp))}
 item{Text("Order Total: -- USDT",color=muted)}
 item{Button(submit,Modifier.fillMaxWidth().padding(vertical=14.dp),colors=ButtonDefaults.buttonColors(containerColor=if(side=="BUY")green else red)){Text("$side $pair",color=Color.White)}}
 item{Text("Order Book",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("Price (USDT)       Amount       Total",color=muted,modifier=Modifier.padding(10.dp));repeat(6){Text("—                       —                —",color=if(it%2==0)green else red,modifier=Modifier.padding(5.dp))};Text("Recent Trades",fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.padding(top=16.dp));Text("No recent trade data connected.",color=muted,modifier=Modifier.padding(12.dp));Text("Open Orders     Order History     Holdings",color=yellow,modifier=Modifier.padding(vertical=18.dp))}
 }}
@Composable private fun AssetsScreen(deposit:()->Unit,withdraw:()->Unit){LazyColumn(Modifier.fillMaxSize().padding(16.dp)){item{Text("Assets",fontSize=23.sp,fontWeight=FontWeight.Bold);CardPanel{Text("Estimated Total Value (USDT)",color=muted);Text("--",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Spot Testnet balances",color=muted);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Action("Deposit",deposit);Action("Withdraw",withdraw);Action("Transfer",deposit)}}};item{Text("My Assets",fontSize=20.sp,fontWeight=FontWeight.Bold)};items(coins.take(12)){Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(it.symbol.removeSuffix("USDT"),fontWeight=FontWeight.Bold);Text(it.name,color=muted,fontSize=12.sp)};Text("--",color=ink)};HorizontalDivider(color=panel2)};item{Text("Balances require a signed-in Testnet account. Shared Testnet account; not a separate wallet per user.",color=muted,fontSize=11.sp,modifier=Modifier.padding(12.dp))}}}
@Composable private fun ProfileScreen(login:()->Unit,item:(String)->Unit){LazyColumn(Modifier.fillMaxSize().padding(16.dp)){item{Text("Profile",fontSize=23.sp,fontWeight=FontWeight.Bold);CardPanel{Text("Welcome to APEX",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Sign in to manage your account",color=muted);Button(login,colors=ButtonDefaults.buttonColors(containerColor=yellow)){Text("Log In / Sign Up",color=bg)}}};listOf("Identity Verification (KYC)","Security","Payment Methods","Notifications","Help & Support","About APEX").forEach{label->item{Row(Modifier.fillMaxWidth().clickable{item(label)}.padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),color=ink);Icon(Icons.Default.ChevronRight,null,tint=muted)};HorizontalDivider(color=panel2)}};item{Text("Demo profile. Authentication and KYC are not connected.",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))}}}
@Composable private fun PairDialog(close:()->Unit,choose:(String)->Unit){var q by remember{mutableStateOf("")};AlertDialog(onDismissRequest=close,containerColor=panel,title={Text("Trading Pairs",color=ink)},text={Column(Modifier.heightIn(max=420.dp)){Field(q,{q=it},"Search pairs");LazyColumn{items(coins.filter{it.symbol.contains(q,true)||it.name.contains(q,true)}){Text(it.symbol+"  "+it.name,Modifier.fillMaxWidth().clickable{choose(it.symbol)}.padding(12.dp),color=ink)}}}},confirmButton={TextButton(close){Text("Close",color=yellow)}})}
@Composable private fun AuthDialog(signup:Boolean,setSignup:(Boolean)->Unit,close:()->Unit,continueAction:()->Unit){var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};AlertDialog(onDismissRequest=close,containerColor=panel,title={Text(if(signup)"Sign Up" else "Log In",color=ink)},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){TextButton({setSignup(!signup)}){Text(if(signup)"Already registered? Log in" else "Create account",color=yellow)};Field(email,{email=it},"Email");OutlinedTextField(password,{password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true);Text("Native Firebase authentication connection pending.",color=muted,fontSize=12.sp)}},confirmButton={TextButton({close();continueAction()}){Text("Continue",color=yellow)}},dismissButton={TextButton(close){Text("Cancel",color=muted)}})}
@Composable private fun CardPanel(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().padding(vertical=7.dp).background(panel,RoundedCornerShape(15.dp)).padding(16.dp),content=content)}
@Composable private fun RowScope.Action(label:String,click:()->Unit){Column(Modifier.weight(1f).background(panel2,RoundedCornerShape(8.dp)).clickable{click()}.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(label,color=ink,fontSize=11.sp)}}
@Composable private fun CoinItem(c:Coin,fav:Boolean,onFav:()->Unit,open:()->Unit){Row(Modifier.fillMaxWidth().clickable{open()}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.symbol.replace("USDT","/USDT"),fontWeight=FontWeight.SemiBold);Text(c.name,color=muted,fontSize=12.sp)};Column(horizontalAlignment=Alignment.End){Text(c.price,color=ink);Text(c.change,color=if(c.up)green else red,fontSize=12.sp)};IconButton(onFav){Icon(if(fav)Icons.Default.Star else Icons.Default.StarBorder,null,tint=yellow)}};HorizontalDivider(color=panel2)}
@Composable private fun Field(value:String,change:(String)->Unit,label:String){OutlinedTextField(value,change,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=yellow,unfocusedBorderColor=panel2,focusedLabelColor=yellow))}
