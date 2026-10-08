import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const envDir = fileURLToPath(new URL('../..', import.meta.url))
  const env = loadEnv(mode, envDir, '')
  return {
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    define: {
      __AMAP_KEY__: JSON.stringify(env.AMAP_WEB_JS_KEY ?? env.VITE_AMAP_KEY ?? ''),
    },
    server: {
      port: 5173,
      proxy: {
        '/trip': { target: env.VITE_API_TARGET || 'http://localhost:8081', changeOrigin: true },
        '/_AMapService': { target: env.VITE_API_TARGET || 'http://localhost:8081', changeOrigin: true },
      },
    },
    build: {
      outDir: '../src/main/resources/static',
      emptyOutDir: true,
      assetsDir: 'assets',
    },
  }
})
