# AITripPlan Web

Desktop browser application only. The three-column workspace supports resizing and collapsing panels. Minimum layout width is 960px; narrower windows scroll horizontally rather than switching to a mobile interface. Native apps, mini programs and mobile-specific interactions are out of scope.

## Development

```powershell
npm install
npm run dev
```

The Vite server proxies `/trip` requests to `http://localhost:8081` by default. Set `VITE_API_TARGET` to override it.

## AMap

Configure `AMAP_WEB_JS_KEY` in the project-root `.env` (or `VITE_AMAP_KEY` for the frontend). Set `AMAP_SECURITY_JS_CODE` on the backend; the browser accesses the `/_AMapService` proxy rather than receiving the security code. The backend MCP uses a separate Web service key, `AMAP_MAPS_API_KEY`. Without a JS key, the page uses an interactive route preview.

## Production build

```powershell
npm run build
```

Vite writes the deployable page and hashed assets to `manager_agent/src/main/resources/static` for Spring Boot to serve.

Generated bundles are not committed because they can include environment-specific public JS keys. Run the build before packaging the backend; `.env`, private keys and logs must never be committed.
