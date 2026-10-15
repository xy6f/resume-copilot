import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前后端分离的唯一耦合点：/api 代理指向后端 8083（见 application.yml server.port）
// 代码里一律使用相对路径 /api/...，生产构建同构、开发期免 CORS。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5175,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        // SSE（GET /api/match/{id}/advice）走 http-proxy 默认直通，不缓冲、不改写
      },
    },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200, // element-plus 全量引入，阈值放宽
  },
})
