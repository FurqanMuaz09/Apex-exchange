require("dotenv").config({ path: require("path").join(__dirname, ".env") });
const { initializeApp, cert } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const serviceAccount = process.env.FIREBASE_SERVICE_ACCOUNT_JSON
  ? JSON.parse(Buffer.from(process.env.FIREBASE_SERVICE_ACCOUNT_JSON.trim(), "base64url").toString("utf8"))
  : require("./secrets/serviceAccountKey.json");

initializeApp({ credential: cert(serviceAccount) });

const db = getFirestore();
const express = require("express");
const cors = require("cors");
const rateLimit = require("express-rate-limit");
const crypto = require("crypto");

const app = express();
app.use(cors());

app.use(express.json({ limit: "16kb" }));

const orderLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 10,
  standardHeaders: "draft-7",
  legacyHeaders: false
});


const PORT = process.env.PORT || 3000;
const KEY = process.env.BINANCE_TESTNET_API_KEY;
const SECRET = process.env.BINANCE_TESTNET_API_SECRET;
const BASE = "https://testnet.binance.vision";

app.get("/", (_req, res) => {
  res.json({
    service: "APEX Binance Testnet Backend",
    mode: "TESTNET ONLY",
    configured: Boolean(KEY && SECRET)
  });
});

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

async function signedRequest(method, path, params = {}) {
  if (!KEY || !SECRET) {
    throw new Error("Binance Testnet API keys are not configured.");
  }

  const values = {
    ...params,
    timestamp: Date.now(),
    recvWindow: 5000
  };

  const query = new URLSearchParams(values).toString();
  const signature = crypto
    .createHmac("sha256", SECRET)
    .update(query)
    .digest("hex");

  const response = await fetch(
    `${BASE}${path}?${query}&signature=${signature}`,
    {
      method,
      headers: { "X-MBX-APIKEY": KEY }
    }
  );

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.msg || "Binance Testnet request failed.");
  }

  return data;
}

app.get("/api/account", requireFirebaseAuth, async (_req, res) => {
  try {
    const account = await signedRequest("GET", "/api/v3/account");

    res.json({
      mode: "TESTNET ONLY",
      canTrade: account.canTrade,
      balances: account.balances.filter(
        x => Number(x.free) > 0 || Number(x.locked) > 0
      )
    });
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
});


async function requireFirebaseAuth(req, res, next) {
  if (!require("firebase-admin/app").getApps().length) {
    return res.status(503).json({ error: "Firebase Admin is not configured" });
  }
  const header = req.headers.authorization || "";
  if (!header.startsWith("Bearer ")) {
    return res.status(401).json({ error: "Login required" });
  }
  try {
    req.user = await getAuth().verifyIdToken(header.slice(7));
    next();
  } catch {
    return res.status(401).json({ error: "Invalid or expired login token" });
  }
}


app.get("/api/profile", requireFirebaseAuth, async (req, res) => {
  try {
    const ref = db.collection("users").doc(req.user.uid);
    const snap = await ref.get();
    res.json({ profile: snap.exists ? snap.data() : null });
  } catch (error) {
    console.error("Profile read failed:", error.message);
    res.status(500).json({ error: "Could not load profile" });
  }
});

app.put("/api/profile", requireFirebaseAuth, async (req, res) => {
  try {
    const body = req.body || {};
    const profile = {};

    for (const key of ["displayName", "phone", "country"]) {
      if (typeof body[key] === "string") {
        profile[key] = body[key].trim().slice(0, 120);
      }
    }

    profile.updatedAt = FieldValue.serverTimestamp();

    await db.collection("users").doc(req.user.uid).set(profile, { merge: true });
    res.json({ ok: true, message: "Profile saved" });
  } catch (error) {
    console.error("Profile save failed:", error.message);
    res.status(500).json({ error: "Could not save profile" });
  }
});

app.get("/api/orders", requireFirebaseAuth, async (req, res) => {
  try {
    const symbol = String(req.query.symbol || "BTCUSDT").toUpperCase();
    if (!/^[A-Z0-9]{5,20}$/.test(symbol)) {
      return res.status(400).json({ error: "Invalid symbol." });
    }
    const orders = await signedRequest("GET", "/api/v3/allOrders", {
      symbol,
      limit: 50
    });
    res.json({ mode: "TESTNET ONLY", symbol, orders });
  } catch (error) {
    res.status(500).json({ error: error.message || "Could not load order history." });
  }
});

app.post("/api/order", orderLimiter, requireFirebaseAuth, async (req, res) => {
  try {
    const { symbol, side, type, quantity, price } = req.body || {};

    const s = String(symbol || "").toUpperCase();
    const sd = String(side || "").toUpperCase();
    const t = String(type || "").toUpperCase();

    if (!/^[A-Z0-9]{3,20}$/.test(s)) {
      return res.status(400).json({ error: "Invalid symbol" });
    }

    if (!["BUY", "SELL"].includes(sd)) {
      return res.status(400).json({ error: "Invalid side" });
    }

    if (!["MARKET", "LIMIT"].includes(t)) {
      return res.status(400).json({ error: "Invalid order type" });
    }

    if (
      !/^\d+(\.\d+)?$/.test(String(quantity ?? "")) ||
      !Number.isFinite(Number(quantity)) ||
      Number(quantity) <= 0
    ) {
      return res.status(400).json({ error: "Invalid quantity" });
    }

    const params = {
      symbol: s,
      side: sd,
      type: t,
      quantity: String(quantity)
    };

    if (t === "LIMIT") {
      if (
        !/^\d+(\.\d+)?$/.test(String(price ?? "")) ||
        !Number.isFinite(Number(price)) ||
        Number(price) <= 0
      ) {
        return res.status(400).json({
          error: "Limit order needs a valid price"
        });
      }

      params.price = String(price);
      params.timeInForce = "GTC";
    }

    const result = await signedRequest("POST", "/api/v3/order", params);

    let fee = null;
    if (t === "MARKET" && result.status === "FILLED") {
      const executedValue = Number(result.cummulativeQuoteQty || 0);
      if (executedValue > 0) {
        fee = Number((executedValue * 0.005).toFixed(8));
        await db.collection("apex_fees").add({
          uid: req.user.uid,
          symbol: s,
          side: sd,
          orderId: String(result.orderId),
          executedValue,
          feeRate: 0.005,
          feeAmount: fee,
          asset: s.endsWith("USDT") ? "USDT" : "QUOTE_ASSET",
          mode: "TESTNET ONLY",
          createdAt: FieldValue.serverTimestamp()
        });
      }
    }

    res.json({ mode: "TESTNET ONLY", order: result, fee });
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
});

app.listen(PORT, () => {
  console.log(`APEX Testnet backend listening on ${PORT}`);
});
