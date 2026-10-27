"use strict";

const API = "https://api.binance.com/api/v3";
const $ = (id) => document.getElementById(id);

let marketData = [];
let currentFilter = "All";
let currentPair = "BTCUSDT";
let currentSide = "BUY";
let refreshTimer;

const APEX_BACKEND = "https://apex-exchange-backend.hostless.app";

async function submitApexOrder() {
  try {
    const user = window.apexUser;
    if (!user || !window.apexGetIdToken) {
      notify("Pehle login karo.");
      window.dispatchEvent(new Event("apex-login-request"));
      return;
    }

    const amount = Number($("orderAmount")?.value || 0);
    const price = Number($("orderPrice")?.value || 0);
    const orderType = String($("orderType")?.value || "Market").toUpperCase();
    const marketPrice = Number($("tradePrice")?.textContent?.replace(/[^0-9.]/g, "") || 0);
    const effectivePrice = orderType === "LIMIT" ? price : marketPrice;

    if (!Number.isFinite(amount) || amount <= 0) {
      notify("Valid order amount enter karo.");
      return;
    }

    if (orderType === "STOP-LIMIT") { notify("Stop-limit abhi supported nahi; Market ya Limit chuno."); return; }

    if (orderType === "LIMIT" && (!Number.isFinite(price) || price <= 0)) {
      notify("Limit price enter karo.");
      return;
    }

    if (!Number.isFinite(effectivePrice) || effectivePrice <= 0) {
      notify("Price abhi available nahi; dobara try karo.");
      return;
    }

    const quantity = (amount / effectivePrice).toFixed(8).replace(/0+$/, "").replace(/\.$/, "");
    const token = await window.apexGetIdToken();
    const response = await fetch(`${APEX_BACKEND}/api/order`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify({
        symbol: currentPair,
        side: currentSide,
        type: orderType,
        quantity,
        ...(orderType === "LIMIT" ? { price: String(price) } : {})
      })
    });

    const result = await response.json();
    if (!response.ok) throw new Error(result.error || "Order failed");
    notify(`Testnet order status: ${result.order?.status || "submitted"}`);
    await loadApexOrderHistory();
  } catch (error) {
    console.error("APEX order failed:", error);
    notify(error.message || "Order submit nahi hua.");
  }
}

function notify(message) {
  const toast = $("toast");
  if (!toast) {
    console.log(message);
    return;
  }
  toast.textContent = message;
  toast.classList.add("show");
  setTimeout(() => toast.classList.remove("show"), 3000);
}

async function api(path) {
  const response = await fetch(`${API}${path}`);
  if (!response.ok) throw new Error(`Market API error: ${response.status}`);
  return response.json();
}

function money(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return "—";
  return n >= 1
    ? n.toLocaleString("en-US", { maximumFractionDigits: 4 })
    : n.toLocaleString("en-US", { maximumFractionDigits: 8 });
}

function changeText(value) {
  const n = Number(value);
  return `${n > 0 ? "+" : ""}${n.toFixed(2)}%`;
}

function setText(id, value) {
  const el = $(id);
  if (el) el.textContent = value;
}

function setColor(el, value) {
  if (!el) return;
  el.style.color = Number(value) >= 0 ? "#0ecb81" : "#f6465d";
}


async function apexPrivateGet(path) {
  if (!window.apexUser || !window.apexGetIdToken) {
    throw new Error("Please log in first.");
  }
  const token = await window.apexGetIdToken();
  const response = await fetch(`${APEX_BACKEND}${path}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Backend request failed.");
  return data;
}

async function loadApexAccount() {
  const balanceEl = $("balance");
  const assetSection = $("assets");
  if (!window.apexUser) {
    if (balanceEl) balanceEl.innerHTML = 'Log in to view <small>USDT</small>';
    const total = assetSection?.querySelector("[data-wallet-total]");
    if (total) total.textContent = "Log in to view";
    const list = $("walletAssetList");
    if (list) list.textContent = "Log in to view Testnet balances.";
    return;
  }
  try {
    const data = await apexPrivateGet("/api/account");
    const balances = (data.balances || []).filter(x =>
      Number(x.free) > 0 || Number(x.locked) > 0
    );
    const total = balances.reduce((sum, x) => {
      const ticker = marketData.find(m => m.symbol === `${x.asset}USDT`);
      return sum + (ticker ? (Number(x.free) + Number(x.locked)) * Number(ticker.lastPrice) : 0);
    }, 0);
    if (balanceEl) balanceEl.innerHTML = `${money(total)} <small>USDT*</small>`;
    const totalEl = assetSection?.querySelector("[data-wallet-total]");
    if (totalEl) totalEl.textContent = `${money(total)} USDT*`;
    const list = $("walletAssetList");
    if (list) {
      list.replaceChildren();
      if (!balances.length) list.textContent = "No funded Testnet assets yet.";
      balances.forEach(x => {
        const row = document.createElement("div");
        row.className = "market-row";
        row.style.cssText = "display:flex;justify-content:space-between;padding:12px 8px;border-bottom:1px solid #292d35";
        const coin = apexCoinInfo(x.asset);
const name = document.createElement("span");
name.style.cssText = "display:flex;align-items:center;gap:8px";
{
 const img = document.createElement("img");
 img.alt = coin.symbol; img.width = 30; img.height = 30;
 let ix=0; img.onerror=()=>{ix++;if(ix<coin.logos.length)img.src=coin.logos[ix];else img.remove();};
 img.src=coin.logos[0]; name.appendChild(img);
}
const labels = document.createElement("span");
labels.style.cssText = "display:flex;flex-direction:column";
const ticker = document.createElement("strong"); ticker.textContent = coin.symbol;
const fullname = document.createElement("small"); fullname.textContent = coin.name;
fullname.style.color = "#929aa5"; labels.append(ticker, fullname); name.appendChild(labels);
        const qty = document.createElement("span");
        qty.textContent = `${Number(x.free)} available · ${Number(x.locked)} locked`;
        row.append(name, qty);
        list.appendChild(row);
      });
    }
  } catch (e) {
    console.error(e);
    if (balanceEl) balanceEl.innerHTML = 'Balance unavailable <small>USDT</small>';
    const list = $("walletAssetList");
    if (list) list.textContent = e.message;
  }
}

$("refreshHistory")?.addEventListener("click", loadApexOrderHistory);

async function loadApexOrderHistory() {
  const openBox = $("openOrdersList");
  const filledBox = $("filledOrdersList");
  const otherBox = $("otherOrdersList");

  if (!openBox || !filledBox || !otherBox) return;
  setText("historyPair", "All pairs");

  if (!window.apexUser) {
    openBox.textContent = "Log in to view orders.";
    filledBox.textContent = "Log in to view orders.";
    otherBox.textContent = "Log in to view orders.";
    return;
  }

  openBox.textContent = "Loading...";
  filledBox.textContent = "Loading...";
  otherBox.textContent = "Loading...";

  try {
    const data = await apexPrivateGet("/api/orders");
    const orders = data.orders || [];
    openBox.replaceChildren();
    filledBox.replaceChildren();
    otherBox.replaceChildren();

    const openOrders = [];
    const filledOrders = [];
    const otherOrders = [];

    orders.forEach(o => {
      const status = String(o.status || "").toUpperCase();
      if (["NEW", "PARTIALLY_FILLED"].includes(status)) {
        openOrders.push(o);
      } else if (status === "FILLED") {
        filledOrders.push(o);
      } else {
        otherOrders.push(o);
      }
    });

    function renderOrders(box, list, emptyMessage, allowCancel) {
      if (!list.length) {
        box.textContent = emptyMessage;
        return;
      }

      list.forEach(o => {
        const row = document.createElement("div");
        row.style.cssText = "padding:14px 8px;border-bottom:1px solid #292d35;display:flex;flex-direction:column;gap:7px";

        const title = document.createElement("strong");
        title.textContent = `${o.symbol || "—"} · ${o.side || "—"} ${o.type || ""}`;
        title.style.color = o.side === "BUY" ? "#0ecb81" : "#f6465d";

        const qty = Number(o.origQty || 0);
        const price = Number(o.price || 0);
        const executedTotal = Number(o.cummulativeQuoteQty || 0);
        const total = executedTotal > 0 ? executedTotal : qty * price;

        const details = document.createElement("div");
        details.style.cssText = "font-size:13px;color:#b7bdc6;line-height:1.8;overflow-wrap:anywhere";
        const date = o.createdAt ? new Date(o.createdAt).toLocaleString() : "Time unavailable";
        details.textContent =
          `Qty: ${o.origQty || "—"} · Total: ${total.toFixed(2)} USDT · Price: ${o.price || "Market"} · Status: ${o.status || "—"} · Order ID: ${o.orderId || "—"} · ${date}`;

        row.append(title, details);

        if (allowCancel && ["NEW", "PARTIALLY_FILLED"].includes(String(o.status || "").toUpperCase())) {
          const cancel = document.createElement("button");
          cancel.type = "button";
          cancel.textContent = "Cancel Order";
          cancel.style.cssText = "align-self:flex-start;background:#f6465d;color:white;border:0;border-radius:5px;padding:8px 12px;cursor:pointer";

          cancel.addEventListener("click", async () => {
            if (!confirm(`Cancel ${o.symbol} order #${o.orderId}?`)) return;
            cancel.disabled = true;
            cancel.textContent = "Cancelling...";

            try {
              const token = await window.apexGetIdToken();
              const response = await fetch(
                `${APEX_BACKEND}/api/order/${encodeURIComponent(o.symbol)}/${encodeURIComponent(o.orderId)}`,
                { method: "DELETE", headers: { Authorization: `Bearer ${token}` } }
              );
              const result = await response.json();
              if (!response.ok) throw new Error(result.error || "Cancel failed");
              notify("Testnet order cancelled.");
              await loadApexOrderHistory();
            } catch (e) {
              notify(e.message || "Order cancel nahi hua.");
              cancel.disabled = false;
              cancel.textContent = "Cancel Order";
            }
          });

          row.appendChild(cancel);
        }

        box.appendChild(row);
      });
    }

    renderOrders(openBox, openOrders, "No open orders.", true);
    renderOrders(filledBox, filledOrders, "No filled orders.", false);
    renderOrders(otherBox, otherOrders, "No cancelled or other orders.", false);
  } catch (e) {
    console.error(e);
    openBox.textContent = e.message || "Order history load nahi hui.";
    filledBox.textContent = "Order history load nahi hui.";
    otherBox.textContent = "Order history load nahi hui.";
  }
}

function showPage(name) {
  const pages = document.querySelectorAll(".page");
  pages.forEach((page) => page.classList.toggle("active", page.id === name));

  document.querySelectorAll(".nav-item[data-tab]").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === name);
  });

  if (name === "trade") { refreshTrade(); loadApexOrderHistory(); }
  if (name === "assets" || name === "home") loadApexAccount();
}


const APEX_COINS = {
BTC:["Bitcoin","btc"], ETH:["Ethereum","eth"], BNB:["BNB","bnb"],
SOL:["Solana","sol"], XRP:["XRP","xrp"], ADA:["Cardano","ada"],
DOGE:["Dogecoin","doge"], TRX:["TRON","trx"], TON:["Toncoin","ton"],
LTC:["Litecoin","ltc"], DOT:["Polkadot","dot"], LINK:["Chainlink","link"],
BCH:["Bitcoin Cash","bch"], AVAX:["Avalanche","avax"], SHIB:["Shiba Inu","shib"],
UNI:["Uniswap","uni"], XLM:["Stellar","xlm"], ATOM:["Cosmos","atom"],
NEAR:["NEAR Protocol","near"], APT:["Aptos","apt"], SUI:["Sui","sui"],
FIL:["Filecoin","fil"], ETC:["Ethereum Classic","etc"],
USDT:["Tether","usdt"]
};

function apexCoinInfo(asset) {
  const raw = String(asset || "").toUpperCase();
  const symbol = raw.endsWith("USDT") && raw !== "USDT" ? raw.slice(0, -4) : raw;
  const info = APEX_COINS[symbol] || [symbol, symbol.toLowerCase()];
  const slug = info[1];
  return {
    symbol,
    name: info[0],
    logos: [
      "https://cdn.jsdelivr.net/gh/atomiclabs/cryptocurrency-icons@master/128/color/" + slug + ".png",
      "https://assets.coincap.io/assets/icons/" + symbol.toLowerCase() + "@2x.png",
      "https://cryptoicons.org/api/icon/" + slug + "/200"
    ]
  };
}

function makeMarketRow(item) {
  const row = document.createElement("button");
  row.type = "button";
  row.className = "market-row";
  row.style.cssText = "display:grid;grid-template-columns:minmax(0,1.4fr) minmax(70px,1fr) auto;align-items:center;gap:8px;width:100%;padding:12px 8px;border:0;border-bottom:1px solid #292d35;background:transparent;color:inherit;text-align:left";

  const coin = apexCoinInfo(item.symbol);
  const asset = document.createElement("span");
  asset.style.cssText = "display:flex;align-items:center;gap:8px;min-width:0";

  const img = document.createElement("img");
  img.alt = coin.symbol;
  img.width = 30;
  img.height = 30;
  img.style.cssText = "width:30px;height:30px;object-fit:contain;flex-shrink:0";
  let ix = 0;
  img.onerror = () => {
    ix++;
    if (ix < coin.logos.length) img.src = coin.logos[ix];
    else img.style.display = "none";
  };
  img.src = coin.logos[0];
  asset.appendChild(img);

  const labels = document.createElement("span");
  labels.style.cssText = "display:flex;flex-direction:column;min-width:0";
  const ticker = document.createElement("strong");
  ticker.textContent = coin.symbol;
  const fullname = document.createElement("small");
  fullname.textContent = coin.name;
  fullname.style.color = "#929aa5";
  labels.append(ticker, fullname);
  asset.appendChild(labels);

  const favorites = JSON.parse(localStorage.getItem("apexFavorites") || "[]");
  const isFavorite = favorites.includes(item.symbol);

  const star = document.createElement("span");
  star.textContent = isFavorite ? "★" : "☆";
  star.title = isFavorite ? "Remove favorite" : "Add favorite";
  star.setAttribute("role", "button");
  star.setAttribute("tabindex", "0");
  star.setAttribute("aria-label", star.title);
  star.style.cssText = "font-size:22px;color:#f0b90b;padding:0 7px;cursor:pointer;flex-shrink:0";

  const toggleFavorite = (event) => {
    event.stopPropagation();
    const saved = JSON.parse(localStorage.getItem("apexFavorites") || "[]");
    const updated = saved.includes(item.symbol)
      ? saved.filter((symbol) => symbol !== item.symbol)
      : [...saved, item.symbol];
    localStorage.setItem("apexFavorites", JSON.stringify(updated));
    renderMarkets();
  };

  star.addEventListener("click", toggleFavorite);
  star.addEventListener("keydown", (event) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      toggleFavorite(event);
    }
  });
  const price = document.createElement("span");
  price.textContent = money(item.lastPrice);

  const change = document.createElement("span");
  change.textContent = changeText(item.priceChangePercent);
  const positive = Number(item.priceChangePercent) >= 0;
  change.style.cssText = "text-align:center;min-width:72px;padding:6px 8px;border-radius:5px;font-size:12px;font-weight:700;color:#fff;background:" + (positive ? "#0ecb81" : "#f6465d");

  const changeGroup = document.createElement("span");
  changeGroup.style.cssText = "display:flex;align-items:center;justify-content:flex-end;gap:4px";
  changeGroup.append(star, change);

  row.append(asset, price, changeGroup);
  row.addEventListener("click", () => {
    currentPair = item.symbol;
    setText("pairName", coin.symbol + "/USDT");
    setText("tradePrice", money(item.lastPrice));
    showPage("trade");
    refreshTrade();
  });

  return row;
}

function renderMarkets() {
  let items = marketData.filter((item) => item.symbol.endsWith("USDT"));

  const search = $("marketSearch");
  const query = search ? search.value.trim().toUpperCase() : "";
  if (query) items = items.filter((item) => item.symbol.includes(query));

  if (currentFilter === "Gainers" || currentFilter === "Gainer") {
    items = items.filter((item) => Number(item.priceChangePercent) > 0)
      .sort((a, b) => Number(b.priceChangePercent) - Number(a.priceChangePercent));
  } else if (currentFilter === "Losers" || currentFilter === "Loser") {
    items = items.filter((item) => Number(item.priceChangePercent) < 0)
      .sort((a, b) => Number(a.priceChangePercent) - Number(b.priceChangePercent));
  } else if (currentFilter === "Hot") {
    items.sort((a, b) => Number(b.quoteVolume) - Number(a.quoteVolume));
  } else if (currentFilter === "New") {
    items = items.slice(-30).reverse();
  } else if (currentFilter === "Favorite") {
    const favorites = JSON.parse(localStorage.getItem("apexFavorites") || "[]");
    items = items.filter((item) => favorites.includes(item.symbol));
  } else {
    items.sort((a, b) => Number(b.quoteVolume) - Number(a.quoteVolume));
  }

  const home = $("homeMarkets");
  const all = $("allMarkets");

  if (home) {
    home.replaceChildren();
    items.slice(0, 8).forEach((item) => home.appendChild(makeMarketRow(item)));
  }
  if (all) {
    all.replaceChildren();
    items.slice(0, 100).forEach((item) => all.appendChild(makeMarketRow(item)));
  }
}

async function refreshMarkets() {
  try {
    marketData = await api("/ticker/24hr");
    renderMarkets();

    const lookup = Object.fromEntries(marketData.map((x) => [x.symbol, x]));
    for (const [symbol, id, changeId] of [
      ["BTCUSDT", "tickerBTC", "tickerBTCChange"],
      ["ETHUSDT", "tickerETH", "tickerETHChange"],
      ["BNBUSDT", "tickerBNB", "tickerBNBChange"]
    ]) {
      const item = lookup[symbol];
      if (!item) continue;
      setText(id, money(item.lastPrice));
      setText(changeId, changeText(item.priceChangePercent));
      setColor($(changeId), item.priceChangePercent);
    }

    const btc = lookup[currentPair];
    if (btc) setText("tradePrice", money(btc.lastPrice));

    document.querySelectorAll(".welcome .tag").forEach((tag) => {
      tag.textContent = "Live market data";
      tag.style.color = "#0ecb81";
    });
  } catch (error) {
    console.error(error);
    notify("Market data unavailable. Check your internet connection.");
  }
}

async function refreshTrade() {
  try {
    const [book, trades] = await Promise.all([
      api(`/depth?symbol=${encodeURIComponent(currentPair)}&limit=10`),
      api(`/trades?symbol=${encodeURIComponent(currentPair)}&limit=10`)
    ]);

    const bids = $("bids");
    const asks = $("asks");
    const recent = $("recentTrades");

    function renderBook(container, rows, className) {
      if (!container) return;
      container.replaceChildren();
      rows.slice(0, 8).forEach(([price, amount]) => {
        const row = document.createElement("div");
        row.className = `book-row ${className}`;
        row.style.cssText =
          "display:flex;justify-content:space-between;gap:12px;padding:4px 2px;";
        const p = document.createElement("span");
        p.textContent = money(price);
        const a = document.createElement("span");
        a.textContent = money(amount);
        row.append(p, a);
        container.appendChild(row);
      });
    }

    renderBook(bids, book.bids, "bid");
    renderBook(asks, book.asks, "ask");

    if (recent) {
      recent.replaceChildren();
      trades.slice(0, 8).forEach((trade) => {
        const row = document.createElement("div");
        row.className = "recent-row";
        row.style.cssText =
          "display:flex;justify-content:space-between;gap:10px;padding:5px 2px;";
        const price = document.createElement("span");
        price.textContent = money(trade.price);
        setColor(price, trade.isBuyerMaker ? -1 : 1);
        const amount = document.createElement("span");
        amount.textContent = money(trade.qty);
        const time = document.createElement("span");
        time.textContent = new Date(trade.time).toLocaleTimeString();
        row.append(price, amount, time);
        recent.appendChild(row);
      });
    }
  } catch (error) {
    console.error(error);
    notify("Order-book data could not load.");
  }
}

function updateTotal() {
  const amount = Number($("orderAmount")?.value || 0);
  const priceInput = Number($("orderPrice")?.value || 0);
  const ticker = marketData.find((item) => item.symbol === currentPair);
  const isLimit = String($("orderType")?.value || "").toUpperCase() === "LIMIT";
  const price = isLimit ? priceInput : Number(ticker?.lastPrice || 0);
  const coinQty = price > 0 ? amount / price : 0;

  setText("orderTotal", `${money(amount)} USDT`);
  const hint = $("orderAmountHint");
  if (hint) hint.textContent = price > 0
    ? `Approx. ${coinQty.toFixed(8)} ${currentPair.replace("USDT", "")}`
    : "Market price loading…";
}

function updatePriceVisibility() {
  const isLimit = String($("orderType")?.value || "").toUpperCase() === "LIMIT";
  const label = $("priceLabel");
  if (label) label.style.display = isLimit ? "" : "none";
}

$("placeBuyOrder")?.addEventListener("click", () => {
  currentSide = "BUY";
  submitApexOrder();
});
$("placeSellOrder")?.addEventListener("click", () => {
  currentSide = "SELL";
  submitApexOrder();
});
$("orderType")?.addEventListener("change", () => {
  updatePriceVisibility();
  updateTotal();
});
updatePriceVisibility();

document.addEventListener("click", (event) => {
  const tab = event.target.closest("[data-tab]");
  if (tab) {
    showPage(tab.dataset.tab);
    return;
  }

  const filter = event.target.closest("[data-filter], [data-mktfilter]");
  if (filter) {
    currentFilter = filter.dataset.filter || filter.dataset.mktfilter || "All";
    const group = filter.parentElement;
    if (group) {
      group.querySelectorAll(".chip").forEach((chip) => {
        chip.classList.toggle("selected", chip === filter);
      });
    }
    renderMarkets();
    return;
  }

  const orderSide = event.target.closest("[data-order]");
  if (orderSide) {
    currentSide = String(orderSide.dataset.order).toUpperCase();
    document.querySelectorAll("[data-order]").forEach((button) => {
      button.classList.toggle(
        "selected",
        String(button.dataset.order).toUpperCase() === currentSide
      );
    });
    return;
  }

  const action = event.target.closest("[data-action]");
  if (action) {
    notify("Account login and this feature are not connected yet.");
    return;
  }

  if (event.target.closest("#loginBtn")) {
    window.dispatchEvent(new Event("apex-login-request"));
    return;
  }

  if (event.target.closest("#placeOrder")) {
    submitApexOrder();
    return;
  }

if (event.target.closest("#hideBalance")) {
    const balance = $("balance");
    if (balance) {
      balance.dataset.hidden = balance.dataset.hidden !== "true";
      balance.textContent = balance.dataset.hidden === "true"
        ? "•••••• USDT"
        : "Connect account USDT";
    }
  }
});

$("marketSearch")?.addEventListener("input", renderMarkets);
$("orderAmount")?.addEventListener("input", updateTotal);
$("orderPrice")?.addEventListener("input", updateTotal);
document.querySelectorAll("[data-interval]").forEach((button) => {
  button.addEventListener("click", async () => {
    document.querySelectorAll("[data-interval]").forEach((b) =>
      b.classList.toggle("selected", b === button)
    );

    const interval = button.dataset.interval;
    try {
      const candles = await api(
        `/klines?symbol=${encodeURIComponent(currentPair)}&interval=${encodeURIComponent(interval)}&limit=50`
      );
      const svg = document.querySelector(".chart-placeholder svg");
      if (!svg || !candles.length) return;

      const values = candles.map((c) => Number(c[4]));
      const min = Math.min(...values);
      const max = Math.max(...values);
      const range = max - min || 1;
      const points = values.map((value, index) => {
        const x = (index / Math.max(values.length - 1, 1)) * 340;
        const y = 135 - ((value - min) / range) * 120;
        return `${index === 0 ? "M" : "L"}${x.toFixed(1)} ${y.toFixed(1)}`;
      }).join(" ");

      const path = svg.querySelector("path");
      if (path) {
        path.setAttribute("d", points);
        path.setAttribute("fill", "none");
        path.setAttribute("stroke", "#0ecb81");
        path.setAttribute("stroke-width", "2.5");
      }
    } catch (error) {
      console.error(error);
      notify("Chart data could not load.");
    }
  });
});

showPage("home");
refreshMarkets();
refreshTrade();
refreshTimer = setInterval(() => {
  refreshMarkets();
  if ($("trade")?.classList.contains("active")) refreshTrade();
}, 15000);

window.addEventListener("apex-auth-changed", () => { loadApexAccount(); loadApexOrderHistory(); });


/* APEX_ORDER_PANEL_TABS */
document.addEventListener("click", event => {
  const button = event.target.closest("[data-order-panel]");
  if (!button) return;

  const showHistory = button.dataset.orderPanel === "history";
  const openPanel = document.getElementById("openOrdersPanel");
  const historyPanel = document.getElementById("orderHistoryPanel");
  if (!openPanel || !historyPanel) return;

  openPanel.hidden = showHistory;
  historyPanel.hidden = !showHistory;

  document.querySelectorAll("[data-order-panel]").forEach(tab => {
    const active = tab === button;
    tab.setAttribute("aria-selected", String(active));
    tab.style.background = active ? "#17372f" : "#161a1e";
    tab.style.color = active ? "#ffffff" : "#b7bdc6";
    tab.style.borderColor = active ? "#0ecb81" : "#292d35";
  });
});
