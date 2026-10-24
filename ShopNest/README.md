# ShopNest — Server-connected e-commerce demo

## Run it
1. Open a terminal in the `backend` folder.
2. Run `npm install` once if dependencies are not installed.
3. Run `npm run dev` (or `npm start`). The server listens on `http://localhost:8080`.
4. In VS Code, open the project folder and use Live Server to open `login.html` (or serve the folder on any local HTTP server).
5. Sign in with **user123 / 123**.

## What is connected
- Login (`POST /api/login`)
- Profile (`GET /api/profile`)
- Product catalogue, category filters and search (`GET /api/products`, `GET /api/products/:id`)
- Server-side cart (`GET /api/cart`, `POST /api/cart/items`, `PATCH /api/cart/items/:id`, `DELETE /api/cart`)
- Order creation with server-calculated prices (`POST /api/orders`)
- Demo payment simulation (`POST /api/payments/demo`)
- Order history UI at `orders.html` (`GET /api/orders`, `GET /api/orders/:id`)
- Request/response logging in the backend terminal, with passwords/card fields redacted.

## Demo payment
Choose UPI or Card and select a successful or declined test outcome, or choose Cash on Delivery. No real payment gateway is used and **no real money is charged**. Never enter real card numbers, CVV, or bank credentials. Orders, carts, and payments are in-memory demo data and reset when the server restarts.

## Notes
- API base URL is defined at the top of `app.js` and in `login.html`.
- The demo login is intentionally hard-coded and is not production authentication.
- The frontend stores only the demo username in localStorage; the cart is stored on the server for the current server process.
