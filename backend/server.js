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

const PORT = process.env.PORT || 3000;
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
