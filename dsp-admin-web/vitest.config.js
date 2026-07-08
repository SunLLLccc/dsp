import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    // askStream 测试需要 fetch / ReadableStream / localStorage / window
    environment: 'jsdom',
    globals: true
  }
})
