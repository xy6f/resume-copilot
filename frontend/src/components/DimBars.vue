<template>
  <div class="dims">
    <div v-for="(d, i) in rows" :key="d.name" class="dim-row">
      <span class="dim-name">{{ d.name }}</span>
      <div class="dim-bar">
        <div class="dim-fill" :class="{ cyanish: d.cyan }" :style="{ width: widths[i] + '%' }"></div>
      </div>
      <span class="dim-val">{{ vals[i] }}</span>
    </div>
  </div>
</template>

<script setup>
/* 五维条形动画：宽度扫过 + 数字滚动（旧版 dimsBars 迁入） */
import { nextTick, ref, watch } from 'vue'
import { countUp } from '../utils/countUp'

const props = defineProps({
  dimensions: { type: Object, default: () => ({}) },
})

const DEFS = [
  ['技能覆盖', 'skillCoverage', false],
  ['经历相关性', 'experienceRelevance', false],
  ['年限匹配', 'yearsFit', true],
  ['项目深度', 'projectDepth', true],
  ['表达质量', 'expressionQuality', true],
]
const rows = DEFS.map(([name, key, cyan]) => ({ name, key, cyan }))

const widths = ref(rows.map(() => 0))
const vals = ref(rows.map(() => 0))

watch(() => props.dimensions, async (dims) => {
  // 先归零渲染一帧，再扫到目标值——确保 CSS 过渡从头播放
  widths.value = rows.map(() => 0)
  vals.value = rows.map(() => 0)
  await nextTick()
  requestAnimationFrame(() => {
    rows.forEach((d, i) => {
      widths.value[i] = Number(dims?.[d.key] ?? 0)
    })
  })
  rows.forEach((d, i) => {
    countUp((x) => { vals.value[i] = Math.round(x) }, Number(dims?.[d.key] ?? 0), 1200, 300)
  })
}, { immediate: true, deep: true })
</script>
