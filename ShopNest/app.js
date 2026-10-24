const API_BASE_URL = "http://localhost:8080";
const money = n => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(n);
const getUser = () => localStorage.getItem("shopnest_user");
async function api(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers: { "Content-Type": "application/json", ...(options.headers || {}) } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.success === false) throw new Error(data.message || `Request failed (${response.status})`);
  return data;
}
const jsonBody = body => ({ method: "POST", body: JSON.stringify(body) });
function requireLogin() { if (!getUser()) { location.href = "login.html"; return false; } return true; }
function toast(message) { let el = document.createElement("div"); el.className = "toast"; el.textContent = message; document.body.appendChild(el); setTimeout(() => el.remove(), 2200); }
function updateCount(n) { document.querySelectorAll(".cart-count").forEach(el => el.textContent = n ?? 0); }
async function refreshCartCount() { if (!getUser()) return updateCount(0); try { const d = await api(`/api/cart?username=${encodeURIComponent(getUser())}`); updateCount(d.cart.itemCount); } catch (e) { console.error(e); } }
async function addCart(id) { try { const d = await api("/api/cart/items", jsonBody({ username: getUser(), productId: Number(id), quantity: 1 })); updateCount(d.cart.itemCount); toast("Added to cart ✓ (server confirmed)"); } catch(e) { toast(e.message); } }
function productCard(p) { return `<article class="card"><a href="product.html?id=${p.id}" class="pic"><img src="${p.image}" alt="${p.name}" loading="lazy"><span class="badge">${p.badge || p.category}</span></a><div class="body"><div class="cat">${p.category}</div><h3><a href="product.html?id=${p.id}">${p.name}</a></h3><div class="price-row"><span class="price">${money(p.price)}</span><del class="old">${money(p.old || p.price)}</del></div><button class="add" data-add="${p.id}">Add to cart</button></div></article>`; }
async function loadProducts() {
  const grid = document.getElementById("grid"); if (!grid) return;
  grid.innerHTML = '<p class="muted">Loading products from server…</p>';
  const params = new URLSearchParams(location.search);
  try {
    const d = await api(`/api/products?${new URLSearchParams({ q: params.get("q") || "", cat: params.get("cat") || "" })}`);
    grid.innerHTML = d.products.length ? d.products.map(productCard).join("") : '<div class="empty">No products match your search.</div>';
    const result = document.getElementById("result"); if (result) result.textContent = `${d.count} products loaded from server`;
  } catch(e) { grid.innerHTML = `<p class="message">${e.message}. Check that the backend is running.</p>`; }
  grid.onclick = e => { const btn = e.target.closest("[data-add]"); if (btn) addCart(btn.dataset.add); };
}
async function loadProductDetail() {
  const root = document.getElementById("detail"); if (!root) return;
  const id = new URLSearchParams(location.search).get("id") || "1";
  root.innerHTML = '<p class="muted">Loading product from server…</p>';
  try { const d = await api(`/api/products/${encodeURIComponent(id)}`); const p = d.product;
    root.innerHTML = `<section class="product-detail"><div class="detail-image"><img src="${p.image}" alt="${p.name}"></div><div class="detail-copy"><span class="tag">${p.badge || p.category}</span><h1>${p.name}</h1><p class="muted">Category: ${p.category}</p><div class="price">${money(p.price)} <del>${money(p.old || p.price)}</del></div><p>Quality essentials, selected for your everyday setup. Product information and price were loaded from the ShopNest server.</p><button class="primary" id="detailAdd">Add to cart</button> <a class="chip" href="products.html">Continue shopping</a><p class="muted" id="detailMsg"></p></div></section>`;
    document.getElementById("detailAdd").onclick = async () => { await addCart(p.id); document.getElementById("detailMsg").textContent = "Cart action sent to server."; };
  } catch(e) { root.innerHTML = `<p class="message">${e.message}</p>`; }
}
async function loadCart() {
  const list = document.getElementById("cartList"); if (!list) return;
  list.innerHTML = '<p class="muted">Loading your cart from server…</p>';
  async function render() {
    try {
      const d = await api(`/api/cart?username=${encodeURIComponent(getUser())}`); const c = d.cart; updateCount(c.itemCount);
      document.getElementById("sub").textContent = money(c.subtotal); document.getElementById("total").textContent = money(c.total);
      if (!c.items.length) { list.innerHTML = '<div class="empty">Your cart is empty.<br><br><a class="primary" href="products.html">Continue shopping</a></div>'; return; }
      list.innerHTML = c.items.map(i => `<div class="cart-row"><img src="${i.image}" alt="${i.name}"><div><h3>${i.name}</h3><div class="muted">${money(i.price)} each</div><div class="qty"><button data-qty="${i.id}" data-value="${i.qty-1}">−</button><b>${i.qty}</b><button data-qty="${i.id}" data-value="${i.qty+1}">+</button></div><button class="remove" data-qty="${i.id}" data-value="0">Remove</button></div><strong>${money(i.lineTotal)}</strong></div>`).join("");
    } catch(e) { list.innerHTML = `<p class="message">${e.message}</p>`; }
  }
  list.onclick = async e => { const b = e.target.closest("[data-qty]"); if (!b) return; b.disabled = true; try { await api(`/api/cart/items/${b.dataset.qty}`, { method: "PATCH", body: JSON.stringify({ username: getUser(), quantity: Number(b.dataset.value) }) }); await render(); } catch(err) { toast(err.message); b.disabled = false; } };
  await render();
}
async function loadCheckout() {
  const form = document.getElementById("checkoutForm"); if (!form) return;
  const msg = document.getElementById("orderMsg"); const totalEl = document.getElementById("orderTotal"); const paymentMethod = document.getElementById("paymentMethod");
  const paymentFields = document.getElementById("paymentFields"); const outcomeField = document.getElementById("demoOutcome");
  function syncPaymentFields() { if (paymentFields) paymentFields.hidden = !["UPI", "Card"].includes(paymentMethod.value); if (outcomeField) outcomeField.hidden = paymentMethod.value === "Cash on Delivery"; }
  paymentMethod?.addEventListener("change", syncPaymentFields); syncPaymentFields();
  try { const d = await api(`/api/cart?username=${encodeURIComponent(getUser())}`); totalEl.textContent = money(d.cart.total); if (!d.cart.items.length) { msg.textContent = "Your cart is empty. Add a product before checkout."; msg.style.color = "#d9363e"; form.querySelector("button[type=submit]").disabled = true; } } catch(e) { msg.textContent = e.message; msg.style.color = "#d9363e"; }
  form.onsubmit = async e => {
    e.preventDefault(); const btn = form.querySelector("button[type=submit]"); btn.disabled = true; btn.textContent = "Sending order to server…"; msg.textContent = "Creating order on server…"; msg.style.color = "#17212b";
    const fields = [...form.querySelectorAll("[data-field]")]; const value = name => form.querySelector(`[data-field="${name}"]`).value.trim();
    const method = paymentMethod.value;
    try {
      const orderData = await api("/api/orders", jsonBody({ username: getUser(), address: { firstName: value("firstName"), lastName: value("lastName"), street: value("street"), city: value("city"), pin: value("pin") }, paymentMethod: method }));
      msg.textContent = "Order created. Sending demo payment request…";
      const payData = await api("/api/payments/demo", jsonBody({ orderId: orderData.order.id, username: getUser(), outcome: method === "Cash on Delivery" ? "success" : document.getElementById("paymentOutcome").value }));
      msg.textContent = `${payData.message} Order: ${orderData.order.id}`; msg.style.color = "#0b8f62";
      totalEl.textContent = money(payData.order.total); btn.textContent = "Order confirmed ✓";
      setTimeout(() => location.href = "home.html", 2600);
    } catch(err) { msg.textContent = err.message; msg.style.color = "#d9363e"; btn.disabled = false; btn.textContent = "Place order & run demo payment"; }
  };
}
async function setupCommon() {
  if (!requireLogin()) return;
  document.querySelectorAll(".userName").forEach(el => el.textContent = getUser());
  document.getElementById("logout")?.addEventListener("click", async () => { try { await api(`/api/profile?username=${encodeURIComponent(getUser())}`); } catch(e) {} localStorage.removeItem("shopnest_user"); location.href = "login.html"; });
  const q = document.getElementById("searchInput"), b = document.getElementById("searchBtn");
  function go() { const text = q?.value.trim() || "", cat = document.getElementById("searchCat")?.value || "All", params = new URLSearchParams(); if (text) params.set("q", text); if (cat !== "All") params.set("cat", cat); location.href = `products.html${params.size ? `?${params}` : ""}`; }
  b?.addEventListener("click", go); q?.addEventListener("keydown", e => { if (e.key === "Enter") go(); });
  try { const d = await api(`/api/profile?username=${encodeURIComponent(getUser())}`); document.querySelectorAll(".userName").forEach(el => el.textContent = d.user.displayName); } catch(e) { console.error("Profile request failed", e); }
  await refreshCartCount();
}
document.addEventListener("DOMContentLoaded", async () => {
  if (document.body.classList.contains("auth-page")) return;
  if (!requireLogin()) return;
  await setupCommon(); await loadProducts(); await loadProductDetail(); await loadCart(); await loadCheckout(); await loadOrders();
});

async function loadOrders() {
  const root = document.getElementById("ordersList"); if (!root) return;
  root.innerHTML = '<p class="muted">Loading order history from server…</p>';
  try {
    const d = await api(`/api/orders?username=${encodeURIComponent(getUser())}`);
    if (!d.orders.length) { root.innerHTML = '<div class="empty">No orders yet. <a href="products.html">Start shopping</a></div>'; return; }
    root.innerHTML = d.orders.map(o => `<article class="panel order-card"><div class="order-head"><div><span class="eyebrow">ORDER ${o.id}</span><h3>${new Date(o.createdAt).toLocaleString()}</h3></div><span class="tag">${o.status}</span></div><div class="muted">${o.items.map(i => `${i.name} × ${i.qty}`).join(" · ")}</div><div class="sum total"><span>Total · ${o.paymentMethod}</span><strong>${money(o.total)}</strong></div><div class="muted">Payment status: ${o.paymentStatus}</div><button class="chip" data-order="${o.id}">Refresh order status</button><p class="message" id="status-${o.id}"></p></article>`).join("");
    root.onclick = async e => { const btn = e.target.closest("[data-order]"); if (!btn) return; try { const status = await api(`/api/orders/${encodeURIComponent(btn.dataset.order)}?username=${encodeURIComponent(getUser())}`); document.getElementById(`status-${btn.dataset.order}`).textContent = `${status.order.status} · ${status.order.paymentStatus}${status.payment ? ` · Payment ${status.payment.id}` : ""}`; } catch(err) { toast(err.message); } };
  } catch(e) { root.innerHTML = `<p class="message">${e.message}</p>`; }
}
