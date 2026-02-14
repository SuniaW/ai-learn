import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueJsx(), vueDevTools()],
  server: {
    port: 5173, // 可选：固定端口
    proxy: {
      // 所有以 /ai 开头的请求都代理到 Spring Boot 后端
      '/ai': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 如果后端 context path 不是根路径，可加 rewrite（此处不需要）
        // rewrite: (path) => path.replace(/^\/ai/, '/ai')
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
