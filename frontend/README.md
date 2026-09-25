# Drop in Chess Frontend

React + TypeScript frontend for Drop in Chess.

## Requirements

- Node.js
- npm

## To Run

From the `frontend` directory:

```bash
npm install
npm run dev
```

## To Test
From the `frontend` directory:

```bash
npm test
```

## Backend startup feedback

App gates route entry with BackendHealthStatus. It calls GET /health, expects {"status":"CONNECTED"}, displays connecting feedback immediately and possible wake-up feedback after 3 seconds. Requests time out at 10 seconds and safe health GETs retry after 2 seconds. At 90 seconds the user gets a Try again action. Cleanup cancels timers/requests. Game creation and move POSTs are not automatically replayed. Checks occur on initial load and route changes; this is not an ongoing availability monitor for a page left open.

Set VITE_API_BASE_URL to the HTTPS Render backend origin in Vercel's environment settings and rebuild. All frontend API calls use this value, defaulting to http://localhost:8080 locally. See .env.example. The backend's current CORS annotations still allow localhost:5173 only: configure the actual Vercel origin before deployment. Backend sessions currently live in memory and may disappear on service restart; stored JSON starting positions are packaged resources and remain available.
