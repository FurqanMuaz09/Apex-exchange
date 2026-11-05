require('dotenv').config();

const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const rateLimit = require('express-rate-limit');
const { initializeApp, getApps, cert } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');

const app = express();
app.disable('x-powered-by');
app.use(cors());
app.use(express.json({ limit: '8kb' }));

const PORT = process.env.PORT || process.env.Port || 3000;
const KEY = (process.env.BINANCE_TESTNET_API_KEY || '').trim();
const SECRET = (process.env.BINANCE_TESTNET_API_SECRET || '').trim();
const BASE = 'https://testnet.binance.vision';

const serviceAccount = (() => {
  const value = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!value) return require('./secrets/serviceAccountKey.json');

  const raw = value.trim().startsWith('{')
    ? value.trim()
    : Buffer.from(value.trim(), 'base64').toString('utf8');

  return JSON.parse(raw);
})();

if (!getApps().length) {
  initializeApp({ credential: cert(serviceAccount) });
}

const auth = getAuth();
const db = getFirestore();

const orderLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 10,
  standardHeaders: true,
  legacyHeaders: false
});

const adminNotificationLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 5,
  standardHeaders: true,
  legacyHeaders: false
});

const ADMIN_UIDS = new Set(
  (process.env.APEX_ADMIN_UIDS || '')
    .split(',')
    .map(uid => uid.trim())
    .filter(Boolean)
);

function requireApexAdmin(req, res, next) {
  if (!ADMIN_UIDS.has(req.user.uid)) {
    return res.status(403).json({ error: 'APEX admin access required.' });
  }
  next();
}


async function requireFirebaseAuth(req, res, next) {
  try {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : '';
    if (!token) return res.status(401).json({ error: 'Login required.' });
    const decoded = await auth.verifyIdToken(token);
    req.user = { uid: decoded.uid };
    next();
  } catch {
    return res.status(401).json({ error: 'Invalid or expired login token.' });
  }
}

function userOrders(uid) {
  return db.collection('users').doc(uid).collection('orders');
}

function userWallet(uid) {
  return db.collection('wallets').doc(uid);
}
function userWalletTransactions(uid) {
  return userWallet(uid).collection('transactions');
}
async function ensureWallet(uid) {
  const ref = userWallet(uid);
  await db.runTransaction(async tx => {
    const snapshot = await tx.get(ref);
    if (!snapshot.exists) tx.create(ref, {
      uid, mode: 'TESTNET_LEDGER_ONLY', status: 'PENDING_CUSTODY_INFRASTRUCTURE',
      balances: {}, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()
    });
  });
  return ref.get();
}

async function binanceRequest(method, path, params = {}) {
  if (!KEY || !SECRET) throw new Error('Binance Testnet API keys are not configured.');

  const queryParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      queryParams.set(key, String(value));
    }
  }
  queryParams.set('recvWindow', '5000');
  queryParams.set('timestamp', String(Date.now()));

  const query = queryParams.toString();
  const signature = crypto.createHmac('sha256', SECRET).update(query).digest('hex');
  const url = `${BASE}${path}?${query}&signature=${signature}`;

  const response = await fetch(url, {
    method,
    headers: { 'X-MBX-APIKEY': KEY }
  });
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const error = new Error(data.msg || 'Binance Testnet request failed.');
    error.status = response.status;
    error.binanceCode = data.code;
    throw error;
  }
  return data;
}

function sendError(res, error) {
  console.error('APEX backend error:', error.message);
  res.status(error.status && error.status >= 400 && error.status < 600 ? error.status : 400)
    .json({ error: error.message, binanceCode: error.binanceCode ?? null });
}

function orderDocumentId(symbol, orderId) {
  return `${symbol}_${orderId}`;
}

app.get('/', (_req, res) => res.json({
  service: 'APEX Binance Testnet Backend',
  mode: 'TESTNET ONLY',
  configured: Boolean(KEY && SECRET),
  orderPlacement: 'AUTHENTICATED'
}));

app.get('/health', (_req, res) => res.json({ ok: true }));

app.post('/api/admin/notifications', requireFirebaseAuth, requireApexAdmin, adminNotificationLimiter, async (req, res) => {
  try {
    const uid = String(req.body.uid || '').trim();
    const title = String(req.body.title || '').trim();
    const message = String(req.body.message || '').trim();

    if (!/^[A-Za-z0-9_-]{1,128}$/.test(uid)) {
      return res.status(400).json({ error: 'A valid recipient Firebase UID is required.' });
    }
    if (!title || title.length > 80) {
      return res.status(400).json({ error: 'Title is required and must be 80 characters or fewer.' });
    }
    if (!message || message.length > 1000) {
      return res.status(400).json({ error: 'Message is required and must be 1000 characters or fewer.' });
    }

    const userRef = db.collection('users').doc(uid);
    const userSnapshot = await userRef.get();
    if (!userSnapshot.exists) {
      return res.status(404).json({ error: 'Recipient account was not found.' });
    }

    const notificationRef = userRef.collection('notifications').doc();
    await notificationRef.set({
      title,
      message,
      createdAt: FieldValue.serverTimestamp(),
      read: false,
      type: 'admin'
    });

    res.status(201).json({ ok: true, notificationId: notificationRef.id });
  } catch (error) {
    sendError(res, error);
  }
});

const activeListenKeys = new Map();

app.post('/api/account/listen-key', requireFirebaseAuth, async (req, res) => {
  try {
    const uid = req.user.uid;
    const existing = activeListenKeys.get(uid);
    if (existing) return res.json({ listenKey: existing.listenKey });

    const response = await fetch(`${BASE}/api/v3/userDataStream`, {
      method: 'POST',
      headers: { 'X-MBX-APIKEY': KEY }
    });
    const data = await response.json();
    if (!response.ok || !data.listenKey) {
      throw new Error(data.msg || 'Could not start Binance account stream.');
    }

    const entry = { listenKey: data.listenKey, timer: null };
    entry.timer = setInterval(async () => {
      try {
        await fetch(`${BASE}/api/v3/userDataStream?listenKey=${encodeURIComponent(entry.listenKey)}`, {
          method: 'PUT',
          headers: { 'X-MBX-APIKEY': KEY }
        });
      } catch (error) {
        console.error('Binance listen-key keepalive failed:', error.message);
      }
    }, 30 * 60 * 1000);
    activeListenKeys.set(uid, entry);
    res.json({ listenKey: entry.listenKey });
  } catch (error) {
    sendError(res, error);
  }
});

app.get('/api/holdings/metrics', requireFirebaseAuth, async (req, res) => {
  try {
    const symbol = String(req.query.symbol || '').toUpperCase();
    if (!/^[A-Z0-9]{5,20}$/.test(symbol)) {
      return res.status(400).json({ error: 'A valid trading pair symbol is required.' });
    }

    const [trades, tickerResponse] = await Promise.all([
      binanceRequest('GET', '/api/v3/myTrades', { symbol, limit: 1000 }),
      fetch(`${BASE}/api/v3/ticker/price?symbol=${encodeURIComponent(symbol)}`)
    ]);
    const ticker = await tickerResponse.json().catch(() => ({}));
    if (!tickerResponse.ok || !Number.isFinite(Number(ticker.price))) {
      throw new Error('Could not load the current market price for holdings.');
    }

    const baseAsset = symbol.replace(/(USDT|USDC|BTC|ETH|BNB|FDUSD|TUSD|USDP|DAI)$/, '');
    const quoteAsset = symbol.slice(baseAsset.length);
    const lots = [];
    let realizedPnl = 0;
    let matchedSells = 0;

    trades.sort((a, b) => Number(a.time) - Number(b.time));
    for (const trade of trades) {
      const qty = Number(trade.qty);
      const quoteQty = Number(trade.quoteQty);
      const fee = Number(trade.commission || 0);
      if (!Number.isFinite(qty) || qty <= 0 || !Number.isFinite(quoteQty)) continue;

      if (trade.isBuyer) {
        const netQty = Math.max(0, qty - (trade.commissionAsset === baseAsset ? fee : 0));
        const totalCost = quoteQty + (trade.commissionAsset === quoteAsset ? fee : 0);
        if (netQty > 0) lots.push({ qty: netQty, unitCost: totalCost / netQty });
        continue;
      }

      let remaining = qty + (trade.commissionAsset === baseAsset ? fee : 0);
      const proceeds = Math.max(0, quoteQty - (trade.commissionAsset === quoteAsset ? fee : 0));
      let allocatedCost = 0;
      let matchedQty = 0;
      while (remaining > 1e-12 && lots.length) {
        const lot = lots[0];
        const used = Math.min(remaining, lot.qty);
        allocatedCost += used * lot.unitCost;
        matchedQty += used;
        lot.qty -= used;
        remaining -= used;
        if (lot.qty <= 1e-12) lots.shift();
      }
      if (matchedQty > 0) {
        realizedPnl += proceeds * (matchedQty / (qty + (trade.commissionAsset === baseAsset ? fee : 0))) - allocatedCost;
        matchedSells++;
      }
    }

    const trackedQuantity = lots.reduce((sum, lot) => sum + lot.qty, 0);
    const costBasis = lots.reduce((sum, lot) => sum + lot.qty * lot.unitCost, 0);
    const averageEntryPrice = trackedQuantity > 0 ? costBasis / trackedQuantity : 0;
    const currentPrice = Number(ticker.price);
    const unrealizedPnl = trackedQuantity > 0 ? currentPrice * trackedQuantity - costBasis : 0;

    res.json({
      mode: 'TESTNET ONLY',
      symbol,
      baseAsset,
      quoteAsset,
      currentPrice,
      trackedQuantity,
      costBasis,
      averageEntryPrice,
      unrealizedPnl,
      pnlPercent: costBasis > 0 ? ((unrealizedPnl + realizedPnl) / costBasis) * 100 : 0,
      realizedPnl,
      trades: trades.length,
      matchedSells
    });
  } catch (error) {
    sendError(res, error);
  }
});

app.get('/api/pnl/today', requireFirebaseAuth, async (req, res) => {
  try {
    const snapshot = await userOrders(req.user.uid).limit(500).get();
    const symbols = [...new Set(snapshot.docs.map(doc => String(doc.data().symbol || '').toUpperCase()).filter(symbol => /^[A-Z0-9]{5,20}USDT$/.test(symbol)))];

    // Daily boundary is midnight in Pakistan Standard Time (UTC+05:00).
    const pkNow = new Date(Date.now() + 5 * 60 * 60 * 1000);
    const startTime = Date.UTC(pkNow.getUTCFullYear(), pkNow.getUTCMonth(), pkNow.getUTCDate()) - 5 * 60 * 60 * 1000;
    let realizedPnl = 0;
    let tradesChecked = 0;

    for (const symbol of symbols) {
      const trades = await binanceRequest('GET', '/api/v3/myTrades', { symbol, limit: 1000 });
      trades.sort((a, b) => Number(a.time) - Number(b.time));
      const lots = [];

      for (const trade of trades) {
        const qty = Number(trade.qty);
        const quote = Number(trade.quoteQty);
        if (!Number.isFinite(qty) || qty <= 0 || !Number.isFinite(quote)) continue;
        const unitCostOrProceeds = quote / qty;

        if (trade.isBuyer) {
          lots.push({ qty, unitCost: unitCostOrProceeds });
          continue;
        }

        let remaining = qty;
        let cost = 0;
        while (remaining > 1e-12 && lots.length) {
          const lot = lots[0];
          const matched = Math.min(remaining, lot.qty);
          cost += matched * lot.unitCost;
          lot.qty -= matched;
          remaining -= matched;
          if (lot.qty <= 1e-12) lots.shift();
        }

        if (Number(trade.time) >= startTime && remaining < qty) {
          const matchedQty = qty - remaining;
          realizedPnl += matchedQty * unitCostOrProceeds - cost;
          if (trade.commissionAsset === 'USDT') realizedPnl -= Number(trade.commission || 0);
          tradesChecked++;
        }
      }
    }

    res.json({
      mode: 'TESTNET ONLY',
      period: 'TODAY_ASIA_KARACHI',
      pnl: Number(realizedPnl.toFixed(8)),
      currency: 'USDT',
      type: 'REALIZED_FIFO',
      trades: tradesChecked
    });
  } catch (error) {
    sendError(res, error);
  }
});



const walletTestLimiter = rateLimit({ windowMs: 60 * 1000, limit: 3, standardHeaders: true, legacyHeaders: false });

// TESTNET DEMO ONLY: credits have no monetary value and never move on a blockchain.
app.post('/api/wallet/testnet/deposit', requireFirebaseAuth, walletTestLimiter, async (req, res) => {
  try {
    const uid = req.user.uid;
    const walletRef = userWallet(uid);
    const now = new Date();
    const dayKey = now.toISOString().slice(0, 10);
    const faucetRef = userWalletTransactions(uid).doc('faucet_' + dayKey);
    const txRef = userWalletTransactions(uid).doc();
    await db.runTransaction(async tx => {
      const [walletSnap, faucetSnap] = await Promise.all([tx.get(walletRef), tx.get(faucetRef)]);
      if (faucetSnap.exists) {
        const error = new Error('Today\'s demo credit has already been claimed. Try again tomorrow.');
        error.status = 429; throw error;
      }
      const wallet = walletSnap.exists ? walletSnap.data() : { balances: {} };
      const balances = { ...(wallet.spotBalances || wallet.balances || {}) };
      balances.USDT = Number((Number(balances.USDT || 0) + 100).toFixed(8));
      tx.set(walletRef, {
        uid, mode: 'TESTNET_SIMULATED', status: 'TESTNET_DEMO_ACTIVE', balances, spotBalances: balances, fundingBalances: wallet.fundingBalances || {},
        createdAt: wallet.createdAt || FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });
      tx.create(faucetRef, { type: 'TESTNET_DEMO_DEPOSIT', asset: 'USDT', amount: 100,
        status: 'COMPLETED_SIMULATED', createdAt: FieldValue.serverTimestamp() });
      tx.create(txRef, { type: 'TESTNET_DEMO_DEPOSIT', asset: 'USDT', amount: 100,
        status: 'COMPLETED_SIMULATED', createdAt: FieldValue.serverTimestamp() });
    });
    res.status(201).json({ ok: true, mode: 'TESTNET_SIMULATED', amount: 100, asset: 'USDT',
      message: '100 demo USDT credited. This is not real money and cannot be transferred to a blockchain.' });
  } catch (error) { sendError(res, error); }
});


const walletTransferLimiter = rateLimit({ windowMs: 60 * 1000, limit: 10, standardHeaders: true, legacyHeaders: false });

app.post('/api/wallet/transfer', requireFirebaseAuth, walletTransferLimiter, async (req, res) => {
  try {
    const asset = String(req.body.asset || 'USDT').toUpperCase();
    const from = String(req.body.from || '').toLowerCase();
    const to = String(req.body.to || '').toLowerCase();
    const amount = Number(req.body.amount);
    if (!/^[A-Z0-9]{2,12}$/.test(asset) || !['spot', 'funding'].includes(from) ||
        !['spot', 'funding'].includes(to) || from === to || !Number.isFinite(amount) || amount <= 0 || amount > 1000000) {
      return res.status(400).json({ error: 'Invalid internal transfer details.' });
    }
    const rounded = Number(amount.toFixed(8));
    if (Math.abs(rounded - amount) > 1e-10) return res.status(400).json({ error: 'Maximum 8 decimal places are allowed.' });
    const uid = req.user.uid;
    const walletRef = userWallet(uid);
    const txRef = userWalletTransactions(uid).doc();
    await db.runTransaction(async tx => {
      const snap = await tx.get(walletRef);
      if (!snap.exists) {
        const error = new Error('Wallet not initialized.'); error.status = 409; throw error;
      }
      const wallet = snap.data();
      const spot = { ...(wallet.spotBalances || wallet.balances || {}) };
      const funding = { ...(wallet.fundingBalances || {}) };
      const source = from === 'spot' ? spot : funding;
      const target = to === 'spot' ? spot : funding;
      const available = Number(source[asset] || 0);
      if (!Number.isFinite(available) || available + 1e-12 < rounded) {
        const error = new Error('Insufficient ' + from + ' wallet ' + asset + ' balance.'); error.status = 400; throw error;
      }
      source[asset] = Number((available - rounded).toFixed(8));
      target[asset] = Number((Number(target[asset] || 0) + rounded).toFixed(8));
      tx.update(walletRef, {
        balances: spot, spotBalances: spot, fundingBalances: funding,
        updatedAt: FieldValue.serverTimestamp()
      });
      tx.create(txRef, {
        type: 'INTERNAL_TRANSFER', asset, amount: rounded, from, to,
        status: 'COMPLETED_INTERNAL', createdAt: FieldValue.serverTimestamp()
      });
    });
    res.status(200).json({ ok: true, asset, amount: rounded, from, to,
      status: 'COMPLETED_INTERNAL', message: 'Internal transfer completed instantly.' });
  } catch (error) { sendError(res, error); }
});

app.post('/api/wallet/testnet/withdraw', requireFirebaseAuth, walletTestLimiter, async (req, res) => {
  try {
    const amount = Number(req.body.amount);
    const asset = String(req.body.asset || 'USDT').toUpperCase();
    const destination = String(req.body.destination || '').trim();
    if (asset !== 'USDT' || !Number.isFinite(amount) || amount <= 0 || amount > 1000 ||
        !/^\\S{8,160}$/.test(destination)) {
      return res.status(400).json({ error: 'Enter a valid demo USDT amount (max 1,000) and destination label/address (8–160 characters).' });
    }
    const rounded = Number(amount.toFixed(8));
    if (Math.abs(rounded - amount) > 1e-10) return res.status(400).json({ error: 'Maximum 8 decimal places are allowed.' });
    const uid = req.user.uid;
    const walletRef = userWallet(uid);
    const txRef = userWalletTransactions(uid).doc();
    await db.runTransaction(async tx => {
      const walletSnap = await tx.get(walletRef);
      if (!walletSnap.exists) {
        const error = new Error('No demo wallet found. Claim the testnet demo credit first.');
        error.status = 409; throw error;
      }
      const wallet = walletSnap.data();
      const balances = { ...(wallet.spotBalances || wallet.balances || {}) };
      const available = Number(balances.USDT || 0);
      if (available < rounded) {
        const error = new Error('Insufficient demo USDT balance.');
        error.status = 400; throw error;
      }
      balances.USDT = Number((available - rounded).toFixed(8));
      tx.update(walletRef, { balances, spotBalances: balances, updatedAt: FieldValue.serverTimestamp() });
      tx.create(txRef, { type: 'TESTNET_DEMO_WITHDRAWAL', asset, amount: rounded,
        destination, status: 'COMPLETED_SIMULATED', createdAt: FieldValue.serverTimestamp() });
    });
    res.status(201).json({ ok: true, mode: 'TESTNET_SIMULATED', amount: rounded, asset,
      status: 'COMPLETED_SIMULATED',
      message: 'Demo withdrawal recorded internally only. No blockchain transfer was made.' });
  } catch (error) { sendError(res, error); }
});

app.get('/api/wallet', requireFirebaseAuth, async (req, res) => {
  try {
    const snapshot = await ensureWallet(req.user.uid);
    const wallet = snapshot.data() || {};
    res.json({
      mode: wallet.mode || 'TESTNET_LEDGER_ONLY',
      status: wallet.status || 'PENDING_CUSTODY_INFRASTRUCTURE',
      balances: wallet.spotBalances || wallet.balances || {},
      spotBalances: wallet.spotBalances || wallet.balances || {},
      fundingBalances: wallet.fundingBalances || {},
      depositsEnabled: true,
      withdrawalsEnabled: true,
      realDepositsEnabled: false,
      realWithdrawalsEnabled: false,
      message: 'Testnet demo only. No real funds or blockchain transfers.'
    });
  } catch (error) { sendError(res, error); }
});

app.get('/api/wallet/transactions', requireFirebaseAuth, async (req, res) => {
  try {
    const snapshot = await userWalletTransactions(req.user.uid).orderBy('createdAt', 'desc').limit(50).get();
    const transactions = snapshot.docs.map(doc => {
      const data = doc.data();
      return { id: doc.id, type: data.type, asset: data.asset, amount: data.amount,
        status: data.status, createdAt: data.createdAt?.toDate?.().toISOString() || null };
    });
    res.json({ mode: 'TESTNET_LEDGER_ONLY', transactions });
  } catch (error) { sendError(res, error); }
});

app.get('/api/account', requireFirebaseAuth, async (_req, res) => {
  try {
    const account = await binanceRequest('GET', '/api/v3/account');
    res.json({
      mode: 'TESTNET ONLY',
      canTrade: account.canTrade,
      balances: (account.balances || []).filter(
        item => Number(item.free) > 0 || Number(item.locked) > 0
      )
    });
  } catch (error) {
    sendError(res, error);
  }
});

app.post('/api/order', requireFirebaseAuth, orderLimiter, async (req, res) => {
  try {
    const symbol = String(req.body.symbol || '').toUpperCase();
    const side = String(req.body.side || '').toUpperCase();
    const type = String(req.body.type || '').toUpperCase();
    let quantity = Number(req.body.quantity);
    const price = Number(req.body.price);

    if (!/^[A-Z0-9]{5,20}$/.test(symbol) || !symbol.endsWith('USDT')) {
      return res.status(400).json({ error: 'Invalid USDT trading pair.' });
    }
    if (!['BUY', 'SELL'].includes(side)) {
      return res.status(400).json({ error: 'Side must be BUY or SELL.' });
    }
    if (!['MARKET', 'LIMIT'].includes(type)) {
      return res.status(400).json({ error: 'Only MARKET and LIMIT orders are supported.' });
    }
    if (!Number.isFinite(quantity) || quantity <= 0) {
      return res.status(400).json({ error: 'Quantity must be greater than zero.' });
    }
    if (type === 'LIMIT' && (!Number.isFinite(price) || price <= 0)) {
      return res.status(400).json({ error: 'A valid limit price is required.' });
    }

    // Match quantity to Binance Testnet LOT_SIZE rules.
    const infoResponse = await fetch(
      `${BASE}/api/v3/exchangeInfo?symbol=${encodeURIComponent(symbol)}`
    );
    const info = await infoResponse.json();
    if (!infoResponse.ok || !info.symbols?.length) {
      return res.status(400).json({ error: 'Trading pair is unavailable on Testnet.' });
    }

    const lot = info.symbols[0].filters.find(f => f.filterType === 'LOT_SIZE');
    if (!lot) {
      return res.status(400).json({ error: 'LOT_SIZE rules unavailable for this pair.' });
    }

    const step = Number(lot.stepSize);
    const precision = (lot.stepSize.split('.')[1] || '').replace(/0+$/, '').length;
    quantity = Number((Math.floor(quantity / step + 1e-9) * step).toFixed(precision));

    if (!Number.isFinite(quantity) || quantity < Number(lot.minQty)) {
      return res.status(400).json({
        error: `Quantity is below this pair's minimum (${lot.minQty}). Increase USDT amount and try again.`
      });
    }

    const params = { symbol, side, type, quantity };
    if (type === 'LIMIT') {
      params.price = price;
      params.timeInForce = 'GTC';
    }

    const result = await binanceRequest('POST', '/api/v3/order', params);
    const docId = orderDocumentId(symbol, result.orderId);

    await userOrders(req.user.uid).doc(docId).set({
      uid: req.user.uid,
      symbol,
      side,
      type,
      orderId: result.orderId,
      clientOrderId: result.clientOrderId || '',
      status: result.status || 'NEW',
      origQty: result.origQty || String(quantity),
      executedQty: result.executedQty || '0',
      price: result.price || String(type === 'LIMIT' ? price : 0),
      cummulativeQuoteQty: result.cummulativeQuoteQty || '0',
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });

    res.status(201).json({ mode: 'TESTNET ONLY', order: result });
  } catch (error) {
    sendError(res, error);
  }
});

app.get('/api/holdings-orders', requireFirebaseAuth, async (req, res) => {
  try {
    const snapshot = await userOrders(req.user.uid)
      .orderBy('createdAt', 'desc')
      .limit(100)
      .get();

    const orders = snapshot.docs.map(doc => {
      const data = doc.data();
      return {
        symbol: data.symbol,
        side: data.side,
        status: data.status,
        executedQty: data.executedQty || '0',
        cummulativeQuoteQty: data.cummulativeQuoteQty || '0',
        createdAt: data.createdAt?.toDate?.().toISOString() || null
      };
    });

    res.json({ mode: 'TESTNET ONLY', orders });
  } catch (error) {
    sendError(res, error);
  }
});

app.get('/api/orders', requireFirebaseAuth, async (req, res) => {
  try {
    const snapshot = await userOrders(req.user.uid)
      .orderBy('createdAt', 'desc')
      .limit(100)
      .get();

    for (const doc of snapshot.docs) {
      const saved = doc.data();
      if (!['NEW', 'PARTIALLY_FILLED'].includes(saved.status)) continue;
      if (!saved.symbol || saved.orderId == null) continue;

      try {
        const live = await binanceRequest('GET', '/api/v3/order', {
          symbol: saved.symbol,
          orderId: saved.orderId
        });

        await doc.ref.update({
          status: live.status || saved.status,
          executedQty: live.executedQty || saved.executedQty || '0',
          cummulativeQuoteQty: live.cummulativeQuoteQty || saved.cummulativeQuoteQty || '0',
          updatedAt: FieldValue.serverTimestamp()
        });
      } catch (syncError) {
        console.warn('Order status sync failed:', saved.symbol, saved.orderId, syncError.message);
      }
    }

    const updated = await userOrders(req.user.uid)
      .orderBy('createdAt', 'desc')
      .limit(100)
      .get();

    const orders = updated.docs.map(doc => ({
      ...doc.data(),
      historyId: doc.id,
      createdAt: doc.data().createdAt?.toDate?.().toISOString() || null,
      updatedAt: doc.data().updatedAt?.toDate?.().toISOString() || null
    }));

    res.json({ mode: 'TESTNET ONLY', orders });
  } catch (error) {
    sendError(res, error);
  }
});

app.delete('/api/order/:symbol/:orderId', requireFirebaseAuth, orderLimiter, async (req, res) => {
  try {
    const symbol = String(req.params.symbol || '').toUpperCase();
    const orderId = String(req.params.orderId || '');

    if (!/^[A-Z0-9]{5,20}$/.test(symbol) || !symbol.endsWith('USDT') ||
        !/^\d+$/.test(orderId)) {
      return res.status(400).json({ error: 'Invalid pair or order ID.' });
    }

    const ref = userOrders(req.user.uid).doc(orderDocumentId(symbol, orderId));
    const saved = await ref.get();

    if (!saved.exists) {
      return res.status(404).json({ error: 'Order not found in your account history.' });
    }

    const current = saved.data();
    if (!['NEW', 'PARTIALLY_FILLED'].includes(current.status)) {
      return res.status(409).json({ error: `Order cannot be cancelled: ${current.status}` });
    }

    const result = await binanceRequest('DELETE', '/api/v3/order', {
      symbol,
      orderId
    });

    await ref.update({
      status: result.status || 'CANCELED',
      executedQty: result.executedQty || current.executedQty || '0',
      cummulativeQuoteQty: result.cummulativeQuoteQty || current.cummulativeQuoteQty || '0',
      updatedAt: FieldValue.serverTimestamp()
    });

    res.json({ mode: 'TESTNET ONLY', order: result });
  } catch (error) {
    sendError(res, error);
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`APEX Testnet backend listening on port ${PORT}`);
});
