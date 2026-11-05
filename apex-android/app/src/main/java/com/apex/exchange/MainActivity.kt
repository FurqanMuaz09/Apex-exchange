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
                            Text("Last Price", color = Muted, fontSize = 11.sp, modifier = Modifier.width(88.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Spacer(Modifier.width(8.dp))
                            Text("24h chg%", color = Muted, fontSize = 11.sp, modifier = Modifier.width(86.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Spacer(Modifier.width(30.dp))
                        }
                    }
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
                            Column(Modifier.width(88.dp), horizontalAlignment = Alignment.End) {
                                Text(coin.price, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Text(coinUsdPriceLabel(coin.price), color = Muted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.width(86.dp), horizontalAlignment = Alignment.End) {
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