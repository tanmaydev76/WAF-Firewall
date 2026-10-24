const express = require("express");
const cors = require("cors");
const crypto = require("crypto");
const products = require("./products.json");

const app = express();
const PORT = process.env.PORT || 8080;
// Demo data lives in memory and resets when the server restarts.
const carts = new Map();
const orders = new Map();
const payments = new Map();

app.use(cors());
app.use(express.json({ limit: "1mb" }));

// Log every API request and its response status in the terminal.
app.use("/api", (req, res, next) => {
  const started = Date.now();
  const safeBody = { ...req.body };
  if (safeBody.password) safeBody.password = "[REDACTED]";
  if (safeBody.cardNumber) safeBody.cardNumber = "[REDACTED]";
  if (safeBody.cvv) safeBody.cvv = "[REDACTED]";
  console.log(`\n[${new Date().toISOString()}] --> ${req.method} ${req.originalUrl}`);
  if (Object.keys(safeBody).length) console.log("Request body:", JSON.stringify(safeBody, null, 2));
  res.on("finish", () => console.log(`<-- ${req.method} ${req.originalUrl} ${res.statusCode} (${Date.now()-started}ms)`));
  next();
});

const cleanUser = (value) => String(value || "").trim().slice(0, 80);
const getCart = (username) => carts.get(username) || [];
const cartView = (username) => {
  const items = getCart(username).map(line => {
    const product = products.find(p => p.id === Number(line.productId));
    return product ? { ...product, qty: line.quantity, lineTotal: product.price * line.quantity } : null;
  }).filter(Boolean);
  return { items, itemCount: items.reduce((n, i) => n + i.qty, 0), subtotal: items.reduce((n, i) => n + i.lineTotal, 0), delivery: 0, total: items.reduce((n, i) => n + i.lineTotal, 0) };
};

app.get("/api/health", (req, res) => res.json({ success: true, message: "ShopNest backend is running", timestamp: new Date().toISOString() }));

app.post("/api/login", (req, res) => {
  const { username, password } = req.body || {};
  if (username === "user123" && password === "123") {
    return res.json({ success: true, message: "Login successful", user: { username, displayName: username } });
  }
  return res.status(401).json({ success: false, message: "Invalid username or password. Use user123 / 123 for the demo." });
});

app.get("/api/profile", (req, res) => {
  const username = cleanUser(req.query.username);
  if (!username) return res.status(400).json({ success: false, message: "Username is required" });
  res.json({ success: true, user: { username, displayName: username }, message: "Profile loaded from server" });
});

app.get("/api/products", (req, res) => {
  const q = String(req.query.q || "").toLowerCase().trim();
  const cat = String(req.query.cat || "").toLowerCase().trim();
  const result = products.filter(p => (!q || `${p.name} ${p.category} ${p.badge}`.toLowerCase().includes(q)) && (!cat || cat === "all" || p.category.toLowerCase() === cat));
  res.json({ success: true, products: result, count: result.length, filters: { q, category: cat || "All" } });
});

app.get("/api/products/:id", (req, res) => {
  const product = products.find(p => p.id === Number(req.params.id));
  if (!product) return res.status(404).json({ success: false, message: "Product not found" });
  res.json({ success: true, product });
});

app.get("/api/cart", (req, res) => {
  const username = cleanUser(req.query.username);
  if (!username) return res.status(400).json({ success: false, message: "Username is required" });
  res.json({ success: true, cart: cartView(username) });
});

app.post("/api/cart/items", (req, res) => {
  const username = cleanUser(req.body.username);
  const productId = Number(req.body.productId);
  const quantity = Number(req.body.quantity || 1);
  if (!username || !Number.isInteger(quantity) || quantity < 1 || quantity > 99) return res.status(400).json({ success: false, message: "Valid username and quantity are required" });
  if (!products.some(p => p.id === productId)) return res.status(404).json({ success: false, message: "Product not found" });
  const cart = [...getCart(username)];
  const line = cart.find(x => x.productId === productId);
  if (line) line.quantity = Math.min(99, line.quantity + quantity); else cart.push({ productId, quantity });
  carts.set(username, cart);
  res.json({ success: true, message: "Item added to cart", cart: cartView(username) });
});

app.patch("/api/cart/items/:id", (req, res) => {
  const username = cleanUser(req.body.username);
  const productId = Number(req.params.id);
  const quantity = Number(req.body.quantity);
  if (!username || !Number.isInteger(quantity) || quantity < 0 || quantity > 99) return res.status(400).json({ success: false, message: "Quantity must be between 0 and 99" });
  let cart = [...getCart(username)];
  if (quantity === 0) cart = cart.filter(x => x.productId !== productId);
  else if (cart.some(x => x.productId === productId)) cart = cart.map(x => x.productId === productId ? { ...x, quantity } : x);
  else return res.status(404).json({ success: false, message: "Cart item not found" });
  carts.set(username, cart);
  res.json({ success: true, message: quantity === 0 ? "Item removed from cart" : "Cart quantity updated", cart: cartView(username) });
});

app.delete("/api/cart", (req, res) => {
  const username = cleanUser(req.query.username || req.body.username);
  if (!username) return res.status(400).json({ success: false, message: "Username is required" });
  carts.set(username, []);
  res.json({ success: true, message: "Cart cleared", cart: cartView(username) });
});

app.post("/api/orders", (req, res) => {
  const { username, items, address, paymentMethod } = req.body || {};
  const user = cleanUser(username);
  if (!user) return res.status(400).json({ success: false, message: "Please sign in before ordering" });
  if (!address || !address.firstName || !address.lastName || !address.street || !address.city || !/^\d{6}$/.test(String(address.pin || ""))) return res.status(400).json({ success: false, message: "Complete all delivery details with a valid 6-digit PIN code" });
  const cart = getCart(user);
  if (!cart.length) return res.status(400).json({ success: false, message: "Your cart is empty" });
  // Ignore any client-supplied prices or totals; compute them from server catalogue.
  const summary = cartView(user);
  const id = `SN-${Date.now().toString().slice(-7)}-${crypto.randomBytes(2).toString("hex").toUpperCase()}`;
  const method = ["UPI", "Card", "Cash on Delivery"].includes(paymentMethod) ? paymentMethod : "UPI";
  const order = { id, username: user, items: summary.items, address, subtotal: summary.subtotal, delivery: 0, total: summary.total, paymentMethod: method, paymentStatus: method === "Cash on Delivery" ? "PAY_ON_DELIVERY" : "PENDING", status: "Created", createdAt: new Date().toISOString() };
  orders.set(id, order);
  res.status(201).json({ success: true, message: "Order created. Continue to demo payment.", order });
});

app.post("/api/payments/demo", (req, res) => {
  const { orderId, username, outcome } = req.body || {};
  const order = orders.get(String(orderId || ""));
  if (!order || order.username !== cleanUser(username)) return res.status(404).json({ success: false, message: "Order not found for this user" });
  if (order.paymentMethod === "Cash on Delivery") {
    order.paymentStatus = "PAY_ON_DELIVERY";
    order.status = "Confirmed";
    payments.set(order.id, { id: `PAY-${Date.now()}`, orderId: order.id, method: order.paymentMethod, status: "PAY_ON_DELIVERY", createdAt: new Date().toISOString() });
    carts.set(order.username, []);
    return res.json({ success: true, message: "Order confirmed. Pay cash when it is delivered.", payment: payments.get(order.id), order });
  }
  // The UI can deliberately simulate success or failure; no real payment is processed.
  if (outcome === "fail") {
    order.paymentStatus = "FAILED";
    order.status = "Payment failed";
    const payment = { id: `PAY-${Date.now()}`, orderId: order.id, method: order.paymentMethod, status: "FAILED", amount: order.total, createdAt: new Date().toISOString() };
    payments.set(order.id, payment);
    return res.status(402).json({ success: false, message: "Demo payment declined. You can retry checkout.", payment, order });
  }
  order.paymentStatus = "SUCCESS";
  order.status = "Confirmed";
  const payment = { id: `PAY-${Date.now()}`, orderId: order.id, method: order.paymentMethod, status: "SUCCESS", amount: order.total, createdAt: new Date().toISOString(), demo: true };
  payments.set(order.id, payment);
  carts.set(order.username, []);
  res.json({ success: true, message: "Demo payment successful! No real money was charged.", payment, order });
});

app.get("/api/orders", (req, res) => {
  const username = cleanUser(req.query.username);
  if (!username) return res.status(400).json({ success: false, message: "Username is required" });
  res.json({ success: true, orders: [...orders.values()].filter(o => o.username === username).reverse() });
});

app.get("/api/orders/:id", (req, res) => {
  const order = orders.get(req.params.id);
  if (!order || order.username !== cleanUser(req.query.username)) return res.status(404).json({ success: false, message: "Order not found" });
  res.json({ success: true, order, payment: payments.get(order.id) || null });
});

app.listen(PORT, () => console.log(`\nShopNest backend running at http://localhost:${PORT}\nDemo login: user123 / 123\nTerminal request logging is enabled.\n`));
