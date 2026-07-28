import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// 仅供测试使用的独立配置，不影响 vite.config.js 的生产构建：
// 1. define 强制以非生产模式加载 Vue，确保 @vue/test-utils 的 emitted() 事件捕获
//    （依赖 Vue 开发模式下的 devtools 钩子）在任何宿主 NODE_ENV 下都能正常工作；
// 2. server.fs.allow 放开仓库根目录，使部分用例可通过 ?raw 读取 frontend 之外的 agent.md 做静态校验。
const repoRoot = fileURLToPath(new URL('..', import.meta.url))

export default defineConfig({
  plugins: [vue()],
  define: { 'process.env.NODE_ENV': JSON.stringify('test') },
  test: {
    environment: 'jsdom',
    server: { deps: { inline: ['@vue/test-utils'] } }
  },
  server: { fs: { allow: [repoRoot] } }
})
