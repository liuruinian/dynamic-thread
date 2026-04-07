import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 所有 API 代理到 Server
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/dingtalk': {
        target: 'https://oapi.dingtalk.com',
        changeOrigin: true,
        secure: true,
        rewrite: (path) => path.replace(/^\/dingtalk/, '')
      }
    }
  },
  build: {
    // Output to server module's static directory
    outDir: '../dynamic-thread-server/src/main/resources/static',
    assetsDir: 'assets',
    emptyOutDir: true
  }
})
