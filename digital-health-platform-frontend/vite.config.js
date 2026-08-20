import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// dev: 把 /api 代理到后端 Spring Boot(8080);build: 由 nginx 反代
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 【新增】允许所有主机访问（解决 cpolar 穿透被拦截的问题）
    allowedHosts: 'all', 
    proxy: {
      '/api': { 
        target: 'http://localhost:8080', 
        changeOrigin: true 
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true
  }
})