<template>
  <svg
    class="module-icon"
    :data-module-icon="name"
    viewBox="0 0 48 48"
    role="img"
    :aria-label="label || name"
    focusable="false"
  >
    <path v-for="(d, index) in shape.paths || []" :key="`p${index}`" :d="d" />
    <circle v-for="(circle, index) in shape.circles || []" :key="`c${index}`" :cx="circle[0]" :cy="circle[1]" :r="circle[2]" />
    <ellipse v-for="(ellipse, index) in shape.ellipses || []" :key="`e${index}`" :cx="ellipse[0]" :cy="ellipse[1]" :rx="ellipse[2]" :ry="ellipse[3]" />
    <rect v-for="(rect, index) in shape.rects || []" :key="`r${index}`" :x="rect[0]" :y="rect[1]" :width="rect[2]" :height="rect[3]" :rx="rect[4]" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

// 首页八个模块的线性图标几何定义：均绘制在 48×48 画布内，靠 viewBox 等比缩放
const MODULE_ICON_SHAPES = {
  // 软件下载：向下的下载箭头 + 承载托盘
  downloads: { paths: ['M24 8v22', 'M15 22l9 9 9-9', 'M12 36h24', 'M15 40h18'] },
  // 标准发布：带折角与条目行的规范文档
  standards: { paths: ['M15 7h15l7 7v27H15z', 'M30 7v8h7', 'M20 22h12', 'M20 29h12', 'M20 36h7', 'M11 13v28'] },
  // infra论坛：带对话内容行的气泡
  forum: { paths: ['M10 13h22a6 6 0 0 1 6 6v7a6 6 0 0 1-6 6H21l-8 6v-6h-3z', 'M18 21h13', 'M18 27h8'] },
  // 中间件：上下两层服务之间的连接层
  middleware: { paths: ['M13 13h22v8H13z', 'M13 27h22v8H13z', 'M18 21v6', 'M30 21v6', 'M19 17h1', 'M19 31h1', 'M27 17h5', 'M27 31h5'] },
  // 数据库：经典的多层数据盘
  database: {
    ellipses: [[24, 12, 13, 5]],
    paths: ['M11 12v20c0 3 6 6 13 6s13-3 13-6V12', 'M11 22c0 3 6 6 13 6s13-3 13-6', 'M11 32c0 3 6 6 13 6s13-3 13-6']
  },
  // 网络：中心节点向四周互联的拓扑
  network: {
    circles: [[24, 24, 4], [12, 14, 4], [36, 14, 4], [12, 34, 4], [36, 34, 4]],
    paths: ['M15.5 16.5 20.5 21', 'M32.5 16.5 27.5 21', 'M15.5 31.5 20.5 27', 'M32.5 31.5 27.5 27']
  },
  // 主机：带指示灯与支脚的机架服务器
  host: {
    rects: [[12, 9, 24, 30, 3]],
    circles: [[31, 31, 2]],
    paths: ['M17 16h14', 'M17 23h14', 'M17 30h8', 'M19 39v4', 'M29 39v4']
  },
  // 网络安全：带校验勾与防护分区的盾牌
  'network-security': { paths: ['M24 7 37 12v10c0 9-5.5 15-13 19-7.5-4-13-10-13-19V12z', 'M18 24l4 4 8-9', 'M24 7v34'] }
}

// 兜底图标：模块标识未匹配时仍渲染一个通用方块，避免出现缺图
const FALLBACK_SHAPE = { rects: [[10, 10, 28, 28, 4]], paths: ['M18 24h12'] }

const props = defineProps({
  name: { type: String, required: true },
  label: { type: String, default: '' }
})

const shape = computed(() => MODULE_ICON_SHAPES[props.name] || FALLBACK_SHAPE)
</script>

<style scoped>
.module-icon {
  display: block;
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
