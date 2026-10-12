import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/* 开发/预览代理：/admin/** → 后端 8080（避免浏览器跨域，管理端不改后端 CORS 配置） */
const proxy = {
  '/admin': { target: 'http://localhost:8080', changeOrigin: true }
}

export default defineConfig({
  plugins: [vue()],
  server: { port: 5174, proxy },
  preview: { port: 5174, proxy }
})