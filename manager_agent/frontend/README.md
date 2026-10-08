# AITripPlan Web

## Development

```powershell
npm install
npm run dev
```

The Vite server proxies `/trip` requests to `http://localhost:8081` by default. Set `VITE_API_TARGET` to override it.

## AMap

Copy `.env.example` to `.env.local` and set `VITE_AMAP_KEY` and `VITE_AMAP_SECURITY_CODE`. Without a key, the page uses the interactive route preview with the bundled sample coordinates.

## Production build

```powershell
npm run build
```

Vite writes the deployable page and hashed assets to `manager_agent/src/main/resources/static` for Spring Boot to serve.
